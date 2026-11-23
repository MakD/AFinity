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
    val audioCodec: String? = null,
    val isServerTranscode: Boolean = false,
    val positionSampledAtMs: Long = 0L,
    val positionEpoch: Long = 0L,
) {
    fun positionAt(uptimeMs: Long): Long {
        if (!isPlaying) return positionMs
        val estimate = positionMs + (uptimeMs - positionSampledAtMs).coerceAtLeast(0L)
        return if (durationMs > 0L) estimate.coerceAtMost(durationMs) else estimate
    }
}
