package com.makd.afinity.data.repository.livetv

import androidx.core.net.toUri
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.extensions.toAfinityChannel
import com.makd.afinity.data.models.extensions.toAfinityItem
import com.makd.afinity.data.models.extensions.toAfinityProgram
import com.makd.afinity.data.models.livetv.AfinityChannel
import com.makd.afinity.data.models.livetv.AfinityProgram
import com.makd.afinity.data.models.livetv.AfinityRecording
import com.makd.afinity.data.models.livetv.ChannelFavoriteChange
import com.makd.afinity.data.models.livetv.ChannelSortOrder
import com.makd.afinity.data.models.livetv.ChannelType
import com.makd.afinity.data.models.livetv.LiveTvPlaybackInfo
import com.makd.afinity.data.models.livetv.LiveTvSimilarItem
import com.makd.afinity.data.models.livetv.ProgramCategory
import com.makd.afinity.data.models.livetv.RecordingPadding
import com.makd.afinity.data.models.livetv.SeriesRecordingOptions
import com.makd.afinity.data.models.livetv.SeriesRecordingRule
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.JellyfinApiInvoker
import com.makd.afinity.data.repository.NoActiveSessionException
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.userdata.UserDataRepository
import com.makd.afinity.data.websocket.JellyfinWebSocketManager
import com.makd.afinity.data.websocket.WebSocketState
import com.makd.afinity.di.ApplicationScope
import com.makd.afinity.di.NetworkModule
import com.makd.afinity.util.MediaCapabilities
import com.makd.afinity.util.redactUrl
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.operations.LibraryApi
import org.jellyfin.sdk.api.operations.LiveTvApi
import org.jellyfin.sdk.api.operations.MediaInfoApi
import org.jellyfin.sdk.api.operations.VideoApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.DirectPlayProfile
import org.jellyfin.sdk.model.api.DlnaProfileType
import org.jellyfin.sdk.model.api.EncodingContext
import org.jellyfin.sdk.model.api.GetProgramsDto
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaProtocol
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.OpenLiveStreamDto
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.RecordingStatus
import org.jellyfin.sdk.model.api.SeriesTimerInfoDto
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.jellyfin.sdk.model.api.SubtitleProfile
import org.jellyfin.sdk.model.api.TimerInfoDto
import org.jellyfin.sdk.model.api.TranscodingProfile
import timber.log.Timber

