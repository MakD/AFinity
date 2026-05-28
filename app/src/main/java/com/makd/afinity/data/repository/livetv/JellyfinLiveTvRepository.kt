package com.makd.afinity.data.repository.livetv

import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.extensions.toAfinityChannel
import com.makd.afinity.data.models.extensions.toAfinityProgram
import com.makd.afinity.data.models.livetv.AfinityChannel
import com.makd.afinity.data.models.livetv.AfinityProgram
import com.makd.afinity.data.models.livetv.ChannelType
import com.makd.afinity.data.models.livetv.LiveTvPlaybackInfo
import com.makd.afinity.data.repository.userdata.UserDataRepository
import com.makd.afinity.util.MediaCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import org.jellyfin.sdk.model.api.MediaStreamProtocol
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

@Singleton
class JellyfinLiveTvRepository
@Inject
constructor(
    private val sessionManager: SessionManager,
    private val userDataRepository: UserDataRepository,
) : LiveTvRepository {

    private fun getBaseUrl(): String = sessionManager.getCurrentApiClient()?.baseUrl ?: ""

    private fun getLiveTvApi(): LiveTvApi? {
        val apiClient = sessionManager.getCurrentApiClient() ?: return null
        return LiveTvApi(apiClient)
    }

    private fun buildLiveTvDeviceProfile(maxBitrate: Int): DeviceProfile {
        val nativeVideoCodecs = MediaCapabilities.getSupportedVideoCodecs()
        val nativeAudioCodecs = MediaCapabilities.getSupportedAudioCodecs()

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
                        videoCodec = "h264",
                        audioCodec = "aac",
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
        withContext(Dispatchers.IO) {
            try {
                val liveTvApi = getLiveTvApi() ?: return@withContext emptyList()
                val baseUrl = getBaseUrl()

                val response =
                    liveTvApi.getLiveTvChannels(
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
            } catch (e: Exception) {
                Timber.e(e, "Failed to get Live TV channels")
                emptyList()
            }
        }

    override suspend fun getChannel(channelId: UUID): AfinityChannel? =
        withContext(Dispatchers.IO) {
            try {
                val liveTvApi = getLiveTvApi() ?: return@withContext null
                val baseUrl = getBaseUrl()

                val response = liveTvApi.getChannel(channelId)
                val channelDto = response.content
                val currentProgram = channelDto.currentProgram?.toAfinityProgram(baseUrl)
                channelDto.toAfinityChannel(baseUrl, currentProgram)
            } catch (e: Exception) {
                Timber.e(e, "Failed to get channel: $channelId")
                null
            }
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
        withContext(Dispatchers.IO) {
            try {
                val liveTvApi = getLiveTvApi() ?: return@withContext emptyList()
                val baseUrl = getBaseUrl()

                val response =
                    liveTvApi.getLiveTvPrograms(
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
            } catch (e: Exception) {
                Timber.e(e, "Failed to get programs")
                emptyList()
            }
        }

    override suspend fun getCurrentProgram(channelId: UUID): AfinityProgram? =
        withContext(Dispatchers.IO) {
            try {
                val now = LocalDateTime.now()
                val programs =
                    getPrograms(
                        channelIds = listOf(channelId),
                        maxStartDate = now,
                        minEndDate = now,
                        limit = 1,
                    )

                programs.firstOrNull()
            } catch (e: Exception) {
                Timber.e(e, "Failed to get current program for channel: $channelId")
                null
            }
        }

    override suspend fun getRecommendedPrograms(
        isAiring: Boolean,
        limit: Int,
    ): List<AfinityProgram> =
        withContext(Dispatchers.IO) {
            try {
                val liveTvApi = getLiveTvApi() ?: return@withContext emptyList()
                val baseUrl = getBaseUrl()

                val response =
                    liveTvApi.getRecommendedPrograms(
                        isAiring = isAiring,
                        limit = limit,
                        enableImages = true,
                        imageTypeLimit = 1,
                        enableImageTypes =
                            listOf(ImageType.PRIMARY, ImageType.THUMB, ImageType.BACKDROP),
                        fields = listOf(ItemFields.OVERVIEW, ItemFields.GENRES),
                    )

                response.content.items?.map { programDto -> programDto.toAfinityProgram(baseUrl) }
                    ?: emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Failed to get recommended programs")
                emptyList()
            }
        }

    override suspend fun getChannelPlaybackInfo(channelId: UUID): LiveTvPlaybackInfo? =
        withContext(Dispatchers.IO) {
            try {
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext null
                val baseUrl = getBaseUrl()

                val userId = sessionManager.currentSession.value?.userId ?: return@withContext null

                if (baseUrl.isBlank()) {
                    Timber.e("Missing baseUrl")
                    return@withContext null
                }

                val mediaInfoApi = MediaInfoApi(apiClient)
                val videosApi = VideosApi(apiClient)
                val maxStreamingBitrate = 140_000_000

                val playbackInfoDto =
                    PlaybackInfoDto(
                        userId = userId,
                        maxStreamingBitrate = maxStreamingBitrate,
                        enableDirectPlay = true,
                        enableDirectStream = true,
                        enableTranscoding = false,
                        allowVideoStreamCopy = true,
                        allowAudioStreamCopy = true,
                        autoOpenLiveStream = true,
                        deviceProfile = buildLiveTvDeviceProfile(maxStreamingBitrate),
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
                    return@withContext null
                }

                val source =
                    sources.firstOrNull { it.supportsDirectPlay }
                        ?: sources.firstOrNull { it.supportsDirectStream }
                        ?: sources.first()

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
                val container: String? = source.container ?: "ts"

                val isClientRemote =
                    !baseUrl.contains("192.168.") &&
                        !baseUrl.contains("10.") &&
                        !baseUrl.contains("172.") &&
                        !baseUrl.contains("localhost")

                val isStreamLocalIp =
                    directStreamPath?.contains("192.168.") == true ||
                        directStreamPath?.contains("10.") == true ||
                        directStreamPath?.contains("172.") == true

                val mustProxyStream = isClientRemote && isStreamLocalIp

                when {
                    source.supportsDirectPlay -> {
                        playMethod = PlayMethod.DIRECT_PLAY
                        streamUrl =
                            if (
                                source.isRemote &&
                                    !directStreamPath.isNullOrBlank() &&
                                    !mustProxyStream
                            ) {
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
                        streamUrl = source.transcodingUrl!!.toAbsoluteUrl(baseUrl)
                    }
                    else -> {
                        Timber.w("Jellyfin wanted to transcode. FORCING Direct Play anyway.")
                        playMethod = PlayMethod.DIRECT_PLAY

                        if (
                            !directStreamPath.isNullOrBlank() &&
                                directStreamPath.startsWith("http") &&
                                !mustProxyStream
                        ) {
                            streamUrl = directStreamPath
                        } else {
                            streamUrl =
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
            } catch (e: Exception) {
                Timber.e(e, "Failed to get stream URL for channel: $channelId")
                null
            }
        }

    private fun String.toAbsoluteUrl(baseUrl: String): String =
        if (startsWith("http", ignoreCase = true)) this else "$baseUrl$this"

    override suspend fun getChannelStreamUrl(channelId: UUID): String? =
        getChannelPlaybackInfo(channelId)?.streamUrl

    override suspend fun toggleChannelFavorite(channelId: UUID): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val channel = getChannel(channelId) ?: return@withContext false
                if (channel.favorite) {
                    userDataRepository.removeFromFavorites(channelId)
                } else {
                    userDataRepository.addToFavorites(channelId)
                }
                true
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle favorite for channel: $channelId")
                false
            }
        }

    override suspend fun hasLiveTvAccess(): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext false
                val userId = sessionManager.currentSession.value?.userId ?: return@withContext false
                val userViewsApi = UserViewsApi(apiClient)
                val response = userViewsApi.getUserViews(userId = userId)
                response.content.items.any { it.collectionType == CollectionType.LIVETV }
            } catch (e: Exception) {
                Timber.e(e, "Failed to check access for user")
                false
            }
        }
}
