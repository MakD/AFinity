package com.makd.afinity.data.models.player

import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinitySegment

data class PlayerState(
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val isLoading: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val currentItem: AfinityItem? = null,
    val mediaSourceId: String? = null,
    val audioStreamIndex: Int? = null,
    val subtitleStreamIndex: Int? = null,
    val volume: Int = 100,
    val brightness: Float = 0.5f,
    val playbackSpeed: Float = 1.0f,
    val isControlsVisible: Boolean = true,
    val error: PlayerError? = null,
    val isFullscreen: Boolean = true,
    val sessionId: String? = null,
    val isControlsLocked: Boolean = false
)

data class PlayerError(
    val code: Int,
    val message: String,
    val cause: Throwable? = null
)

sealed class PlayerEvent {
    object Play : PlayerEvent()
    object Pause : PlayerEvent()
    object Stop : PlayerEvent()
    data class Seek(val positionMs: Long) : PlayerEvent()
    data class SeekRelative(val deltaMs: Long) : PlayerEvent()
    data class SetVolume(val volume: Int) : PlayerEvent()
    data class SetBrightness(val brightness: Float) : PlayerEvent()
    data class SetPlaybackSpeed(val speed: Float) : PlayerEvent()
    data class SwitchToTrack(val trackType: Int, val index: Int) : PlayerEvent()
    object ToggleControls : PlayerEvent()
    object ToggleLock : PlayerEvent()
    object ToggleFullscreen : PlayerEvent()
    data class LoadMedia(
        val item: AfinityItem,
        val mediaSourceId: String,
        val audioStreamIndex: Int? = null,
        val subtitleStreamIndex: Int? = null,
        val startPositionMs: Long = 0L
    ) : PlayerEvent()
    data class SkipSegment(val segment: AfinitySegment) : PlayerEvent()

    object OnSeekBarDragStart : PlayerEvent()
    data class OnSeekBarValueChange(val positionMs: Long) : PlayerEvent()
    object OnSeekBarDragFinished : PlayerEvent()
}

data class GestureConfig(
    val doubleTapSeekMs: Long = 10000L,
    val brightnessStepSize: Float = 0.8f,
    val volumeStepSize: Float = 70f,
    val seekStepMs: Long = 30000L,
    val fullSwipeRangeScreenRatio: Float = 0.66f,
    val gestureExclusionAreaVertical: Float = 48f,
    val gestureExclusionAreaHorizontal: Float = 24f,
    val minimumDragThreshold: Float = 15f,
    val gestureAccumulationThreshold: Float = 30f
)