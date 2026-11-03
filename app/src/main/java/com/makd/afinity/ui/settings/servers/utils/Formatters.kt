package com.makd.afinity.ui.settings.servers.utils

import android.content.Context
import com.makd.afinity.R
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

internal fun formatRelativeTime(context: Context, time: LocalDateTime): String {
    val agoMinutes = ChronoUnit.MINUTES.between(time, LocalDateTime.now())
    return when {
        agoMinutes < 1 -> context.getString(R.string.task_just_now)
        agoMinutes < 60 -> context.getString(R.string.task_minutes_ago, agoMinutes)
        agoMinutes < 1440 -> context.getString(R.string.task_hours_ago, agoMinutes / 60)
        else -> context.getString(R.string.task_days_ago, agoMinutes / 1440)
    }
}
