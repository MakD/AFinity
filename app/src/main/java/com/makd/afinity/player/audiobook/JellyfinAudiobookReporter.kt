package com.makd.afinity.player.audiobook

import com.makd.afinity.data.models.media.UserDataPatch
import com.makd.afinity.data.repository.playback.PlaybackRepository
import com.makd.afinity.data.store.ItemStore
import com.makd.afinity.player.audiobookshelf.AudiobookshelfPlaybackManager
import com.makd.afinity.player.audiobookshelf.JellyfinAudiobookSession
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
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

private const val TICKS_PER_SECOND = 10_000_000L
private const val TICKS_PER_MS = 10_000L
private val AUDIOBOOK_RESUME_WINDOW_TICKS = 5 * 60 * TICKS_PER_SECOND
private const val CAST_END_SLACK = 5.0

@Singleton
class JellyfinAudiobookReporter
@Inject
constructor(
    private val playbackRepository: PlaybackRepository,
    private val playbackManager: AudiobookshelfPlaybackManager,
    private val itemStore: ItemStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val reportOrder = Mutex()
    private var progressJob: Job? = null

    @Volatile private var session: JellyfinAudiobookSession? = null
    @Volatile private var partIndex: Int = -1
    @Volatile private var playSessionId: String = ""
    @Volatile private var started: Boolean = false
    @Volatile private var castPlaying: Boolean = false
    @Volatile private var lastCastSeconds: Double = 0.0

    fun begin(session: JellyfinAudiobookSession, startIndex: Int) {
        stopProgress()
        this.session = session
        partIndex = startIndex.coerceIn(0, session.partIds.lastIndex)
        playSessionId = UUID.randomUUID().toString()
        started = false
        castPlaying = false
    }

    fun onLocalPlayingChanged(isPlaying: Boolean) {
        castPlaying = false
        lastCastSeconds = playbackManager.playbackState.value.currentTime
        onPlayingChanged(isPlaying)
    }

    fun onCastProgress(globalSeconds: Double, isPlaying: Boolean) {
        val current = session ?: return
        val index = current.tracks.indexOfLast { it.startOffset <= globalSeconds }.coerceAtLeast(0)
        if (index != partIndex && index in current.partIds.indices) {
            val old = partIndex
            val oldTrack = current.tracks.getOrNull(old)
            val oldPositionMs =
                oldTrack?.let {
                    ((lastCastSeconds - it.startOffset).coerceIn(0.0, it.duration) * 1000).toLong()
                } ?: 0L
            val ended =
                oldTrack != null &&
                    index == old + 1 &&
                    lastCastSeconds >= oldTrack.startOffset + oldTrack.duration - CAST_END_SLACK
            onPartChanged(old, oldPositionMs, index, ended)
        }
        lastCastSeconds = globalSeconds
        if (isPlaying != castPlaying) {
            castPlaying = isPlaying
            onPlayingChanged(isPlaying)
        }
    }

    private fun onPlayingChanged(isPlaying: Boolean) {
        val current = session ?: return
        if (isPlaying) {
            if (!started) reportStart(current, partIndex)
            startProgress()
        } else {
            stopProgress()
            if (started) reportProgress(current, partIndex, positionInPartTicks(current), true)
        }
    }

    fun onPartChanged(oldIndex: Int, oldPositionMs: Long, newIndex: Int, ended: Boolean) {
        val current = session ?: return
        if (oldIndex == newIndex || newIndex !in current.partIds.indices) return
        if (started && oldIndex == partIndex) {
            val runtime = current.partRuntimeTicks.getOrElse(oldIndex) { 0L }
            val position = if (ended) runtime else oldPositionMs * TICKS_PER_MS
            reportStop(current, oldIndex, position, ended)
        }
        partIndex = newIndex
        playSessionId = UUID.randomUUID().toString()
        started = false
        if (playbackManager.playbackState.value.isPlaying) reportStart(current, newIndex)
    }

    fun end(globalSeconds: Double) {
        val current = session ?: return
        stopProgress()
        if (started) {
            val runtime = current.partRuntimeTicks.getOrElse(partIndex) { 0L }
            val position = positionInPartTicks(current, globalSeconds)
            val ended = runtime > 0 && position >= runtime - TICKS_PER_SECOND
            reportStop(current, partIndex, if (ended) runtime else position, ended)
        }
        session = null
        partIndex = -1
        started = false
    }

    private fun positionInPartTicks(
        current: JellyfinAudiobookSession,
        globalSeconds: Double = playbackManager.playbackState.value.currentTime,
    ): Long {
        val track = current.tracks.getOrNull(partIndex) ?: return 0L
        val seconds = (globalSeconds - track.startOffset).coerceIn(0.0, track.duration)
        return (seconds * TICKS_PER_SECOND).toLong()
    }

    private fun startProgress() {
        stopProgress()
        progressJob = scope.launch {
            while (isActive) {
                delay(10.seconds)
                val current = session ?: break
                if (!started) continue
                reportProgress(current, partIndex, positionInPartTicks(current), false)
            }
        }
    }

    private fun stopProgress() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun reportStart(current: JellyfinAudiobookSession, index: Int) {
        val partId = current.partIds.getOrNull(index) ?: return
        val sessionId = playSessionId
        started = true
        inOrder {
            playbackRepository.reportPlaybackStart(
                itemId = partId,
                sessionId = sessionId,
                mediaSourceId = partId.toString(),
                playMethod = "DirectPlay",
                canSeek = true,
            )
        }
    }

    private fun reportProgress(
        current: JellyfinAudiobookSession,
        index: Int,
        positionTicks: Long,
        paused: Boolean,
    ) {
        val partId = current.partIds.getOrNull(index) ?: return
        val sessionId = playSessionId
        inOrder {
            playbackRepository.reportPlaybackProgress(
                itemId = partId,
                sessionId = sessionId,
                positionTicks = positionTicks,
                isPaused = paused,
                playMethod = "DirectPlay",
            )
        }
    }

    private fun reportStop(
        current: JellyfinAudiobookSession,
        index: Int,
        positionTicks: Long,
        ended: Boolean,
    ) {
        val partId = current.partIds.getOrNull(index) ?: return
        val runtime = current.partRuntimeTicks.getOrElse(index) { 0L }
        val sessionId = playSessionId
        applyLocally(partId, positionTicks, runtime, ended)
        inOrder {
            playbackRepository.reportPlaybackStop(
                itemId = partId,
                sessionId = sessionId,
                positionTicks = positionTicks,
                mediaSourceId = partId.toString(),
                runtimeTicks = runtime,
                isEnded = ended,
                isAudiobook = true,
            )
        }
    }

    private fun applyLocally(
        partId: UUID,
        positionTicks: Long,
        runtimeTicks: Long,
        ended: Boolean,
    ) {
        val finished =
            ended ||
                (runtimeTicks > 0 && runtimeTicks - positionTicks < AUDIOBOOK_RESUME_WINDOW_TICKS)
        val patch =
            when {
                finished -> UserDataPatch(played = true, playbackPositionTicks = 0L)
                positionTicks < AUDIOBOOK_RESUME_WINDOW_TICKS ->
                    UserDataPatch(playbackPositionTicks = 0L)
                else -> UserDataPatch(playbackPositionTicks = positionTicks)
            }
        itemStore.applyPatch(partId, patch)
    }

    private fun inOrder(block: suspend () -> Unit) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            reportOrder.withLock {
                try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Failed to report audiobook playback")
                }
            }
        }
    }
}
