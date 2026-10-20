package com.makd.afinity.cast

import com.makd.afinity.player.audiobookshelf.AudiobookshelfPlaybackManager
import com.makd.afinity.player.music.MusicPlaybackManager
import com.makd.afinity.player.music.MusicQueueManager
import dagger.Lazy
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Singleton
class CastPlaybackBridge
@Inject
constructor(
    private val castManager: CastManager,
    private val musicPlaybackManager: Lazy<MusicPlaybackManager>,
    private val musicQueueManager: Lazy<MusicQueueManager>,
    private val audiobookshelfPlaybackManager: Lazy<AudiobookshelfPlaybackManager>,
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            castManager.castState.collect { state ->
                when {
                    state.isMusicCasting -> syncMusic(state)
                    state.isAbsCasting -> syncAbs(state)
                }
            }
        }
    }

    private fun syncMusic(state: CastSessionState) {
        val playbackManager = musicPlaybackManager.get()
        val trackId = state.musicTrackId
        if (trackId != null && playbackManager.state.value.currentTrack?.id != trackId) {
            val queueManager = musicQueueManager.get()
            val queue = queueManager.queue.value
            val index = queue.indexOfFirst { it.id == trackId }
            if (index >= 0) {
                queueManager.onTrackChanged(index)
                playbackManager.updateTrack(queue[index])
            }
        }
        val durationMs =
            playbackManager.state.value.currentTrack?.runtimeTicks?.div(10_000) ?: state.duration
        playbackManager.updatePosition(state.currentPosition, 0L, durationMs)
        playbackManager.updatePlayingState(state.isPlaying)
        playbackManager.updateBufferingState(state.isBuffering)
    }

    private fun syncAbs(state: CastSessionState) {
        val playbackManager = audiobookshelfPlaybackManager.get()
        playbackManager.updatePosition(state.currentPosition / 1000.0)
        playbackManager.updatePlayingState(state.isPlaying)
        playbackManager.updateBufferingState(state.isBuffering)
    }
}
