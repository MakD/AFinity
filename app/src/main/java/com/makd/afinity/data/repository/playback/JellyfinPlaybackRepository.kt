package com.makd.afinity.data.repository.playback

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.operations.MediaInfoApi
import org.jellyfin.sdk.api.operations.PlayStateApi
import org.jellyfin.sdk.api.operations.SessionApi
import org.jellyfin.sdk.api.operations.UserApi
import org.jellyfin.sdk.api.operations.VideosApi
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.RepeatMode
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.jellyfin.sdk.model.api.SubtitleProfile
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyfinPlaybackRepository @Inject constructor(
    private val apiClient: ApiClient
) : PlaybackRepository {

    private suspend fun getCurrentUserId(): UUID? {
        return withContext(Dispatchers.IO) {
            try {
                val userApi = UserApi(apiClient)
                val response = userApi.getCurrentUser()
                response.content?.id
            } catch (e: Exception) {
                Timber.e(e, "Failed to get current user ID")
                null
            }
        }
    }

    override suspend fun getPlaybackInfo(
        itemId: UUID,
        maxStreamingBitrate: Int?,
        maxAudioChannels: Int?,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
        mediaSourceId: String?
    ): PlaybackInfoDto? {
        return withContext(Dispatchers.IO) {
            try {
                val userId = getCurrentUserId() ?: return@withContext null
                val mediaInfoApi = MediaInfoApi(apiClient)

                val deviceProfile = DeviceProfile(
                    name = "Direct play all",
                    maxStaticBitrate = maxStreamingBitrate ?: 1_000_000_000,
                    maxStreamingBitrate = maxStreamingBitrate ?: 1_000_000_000,
                    codecProfiles = emptyList(),
                    containerProfiles = emptyList(),
                    directPlayProfiles = emptyList(),
                    transcodingProfiles = emptyList(),
                    subtitleProfiles = listOf(
                        SubtitleProfile("srt", SubtitleDeliveryMethod.EXTERNAL),
                        SubtitleProfile("ass", SubtitleDeliveryMethod.EXTERNAL),
                        SubtitleProfile("vtt", SubtitleDeliveryMethod.EXTERNAL),
                        SubtitleProfile("sub", SubtitleDeliveryMethod.EXTERNAL),
                        SubtitleProfile("idx", SubtitleDeliveryMethod.EXTERNAL)
                    ),
                )

                val playbackInfoDto = PlaybackInfoDto(
                    userId = userId,
                    maxStreamingBitrate = maxStreamingBitrate,
                    startTimeTicks = 0L,
                    audioStreamIndex = audioStreamIndex,
                    subtitleStreamIndex = subtitleStreamIndex,
                    maxAudioChannels = maxAudioChannels,
                    mediaSourceId = mediaSourceId,
                    deviceProfile = deviceProfile,
                    enableDirectPlay = true,
                    enableDirectStream = true,
                    enableTranscoding = true,
                    allowVideoStreamCopy = true,
                    allowAudioStreamCopy = true
                )

                val response = mediaInfoApi.getPostedPlaybackInfo(
                    itemId = itemId,
                    data = playbackInfoDto
                )

                Timber.d("Got playback info response with ${response.content.mediaSources?.size ?: 0} media sources")

                playbackInfoDto
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to get playback info for item: $itemId")
                null
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error getting playback info for item: $itemId")
                null
            }
        }
    }


    override suspend fun getMediaSources(
        itemId: UUID,
        maxStreamingBitrate: Int?,
        maxAudioChannels: Int?,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
        mediaSourceId: String?
    ): List<MediaSourceInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val userId = getCurrentUserId() ?: return@withContext emptyList()
                val mediaInfoApi = MediaInfoApi(apiClient)

                val deviceProfile = DeviceProfile(
                    name = "Direct play all",
                    maxStaticBitrate = maxStreamingBitrate ?: 1_000_000_000,
                    maxStreamingBitrate = maxStreamingBitrate ?: 1_000_000_000,
                    codecProfiles = emptyList(),
                    containerProfiles = emptyList(),
                    directPlayProfiles = emptyList(),
                    transcodingProfiles = emptyList(),
                    subtitleProfiles = listOf(
                        SubtitleProfile("srt", SubtitleDeliveryMethod.EXTERNAL),
                        SubtitleProfile("ass", SubtitleDeliveryMethod.EXTERNAL),
                        SubtitleProfile("vtt", SubtitleDeliveryMethod.EXTERNAL),
                        SubtitleProfile("sub", SubtitleDeliveryMethod.EXTERNAL),
                        SubtitleProfile("idx", SubtitleDeliveryMethod.EXTERNAL)
                    ),
                )

                val playbackInfoDto = PlaybackInfoDto(
                    userId = userId,
                    maxStreamingBitrate = maxStreamingBitrate,
                    startTimeTicks = 0L,
                    audioStreamIndex = audioStreamIndex,
                    subtitleStreamIndex = subtitleStreamIndex,
                    maxAudioChannels = maxAudioChannels,
                    mediaSourceId = mediaSourceId,
                    deviceProfile = deviceProfile,
                    enableDirectPlay = true,
                    enableDirectStream = true,
                    enableTranscoding = true,
                    allowVideoStreamCopy = true,
                    allowAudioStreamCopy = true
                )

                val response = mediaInfoApi.getPostedPlaybackInfo(
                    itemId = itemId,
                    data = playbackInfoDto
                )

                Timber.d("Got ${response.content.mediaSources?.size ?: 0} media sources for item: $itemId")
                response.content.mediaSources ?: emptyList()
            } catch (e: Exception) {
                Timber.e(e, "Failed to get media sources for item: $itemId")
                emptyList()
            }
        }
    }

    override suspend fun getStreamUrl(
        itemId: UUID,
        mediaSourceId: String,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
        videoStreamIndex: Int?,
        maxStreamingBitrate: Int?,
        startTimeTicks: Long?
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                val videosApi = VideosApi(apiClient)

                val streamUrl = videosApi.getVideoStreamUrl(
                    itemId = itemId,
                    static = true,
                    mediaSourceId = mediaSourceId,
                    audioStreamIndex = audioStreamIndex,
                    subtitleStreamIndex = subtitleStreamIndex,
                    videoStreamIndex = videoStreamIndex,
                    startTimeTicks = null
                )

                Timber.d("Generated stream URL: $streamUrl")
                streamUrl

            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to get stream URL for item: $itemId, mediaSource: $mediaSourceId")
                null
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error getting stream URL for item: $itemId")
                null
            }
        }
    }

    override suspend fun reportPlaybackStart(
        itemId: UUID,
        sessionId: String,
        mediaSourceId: String,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
        playMethod: String,
        canSeek: Boolean
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val playStateApi = PlayStateApi(apiClient)

                playStateApi.onPlaybackStart(
                    itemId = itemId,
                    mediaSourceId = mediaSourceId,
                    audioStreamIndex = audioStreamIndex,
                    subtitleStreamIndex = subtitleStreamIndex,
                    PlayMethod.fromName(playMethod),
                    playSessionId = sessionId,
                    canSeek = canSeek
                )
                true
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to report playback start for item: $itemId")
                false
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error reporting playback start for item: $itemId")
                false
            }
        }
    }

    override suspend fun reportPlaybackProgress(
        itemId: UUID,
        sessionId: String,
        positionTicks: Long,
        isPaused: Boolean,
        isMuted: Boolean,
        volumeLevel: Int?,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
        playMethod: String,
        repeatMode: String
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val playStateApi = PlayStateApi(apiClient)

                playStateApi.onPlaybackProgress(
                    itemId = itemId,
                    positionTicks = positionTicks,
                    audioStreamIndex = audioStreamIndex,
                    subtitleStreamIndex = subtitleStreamIndex,
                    volumeLevel = volumeLevel,
                    playMethod = PlayMethod.fromName(playMethod),
                    playSessionId = sessionId,
                    repeatMode = RepeatMode.fromName(repeatMode),
                    isPaused = isPaused,
                    isMuted = isMuted
                )
                true
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to report playback progress for item: $itemId")
                false
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error reporting playback progress for item: $itemId")
                false
            }
        }
    }

    override suspend fun reportPlaybackStop(
        itemId: UUID,
        sessionId: String,
        positionTicks: Long,
        mediaSourceId: String,
        nextMediaType: String?,
        playlistItemId: String?
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val playStateApi = PlayStateApi(apiClient)

                playStateApi.onPlaybackStopped(
                    itemId = itemId,
                    mediaSourceId = mediaSourceId,
                    nextMediaType = nextMediaType,
                    positionTicks = positionTicks,
                    playSessionId = sessionId
                )
                true
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to report playback stop for item: $itemId")
                false
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error reporting playback stop for item: $itemId")
                false
            }
        }
    }

    override suspend fun pingSession(sessionId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val playStateApi = PlayStateApi(apiClient)
                playStateApi.pingPlaybackSession(playSessionId = sessionId)
                true
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to ping session: $sessionId")
                false
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error pinging session: $sessionId")
                false
            }
        }
    }

    override suspend fun getActiveSession(): String? {
        return withContext(Dispatchers.IO) {
            try {
                val sessionApi = SessionApi(apiClient)
                val response = sessionApi.getSessions()
                response.content?.firstOrNull { session ->
                    session.deviceId == apiClient.deviceInfo?.id
                }?.id
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to get active session")
                null
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error getting active session")
                null
            }
        }
    }

    override suspend fun endSession(sessionId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val sessionApi = SessionApi(apiClient)
                sessionApi.reportSessionEnded()
                true
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to end session: $sessionId")
                false
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error ending session: $sessionId")
                false
            }
        }
    }

    override suspend fun stopTranscoding(deviceId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Timber.w("stopTranscoding not available in current SDK")
                false
            } catch (e: Exception) {
                Timber.e(e, "Failed to stop transcoding for device: $deviceId")
                false
            }
        }
    }

    override suspend fun getTranscodingJob(deviceId: String): Any? {
        return withContext(Dispatchers.IO) {
            try {
                Timber.w("getTranscodingJob not available in current SDK")
                null
            } catch (e: Exception) {
                Timber.e(e, "Failed to get transcoding job for device: $deviceId")
                null
            }
        }
    }

    override suspend fun getBitrateTestBytes(size: Int): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                val mediaInfoApi = MediaInfoApi(apiClient)
                val constrainedSize = size.coerceIn(1, 100_000_000)
                val response = mediaInfoApi.getBitrateTestBytes(size = constrainedSize)
                response.content
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to get bitrate test bytes")
                null
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error getting bitrate test bytes")
                null
            }
        }
    }

    override suspend fun detectMaxBitrate(): Int? {
        return withContext(Dispatchers.IO) {
            try {
                val testSizes = listOf(1024, 2048, 4096, 8192)
                var maxBitrate = 0

                for (size in testSizes) {
                    val startTime = System.currentTimeMillis()
                    val data = getBitrateTestBytes(size * 1024)
                    val endTime = System.currentTimeMillis()

                    if (data != null) {
                        val duration = (endTime - startTime) / 1000.0
                        val bitrate = (data.size * 8) / duration

                        if (bitrate > maxBitrate) {
                            maxBitrate = bitrate.toInt()
                        }
                    } else {
                        break
                    }
                }

                if (maxBitrate > 0) maxBitrate else null
            } catch (e: Exception) {
                Timber.e(e, "Failed to detect max bitrate")
                null
            }
        }
    }
}