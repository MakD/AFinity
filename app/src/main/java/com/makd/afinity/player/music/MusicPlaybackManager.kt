package com.makd.afinity.player.music

import android.content.Context
import android.media.AudioManager
import androidx.media3.exoplayer.ExoPlayer
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.data.models.music.MusicPlaybackState
import com.makd.afinity.data.models.music.RepeatMode
import com.makd.afinity.data.websocket.JellyfinWebSocketManager
import com.makd.afinity.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.GeneralCommand
import org.jellyfin.sdk.model.api.GeneralCommandType
import org.jellyfin.sdk.model.api.PlaystateCommand
import org.jellyfin.sdk.model.api.PlaystateRequest
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

private const val TICKS_PER_MILLISECOND = 10_000L
private const val REMOTE_SEEK_STEP_MS = 30_000L
private const val REMOTE_VOLUME_STEP = 10

@Singleton
class MusicPlaybackManager
@Inject
constructor(
    @ApplicationScope private val scope: CoroutineScope,
    @ApplicationContext private val context: Context,
    jellyfinWebSocketManager: JellyfinWebSocketManager,
) {

    private val _state = MutableStateFlow(MusicPlaybackState())
    val state: StateFlow<MusicPlaybackState> = _state.asStateFlow()

    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private var isRemoteMuted = false
    private var volumeBeforeRemoteMute = 100

    init {
        scope.launch {
            jellyfinWebSocketManager.remotePlaystateCommands.collect { request ->
                if (exoPlayer == null) return@collect
                withContext(Dispatchers.Main) { applyRemotePlaystate(request) }
            }
        }
        scope.launch {
            jellyfinWebSocketManager.remoteGeneralCommands.collect { command ->
                if (exoPlayer == null) return@collect
                withContext(Dispatchers.Main) { applyRemoteGeneral(command) }
            }
        }
    }

    private fun applyRemotePlaystate(request: PlaystateRequest) {
        val player = exoPlayer ?: return
        when (request.command) {
            PlaystateCommand.PAUSE -> player.pause()
            PlaystateCommand.UNPAUSE -> player.play()
            PlaystateCommand.PLAY_PAUSE -> if (player.isPlaying) player.pause() else player.play()
            PlaystateCommand.STOP -> stop()
            PlaystateCommand.SEEK ->
                request.seekPositionTicks?.let { player.seekTo(it / TICKS_PER_MILLISECOND) }
            PlaystateCommand.REWIND -> seekRelative(-REMOTE_SEEK_STEP_MS)
            PlaystateCommand.FAST_FORWARD -> seekRelative(REMOTE_SEEK_STEP_MS)
            PlaystateCommand.NEXT_TRACK -> player.seekToNextMediaItem()
            PlaystateCommand.PREVIOUS_TRACK -> player.seekToPreviousMediaItem()
        }
    }

    private fun seekRelative(deltaMs: Long) {
        val player = exoPlayer ?: return
        val duration = player.duration
        val target = player.currentPosition.coerceAtLeast(0L) + deltaMs
        player.seekTo(
            if (duration > 0) target.coerceIn(0L, duration) else target.coerceAtLeast(0L)
        )
    }

    private fun applyRemoteGeneral(command: GeneralCommand) {
        when (command.name) {
            GeneralCommandType.SET_VOLUME ->
                command.arguments["Volume"]?.toIntOrNull()?.let { setDeviceVolume(it) }
            GeneralCommandType.VOLUME_UP ->
                setDeviceVolume(getDeviceVolume() + REMOTE_VOLUME_STEP)
            GeneralCommandType.VOLUME_DOWN ->
                setDeviceVolume(getDeviceVolume() - REMOTE_VOLUME_STEP)
            GeneralCommandType.MUTE -> applyRemoteMute(true)
            GeneralCommandType.UNMUTE -> applyRemoteMute(false)
            GeneralCommandType.TOGGLE_MUTE -> applyRemoteMute(!isRemoteMuted)
            else -> Unit
        }
    }

    private fun applyRemoteMute(muted: Boolean) {
        if (muted) {
            volumeBeforeRemoteMute = getDeviceVolume()
            isRemoteMuted = true
            setDeviceVolume(0)
        } else {
            isRemoteMuted = false
            setDeviceVolume(volumeBeforeRemoteMute.coerceAtLeast(1))
        }
    }

    private fun getDeviceVolume(): Int {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return 0
        return (audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) * 100f / max).toInt()
    }

    private fun setDeviceVolume(percent: Int) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val target = (percent.coerceIn(0, 100) / 100f * max).toInt()
        runCatching {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        }
            .onFailure { Timber.w(it, "Failed to set device volume from remote command") }
    }

    private val _currentAudioDecoder = MutableStateFlow("Unknown")
    val currentAudioDecoder: StateFlow<String> = _currentAudioDecoder.asStateFlow()

    fun updateAudioDecoder(decoderName: String, isHardwareAccelerated: Boolean) {
        _currentAudioDecoder.value = if (isHardwareAccelerated) "H/W Dec" else "S/W Dec"
    }

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
            val trackDurationMs =
                it.currentTrack?.runtimeTicks?.div(10_000L)?.takeIf { ticks -> ticks > 0L }
            it.copy(
                positionMs = positionMs,
                bufferedPositionMs = bufferedPositionMs,
                durationMs = trackDurationMs ?: durationMs,
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
