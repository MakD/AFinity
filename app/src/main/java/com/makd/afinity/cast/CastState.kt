package com.makd.afinity.cast

import com.makd.afinity.data.models.media.AfinityItem
import java.util.UUID

data class CastSessionState(
    val isConnected: Boolean = false,
    val deviceName: String? = null,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val currentItem: AfinityItem? = null,
    val currentItemId: UUID? = null,
    val volume: Double = 1.0,
    val isMuted: Boolean = false,
    val audioStreamIndex: Int? = null,
    val subtitleStreamIndex: Int? = null,
    val mediaSourceId: String? = null,
    val sessionId: String? = null,
    val castBitrate: Int = 16_000_000,
    val playbackSpeed: Float = 1.0f,
    val playMethod: String = "DirectPlay",
    val serverBaseUrl: String? = null,
    val enableHevc: Boolean = false,
    val loadedTextTrackIndices: Set<Int> = emptySet(),
)

sealed class CastEvent {
    data class Connected(val deviceName: String) : CastEvent()
    data class Disconnected(val lastPositionMs: Long = 0L) : CastEvent()
    data class PlaybackStarted(val itemId: UUID) : CastEvent()
    data class PlaybackError(val message: String) : CastEvent()
}