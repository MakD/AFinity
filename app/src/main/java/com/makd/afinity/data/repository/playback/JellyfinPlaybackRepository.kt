package com.makd.afinity.data.repository.playback

import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.player.StreamDecision
import com.makd.afinity.data.models.player.VideoQuality
import com.makd.afinity.data.models.user.AfinityUserDataDto
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.player.profile.AndroidDeviceProfileFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.operations.AudioApi
import org.jellyfin.sdk.api.operations.MediaInfoApi
import org.jellyfin.sdk.api.operations.SessionApi
import org.jellyfin.sdk.api.operations.VideoApi
import org.jellyfin.sdk.model.api.DeviceProfile
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.PlaybackInfoResponse
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.RepeatMode
import org.jellyfin.sdk.model.api.TranscodingInfo
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyfinPlaybackRepository
@Inject
constructor(
    private val sessionManager: SessionManager,
    private val databaseRepository: DatabaseRepository,
    private val preferencesRepository: PreferencesRepository,
    private val deviceProfileFactory: AndroidDeviceProfileFactory,
) : PlaybackRepository {

    private suspend fun getCurrentUserId(): UUID? {
        return sessionManager.currentSession.value?.userId
    }

    private suspend fun buildDeviceProfile(
        quality: VideoQuality,
        allowTranscoding: Boolean,
    ): DeviceProfile {
        val maxAudioChannels = preferencesRepository.getTranscodeMaxAudioChannels()
        val allowHdrPassthrough = preferencesRepository.getAllowHdrPassthrough()
        return if (preferencesRepository.useExoPlayer.first()) {
            deviceProfileFactory.createExoPlayerProfile(
                quality = quality,
                maxAudioChannels = maxAudioChannels,
                allowHdrPassthrough = allowHdrPassthrough,
                allowTranscoding = allowTranscoding,
            )
        } else {
            deviceProfileFactory.createMpvProfile(
                quality = quality,
                maxAudioChannels = maxAudioChannels,
                allowHdrPassthrough = allowHdrPassthrough,
                allowTranscoding = allowTranscoding,
            )
        }
    }

    override suspend fun getPlaybackInfo(
        itemId: UUID,
        quality: VideoQuality,
        maxAudioChannels: Int?,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
        mediaSourceId: String?,
        startTimeTicks: Long,
        enableDirectPlay: Boolean,
        allowTranscoding: Boolean,
    ): PlaybackInfoResponse? {
        return withContext(Dispatchers.IO) {
            try {
                val userId = getCurrentUserId() ?: return@withContext null
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext null
                val mediaInfoApi = MediaInfoApi(apiClient)

                val playbackInfoDto =
                    PlaybackInfoDto(
                        userId = userId,
                        maxStreamingBitrate = quality.maxBitrate.takeIf { it > 0 },
                        startTimeTicks = startTimeTicks,
                        audioStreamIndex = audioStreamIndex,
                        subtitleStreamIndex = subtitleStreamIndex,
                        maxAudioChannels = maxAudioChannels,
                        mediaSourceId = mediaSourceId,
                        deviceProfile = buildDeviceProfile(quality, allowTranscoding),
                        enableDirectPlay = enableDirectPlay,
                        enableDirectStream = enableDirectPlay,
                        enableTranscoding = allowTranscoding,
                        allowVideoStreamCopy = enableDirectPlay,
                        allowAudioStreamCopy = enableDirectPlay,
                    )

                val response =
                    mediaInfoApi.getPostedPlaybackInfo(itemId = itemId, data = playbackInfoDto)

                Timber.d(
                    "Got playback info response with ${response.content.mediaSources?.size ?: 0} media sources"
                )

                response.content
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
        quality: VideoQuality,
        maxAudioChannels: Int?,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
        mediaSourceId: String?,
    ): List<MediaSourceInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val userId = getCurrentUserId() ?: return@withContext emptyList()
                val apiClient =
                    sessionManager.getCurrentApiClient() ?: return@withContext emptyList()
                val mediaInfoApi = MediaInfoApi(apiClient)

                val playbackInfoDto =
                    PlaybackInfoDto(
                        userId = userId,
                        maxStreamingBitrate = quality.maxBitrate.takeIf { it > 0 },
                        startTimeTicks = 0L,
                        audioStreamIndex = audioStreamIndex,
                        subtitleStreamIndex = subtitleStreamIndex,
                        maxAudioChannels = maxAudioChannels,
                        mediaSourceId = mediaSourceId,
                        deviceProfile = buildDeviceProfile(quality, true),
                        enableDirectPlay = true,
                        enableDirectStream = true,
                        enableTranscoding = true,
                        allowVideoStreamCopy = true,
                        allowAudioStreamCopy = true,
                    )

                val response =
                    mediaInfoApi.getPostedPlaybackInfo(itemId = itemId, data = playbackInfoDto)

                Timber.d(
                    "Got ${response.content.mediaSources?.size ?: 0} media sources for item: $itemId"
                )
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
        startTimeTicks: Long?,
        playSessionId: String?,
        tag: String?,
    ): String? {
        return try {
            val apiClient = sessionManager.getCurrentApiClient() ?: return null
            val videoApi = VideoApi(apiClient)
            val streamUrl =
                videoApi.getVideoStreamUrl(
                    itemId = itemId,
                    static = true,
                    mediaSourceId = mediaSourceId,
                    audioStreamIndex = audioStreamIndex,
                    subtitleStreamIndex = subtitleStreamIndex,
                    videoStreamIndex = videoStreamIndex,
                    startTimeTicks = startTimeTicks,
                    playSessionId = playSessionId,
                    tag = tag,
                )
            Timber.d("Generated stream URL for item: $itemId")
            streamUrl
        } catch (e: Exception) {
            Timber.e(e, "Failed to build stream URL for item: $itemId")
            null
        }
    }

    override suspend fun resolveAudioStream(
        itemId: UUID,
        playSessionId: String?,
        maxStreamingBitrate: Int?,
    ): StreamDecision? {
        return withContext(Dispatchers.IO) {
            try {
                val userId = getCurrentUserId() ?: return@withContext null
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext null

                val response =
                    MediaInfoApi(apiClient)
                        .getPostedPlaybackInfo(
                            itemId = itemId,
                            data =
                                PlaybackInfoDto(
                                    userId = userId,
                                    maxStreamingBitrate = maxStreamingBitrate,
                                    deviceProfile =
                                        deviceProfileFactory.createMusicProfile(maxStreamingBitrate),
                                    enableDirectPlay = true,
                                    enableDirectStream = true,
                                    enableTranscoding = true,
                                    allowAudioStreamCopy = true,
                                ),
                        )
                val source = response.content.mediaSources?.firstOrNull() ?: return@withContext null

                if (source.supportsDirectPlay) {
                    val url =
                        AudioApi(apiClient)
                            .getAudioStreamUrl(
                                itemId = itemId,
                                static = true,
                                container = source.container,
                                mediaSourceId = source.id,
                                tag = source.eTag,
                                playSessionId = playSessionId,
                            )
                    return@withContext StreamDecision.DirectPlay(url)
                }

                val transcodingUrl =
                    source.transcodingUrl?.takeIf { it.isNotBlank() }
                        ?: run {
                            Timber.e("Audio source $itemId supports neither direct play nor transcoding")
                            return@withContext null
                        }

                Timber.d(
                    "Resolved audio stream for $itemId: protocol=${source.transcodingSubProtocol} reasons=${TranscodingUrl.transcodeReasons(transcodingUrl)}"
                )

                StreamDecision.Transcode(
                    url = apiClient.createUrl(transcodingUrl, ignorePathParameters = true),
                    protocol = source.transcodingSubProtocol,
                    transcodeReasons = TranscodingUrl.transcodeReasons(transcodingUrl),
                    burnedInSubtitleIndex = null,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to resolve audio stream for item: $itemId")
                null
            }
        }
    }

    override suspend fun resolveStream(
        itemId: UUID,
        source: MediaSourceInfo,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
        startTimeTicks: Long?,
        playSessionId: String?,
    ): StreamDecision? {
        return withContext(Dispatchers.IO) {
            try {
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext null
                val mediaSourceId = source.id ?: return@withContext null

                if (source.supportsDirectPlay) {
                    val url =
                        getStreamUrl(
                            itemId = itemId,
                            mediaSourceId = mediaSourceId,
                            audioStreamIndex = audioStreamIndex,
                            subtitleStreamIndex = subtitleStreamIndex,
                            startTimeTicks = startTimeTicks,
                            playSessionId = playSessionId,
                            tag = source.eTag,
                        ) ?: return@withContext null
                    return@withContext StreamDecision.DirectPlay(url)
                }

                val transcodingUrl = source.transcodingUrl?.takeIf { it.isNotBlank() }
                if (transcodingUrl == null) {
                    Timber.e("Source ${source.id} supports neither direct play nor transcoding")
                    return@withContext null
                }

                val rewritten =
                    TranscodingUrl.withSubtitleStreamIndex(transcodingUrl, subtitleStreamIndex).let {
                        if (audioStreamIndex != null) {
                            TranscodingUrl.withAudioStreamIndex(it, audioStreamIndex)
                        } else {
                            it
                        }
                    }
                        .let {
                            if (startTimeTicks != null && startTimeTicks > 0L) {
                                TranscodingUrl.withStartTimeTicks(it, startTimeTicks)
                            } else {
                                it
                            }
                        }

                val absolute = apiClient.createUrl(rewritten, ignorePathParameters = true)
                val reasons = TranscodingUrl.transcodeReasons(rewritten)
                val burnedIn = TranscodingUrl.burnedInSubtitleIndex(rewritten)

                Timber.d(
                    "Resolved stream for $itemId: directStream=${source.supportsDirectStream} protocol=${source.transcodingSubProtocol} reasons=$reasons burnedIn=$burnedIn"
                )

                if (source.supportsDirectStream) {
                    StreamDecision.DirectStream(
                        url = absolute,
                        protocol = source.transcodingSubProtocol,
                        transcodeReasons = reasons,
                    )
                } else {
                    StreamDecision.Transcode(
                        url = absolute,
                        protocol = source.transcodingSubProtocol,
                        transcodeReasons = reasons,
                        burnedInSubtitleIndex = burnedIn,
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to resolve stream for item: $itemId")
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
        liveStreamId: String?,
        canSeek: Boolean,
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext false
                val sessionApi = SessionApi(apiClient)

                sessionApi.reportPlaybackStart(
                    PlaybackStartInfo(
                        itemId = itemId,
                        mediaSourceId = mediaSourceId,
                        audioStreamIndex = audioStreamIndex,
                        subtitleStreamIndex = subtitleStreamIndex,
                        playMethod = PlayMethod.fromName(playMethod),
                        liveStreamId = liveStreamId,
                        playSessionId = sessionId,
                        canSeek = canSeek,
                        isPaused = false,
                        isMuted = false,
                        repeatMode = RepeatMode.REPEAT_NONE,
                        playbackOrder = PlaybackOrder.DEFAULT,
                    )
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
        liveStreamId: String?,
        repeatMode: String,
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val apiClient =
                    sessionManager.getCurrentApiClient()
                        ?: run {
                            Timber.w(
                                "No API client available, saving playback progress locally for item: $itemId"
                            )
                            savePlaybackProgressLocally(
                                itemId,
                                positionTicks,
                                audioStreamIndex,
                                subtitleStreamIndex,
                            )
                            return@withContext false
                        }
                val sessionApi = SessionApi(apiClient)
                sessionApi.reportPlaybackProgress(
                    PlaybackProgressInfo(
                        itemId = itemId,
                        positionTicks = positionTicks,
                        audioStreamIndex = audioStreamIndex,
                        subtitleStreamIndex = subtitleStreamIndex,
                        volumeLevel = volumeLevel,
                        playMethod = PlayMethod.fromName(playMethod),
                        liveStreamId = liveStreamId,
                        playSessionId = sessionId,
                        repeatMode = RepeatMode.fromName(repeatMode),
                        isPaused = isPaused,
                        isMuted = isMuted,
                        canSeek = true,
                        playbackOrder = PlaybackOrder.DEFAULT,
                    )
                )
                true
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to report playback progress for item: $itemId, saving locally")
                savePlaybackProgressLocally(
                    itemId,
                    positionTicks,
                    audioStreamIndex,
                    subtitleStreamIndex,
                )
                false
            } catch (e: Exception) {
                Timber.e(
                    e,
                    "Unexpected error reporting playback progress for item: $itemId, saving locally",
                )
                savePlaybackProgressLocally(
                    itemId,
                    positionTicks,
                    audioStreamIndex,
                    subtitleStreamIndex,
                )
                false
            }
        }
    }

    override suspend fun reportPlaybackStop(
        itemId: UUID,
        sessionId: String,
        positionTicks: Long,
        mediaSourceId: String,
        liveStreamId: String?,
        nextMediaType: String?,
        playlistItemId: String?,
        runtimeTicks: Long,
        isEnded: Boolean,
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val apiClient =
                    sessionManager.getCurrentApiClient()
                        ?: run {
                            Timber.w(
                                "No API client available, saving playback stop locally for item: $itemId"
                            )
                            savePlaybackProgressLocally(
                                itemId = itemId,
                                positionTicks = positionTicks,
                                isStop = true,
                                runtimeTicks = runtimeTicks,
                                isEnded = isEnded,
                            )
                            return@withContext false
                        }
                val sessionApi = SessionApi(apiClient)

                sessionApi.reportPlaybackStopped(
                    PlaybackStopInfo(
                        itemId = itemId,
                        mediaSourceId = mediaSourceId,
                        nextMediaType = nextMediaType,
                        positionTicks = positionTicks,
                        liveStreamId = liveStreamId,
                        playSessionId = sessionId,
                        playlistItemId = playlistItemId,
                        failed = false,
                    )
                )
                clearPendingSync(itemId)
                true
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to report playback stop for item: $itemId, saving locally")
                savePlaybackProgressLocally(
                    itemId = itemId,
                    positionTicks = positionTicks,
                    isStop = true,
                    runtimeTicks = runtimeTicks,
                    isEnded = isEnded,
                )
                false
            } catch (e: Exception) {
                Timber.e(
                    e,
                    "Unexpected error reporting playback stop for item: $itemId, saving locally",
                )
                savePlaybackProgressLocally(
                    itemId = itemId,
                    positionTicks = positionTicks,
                    isStop = true,
                    runtimeTicks = runtimeTicks,
                    isEnded = isEnded,
                )
                false
            }
        }
    }

    override suspend fun savePlaybackStopOffline(
        itemId: UUID,
        positionTicks: Long,
        runtimeTicks: Long,
        isEnded: Boolean,
    ) {
        withContext(Dispatchers.IO) {
            savePlaybackProgressLocally(
                itemId = itemId,
                positionTicks = positionTicks,
                isStop = true,
                runtimeTicks = runtimeTicks,
                isEnded = isEnded,
            )
        }
    }

    private suspend fun savePlaybackProgressLocally(
        itemId: UUID,
        positionTicks: Long,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
        isStop: Boolean = false,
        runtimeTicks: Long = 0L,
        isEnded: Boolean = false,
    ) {
        try {
            val userId =
                getCurrentUserId()
                    ?: run {
                        Timber.w("Cannot save playback progress locally: no active session user ID")
                        return
                    }
            val serverId = sessionManager.currentSession.value?.serverId
            if (serverId == null) {
                Timber.w("Cannot save playback progress locally: no active server session")
                return
            }

            val resolved =
                if (isStop) {
                    val resolvedRuntimeTicks =
                        if (runtimeTicks > 0L) runtimeTicks else resolveRuntimeTicks(itemId, userId)
                    PlaybackCompletion.playbackCompletionResolved(
                        positionTicks,
                        resolvedRuntimeTicks,
                        isEnded,
                    )
                } else {
                    PlaybackCompletion.Resolved(positionTicks.coerceAtLeast(0L), false)
                }

            val existingData = databaseRepository.getUserData(userId, itemId)
            val updatedData =
                AfinityUserDataDto(
                    userId = userId,
                    itemId = itemId,
                    serverId = serverId,
                    played = existingData?.played == true || resolved.played,
                    favorite = existingData?.favorite ?: false,
                    likes = existingData?.likes ?: false,
                    playbackPositionTicks = resolved.positionTicks,
                    toBeSynced = true,
                    audioStreamIndex = audioStreamIndex ?: existingData?.audioStreamIndex,
                    subtitleStreamIndex = subtitleStreamIndex ?: existingData?.subtitleStreamIndex,
                    lastPlayedAt = System.currentTimeMillis(),
                )
            databaseRepository.insertUserData(updatedData)
            Timber.i(
                "Saved playback progress locally for item $itemId: ${resolved.positionTicks / 10000}ms, played=${updatedData.played}"
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to save playback progress locally for item: $itemId")
        }
    }

    private suspend fun clearPendingSync(itemId: UUID) {
        try {
            val userId = getCurrentUserId() ?: return
            val serverId = sessionManager.currentSession.value?.serverId ?: return
            databaseRepository.markUserDataSynced(userId, itemId, serverId)
        } catch (e: Exception) {
            Timber.w(e, "Failed to clear pending sync flag for item: $itemId")
        }
    }

    private suspend fun resolveRuntimeTicks(itemId: UUID, userId: UUID): Long {
        return try {
            databaseRepository.getEpisode(itemId, userId)?.runtimeTicks
                ?: databaseRepository.getMovie(itemId, userId)?.runtimeTicks
                ?: 0L
        } catch (e: Exception) {
            Timber.w(e, "Could not resolve runtime for item: $itemId")
            0L
        }
    }

    override suspend fun pingSession(sessionId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext false
                val sessionApi = SessionApi(apiClient)
                sessionApi.pingPlaybackSession(playSessionId = sessionId)
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
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext null
                val sessionApi = SessionApi(apiClient)
                val response = sessionApi.getSessions()
                response.content
                    ?.firstOrNull { session -> session.deviceId == apiClient.deviceInfo?.id }
                    ?.id
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to get active session")
                null
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error getting active session")
                null
            }
        }
    }

    override suspend fun getTranscodingInfo(): TranscodingInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext null
                val deviceId = apiClient.deviceInfo?.id ?: return@withContext null
                val sessionApi = SessionApi(apiClient)
                val sessions = sessionApi.getSessions(deviceId = deviceId).content.orEmpty()
                val info =
                    sessions.firstOrNull { it.transcodingInfo != null }?.transcodingInfo
                        ?: sessions.firstOrNull { it.deviceId == deviceId }?.transcodingInfo
                if (info == null) {
                    Timber.d(
                        "No transcoding info for deviceId=$deviceId (sessions=${sessions.size})"
                    )
                }
                info
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to read transcoding info")
                null
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error reading transcoding info")
                null
            }
        }
    }

    override suspend fun endSession(sessionId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext false
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
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext null
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

    override suspend fun getPlaybackInfoForCast(
        itemId: UUID,
        deviceProfile: DeviceProfile,
        maxStreamingBitrate: Int?,
        maxAudioChannels: Int?,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
        mediaSourceId: String?,
        startTimeTicks: Long,
    ): PlaybackInfoResponse? {
        return withContext(Dispatchers.IO) {
            try {
                val userId = getCurrentUserId() ?: return@withContext null
                val apiClient = sessionManager.getCurrentApiClient() ?: return@withContext null
                val mediaInfoApi = MediaInfoApi(apiClient)

                val playbackInfoDto =
                    PlaybackInfoDto(
                        userId = userId,
                        maxStreamingBitrate = maxStreamingBitrate,
                        startTimeTicks = startTimeTicks,
                        audioStreamIndex = audioStreamIndex,
                        subtitleStreamIndex = subtitleStreamIndex,
                        maxAudioChannels = maxAudioChannels,
                        mediaSourceId = mediaSourceId,
                        deviceProfile = deviceProfile,
                        enableDirectPlay = true,
                        enableDirectStream = true,
                        enableTranscoding = true,
                        allowVideoStreamCopy = true,
                        allowAudioStreamCopy = true,
                    )

                val response =
                    mediaInfoApi.getPostedPlaybackInfo(itemId = itemId, data = playbackInfoDto)

                Timber.d(
                    "Got cast playback info with ${response.content.mediaSources?.size ?: 0} media sources"
                )
                response.content
            } catch (e: ApiClientException) {
                Timber.e(e, "Failed to get cast playback info for item: $itemId")
                null
            } catch (e: Exception) {
                Timber.e(e, "Unexpected error getting cast playback info for item: $itemId")
                null
            }
        }
    }
}
