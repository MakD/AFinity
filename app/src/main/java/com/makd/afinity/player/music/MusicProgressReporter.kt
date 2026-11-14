package com.makd.afinity.player.music

import com.makd.afinity.data.repository.playback.PlaybackRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

@Singleton
class MusicProgressReporter
@Inject
constructor(private val playbackRepository: PlaybackRepository) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val reportOrder = Mutex()
    private val countedSessions = mutableMapOf<String, Boolean>()
    private var progressJob: Job? = null
    @Volatile private var currentTrackId: UUID? = null
    @Volatile private var playSessionId: String = UUID.randomUUID().toString()
    @Volatile private var playMethod: String = "DirectPlay"

    private fun inOrder(block: suspend () -> Unit) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) { reportOrder.withLock { block() } }
    }

    fun onPlaybackStarted(
        trackId: UUID,
        playSessionId: String? = null,
        playMethod: String = "DirectPlay",
    ) {
        this.playSessionId = playSessionId ?: UUID.randomUUID().toString()
        this.playMethod = playMethod
        currentTrackId = trackId
        val sessionId = this.playSessionId
        inOrder {
            countedSessions[sessionId] =
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
                    .getOrDefault(false)
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
                delay(10.seconds)
                reportOrder.withLock {
                    val trackId = currentTrackId ?: return@withLock
                    val sessionId = playSessionId
                    val method = playMethod
                    val positionTicks = getPositionMs() * 10_000L
                    val paused = isPaused()
                    runCatching {
                        playbackRepository.reportPlaybackProgress(
                            itemId = trackId,
                            sessionId = sessionId,
                            positionTicks = positionTicks,
                            isPaused = paused,
                            playMethod = method,
                            isMusic = true,
                        )
                    }
                        .onFailure { Timber.w(it, "Failed to report playback progress") }
                }
            }
        }
    }

    fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    fun onTrackTransition(previousPositionMs: Long, previousEnded: Boolean) {
        reportStop(previousPositionMs, previousEnded)
    }

    fun onPlaybackStopped(positionMs: Long, isEnded: Boolean = false) {
        reportStop(positionMs, isEnded)
        stopProgressUpdates()
    }

    private fun reportStop(positionMs: Long, isEnded: Boolean) {
        val trackId = currentTrackId ?: return
        currentTrackId = null
        val sessionId = playSessionId
        inOrder {
            val counted = countedSessions.remove(sessionId) == true
            runCatching {
                playbackRepository.reportPlaybackStop(
                    itemId = trackId,
                    sessionId = sessionId,
                    positionTicks = positionMs * 10_000L,
                    mediaSourceId = trackId.toString(),
                    isEnded = isEnded,
                    isMusic = true,
                    playCountedOnServer = counted,
                )
            }
                .onFailure { Timber.w(it, "Failed to report playback stop for $trackId") }
        }
    }
}
