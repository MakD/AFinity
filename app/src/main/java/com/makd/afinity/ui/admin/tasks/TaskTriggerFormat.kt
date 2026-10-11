package com.makd.afinity.ui.admin.tasks

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.makd.afinity.R
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import org.jellyfin.sdk.model.api.DayOfWeek
import org.jellyfin.sdk.model.api.TaskTriggerInfo
import org.jellyfin.sdk.model.api.TaskTriggerInfoType

internal const val TICKS_PER_MINUTE = 600_000_000L
internal const val TICKS_PER_HOUR = 60 * TICKS_PER_MINUTE
private const val TICKS_PER_DAY = 24 * TICKS_PER_HOUR
private const val NANOS_PER_TICK = 100L

internal fun formatTimeOfDay(hour: Int, minute: Int): String =
    LocalTime.of(hour, minute).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

internal fun formatTimeOfDayTicks(ticks: Long): String {
    val nanos = ticks.coerceIn(0L, TICKS_PER_DAY - 1) * NANOS_PER_TICK
    return LocalTime.ofNanoOfDay(nanos).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
}

internal fun formatDurationMinutes(totalMinutes: Long): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val measures = buildList {
        if (hours > 0) add(Measure(hours, MeasureUnit.HOUR))
        if (minutes > 0 || hours == 0L) add(Measure(minutes, MeasureUnit.MINUTE))
    }
    return MeasureFormat.getInstance(Locale.getDefault(), MeasureFormat.FormatWidth.WIDE)
        .formatMeasures(*measures.toTypedArray())
}

internal fun formatDurationTicks(ticks: Long): String =
    formatDurationMinutes(ticks / TICKS_PER_MINUTE)

internal fun DayOfWeek.displayName(): String =
    java.time.DayOfWeek.valueOf(name).getDisplayName(TextStyle.FULL, Locale.getDefault())

@Composable
internal fun TaskTriggerInfo.label(): String =
    when (type) {
        TaskTriggerInfoType.DAILY_TRIGGER ->
            stringResource(
                R.string.task_trigger_daily_fmt,
                formatTimeOfDayTicks(timeOfDayTicks ?: 0L),
            )
        TaskTriggerInfoType.WEEKLY_TRIGGER ->
            stringResource(
                R.string.task_trigger_weekly_fmt,
                dayOfWeek?.displayName().orEmpty(),
                formatTimeOfDayTicks(timeOfDayTicks ?: 0L),
            )
        TaskTriggerInfoType.INTERVAL_TRIGGER ->
            stringResource(
                R.string.task_trigger_interval_fmt,
                formatDurationTicks(intervalTicks ?: 0L),
            )
        TaskTriggerInfoType.STARTUP_TRIGGER -> stringResource(R.string.task_trigger_startup)
    }

internal fun TaskTriggerInfoType.iconRes(): Int =
    when (this) {
        TaskTriggerInfoType.DAILY_TRIGGER -> R.drawable.ic_clock
        TaskTriggerInfoType.WEEKLY_TRIGGER -> R.drawable.ic_calendar
        TaskTriggerInfoType.INTERVAL_TRIGGER -> R.drawable.ic_repeat
        TaskTriggerInfoType.STARTUP_TRIGGER -> R.drawable.ic_power
    }
