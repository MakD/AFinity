package com.makd.afinity.data.repository.livetv

import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.extensions.toAfinityChannel
import com.makd.afinity.data.models.extensions.toAfinityProgram
import com.makd.afinity.data.models.livetv.AfinityChannel
import com.makd.afinity.data.models.livetv.AfinityProgram
import com.makd.afinity.data.models.livetv.ChannelType
import com.makd.afinity.data.models.livetv.LiveTvPlaybackInfo
import com.makd.afinity.data.repository.JellyfinApiInvoker
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.userdata.UserDataRepository
import com.makd.afinity.di.NetworkModule
import com.makd.afinity.util.MediaCapabilities
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.operations.LiveTvApi
import org.jellyfin.sdk.api.operations.MediaInfoApi
import org.jellyfin.sdk.api.operations.UserViewsApi
import org.jellyfin.sdk.api.operations.VideosApi
import org.jellyfin.sdk.model.api.CollectionType
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.DirectPlayProfile
import org.jellyfin.sdk.model.api.DlnaProfileType
import org.jellyfin.sdk.model.api.EncodingContext
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.MediaProtocol
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.OpenLiveStreamDto
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.jellyfin.sdk.model.api.SubtitleProfile
import org.jellyfin.sdk.model.api.TranscodingProfile
import timber.log.Timber
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration

