package com.makd.afinity.player.music

import com.makd.afinity.data.repository.playback.PlaybackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicProgressReporter
@Inject
constructor(private val playbackRepository: PlaybackRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var progressJob: Job? = null
    private var currentTrackId: UUID? = null
    private var playSessionId: String = UUID.randomUUID().toString()
    private var playMethod: String = "DirectPlay"

    fun onPlaybackStarted(
        trackId: UUID,
        startPositionMs: Long,
        playSessionId: String? = null,
        playMethod: String = "DirectPlay",
    ) {
        this.playSessionId = playSessionId ?: UUID.randomUUID().toString()
        this.playMethod = playMethod
        currentTrackId = trackId
        val sessionId = this.playSessionId
        scope.launch {
            runCatching {
                playbackRepository.reportPlaybackStart(
                    itemId = trackId,
                    sessionId = sessionId,
                    mediaSourceId = trackId.toString(),
                    playMethod = playMethod,
                    canSeek = true,
                )
            }
                .onFailure { Timber.w(it, "Failed to report playback start for $trackId") }
        }
    }

    fun updatePlayMethod(trackId: UUID, playMethod: String) {
        if (currentTrackId != trackId) return
        this.playMethod = playMethod
    }

    fun startProgressUpdates(getPositionMs: () -> Long, isPaused: () -> Boolean) {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                delay(10_000L)
                val trackId = currentTrackId ?: break
                runCatching {
                    playbackRepository.reportPlaybackProgress(
                        itemId = trackId,
                        sessionId = playSessionId,
                        positionTicks = getPositionMs() * 10_000L,
                        isPaused = isPaused(),
                        playMethod = playMethod,
                    )
                }
                    .onFailure { Timber.w(it, "Failed to report playback progress") }
            }
        }
    }

    fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    fun onPlaybackStopped(positionMs: Long) {
        val trackId = currentTrackId ?: return
        val sessionId = playSessionId
        scope.launch {
            runCatching {
                playbackRepository.reportPlaybackStop(
                    itemId = trackId,
                    sessionId = sessionId,
                    positionTicks = positionMs * 10_000L,
                    mediaSourceId = trackId.toString(),
                )
            }
                .onFailure { Timber.w(it, "Failed to report playback stop for $trackId") }
        }
        stopProgressUpdates()
        currentTrackId = null
    }
}
