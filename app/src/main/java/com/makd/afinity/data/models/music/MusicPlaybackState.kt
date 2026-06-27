package com.makd.afinity.data.models.music

data class MusicPlaybackState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val currentTrack: AfinityTrack? = null,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val shuffled: Boolean = false,
    val sleepTimerEndMs: Long? = null,
)