@Singleton
class JellyfinLiveTvRepository
@Inject
constructor(
    private val sessionManager: SessionManager,
    private val mediaRepository: MediaRepository,
    private val userDataRepository: UserDataRepository,
    private val apiInvoker: JellyfinApiInvoker,
    private val jellyfin: Jellyfin,
) : LiveTvRepository {

    private companion object {
        val MPEGTS_VIDEO_CODECS = listOf("h264", "hevc", "mpeg2video", "mpeg4")
        val MPEGTS_AUDIO_CODECS = listOf("aac", "ac3", "eac3", "mp3", "mp2")
    }

    private fun getBaseUrl(): String = sessionManager.getCurrentApiClient()?.baseUrl ?: ""

    private suspend fun <T> apiCall(
        default: T,
        errorMessage: String,
        block: suspend (apiClient: ApiClient, userId: UUID) -> T,
    ): T = apiInvoker.apiCall(default, errorMessage, block)

    private fun buildLiveTvDeviceProfile(maxBitrate: Int): DeviceProfile {
        val nativeVideoCodecs = MediaCapabilities.getSupportedVideoCodecs()
        val nativeAudioCodecs = MediaCapabilities.getSupportedAudioCodecs()

        val nativeVideo = nativeVideoCodecs.split(",")
        val nativeAudio = nativeAudioCodecs.split(",")
        val hlsVideoCodecs =
            MPEGTS_VIDEO_CODECS.filter { it in nativeVideo }.joinToString(",").ifEmpty { "h264" }
        val hlsAudioCodecs =
            MPEGTS_AUDIO_CODECS.filter { it == "mp2" || it in nativeAudio }
                .joinToString(",")
                .ifEmpty { "aac" }

        return DeviceProfile(
            name = "AFinity-LiveTV",
            maxStaticBitrate = maxBitrate,
            maxStreamingBitrate = maxBitrate,
            directPlayProfiles =
                listOf(
                    DirectPlayProfile(
                        type = DlnaProfileType.VIDEO,
                        container = "ts,mpegts,hls,mpeg,mpg,m2ts,mp4,mkv,mov,webm,avi",
                        videoCodec = nativeVideoCodecs,
                        audioCodec = nativeAudioCodecs,
                    )
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
        apiCall(emptyList(), "Failed to get Live TV channels") { apiClient, _ ->
            val baseUrl = getBaseUrl()

            val response =
                LiveTvApi(apiClient)
                    .getLiveTvChannels(
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
                        sortBy = listOf(ItemSortBy.SORT_NAME),
                        sortOrder = SortOrder.ASCENDING,
                        enableImages = true,
                        imageTypeLimit = 1,
                        enableImageTypes = listOf(ImageType.PRIMARY, ImageType.THUMB),
                        fields = listOf(ItemFields.CHANNEL_INFO),
                        addCurrentProgram = true,
                    )

            response.content.items?.mapNotNull { channelDto ->
                val currentProgram = channelDto.currentProgram?.toAfinityProgram(baseUrl)
                channelDto.toAfinityChannel(baseUrl, currentProgram)
            } ?: emptyList()
        }

    override suspend fun getChannel(channelId: UUID): AfinityChannel? =
        apiCall(null, "Failed to get channel: $channelId") { apiClient, _ ->
            val baseUrl = getBaseUrl()
            val channelDto = LiveTvApi(apiClient).getChannel(channelId).content
            val currentProgram = channelDto.currentProgram?.toAfinityProgram(baseUrl)
            channelDto.toAfinityChannel(baseUrl, currentProgram)
        }

    override suspend fun getPrograms(
        channelIds: List<UUID>?,
        minStartDate: LocalDateTime?,
        maxStartDate: LocalDateTime?,
        minEndDate: LocalDateTime?,
        maxEndDate: LocalDateTime?,
        hasAired: Boolean?,
        isMovie: Boolean?,
        isSeries: Boolean?,
        isNews: Boolean?,
        isKids: Boolean?,
        isSports: Boolean?,
        limit: Int?,
    ): List<AfinityProgram> =
        apiCall(emptyList(), "Failed to get programs") { apiClient, _ ->
            val baseUrl = getBaseUrl()

            val response =
                LiveTvApi(apiClient)
                    .getLiveTvPrograms(
                        channelIds = channelIds,
                        minStartDate = minStartDate,
                        maxStartDate = maxStartDate,
                        minEndDate = minEndDate,
                        maxEndDate = maxEndDate,
                        hasAired = hasAired,
                        isMovie = isMovie,
                        isSeries = isSeries,
                        isNews = isNews,
                        isKids = isKids,
                        isSports = isSports,
                        limit = limit,
                        sortBy = listOf(ItemSortBy.START_DATE),
                        sortOrder = listOf(SortOrder.ASCENDING),
                        enableImages = true,
                        imageTypeLimit = 1,
                        enableImageTypes =
                            listOf(ImageType.PRIMARY, ImageType.THUMB, ImageType.BACKDROP),
                        fields =
                            listOf(ItemFields.OVERVIEW, ItemFields.GENRES, ItemFields.CHANNEL_INFO),
                    )

            response.content.items?.map { programDto -> programDto.toAfinityProgram(baseUrl) }
                ?: emptyList()
        }

    override suspend fun getCurrentProgram(channelId: UUID): AfinityProgram? {
        val now = LocalDateTime.now()
        return getPrograms(
                channelIds = listOf(channelId),
                maxStartDate = now,
                minEndDate = now,
                limit = 1,
            )
            .firstOrNull()
    }

    override suspend fun getRecommendedPrograms(
        isAiring: Boolean,
        limit: Int,
    ): List<AfinityProgram> =
        apiCall(emptyList(), "Failed to get recommended programs") { apiClient, _ ->
            val baseUrl = getBaseUrl()
            LiveTvApi(apiClient)
                .getRecommendedPrograms(
                    isAiring = isAiring,
                    limit = limit,
                    enableImages = true,
                    imageTypeLimit = 1,
                    enableImageTypes =
                        listOf(ImageType.PRIMARY, ImageType.THUMB, ImageType.BACKDROP),
                    fields = listOf(ItemFields.OVERVIEW, ItemFields.GENRES),
                )
                .content
                .items
                ?.map { programDto -> programDto.toAfinityProgram(baseUrl) } ?: emptyList()
        }

    override suspend fun getChannelPlaybackInfo(
        channelId: UUID,
        forceDirectPlay: Boolean,
    ): LiveTvPlaybackInfo? =
        apiCall(null, "Failed to get stream URL for channel: $channelId") { apiClient, userId ->
            val baseUrl = getBaseUrl()
            if (baseUrl.isBlank()) {
                Timber.e("Missing baseUrl")
                return@apiCall null
            }

            val untimedClient = untimedApiClient(apiClient)
            val mediaInfoApi = MediaInfoApi(untimedClient)
            val videosApi = VideosApi(apiClient)
            val maxStreamingBitrate = 140_000_000
            val deviceProfile = buildLiveTvDeviceProfile(maxStreamingBitrate)

            val playbackInfoDto =
                PlaybackInfoDto(
                    userId = userId,
                    maxStreamingBitrate = maxStreamingBitrate,
                    enableDirectPlay = true,
                    enableDirectStream = true,
                    enableTranscoding = true,
                    allowVideoStreamCopy = true,
                    allowAudioStreamCopy = true,
                    autoOpenLiveStream = false,
                    deviceProfile = deviceProfile,
                )

            val playbackResponse =
                mediaInfoApi.getPostedPlaybackInfo(itemId = channelId, data = playbackInfoDto)
            val playbackInfo = playbackResponse.content

            Timber.d(
                "PlaybackInfo: playSessionId=${playbackInfo.playSessionId}, mediaSources=${playbackInfo.mediaSources?.size ?: 0}"
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
                            MediaInfoApi(untimedClient)
                                .openLiveStream(
                                    data =
                                        OpenLiveStreamDto(
                                            openToken = token,
                                            userId = userId,
                                            playSessionId = playbackInfo.playSessionId,
                                            maxStreamingBitrate = maxStreamingBitrate,
                                            itemId = channelId,
                                            enableDirectPlay = true,
                                            enableDirectStream = true,
                                            deviceProfile = deviceProfile,
                                            directPlayProtocols = listOf(MediaProtocol.HTTP),
                                        )
                                )
                                .content
                                .mediaSource
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to open live stream for channel $channelId")
                            null
                        }
                    } ?: selected

            val liveStreamId = source.liveStreamId
            val mediaSourceId = source.id ?: channelId.toString()
            val playSessionId =
                playbackInfo.playSessionId ?: UUID.randomUUID().toString().replace("-", "")
            val directStreamPath = source.path

            Timber.d(
                "Source: id=$mediaSourceId, liveStreamId=$liveStreamId, path=$directStreamPath, supportsDirectPlay=${source.supportsDirectPlay}, supportsDirectStream=${source.supportsDirectStream}, transcodingUrl=${source.transcodingUrl}"
            )

            val playMethod: PlayMethod
            val streamUrl: String
            var container: String = source.container ?: "ts"

            when {
                source.supportsDirectPlay || forceDirectPlay -> {
                    if (!source.supportsDirectPlay) {
                        Timber.d("Server did not offer direct play, forcing it for $channelId")
                    }
                    playMethod = PlayMethod.DIRECT_PLAY
                    streamUrl =
                        if (source.isRemote && !directStreamPath.isNullOrBlank()) {
                            directStreamPath
                        } else {
                            videosApi.getVideoStreamUrl(
                                itemId = channelId,
                                container = container,
                                static = true,
                                tag = source.eTag,
                                mediaSourceId = mediaSourceId,
                                liveStreamId = liveStreamId,
                                playSessionId = playSessionId,
                            )
                        }
                }
                source.supportsDirectStream && !source.transcodingUrl.isNullOrBlank() -> {
                    playMethod = PlayMethod.DIRECT_STREAM
                    container = source.transcodingContainer ?: container
                    streamUrl =
                        apiClient.createUrl(source.transcodingUrl!!, ignorePathParameters = true)
                }
                source.supportsTranscoding && !source.transcodingUrl.isNullOrBlank() -> {
                    playMethod = PlayMethod.TRANSCODE
                    container = source.transcodingContainer ?: container
                    streamUrl =
                        apiClient.createUrl(source.transcodingUrl!!, ignorePathParameters = true)
                }
                else -> {
                    Timber.e("No playable stream for channel $channelId")
                    return@apiCall null
                }
            }

            Timber.d("Selected Live TV stream: method=$playMethod, url=$streamUrl")
            LiveTvPlaybackInfo(
                streamUrl = streamUrl,
                mediaSourceId = mediaSourceId,
                playSessionId = playSessionId,
                liveStreamId = liveStreamId,
                playMethod = playMethod.serialName,
                container = container,
            )
        }

    private fun untimedApiClient(source: ApiClient): ApiClient =
        try {
            jellyfin.createApi(
                baseUrl = source.baseUrl,
                accessToken = source.accessToken,
                httpClientOptions =
                    NetworkModule.JELLYFIN_HTTP_OPTIONS.copy(
                        requestTimeout = Duration.ZERO,
                        socketTimeout = Duration.ZERO,
                    ),
            )
        } catch (e: Exception) {
            Timber.w(e, "Failed to build untimed Live TV client, falling back to session client")
            source
        }

    override suspend fun getChannelStreamUrl(channelId: UUID): String? =
        getChannelPlaybackInfo(channelId)?.streamUrl

    override suspend fun toggleChannelFavorite(channelId: UUID): Boolean =
        apiCall(false, "Failed to toggle favorite for channel: $channelId") { _, _ ->
            val channel = getChannel(channelId) ?: return@apiCall false
            if (channel.favorite) {
                userDataRepository.removeFromFavorites(channelId)
            } else {
                userDataRepository.addToFavorites(channelId)
            }
            true
        }

    override suspend fun hasLiveTvAccess(): Boolean {
        if (sessionManager.currentSession.value?.canAccessLiveTv == false) return false
        mediaRepository.hasLiveTvLibrary.value?.let {
            return it
        }
        return apiCall(false, "Failed to check access for user") { apiClient, userId ->
            UserViewsApi(apiClient).getUserViews(userId = userId).content.items.any {
                it.collectionType == CollectionType.LIVETV
            }
        }
    }
}
