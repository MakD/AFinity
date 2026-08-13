package com.makd.afinity.data.repository.playback

object PlaybackCompletion {

    private const val TICKS_PER_SECOND = 10_000_000L
    private const val MIN_RESUME_PCT = 5.0
    private const val MAX_RESUME_PCT = 90.0
    private const val MIN_RESUME_DURATION_SECONDS = 300L

    data class Resolved(val positionTicks: Long, val played: Boolean)

    fun playbackCompletionResolved(
        positionTicks: Long,
        runtimeTicks: Long,
        isEnded: Boolean,
    ): Resolved {
        if (isEnded) return Resolved(0L, true)

        val position = positionTicks.coerceAtLeast(0L)
        if (runtimeTicks <= 0L || position == 0L) return Resolved(position, false)

        val percentage = position.toDouble() / runtimeTicks.toDouble() * 100.0

        return when {
            percentage < MIN_RESUME_PCT -> Resolved(0L, false)
            percentage > MAX_RESUME_PCT || position >= runtimeTicks - TICKS_PER_SECOND ->
                Resolved(0L, true)
            runtimeTicks < MIN_RESUME_DURATION_SECONDS * TICKS_PER_SECOND -> Resolved(0L, true)
            else -> Resolved(position, false)
        }
    }
}
