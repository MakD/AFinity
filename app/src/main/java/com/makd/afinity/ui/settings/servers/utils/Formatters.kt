package com.makd.afinity.ui.settings.servers.utils

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

internal fun formatListeningDuration(seconds: Double): String {
    val h = (seconds / 3600).toInt()
    val m = ((seconds % 3600) / 60).toInt()
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0 -> "${h}h"
        m > 0 -> "${m}m"
        seconds > 0 -> "<1m"
        else -> "0m"
    }
}

internal fun formatLastRun(startTimeUtc: LocalDateTime?, endTimeUtc: LocalDateTime?): String {
    endTimeUtc ?: return "Never run"
    return try {
        val now = LocalDateTime.now()
        val agoMinutes = ChronoUnit.MINUTES.between(endTimeUtc, now)
        val agoText =
            when {
                agoMinutes < 1 -> "just now"
                agoMinutes < 60 -> "about ${agoMinutes}m ago"
                agoMinutes < 1440 -> "about ${agoMinutes / 60}h ago"
                else -> "${agoMinutes / 1440}d ago"
            }
        val durationText =
            if (startTimeUtc != null) {
                val durationSeconds = ChronoUnit.SECONDS.between(startTimeUtc, endTimeUtc)
                when {
                    durationSeconds < 60 -> ", taking less than a minute"
                    durationSeconds < 3600 -> ", taking ${durationSeconds / 60}m"
                    else -> ", taking ${durationSeconds / 3600}h ${(durationSeconds % 3600) / 60}m"
                }
            } else ""
        "Last ran $agoText$durationText"
    } catch (e: Exception) {
        ""
    }
}

internal fun formatTicks(ticks: Long): String {
    val totalSeconds = ticks / 10_000_000L
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}
