package com.makd.afinity.data.repository.livetv

import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.extensions.toAfinityChannel
import com.makd.afinity.data.models.extensions.toAfinityProgram
import com.makd.afinity.data.models.livetv.AfinityChannel
import com.makd.afinity.data.models.livetv.AfinityProgram
import com.makd.afinity.data.models.livetv.ChannelType
import com.makd.afinity.data.models.livetv.LiveTvPlaybackInfo
import com.makd.afinity.data.repository.JellyfinApiInvoker
import com.makd.afinity.data.repository.userdata.UserDataRepository
import com.makd.afinity.util.MediaCapabilities
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
    private val apiInvoker: JellyfinApiInvoker,
) : LiveTvRepository {

    private fun getBaseUrl(): String = sessionManager.getCurrentApiClient()?.baseUrl ?: ""

    private suspend fun <T> apiCall(
        default: T,
        errorMessage: String,
        block: suspend (apiClient: ApiClient, userId: UUID) -> T,
    ): T = apiInvoker.apiCall(default, errorMessage, block)

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

    override suspend fun getChannelPlaybackInfo(channelId: UUID): LiveTvPlaybackInfo? =
        apiCall(null, "Failed to get stream URL for channel: $channelId") { apiClient, userId ->
            val baseUrl = getBaseUrl()
            if (baseUrl.isBlank()) {
                Timber.e("Missing baseUrl")
                return@apiCall null
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
                    return@apiCall null
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
                val container: String = source.container ?: "ts"

                val isClientRemote = !isPrivateHost(baseUrl)

                val isStreamLocalIp = isPrivateHost(directStreamPath)

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
        }

    private fun String.toAbsoluteUrl(baseUrl: String): String =
        if (startsWith("http", ignoreCase = true)) this else "$baseUrl$this"

    private fun isPrivateHost(url: String?): Boolean {
        val host = url?.toHttpUrlOrNull()?.host ?: return false
        if (host.equals("localhost", ignoreCase = true)) return true
        val octets = host.split(".")
        if (octets.size != 4) return false
        val values = octets.map { it.toIntOrNull() ?: return false }
        if (values.any { it !in 0..255 }) return false
        return when {
            values[0] == 10 -> true
            values[0] == 127 -> true
            values[0] == 192 && values[1] == 168 -> true
            values[0] == 172 && values[1] in 16..31 -> true
            values[0] == 169 && values[1] == 254 -> true
            else -> false
        }
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

    override suspend fun hasLiveTvAccess(): Boolean =
        apiCall(false, "Failed to check access for user") { apiClient, userId ->
            UserViewsApi(apiClient)
                .getUserViews(userId = userId)
                .content
                .items
                .any { it.collectionType == CollectionType.LIVETV }
        }
}
