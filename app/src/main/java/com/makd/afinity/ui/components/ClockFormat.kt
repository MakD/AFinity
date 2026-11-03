package com.makd.afinity.ui.components

import java.util.Locale

private const val MS_PER_SECOND = 1_000L
private const val TICKS_PER_SECOND = 10_000_000L
private const val SECONDS_PER_HOUR = 3_600L
private const val SECONDS_PER_MINUTE = 60L

fun formatClock(ms: Long): String = clockText(ms / MS_PER_SECOND)

fun formatClockSeconds(seconds: Double): String = clockText(seconds.toLong())

fun formatClockTicks(ticks: Long): String = clockText(ticks / TICKS_PER_SECOND)

private fun clockText(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0L)
    val hours = seconds / SECONDS_PER_HOUR
    val minutes = seconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val secs = seconds % SECONDS_PER_MINUTE
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, secs)
    }
}
