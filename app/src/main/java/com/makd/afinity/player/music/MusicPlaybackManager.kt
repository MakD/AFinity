package com.makd.afinity.player.music

import androidx.media3.exoplayer.ExoPlayer
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.data.models.music.MusicPlaybackState
import com.makd.afinity.data.models.music.RepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicPlaybackManager @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(MusicPlaybackState())
    val state: StateFlow<MusicPlaybackState> = _state.asStateFlow()

    @Volatile private var exoPlayer: ExoPlayer? = null

    private var sleepTimerJob: Job? = null

    fun setPlayer(player: ExoPlayer) {
        exoPlayer = player
    }

    fun getPlayer(): ExoPlayer? = exoPlayer

    fun clearPlayer() {
        exoPlayer = null
        cancelSleepTimer()
    }

    fun updateTrack(track: AfinityTrack?) {
        _state.update { it.copy(currentTrack = track) }
    }

    fun updatePlayingState(isPlaying: Boolean) {
        _state.update { it.copy(isPlaying = isPlaying) }
    }

    fun updateBufferingState(isBuffering: Boolean) {
        _state.update { it.copy(isBuffering = isBuffering) }
    }

    fun updatePosition(positionMs: Long, bufferedPositionMs: Long = 0L, durationMs: Long = 0L) {
        _state.update {
            it.copy(
                positionMs = positionMs,
                bufferedPositionMs = bufferedPositionMs,
                durationMs = durationMs,
            )
        }
    }

    fun updateRepeatMode(mode: RepeatMode) {
        _state.update { it.copy(repeatMode = mode) }
    }

    fun updateShuffled(shuffled: Boolean) {
        _state.update { it.copy(shuffled = shuffled) }
    }

    fun play() {
        exoPlayer?.play()
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun stop() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        exoPlayer?.let {
            it.stop()
            it.clearMediaItems()
        }
        exoPlayer = null
        _state.update { MusicPlaybackState() }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    fun skipToNext() {
        exoPlayer?.seekToNextMediaItem()
    }

    fun skipToPrevious() {
        exoPlayer?.seekToPreviousMediaItem()
    }

    fun seekToIndex(index: Int, positionMs: Long = 0L) {
        exoPlayer?.seekTo(index, positionMs)
    }

    fun setSleepTimer(durationMs: Long) {
        sleepTimerJob?.cancel()
        val endMs = System.currentTimeMillis() + durationMs
        _state.update { it.copy(sleepTimerEndMs = endMs) }
        sleepTimerJob = scope.launch {
            delay(durationMs)
            exoPlayer?.pause()
            _state.update { it.copy(sleepTimerEndMs = null) }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _state.update { it.copy(sleepTimerEndMs = null) }
    }
}
