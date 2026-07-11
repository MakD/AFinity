package com.makd.afinity.ui.settings.servers.utils

import android.content.Context
import com.makd.afinity.R
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

internal fun formatLastRun(
    context: Context,
    startTimeUtc: LocalDateTime?,
    endTimeUtc: LocalDateTime?,
): String {
    endTimeUtc ?: return context.getString(R.string.task_never_run)
    return try {
        val now = LocalDateTime.now()
        val agoMinutes = ChronoUnit.MINUTES.between(endTimeUtc, now)
        val agoText =
            when {
                agoMinutes < 1 -> context.getString(R.string.task_just_now)
                agoMinutes < 60 -> context.getString(R.string.task_minutes_ago, agoMinutes)
                agoMinutes < 1440 -> context.getString(R.string.task_hours_ago, agoMinutes / 60)
                else -> context.getString(R.string.task_days_ago, agoMinutes / 1440)
            }
        val durationText =
            if (startTimeUtc != null) {
                val durationSeconds = ChronoUnit.SECONDS.between(startTimeUtc, endTimeUtc)
                when {
                    durationSeconds < 60 ->
                        context.getString(R.string.task_duration_less_than_minute)
                    durationSeconds < 3600 ->
                        context.getString(R.string.task_duration_minutes, durationSeconds / 60)
                    else ->
                        context.getString(
                            R.string.task_duration_hours_minutes,
                            durationSeconds / 3600,
                            (durationSeconds % 3600) / 60,
                        )
                }
            } else ""
        context.getString(R.string.task_last_ran_fmt, agoText, durationText)
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
