package com.makd.afinity.data.models.player

import com.makd.afinity.data.models.livetv.LiveTvPlaybackInfo
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinitySegment
import java.util.UUID

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
    val isControlsLocked: Boolean = false,
    val isInPictureInPictureMode: Boolean = false,
)

data class PlayerError(val code: Int, val message: String, val cause: Throwable? = null)

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

    object EnterPictureInPicture : PlayerEvent()

    data class LoadMedia(
        val item: AfinityItem,
        val mediaSourceId: String,
        val audioStreamIndex: Int? = null,
        val subtitleStreamIndex: Int? = null,
        val startPositionMs: Long = 0L,
    ) : PlayerEvent()

    data class LoadLiveChannel(
        val channelId: UUID,
        val channelName: String,
        val streamUrl: String,
        val playbackInfo: LiveTvPlaybackInfo,
    ) : PlayerEvent()

    data class SkipSegment(val segment: AfinitySegment) : PlayerEvent()

    object OnSeekBarDragStart : PlayerEvent()

    data class OnSeekBarValueChange(val positionMs: Long) : PlayerEvent()

    data class OnSeekBarDragFinished(val positionMs: Long) : PlayerEvent()

    object ToggleRemainingTime : PlayerEvent()

    data class SetVideoZoomMode(val mode: VideoZoomMode) : PlayerEvent()

    data object CycleVideoZoomMode : PlayerEvent()

    data object CycleScreenRotation : PlayerEvent()

    data object RequestCastDeviceSelection : PlayerEvent()

    data class SwitchVersion(val mediaSourceId: String) : PlayerEvent()

    data object TogglePlaybackStats : PlayerEvent()

    data object ToggleVersionPicker : PlayerEvent()
}

data class PlaybackStats(
    val playerType: String = "Unknown",
    val playMethod: String = "Unknown",
    val videoOutput: String = "",
    val connection: String = "",
    val decoderName: String = "",
    val avSync: String = "",
    val networkSpeed: String = "",
    val cached: String = "",
    val videoRange: String = "",
    val colorInfo: String = "",
    val frameRate: String = "",
    val container: String = "",
    val subtitleTrack: String = "",
    val audioBitrate: String = "",
    val videoResolution: String = "Unknown",
    val videoCodec: String = "Unknown",
    val audioCodec: String = "Unknown",
    val audioChannels: Int = 0,
    val audioSampleRate: Int = 0,
    val droppedFrames: Int = 0,
    val hwDec: String = "Unknown",
    val bufferHealth: String = "Unknown",
    val videoBitrate: String = "Unknown",
) {
    val hasVideo: Boolean
        get() = !videoResolution.startsWith("0x0") && videoResolution != "Unknown"

    companion object {
        fun friendlyCodecName(mimeType: String?): String {
            val subtype =
                mimeType?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: return "UNKNOWN"
            return when (subtype.lowercase()) {
                "mp4a-latm" -> "AAC"
                "avc" -> "H264"
                "raw" -> "PCM"
                "true-hd" -> "TRUEHD"
                "vnd.dts" -> "DTS"
                "vnd.dts.hd" -> "DTS-HD"
                else -> subtype.uppercase()
            }
        }
    }
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
    val gestureAccumulationThreshold: Float = 30f,
)
