package com.makd.afinity.data.repository.livetv

import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.extensions.toAfinityChannel
import com.makd.afinity.data.models.extensions.toAfinityProgram
import com.makd.afinity.data.models.livetv.AfinityChannel
import com.makd.afinity.data.models.livetv.AfinityProgram
import com.makd.afinity.data.models.livetv.ChannelType
import com.makd.afinity.data.repository.userdata.UserDataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.operations.LiveTvApi
import org.jellyfin.sdk.api.operations.MediaInfoApi
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.SortOrder
import timber.log.Timber
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyfinLiveTvRepository @Inject constructor(
    private val sessionManager: SessionManager,
    private val userDataRepository: UserDataRepository
) : LiveTvRepository {

    private fun getBaseUrl(): String = sessionManager.getCurrentApiClient()?.baseUrl ?: ""

    private fun getLiveTvApi(): LiveTvApi? {
        val apiClient = sessionManager.getCurrentApiClient() ?: return null
        return LiveTvApi(apiClient)
    }

    override suspend fun getChannels(
        type: ChannelType?,
        isFavorite: Boolean?,
        limit: Int?
    ): List<AfinityChannel> = withContext(Dispatchers.IO) {
        try {
            val liveTvApi = getLiveTvApi() ?: return@withContext emptyList()
            val baseUrl = getBaseUrl()

            val response = liveTvApi.getLiveTvChannels(
                type = type?.let {
                    when (it) {
                        ChannelType.TV -> org.jellyfin.sdk.model.api.ChannelType.TV
                        ChannelType.RADIO -> org.jellyfin.sdk.model.api.ChannelType.RADIO
                    }
                },
                isFavorite = isFavorite,
                limit = limit,
                sortBy = listOf(ItemSortBy.SORT_NAME),
                sortOrder = SortOrder.ASCENDING,
                enableImages = true,
                imageTypeLimit = 1,
                enableImageTypes = listOf(ImageType.PRIMARY, ImageType.THUMB),
                fields = listOf(ItemFields.CHANNEL_INFO),
                addCurrentProgram = true
            )

            response.content.items?.mapNotNull { channelDto ->
                channelDto.toAfinityChannel(baseUrl, null)
            } ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get Live TV channels")
            emptyList()
        }
    }

    override suspend fun getChannel(channelId: UUID): AfinityChannel? = withContext(Dispatchers.IO) {
        try {
            val liveTvApi = getLiveTvApi() ?: return@withContext null
            val baseUrl = getBaseUrl()

            val response = liveTvApi.getChannel(channelId)
            val channelDto = response.content
            channelDto.toAfinityChannel(baseUrl, null)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get channel: $channelId")
            null
        }
    }

    override suspend fun getPrograms(
        channelIds: List<UUID>?,
        minStartDate: LocalDateTime?,
        maxEndDate: LocalDateTime?,
        limit: Int?
    ): List<AfinityProgram> = withContext(Dispatchers.IO) {
        try {
            val liveTvApi = getLiveTvApi() ?: return@withContext emptyList()
            val baseUrl = getBaseUrl()

            val response = liveTvApi.getLiveTvPrograms(
                channelIds = channelIds,
                minStartDate = minStartDate,
                maxEndDate = maxEndDate,
                limit = limit,
                sortBy = listOf(ItemSortBy.START_DATE),
                sortOrder = listOf(SortOrder.ASCENDING),
                enableImages = true,
                imageTypeLimit = 1,
                enableImageTypes = listOf(ImageType.PRIMARY, ImageType.THUMB, ImageType.BACKDROP),
                fields = listOf(ItemFields.OVERVIEW, ItemFields.GENRES)
            )

            response.content.items?.map { programDto ->
                programDto.toAfinityProgram(baseUrl)
            } ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get programs")
            emptyList()
        }
    }

    override suspend fun getCurrentProgram(channelId: UUID): AfinityProgram? = withContext(Dispatchers.IO) {
        try {
            val now = LocalDateTime.now()
            val programs = getPrograms(
                channelIds = listOf(channelId),
                minStartDate = now.minusHours(2),
                maxEndDate = now.plusHours(1),
                limit = 5
            )

            programs.find { it.isCurrentlyAiring() }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get current program for channel: $channelId")
            null
        }
    }

    override suspend fun getRecommendedPrograms(
        isAiring: Boolean,
        limit: Int
    ): List<AfinityProgram> = withContext(Dispatchers.IO) {
        try {
            val liveTvApi = getLiveTvApi() ?: return@withContext emptyList()
            val baseUrl = getBaseUrl()

            val response = liveTvApi.getRecommendedPrograms(
                isAiring = isAiring,
                limit = limit,
                enableImages = true,
                imageTypeLimit = 1,
                enableImageTypes = listOf(ImageType.PRIMARY, ImageType.THUMB, ImageType.BACKDROP),
                fields = listOf(ItemFields.OVERVIEW, ItemFields.GENRES)
            )

            response.content.items?.map { programDto ->
                programDto.toAfinityProgram(baseUrl)
            } ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get recommended programs")
            emptyList()
        }
    }

    override suspend fun getChannelStreamUrl(channelId: UUID): String? = withContext(Dispatchers.IO) {
        try {
            val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext null
            val baseUrl = getBaseUrl()
            val accessToken = apiClient.accessToken
            val deviceId = apiClient.deviceInfo.id

            val userId = sessionManager.currentSession.value?.userId
                ?: return@withContext null

            if (baseUrl.isBlank() || accessToken.isNullOrBlank()) {
                Timber.e("Missing baseUrl or accessToken")
                return@withContext null
            }

            val mediaInfoApi = MediaInfoApi(apiClient)

            val deviceProfile = DeviceProfile(
                name = "AFinity Live TV",
                maxStreamingBitrate = 20_000_000,
                maxStaticBitrate = 20_000_000,
                codecProfiles = emptyList(),
                containerProfiles = emptyList(),
                directPlayProfiles = emptyList(),
                transcodingProfiles = emptyList(),
                subtitleProfiles = emptyList()
            )

            val playbackInfoDto = PlaybackInfoDto(
                userId = userId,
                maxStreamingBitrate = 20_000_000,
                startTimeTicks = 0L,
                deviceProfile = deviceProfile,
                enableDirectPlay = false,
                enableDirectStream = false,
                enableTranscoding = true,
                allowVideoStreamCopy = true,
                allowAudioStreamCopy = true,
                autoOpenLiveStream = true,
                liveStreamId = null,
                mediaSourceId = null,
                audioStreamIndex = null,
                subtitleStreamIndex = null
            )

            val playbackResponse = mediaInfoApi.getPostedPlaybackInfo(
                itemId = channelId,
                data = playbackInfoDto
            )
            val playbackInfo = playbackResponse.content

            Timber.d("PlaybackInfo: playSessionId=${playbackInfo.playSessionId}, mediaSources=${playbackInfo.mediaSources?.size ?: 0}")

            val sources = playbackInfo.mediaSources
            if (sources.isNullOrEmpty()) {
                Timber.e("PlaybackInfo returned no media sources")
                return@withContext null
            }

            val source = sources.first()
            val liveStreamId = source.liveStreamId
            val mediaSourceId = source.id

            Timber.d("Source: id=$mediaSourceId, liveStreamId=$liveStreamId, transcodingUrl=${source.transcodingUrl}")

            if (!source.transcodingUrl.isNullOrBlank()) {
                val transcodingUrl = source.transcodingUrl!!
                val fullUrl = if (transcodingUrl.startsWith("http")) {
                    transcodingUrl
                } else {
                    "$baseUrl$transcodingUrl"
                }
                Timber.d("Using server-provided transcoding URL: $fullUrl")
                return@withContext fullUrl
            }

            if (liveStreamId.isNullOrBlank()) {
                Timber.e("No LiveStreamId returned - cannot construct stream URL")
                return@withContext null
            }

            val playSessionId = playbackInfo.playSessionId ?: UUID.randomUUID().toString().replace("-", "")

            val params = mutableListOf<String>()
            params.add("api_key=$accessToken")
            params.add("DeviceId=$deviceId")
            params.add("MediaSourceId=$mediaSourceId")
            params.add("LiveStreamId=$liveStreamId")
            params.add("PlaySessionId=$playSessionId")
            params.add("VideoCodec=h264")
            params.add("AudioCodec=aac")
            params.add("AudioStreamIndex=-1")
            params.add("VideoBitrate=20000000")
            params.add("AudioBitrate=128000")
            params.add("MaxFramerate=30")
            params.add("SegmentContainer=ts")
            params.add("TranscodingMaxAudioChannels=2")
            params.add("MinSegments=1")
            params.add("BreakOnNonKeyFrames=False")
            params.add("h264-profile=main")
            params.add("h264-level=41")
            params.add("h264-deinterlace=true")

            val queryString = params.joinToString("&")
            val streamUrl = "${baseUrl}/videos/${channelId}/master.m3u8?$queryString"

            Timber.d("Generated stream URL: $streamUrl")
            streamUrl

        } catch (e: Exception) {
            Timber.e(e, "Failed to get stream URL for channel: $channelId")
            null
        }
    }

    override suspend fun toggleChannelFavorite(channelId: UUID): Boolean = withContext(Dispatchers.IO) {
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

    override suspend fun hasLiveTvAccess(): Boolean = withContext(Dispatchers.IO) {
        try {
            val liveTvApi = getLiveTvApi() ?: return@withContext false
            val response = liveTvApi.getLiveTvInfo()
            response.content.isEnabled
        } catch (e: Exception) {
            Timber.e(e, "Failed to check Live TV access")
            false
        }
    }
}