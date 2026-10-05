package com.makd.afinity.data.repository.download

import com.makd.afinity.data.database.entities.DownloadDto
import com.makd.afinity.data.models.media.AfinityMediaStream
import com.makd.afinity.data.models.player.MusicQuality
import com.makd.afinity.data.models.player.VideoQuality
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.playback.TranscodingUrl
import com.makd.afinity.di.DownloadClient
import com.makd.afinity.player.profile.AndroidDeviceProfileFactory
import com.makd.afinity.util.redactUrl
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.operations.MediaInfoApi
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import timber.log.Timber

data class DownloadTranscodePlan(
    val url: String,
    val container: String,
    val playSessionId: String?,
    val estimatedBytes: Long,
    val audioStreamIndex: Int?,
)

@Singleton
class DownloadTranscodePlanner
@Inject
constructor(
    private val deviceProfileFactory: AndroidDeviceProfileFactory,
    private val preferencesRepository: PreferencesRepository,
    @param:DownloadClient private val okHttpClient: OkHttpClient,
) {

    suspend fun planVideo(apiClient: ApiClient, download: DownloadDto): DownloadTranscodePlan? {
        val bitrate = download.transcodeBitrate?.takeIf { it > 0 } ?: return null
        val quality = VideoQuality(bitrate, download.transcodeMaxWidth)
        val deviceProfile =
            deviceProfileFactory.createDownloadProfile(
                quality = quality,
                maxAudioChannels = preferencesRepository.getTranscodeMaxAudioChannels(),
                allowHdrPassthrough = preferencesRepository.getAllowHdrPassthrough(),
            )

        val response =
            MediaInfoApi(apiClient)
                .getPostedPlaybackInfo(
                    itemId = download.itemId,
                    data =
                        PlaybackInfoDto(
                            userId = download.userId,
                            maxStreamingBitrate = bitrate,
                            startTimeTicks = 0L,
                            audioStreamIndex = download.transcodeAudioIndex,
                            mediaSourceId = download.sourceId,
                            deviceProfile = deviceProfile,
                            enableDirectPlay = true,
                            enableDirectStream = true,
                            enableTranscoding = true,
                            allowVideoStreamCopy = true,
                            allowAudioStreamCopy = true,
                        ),
                )
                .content

        val source =
            response.mediaSources.firstOrNull { it.id == download.sourceId }
                ?: response.mediaSources.firstOrNull()
                ?: return null
        val transcodingUrl = source.transcodingUrl?.takeIf { it.isNotBlank() } ?: return null
        val urlWithAudio =
            download.transcodeAudioIndex?.let {
                TranscodingUrl.withAudioStreamIndex(transcodingUrl, it)
            } ?: transcodingUrl
        val url = TranscodingUrl.withBurnedInSubtitle(urlWithAudio, download.burnSubtitleIndex)
        val absoluteUrl = apiClient.createUrl(url, ignorePathParameters = true)
        Timber.d("Transcoded download negotiated: ${redactUrl(absoluteUrl)}")

        return DownloadTranscodePlan(
            url = absoluteUrl,
            container =
                source.transcodingContainer?.lowercase()?.takeIf { it.isNotBlank() }
                    ?: AndroidDeviceProfileFactory.DOWNLOAD_TRANSCODE_CONTAINER,
            playSessionId = response.playSessionId,
            estimatedBytes =
                estimateBytes(minOf(bitrate, source.bitrate ?: bitrate), download.runtimeTicks),
            audioStreamIndex = TranscodingUrl.audioStreamIndex(url),
        )
    }

    fun planAudio(
        apiClient: ApiClient,
        download: DownloadDto,
        mediaSource: MediaSourceInfo,
    ): DownloadTranscodePlan? {
        val quality = MusicQuality.fromBitrate(download.transcodeBitrate)
        if (quality.isOriginal) return null

        val container = mediaSource.container?.lowercase().orEmpty()
        val playableContainers = MusicQuality.CONTAINERS.split(',')
        val isPlayable = container.split(',').any { it in playableContainers }
        val sourceBitrate = mediaSource.bitrate
        if (isPlayable && sourceBitrate != null && sourceBitrate <= quality.streamingBitrate) {
            return null
        }

        val baseUrl = apiClient.baseUrl?.trimEnd('/') ?: return null
        val playSessionId = UUID.randomUUID().toString()
        val url =
            "$baseUrl/Audio/${download.itemId}/universal"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("userId", download.userId.toString())
                .addQueryParameter("deviceId", apiClient.deviceInfo.id)
                .addQueryParameter("audioCodec", MusicQuality.AUDIO_CODECS)
                .addQueryParameter("container", MusicQuality.CONTAINERS)
                .addQueryParameter("transcodingContainer", MusicQuality.TRANSCODING_CONTAINER)
                .addQueryParameter("transcodingProtocol", MusicQuality.TRANSCODING_PROTOCOL)
                .addQueryParameter("maxStreamingBitrate", quality.streamingBitrate.toString())
                .addQueryParameter("playSessionId", playSessionId)
                .build()
                .toString()

        return DownloadTranscodePlan(
            url = url,
            container = MusicQuality.TRANSCODING_CONTAINER,
            playSessionId = playSessionId,
            estimatedBytes = estimateBytes(quality.maxBitrate, download.runtimeTicks),
            audioStreamIndex = null,
        )
    }

    fun transcodedStreams(
        originalStreams: List<AfinityMediaStream>,
        plan: DownloadTranscodePlan,
        maxWidth: Int?,
    ): List<AfinityMediaStream> {
        val video =
            originalStreams
                .filter { it.type == MediaStreamType.VIDEO }
                .map { stream ->
                    val width = stream.width
                    val height = stream.height
                    val scales = maxWidth != null && width != null && width > maxWidth
                    stream.copy(
                        codec = "h264",
                        videoRangeType = null,
                        videoDoViTitle = null,
                        hdr10PlusPresentFlag = false,
                        width = if (scales) maxWidth else width,
                        height =
                            if (scales && height != null) height * maxWidth / width else height,
                    )
                }
        val audioStreams = originalStreams.filter { it.type == MediaStreamType.AUDIO }
        val audio =
            audioStreams.firstOrNull { it.index == plan.audioStreamIndex }
                ?: audioStreams.firstOrNull { it.isDefault }
                ?: audioStreams.firstOrNull()
        return video + listOfNotNull(audio)
    }

    suspend fun stopEncoding(apiClient: ApiClient, playSessionId: String?) {
        if (playSessionId.isNullOrBlank()) return
        withContext(NonCancellable + Dispatchers.IO) {
            try {
                val baseUrl = apiClient.baseUrl?.trimEnd('/') ?: return@withContext
                val url =
                    "$baseUrl/Videos/ActiveEncodings"
                        .toHttpUrl()
                        .newBuilder()
                        .addQueryParameter("deviceId", apiClient.deviceInfo.id)
                        .addQueryParameter("playSessionId", playSessionId)
                        .build()
                val request =
                    Request.Builder()
                        .url(url)
                        .delete()
                        .header(
                            "Authorization",
                            "MediaBrowser Token=\"${apiClient.accessToken ?: ""}\"",
                        )
                        .build()
                okHttpClient.newCall(request).execute().use { response ->
                    Timber.d("Stop encoding for download returned ${response.code}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to stop the server encode for a download")
            }
        }
    }

    private fun estimateBytes(bitrate: Int, runtimeTicks: Long?): Long {
        val seconds = (runtimeTicks ?: 0L) / TICKS_PER_SECOND
        if (seconds <= 0L || bitrate <= 0) return -1L
        return bitrate.toLong() * seconds / 8L * 11L / 10L
    }

    private companion object {
        const val TICKS_PER_SECOND = 10_000_000L
    }
}
