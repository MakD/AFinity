package com.makd.afinity.ui.admin.tasks

import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.makd.afinity.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import org.jellyfin.sdk.model.api.DayOfWeek
import org.jellyfin.sdk.model.api.TaskTriggerInfo
import org.jellyfin.sdk.model.api.TaskTriggerInfoType

internal const val TICKS_PER_MINUTE = 600_000_000L
internal const val TICKS_PER_HOUR = 60 * TICKS_PER_MINUTE
private const val TICKS_PER_HALF_HOUR = TICKS_PER_HOUR / 2
private const val TICKS_PER_DAY = 24 * TICKS_PER_HOUR
private const val MIN_LIMIT_HALF_HOURS = 2.0
private const val MAX_LIMIT_HALF_HOURS = 2.0 * 24 * 365
private const val NANOS_PER_TICK = 100L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 60 * SECONDS_PER_MINUTE

internal fun formatTimeOfDay(hour: Int, minute: Int): String =
    LocalTime.of(hour, minute).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

internal fun formatTimeOfDayTicks(ticks: Long): String {
    val nanos = ticks.coerceIn(0L, TICKS_PER_DAY - 1) * NANOS_PER_TICK
    return LocalTime.ofNanoOfDay(nanos).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
}

internal fun formatDurationMinutes(totalMinutes: Long): String =
    formatDurationMinutes(totalMinutes, MeasureFormat.FormatWidth.WIDE)

internal fun formatDurationCompact(totalMinutes: Long): String {
    val width =
        when {
            totalMinutes < 60 -> MeasureFormat.FormatWidth.SHORT
            totalMinutes % 60 == 0L -> MeasureFormat.FormatWidth.WIDE
            else -> MeasureFormat.FormatWidth.NARROW
        }
    return formatDurationMinutes(totalMinutes, width)
}

internal fun parseLimitHours(text: String): Long? {
    val halfHours = text.trim().replace(',', '.').toDoubleOrNull()?.times(2) ?: return null
    val isHalfHourStep = halfHours == Math.floor(halfHours)
    if (!isHalfHourStep || halfHours < MIN_LIMIT_HALF_HOURS || halfHours > MAX_LIMIT_HALF_HOURS) {
        return null
    }
    return halfHours.toLong() * TICKS_PER_HALF_HOUR
}

internal fun formatLimitHours(ticks: Long): String {
    val halfHours = ticks / TICKS_PER_HALF_HOUR
    return if (halfHours % 2 == 0L) (halfHours / 2).toString() else (halfHours / 2.0).toString()
}

private fun formatDurationMinutes(totalMinutes: Long, width: MeasureFormat.FormatWidth): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val measures = buildList {
        if (hours > 0) add(Measure(hours, MeasureUnit.HOUR))
        if (minutes > 0 || hours == 0L) add(Measure(minutes, MeasureUnit.MINUTE))
    }
    return MeasureFormat.getInstance(Locale.getDefault(), width)
        .formatMeasures(*measures.toTypedArray())
}

internal fun formatDurationTicks(ticks: Long): String =
    formatDurationMinutes(ticks / TICKS_PER_MINUTE)

internal fun formatRunDuration(start: LocalDateTime, end: LocalDateTime): String? {
    val totalSeconds = ChronoUnit.SECONDS.between(start, end)
    if (totalSeconds < 1) return null
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = totalSeconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    val measures = buildList {
        if (hours > 0) add(Measure(hours, MeasureUnit.HOUR))
        if (minutes > 0) add(Measure(minutes, MeasureUnit.MINUTE))
        if (hours == 0L && seconds > 0) add(Measure(seconds, MeasureUnit.SECOND))
    }
    return MeasureFormat.getInstance(Locale.getDefault(), MeasureFormat.FormatWidth.WIDE)
        .formatMeasures(*measures.toTypedArray())
}

@Composable
internal fun formatFinishTime(end: LocalDateTime): String {
    val today = LocalDate.now()
    val time = end.toLocalTime().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
    return when (end.toLocalDate()) {
        today -> stringResource(R.string.task_finished_today_fmt, time)
        today.minusDays(1) -> stringResource(R.string.task_finished_yesterday_fmt, time)
        else ->
            end.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
    }
}

@Composable
internal fun List<TaskTriggerInfo>.scheduleSummary(): String {
    val first = firstOrNull() ?: return stringResource(R.string.task_schedule_manual)
    return if (size == 1) first.label()
    else stringResource(R.string.task_schedule_more_fmt, first.label(), size - 1)
}

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