@Singleton
class JellyfinLiveTvRepository
@Inject
constructor(
    private val sessionManager: SessionManager,
    private val mediaRepository: MediaRepository,
    private val userDataRepository: UserDataRepository,
    private val apiInvoker: JellyfinApiInvoker,
    private val jellyfin: Jellyfin,
    private val preferencesRepository: PreferencesRepository,
    webSocketManager: JellyfinWebSocketManager,
    @ApplicationScope private val scope: CoroutineScope,
) : LiveTvRepository {

    override val timerChanges: Flow<Unit> =
        merge(
            webSocketManager.liveTvTimerEvents.map {},
            webSocketManager.connectionState
                .drop(1)
                .filter { it == WebSocketState.CONNECTED }
                .map {},
        )

    private companion object {
        val MPEGTS_VIDEO_CODECS = listOf("h264", "hevc", "mpeg2video", "mpeg4")
        val MPEGTS_AUDIO_CODECS = listOf("aac", "ac3", "eac3", "mp3", "mp2")
        const val DIRECT_PLAY_CONTAINERS = "ts,mpegts,mpeg,mpg,m2ts,mp4,mkv,mov,webm,avi"
        val TUNE_TIMEOUT = 30.seconds
        val TUNE_TRANSPORT_TIMEOUT = 3.minutes
        const val SECONDS_PER_MINUTE = 60
        const val SERIES_TIMER_SORT = "SortName"
        val SCHEDULED_STATUSES = setOf(RecordingStatus.NEW, RecordingStatus.IN_PROGRESS)
        val PROGRAM_KINDS =
            setOf(BaseItemKind.PROGRAM, BaseItemKind.LIVE_TV_PROGRAM, BaseItemKind.TV_PROGRAM)
    }

    private fun getBaseUrl(): String = sessionManager.getCurrentApiClient()?.baseUrl ?: ""

    private suspend fun <T> apiCall(
        default: T,
        errorMessage: String,
        block: suspend (apiClient: ApiClient, userId: UUID) -> T,
    ): T = apiInvoker.apiCall(default, errorMessage, block)

    private fun buildLiveTvDeviceProfile(maxBitrate: Int, useExoPlayer: Boolean): DeviceProfile {
        val nativeVideoCodecs = MediaCapabilities.getSupportedVideoCodecs()
        val nativeAudioCodecs = MediaCapabilities.getSupportedAudioCodecs()

        val nativeVideo = nativeVideoCodecs.split(",")
        val nativeAudio = nativeAudioCodecs.split(",")
        val hlsVideoCodecs =
            MPEGTS_VIDEO_CODECS.filter { !useExoPlayer || it in nativeVideo }
                .joinToString(",")
                .ifEmpty { "h264" }
        val hlsAudioCodecs =
            MPEGTS_AUDIO_CODECS.filter { !useExoPlayer || it == "mp2" || it in nativeAudio }
                .joinToString(",")
                .ifEmpty { "aac" }

        return DeviceProfile(
            name = "AFinity-LiveTV",
            maxStaticBitrate = maxBitrate,
            maxStreamingBitrate = maxBitrate,
            directPlayProfiles =
                listOf(
                    if (useExoPlayer) {
                        DirectPlayProfile(
                            type = DlnaProfileType.VIDEO,
                            container = DIRECT_PLAY_CONTAINERS,
                            videoCodec = nativeVideoCodecs,
                            audioCodec = nativeAudioCodecs,
                        )
                    } else {
                        DirectPlayProfile(
                            type = DlnaProfileType.VIDEO,
                            container = DIRECT_PLAY_CONTAINERS,
                        )
                    }
                ),
            transcodingProfiles =
                listOf(
                    TranscodingProfile(
                        type = DlnaProfileType.VIDEO,
                        context = EncodingContext.STREAMING,
                        protocol = MediaStreamProtocol.HLS,
                        container = "ts",
                        videoCodec = hlsVideoCodecs,
                        audioCodec = hlsAudioCodecs,
                        breakOnNonKeyFrames = false,
                        conditions = emptyList(),
                    )
                ),
            codecProfiles = emptyList(),
            containerProfiles = emptyList(),
            subtitleProfiles =
                listOf(
                    SubtitleProfile("vtt", SubtitleDeliveryMethod.HLS),
                    SubtitleProfile("webvtt", SubtitleDeliveryMethod.HLS),
                    SubtitleProfile("srt", SubtitleDeliveryMethod.EXTERNAL),
                ),
        )
    }

    override suspend fun getChannels(
        type: ChannelType?,
        isFavorite: Boolean?,
        isMovie: Boolean?,
        isSeries: Boolean?,
        isNews: Boolean?,
        isKids: Boolean?,
        isSports: Boolean?,
        limit: Int?,
    ): List<AfinityChannel> =
        getChannelsResult(
                type = type,
                isFavorite = isFavorite,
                isMovie = isMovie,
                isSeries = isSeries,
                isNews = isNews,
                isKids = isKids,
                isSports = isSports,
                limit = limit,
            )
            .getOrElse { e ->
                if (e !is NoActiveSessionException) Timber.e(e, "Failed to get Live TV channels")
                emptyList()
            }

    override suspend fun getChannelsResult(
        type: ChannelType?,
        isFavorite: Boolean?,
        isMovie: Boolean?,
        isSeries: Boolean?,
        isNews: Boolean?,
        isKids: Boolean?,
        isSports: Boolean?,
        limit: Int?,
        sortOrder: ChannelSortOrder,
        favoritesFirst: Boolean,
    ): Result<List<AfinityChannel>> =
        apiInvoker.apiResult { apiClient, userId ->
            val baseUrl = getBaseUrl()

            val response =
                LiveTvApi(apiClient)
                    .getLiveTvChannels(
                        userId = userId,
                        type =
                            type?.let {
                                when (it) {
                                    ChannelType.TV -> org.jellyfin.sdk.model.api.ChannelType.TV
                                    ChannelType.RADIO ->
                                        org.jellyfin.sdk.model.api.ChannelType.RADIO
                                }
                            },
                        isFavorite = isFavorite,
                        isMovie = isMovie,
                        isSeries = isSeries,
                        isNews = isNews,
                        isKids = isKids,
                        isSports = isSports,
                        limit = limit,
                        sortBy =
                            listOf(
                                when (sortOrder) {
                                    ChannelSortOrder.NUMBER -> ItemSortBy.SORT_NAME
                                    ChannelSortOrder.NAME -> ItemSortBy.NAME
                                    ChannelSortOrder.RECENTLY_WATCHED -> ItemSortBy.DATE_PLAYED
                                }
                            ),
                        sortOrder =
                            if (sortOrder == ChannelSortOrder.RECENTLY_WATCHED) SortOrder.DESCENDING
                            else SortOrder.ASCENDING,
                        enableFavoriteSorting = favoritesFirst,
                        enableImages = true,
                        imageTypeLimit = 1,
                        enableImageTypes = listOf(ImageType.PRIMARY, ImageType.THUMB),
                        fields = listOf(ItemFields.CHANNEL_INFO),
                        addCurrentProgram = true,
                    )

            response.content.items.mapNotNull { channelDto ->
                val currentProgram = channelDto.currentProgram?.toAfinityProgram(baseUrl)
                channelDto.toAfinityChannel(baseUrl, currentProgram)
            }
        }

    override suspend fun getChannel(channelId: UUID): AfinityChannel? =
        getChannelResult(channelId).getOrElse { e ->
            if (e !is NoActiveSessionException) Timber.e(e, "Failed to get channel: $channelId")
            null
        }

    override suspend fun getChannelResult(channelId: UUID): Result<AfinityChannel> =
        apiInvoker.apiResult { apiClient, _ ->
            val baseUrl = getBaseUrl()
            val channelDto = LiveTvApi(apiClient).getChannel(channelId).content
            val currentProgram = channelDto.currentProgram?.toAfinityProgram(baseUrl)
            channelDto.toAfinityChannel(baseUrl, currentProgram)
        }

    override suspend fun getChannelScheduleResult(channelId: UUID): Result<List<AfinityProgram>> =
        apiInvoker.apiResult { apiClient, userId ->
            val baseUrl = getBaseUrl()
            LiveTvApi(apiClient)
                .getLiveTvPrograms(
                    channelIds = listOf(channelId),
                    userId = userId,
                    hasAired = false,
                    sortBy = listOf(ItemSortBy.START_DATE),
                    sortOrder = listOf(SortOrder.ASCENDING),
                    enableImages = false,
                    fields = FieldSets.LIVE_TV_PROGRAM_SCHEDULE,
                    enableTotalRecordCount = false,
                )
                .content
                .items
                .map { programDto -> programDto.toAfinityProgram(baseUrl) }
        }

    override suspend fun getSimilarToProgram(programId: UUID, limit: Int): List<LiveTvSimilarItem> =
        apiCall(emptyList(), "Failed to get items similar to program $programId") {
            apiClient,
            userId ->
            val baseUrl = getBaseUrl()
            LibraryApi(apiClient)
                .getSimilarItems(
                    itemId = programId,
                    userId = userId,
                    limit = limit,
                    fields = FieldSets.LIVE_TV_PROGRAM_CARD + FieldSets.SIMILAR_ITEMS,
                )
                .content
                .items
                .mapNotNull { dto ->
                    if (dto.type in PROGRAM_KINDS) {
                        LiveTvSimilarItem.Program(dto.toAfinityProgram(baseUrl))
                    } else {
                        dto.toAfinityItem(baseUrl)?.let { LiveTvSimilarItem.Library(it) }
                    }
                }
        }

    override suspend fun scheduleRecordingResult(programId: UUID): Result<Unit> =
        apiInvoker.apiResult { apiClient, _ ->
            val api = LiveTvApi(apiClient)
            val defaults = api.getDefaultTimer(programId = programId.toString()).content
            api.createTimer(defaults.toTimerInfo())
        }

    override suspend fun scheduleSeriesRecordingResult(programId: UUID): Result<Unit> =
        apiInvoker.apiResult { apiClient, _ ->
            val api = LiveTvApi(apiClient)
            val defaults = api.getDefaultTimer(programId = programId.toString()).content
            api.createSeriesTimer(defaults)
        }

    override suspend fun cancelSeriesRecordingResult(seriesTimerId: String): Result<Unit> =
        apiInvoker.apiResult { apiClient, _ ->
            LiveTvApi(apiClient).cancelSeriesTimer(timerId = seriesTimerId)
        }

    override suspend fun cancelRecordingResult(timerId: String): Result<Unit> =
        apiInvoker.apiResult { apiClient, _ ->
            LiveTvApi(apiClient).cancelTimer(timerId = timerId)
        }

    override suspend fun getRecordingPaddingResult(timerId: String): Result<RecordingPadding> =
        apiInvoker.apiResult { apiClient, _ ->
            val timer = LiveTvApi(apiClient).getTimer(timerId = timerId).content
            RecordingPadding(
                prePaddingMinutes = (timer.prePaddingSeconds ?: 0) / SECONDS_PER_MINUTE,
                postPaddingMinutes = (timer.postPaddingSeconds ?: 0) / SECONDS_PER_MINUTE,
            )
        }

    override suspend fun updateRecordingPaddingResult(
        timerId: String,
        padding: RecordingPadding,
    ): Result<Unit> =
        apiInvoker.apiResult { apiClient, _ ->
            val api = LiveTvApi(apiClient)
            val timer = api.getTimer(timerId = timerId).content
            api.updateTimer(
                timerId = timerId,
                data =
                    timer.copy(
                        prePaddingSeconds = padding.prePaddingMinutes * SECONDS_PER_MINUTE,
                        postPaddingSeconds = padding.postPaddingMinutes * SECONDS_PER_MINUTE,
                    ),
            )
        }

    override suspend fun getSeriesRecordingOptionsResult(
        seriesTimerId: String
    ): Result<SeriesRecordingOptions> =
        apiInvoker.apiResult { apiClient, _ ->
            LiveTvApi(apiClient)
                .getSeriesTimer(timerId = seriesTimerId)
                .content
                .toSeriesRecordingOptions()
        }

    override suspend fun getScheduledRecordingsResult(): Result<List<AfinityProgram>> =
        apiInvoker.apiResult { apiClient, _ ->
            val baseUrl = getBaseUrl()
            LiveTvApi(apiClient)
                .getTimers()
                .content
                .items
                .filter { it.status in SCHEDULED_STATUSES }
                .mapNotNull { timer -> timer.programInfo?.toAfinityProgram(baseUrl) }
                .sortedBy { it.startDate }
        }

    override suspend fun getSeriesRecordingRulesResult(): Result<List<SeriesRecordingRule>> =
        apiInvoker.apiResult { apiClient, _ ->
            val baseUrl = getBaseUrl()
            LiveTvApi(apiClient)
                .getSeriesTimers(sortBy = SERIES_TIMER_SORT, sortOrder = SortOrder.ASCENDING)
                .content
                .items
                .mapNotNull { timer ->
                    val id = timer.id ?: return@mapNotNull null
                    SeriesRecordingRule(
                        id = id,
                        name = timer.name.orEmpty(),
                        imageUrl =
                            imageUrl(
                                baseUrl = baseUrl,
                                itemId = timer.parentPrimaryImageItemId?.toString(),
                                type = ImageType.PRIMARY,
                                tag = timer.parentPrimaryImageTag,
                            )
                                ?: imageUrl(
                                    baseUrl = baseUrl,
                                    itemId = timer.parentThumbItemId,
                                    type = ImageType.THUMB,
                                    tag = timer.parentThumbImageTag,
                                ),
                        options = timer.toSeriesRecordingOptions(),
                    )
                }
        }

    override suspend fun getRecordingsResult(): Result<List<AfinityRecording>> =
        apiInvoker.apiResult { apiClient, userId ->
            val baseUrl = getBaseUrl()
            val liveTvApi = LiveTvApi(apiClient)
            val libraryApi = LibraryApi(apiClient)
            coroutineScope {
                val inProgress = async {
                    liveTvApi
                        .getRecordings(
                            userId = userId,
                            isInProgress = true,
                            enableImages = false,
                            enableUserData = false,
                            enableTotalRecordCount = false,
                        )
                        .content
                        .items
                        .map { it.id }
                        .toSet()
                }
                val folders = liveTvApi.getRecordingFolders(userId = userId).content.items
                val recorded =
                    folders
                        .map { folder ->
                            async {
                                libraryApi
                                    .getItems(
                                        userId = userId,
                                        parentId = folder.id,
                                        recursive = true,
                                        mediaTypes = listOf(MediaType.VIDEO),
                                        sortBy = listOf(ItemSortBy.DATE_CREATED),
                                        sortOrder = listOf(SortOrder.DESCENDING),
                                        fields = FieldSets.LIVE_TV_RECORDINGS,
                                        enableImageTypes =
                                            listOf(ImageType.PRIMARY, ImageType.THUMB, ImageType.BACKDROP),
                                        imageTypeLimit = 1,
                                        enableTotalRecordCount = false,
                                    )
                                    .content
                                    .items
                            }
                        }
                        .awaitAll()
                        .flatten()
                        .distinctBy { it.id }
                val inProgressIds = inProgress.await()
                recorded
                    .filter { it.id !in inProgressIds }
                    .sortedByDescending { it.dateCreated }
                    .mapNotNull { dto ->
                        dto.toAfinityItem(baseUrl)?.let { item ->
                            AfinityRecording(
                                item = item,
                                recordedAt = dto.dateCreated,
                                canDelete = dto.canDelete == true,
                            )
                        }
                    }
            }
        }

    private fun imageUrl(baseUrl: String, itemId: String?, type: ImageType, tag: String?): String? {
        if (itemId == null || tag == null) return null
        return baseUrl
            .toUri()
            .buildUpon()
            .appendEncodedPath("Items/$itemId/Images/${type.serialName}")
            .appendQueryParameter("tag", tag)
            .build()
            .toString()
    }

    private fun SeriesTimerInfoDto.toSeriesRecordingOptions(): SeriesRecordingOptions =
        SeriesRecordingOptions(
            recordNewOnly = recordNewOnly == true,
            skipEpisodesInLibrary = skipEpisodesInLibrary == true,
            recordAnyChannel = recordAnyChannel == true,
            recordAnyTime = recordAnyTime == true,
            keepUpTo = keepUpTo ?: 0,
            padding =
                RecordingPadding(
                    prePaddingMinutes = (prePaddingSeconds ?: 0) / SECONDS_PER_MINUTE,
                    postPaddingMinutes = (postPaddingSeconds ?: 0) / SECONDS_PER_MINUTE,
                ),
            channelName = channelName,
            startDate = startDate,
        )

    override suspend fun updateSeriesRecordingOptionsResult(
        seriesTimerId: String,
        options: SeriesRecordingOptions,
    ): Result<Unit> =
        apiInvoker.apiResult { apiClient, _ ->
            val api = LiveTvApi(apiClient)
            val timer = api.getSeriesTimer(timerId = seriesTimerId).content
            api.updateSeriesTimer(
                timerId = seriesTimerId,
                data =
                    timer.copy(
                        recordNewOnly = options.recordNewOnly,
                        skipEpisodesInLibrary = options.skipEpisodesInLibrary,
                        recordAnyChannel = options.recordAnyChannel,
                        recordAnyTime = options.recordAnyTime,
                        keepUpTo = options.keepUpTo,
                        prePaddingSeconds = options.padding.prePaddingMinutes * SECONDS_PER_MINUTE,
                        postPaddingSeconds =
                            options.padding.postPaddingMinutes * SECONDS_PER_MINUTE,
                    ),
            )
        }

    private fun SeriesTimerInfoDto.toTimerInfo(): TimerInfoDto =
        TimerInfoDto(
            id = id,
            serverId = serverId,
            externalId = externalId,
            channelId = channelId,
            externalChannelId = externalChannelId,
            channelName = channelName,
            channelPrimaryImageTag = channelPrimaryImageTag,
            programId = programId,
            externalProgramId = externalProgramId,
            name = name,
            overview = overview,
            startDate = startDate,
            endDate = endDate,
            serviceName = serviceName,
            priority = priority,
            prePaddingSeconds = prePaddingSeconds,
            postPaddingSeconds = postPaddingSeconds,
            isPrePaddingRequired = isPrePaddingRequired,
            parentBackdropItemId = parentBackdropItemId,
            parentBackdropImageTags = parentBackdropImageTags,
            isPostPaddingRequired = isPostPaddingRequired,
            keepUntil = keepUntil,
        )

    override suspend fun getProgramResult(programId: UUID): Result<AfinityProgram> =
        apiInvoker.apiResult { apiClient, userId ->
            LiveTvApi(apiClient)
                .getProgram(programId = programId.toString(), userId = userId)
                .content
                .toAfinityProgram(getBaseUrl())
        }

    override suspend fun getOnNowResult(limit: Int): Result<List<AfinityProgram>> =
        apiInvoker.apiResult { apiClient, userId ->
            val baseUrl = getBaseUrl()
            LiveTvApi(apiClient)
                .getRecommendedPrograms(
                    userId = userId,
                    limit = limit,
                    isAiring = true,
                    enableImages = true,
                    imageTypeLimit = 1,
                    enableImageTypes =
                        listOf(ImageType.PRIMARY, ImageType.THUMB, ImageType.BACKDROP),
                    fields = FieldSets.LIVE_TV_PROGRAM_CARD,
                    enableTotalRecordCount = false,
                )
                .content
                .items
                .map { programDto -> programDto.toAfinityProgram(baseUrl) }
        }

    override suspend fun getUpcomingResult(
        category: ProgramCategory,
        limit: Int,
    ): Result<List<AfinityProgram>> =
        apiInvoker.apiResult { apiClient, userId ->
            val baseUrl = getBaseUrl()
            val isShows = category == ProgramCategory.SHOWS
            LiveTvApi(apiClient)
                .getLiveTvPrograms(
                    userId = userId,
                    hasAired = false,
                    isSeries = if (isShows) true else null,
                    isMovie =
                        when (category) {
                            ProgramCategory.MOVIES -> true
                            ProgramCategory.SHOWS -> false
                            else -> null
                        },
                    isSports =
                        when (category) {
                            ProgramCategory.SPORTS -> true
                            ProgramCategory.SHOWS -> false
                            else -> null
                        },
                    isKids =
                        when (category) {
                            ProgramCategory.KIDS -> true
                            ProgramCategory.SHOWS -> false
                            else -> null
                        },
                    isNews =
                        when (category) {
                            ProgramCategory.NEWS -> true
                            ProgramCategory.SHOWS -> false
                            else -> null
                        },
                    limit = limit,
                    sortBy = listOf(ItemSortBy.START_DATE),
                    sortOrder = listOf(SortOrder.ASCENDING),
                    enableImages = true,
                    imageTypeLimit = 1,
                    enableImageTypes =
                        listOf(ImageType.PRIMARY, ImageType.THUMB, ImageType.BACKDROP),
                    fields = FieldSets.LIVE_TV_PROGRAM_CARD,
                    enableTotalRecordCount = false,
                )
                .content
                .items
                .map { programDto -> programDto.toAfinityProgram(baseUrl) }
        }

    override suspend fun getGuideDataEnd(): LocalDateTime? =
        apiCall(null, "Failed to get the end of the guide data") { apiClient, userId ->
            LiveTvApi(apiClient)
                .getLiveTvPrograms(
                    userId = userId,
                    limit = 1,
                    sortBy = listOf(ItemSortBy.START_DATE),
                    sortOrder = listOf(SortOrder.DESCENDING),
                    enableImages = false,
                    fields = FieldSets.LIVE_TV_GUIDE,
                    enableTotalRecordCount = false,
                )
                .content
                .items
                .firstOrNull()
                ?.endDate
        }

    override suspend fun getGuidePrograms(
        channelIds: List<UUID>,
        windowStart: LocalDateTime,
        windowEnd: LocalDateTime,
    ): List<AfinityProgram> =
        getGuideProgramsResult(channelIds, windowStart, windowEnd).getOrElse { e ->
            if (e !is NoActiveSessionException) Timber.e(e, "Failed to get guide programs")
            emptyList()
        }

    override suspend fun getGuideProgramsResult(
        channelIds: List<UUID>,
        windowStart: LocalDateTime,
        windowEnd: LocalDateTime,
    ): Result<List<AfinityProgram>> =
        apiInvoker.apiResult { apiClient, _ ->
            val baseUrl = getBaseUrl()

            LiveTvApi(apiClient)
                .getPrograms(
                    GetProgramsDto(
                        channelIds = channelIds,
                        minEndDate = windowStart,
                        maxStartDate = windowEnd,
                        sortBy = listOf(ItemSortBy.START_DATE),
                        sortOrder = listOf(SortOrder.ASCENDING),
                        enableImages = false,
                        enableTotalRecordCount = false,
                        fields = emptyList(),
                    )
                )
                .content
                .items
                .map { programDto -> programDto.toAfinityProgram(baseUrl) }
        }

    override suspend fun getChannelPlaybackInfo(
        channelId: UUID,
        allowDirectPlay: Boolean,
    ): LiveTvPlaybackInfo? {
        val negotiation = scope.async { negotiateChannelPlayback(channelId, allowDirectPlay) }
        val answered =
            try {
                withTimeoutOrNull(TUNE_TIMEOUT) { negotiation.join() } != null
            } catch (e: CancellationException) {
                releaseWhenAnswered(negotiation)
                throw e
            }
        if (!answered) {
            Timber.w("Live TV tune for $channelId did not answer within $TUNE_TIMEOUT")
            releaseWhenAnswered(negotiation)
            return null
        }
        return negotiation.await()
    }

    private fun releaseWhenAnswered(negotiation: Deferred<LiveTvPlaybackInfo?>) {
        scope.launch {
            val liveStreamId = negotiation.await()?.liveStreamId ?: return@launch
            Timber.d("Live TV tune answered after its caller gave up, closing $liveStreamId")
            closeLiveStream(liveStreamId)
        }
    }

    override suspend fun closeLiveStream(liveStreamId: String) =
        apiCall<Unit>(Unit, "Failed to close live stream: $liveStreamId") { apiClient, _ ->
            MediaInfoApi(apiClient).closeLiveStream(liveStreamId = liveStreamId)
        }

    private suspend fun negotiateChannelPlayback(
        channelId: UUID,
        allowDirectPlay: Boolean,
    ): LiveTvPlaybackInfo? =
        apiCall(null, "Failed to get stream URL for channel: $channelId") { apiClient, userId ->
            val baseUrl = getBaseUrl()
            if (baseUrl.isBlank()) {
                Timber.e("Missing baseUrl")
                return@apiCall null
            }

            val tuneClient = tuneApiClient(apiClient)
            val mediaInfoApi = MediaInfoApi(tuneClient)
            val videoApi = VideoApi(apiClient)
            val maxStreamingBitrate = 140_000_000
            val deviceProfile =
                buildLiveTvDeviceProfile(
                    maxStreamingBitrate,
                    preferencesRepository.useExoPlayer.first(),
                )

            val playbackInfoDto =
                PlaybackInfoDto(
                    userId = userId,
                    maxStreamingBitrate = maxStreamingBitrate,
                    enableDirectPlay = allowDirectPlay,
                    enableDirectStream = allowDirectPlay,
                    enableTranscoding = true,
                    allowVideoStreamCopy = true,
                    allowAudioStreamCopy = true,
                    autoOpenLiveStream = true,
                    deviceProfile = deviceProfile,
                )

            val playbackResponse =
                mediaInfoApi.getPostedPlaybackInfo(itemId = channelId, data = playbackInfoDto)
            val playbackInfo = playbackResponse.content

            Timber.d(
                "PlaybackInfo: playSessionId=${playbackInfo.playSessionId}, mediaSources=${playbackInfo.mediaSources.size}"
            )

            val sources = playbackInfo.mediaSources
            if (sources.isEmpty()) {
                Timber.e("PlaybackInfo returned no media sources")
                return@apiCall null
            }

            val selected =
                sources.firstOrNull { it.supportsDirectPlay }
                    ?: sources.firstOrNull { it.supportsDirectStream }
                    ?: sources.first()

            val source =
                selected.openToken
                    ?.takeIf { selected.liveStreamId == null }
                    ?.let { token ->
                        try {
                            Timber.d("Opening live stream for channel $channelId")
                            mediaInfoApi
                                .openLiveStream(
                                    data =
                                        OpenLiveStreamDto(
                                            openToken = token,
                                            userId = userId,
                                            playSessionId = playbackInfo.playSessionId,
                                            maxStreamingBitrate = maxStreamingBitrate,
                                            itemId = channelId,
                                            enableDirectPlay = allowDirectPlay,
                                            enableDirectStream = allowDirectPlay,
                                            deviceProfile = deviceProfile,
                                            directPlayProtocols = listOf(MediaProtocol.HTTP),
                                        )
                                )
                                .content
                                .mediaSource
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to open live stream for channel $channelId")
                            null
                        }
                    } ?: selected

            val liveStreamId = source.liveStreamId
            val mediaSourceId = source.id ?: channelId.toString()
            val playSessionId =
                playbackInfo.playSessionId ?: UUID.randomUUID().toString().replace("-", "")
            val transcodingUrl = source.transcodingUrl

            Timber.d(
                "Source: id=$mediaSourceId, liveStreamId=$liveStreamId, protocol=${source.protocol}, isRemote=${source.isRemote}, container=${source.container}, supportsDirectPlay=${source.supportsDirectPlay}, supportsDirectStream=${source.supportsDirectStream}, supportsTranscoding=${source.supportsTranscoding}, hasTranscodingUrl=${!transcodingUrl.isNullOrBlank()}"
            )

            val playMethod: PlayMethod
            val streamUrl: String
            var container: String = source.container ?: "ts"

            when {
                allowDirectPlay && (source.supportsDirectPlay || source.supportsDirectStream) -> {
                    playMethod =
                        if (source.supportsDirectPlay) PlayMethod.DIRECT_PLAY
                        else PlayMethod.DIRECT_STREAM
                    streamUrl =
                        videoApi.getVideoStreamUrl(
                            itemId = channelId,
                            container = container,
                            static = true,
                            tag = source.eTag,
                            mediaSourceId = mediaSourceId,
                            liveStreamId = liveStreamId,
                            playSessionId = playSessionId,
                        )
                }

                source.supportsTranscoding && !transcodingUrl.isNullOrBlank() -> {
                    playMethod = PlayMethod.TRANSCODE
                    container = source.transcodingContainer ?: container
                    streamUrl = apiClient.createUrl(transcodingUrl, ignorePathParameters = true)
                }
                else -> {
                    Timber.e("No playable stream for channel $channelId")
                    if (liveStreamId != null) {
                        MediaInfoApi(apiClient).closeLiveStream(liveStreamId = liveStreamId)
                    }
                    return@apiCall null
                }
            }

            Timber.d("Selected Live TV stream: method=$playMethod, url=${redactUrl(streamUrl)}")
            LiveTvPlaybackInfo(
                streamUrl = streamUrl,
                mediaSourceId = mediaSourceId,
                playSessionId = playSessionId,
                liveStreamId = liveStreamId,
                playMethod = playMethod.serialName,
                container = container,
            )
        }

    private fun tuneApiClient(source: ApiClient): ApiClient =
        try {
            jellyfin.createApi(
                baseUrl = source.baseUrl,
                accessToken = source.accessToken,
                httpClientOptions =
                    NetworkModule.JELLYFIN_HTTP_OPTIONS.copy(
                        requestTimeout = TUNE_TRANSPORT_TIMEOUT,
                        socketTimeout = TUNE_TRANSPORT_TIMEOUT,
                    ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to build Live TV tune client, falling back to session client")
            source
        }

    private val _channelFavoriteChanges =
        MutableSharedFlow<ChannelFavoriteChange>(extraBufferCapacity = 16)
    override val channelFavoriteChanges: SharedFlow<ChannelFavoriteChange> =
        _channelFavoriteChanges.asSharedFlow()

    override suspend fun toggleChannelFavorite(channelId: UUID): Boolean =
        apiCall(false, "Failed to toggle favorite for channel: $channelId") { _, _ ->
            val channel = getChannel(channelId) ?: return@apiCall false
            val favorite = !channel.favorite
            val changed =
                if (favorite) userDataRepository.addToFavorites(channelId)
                else userDataRepository.removeFromFavorites(channelId)
            if (changed) _channelFavoriteChanges.emit(ChannelFavoriteChange(channelId, favorite))
            changed
        }

    override val hasLiveTvAccessFlow: Flow<Boolean> =
        sessionManager.currentSession
            .combine(mediaRepository.hasLiveTvLibrary) { session, hasLibrary ->
                session != null && session.canAccessLiveTv != false && hasLibrary == true
            }
            .distinctUntilChanged()

    override suspend fun hasLiveTvAccessResult(): Result<Boolean> {
        if (sessionManager.currentSession.value?.canAccessLiveTv == false) {
            return Result.success(false)
        }
        mediaRepository.hasLiveTvLibrary.first()?.let {
            return Result.success(it)
        }
        return mediaRepository.getLibrariesResult().map {
            mediaRepository.hasLiveTvLibrary.first() ?: false
        }
    }
}
