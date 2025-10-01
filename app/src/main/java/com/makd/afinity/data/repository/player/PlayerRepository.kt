package com.makd.afinity.data.repository.player

import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.player.PlayerState
import kotlinx.coroutines.flow.StateFlow

interface PlayerRepository {
    val playerState: StateFlow<PlayerState>

    suspend fun loadMedia(
        item: AfinityItem,
        mediaSourceId: String,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
        startPositionMs: Long = 0L
    ): Boolean

    suspend fun play()
    suspend fun pause()
    suspend fun stop()
    suspend fun seekTo(positionMs: Long)
    suspend fun seekRelative(deltaMs: Long)
    suspend fun setVolume(volume: Int)
    suspend fun setBrightness(brightness: Float)
    suspend fun setPlaybackSpeed(speed: Float)
    suspend fun selectAudioTrack(index: Int)
    suspend fun selectSubtitleTrack(index: Int?)
    suspend fun toggleFullscreen()
    suspend fun toggleControlsLock()

    suspend fun reportPlaybackStart()
    suspend fun reportPlaybackProgress()
    suspend fun reportPlaybackStop()

    fun setOnPlaybackStoppedCallback(callback: () -> Unit)

    fun onResume()
    fun onPause()
    fun onDestroy()
}