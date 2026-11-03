package com.makd.afinity.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.makd.afinity.R
import java.util.Locale
import kotlin.math.ceil

private const val TICKS_PER_MINUTE = 600_000_000.0
private const val SECONDS_PER_MINUTE = 60.0
private const val MINUTES_PER_HOUR = 60

fun ticksToTotalMinutes(ticks: Long): Int =
    if (ticks <= 0L) 0 else ceil(ticks / TICKS_PER_MINUTE).toInt()

fun secondsToTotalMinutes(seconds: Double): Int =
    if (seconds <= 0.0) 0 else ceil(seconds / SECONDS_PER_MINUTE).toInt()

fun formatRuntime(context: Context, totalMinutes: Int): String =
    hoursMinutesText(
        totalMinutes,
        context.getString(R.string.meta_runtime_hours_minutes),
        context.getString(R.string.meta_runtime_minutes),
    )

@Composable
fun formatRuntimeMinutes(minutes: Int): String =
    hoursMinutesText(
        minutes,
        stringResource(R.string.meta_runtime_hours_minutes),
        stringResource(R.string.meta_runtime_minutes),
    )

@Composable
fun formatRuntimeTicks(ticks: Long): String = formatRuntimeMinutes(ticksToTotalMinutes(ticks))

@Composable
fun formatRuntimeSeconds(seconds: Double): String =
    formatRuntimeMinutes(secondsToTotalMinutes(seconds))

@Composable
fun formatTimeLeftMinutes(minutes: Int): String =
    hoursMinutesText(
        minutes,
        stringResource(R.string.meta_time_remaining_hours_minutes),
        stringResource(R.string.meta_time_remaining_minutes),
    )

@Composable
fun formatTimeLeftTicks(ticks: Long): String = formatTimeLeftMinutes(ticksToTotalMinutes(ticks))

@Composable
fun formatTimeLeftSeconds(seconds: Double): String =
    formatTimeLeftMinutes(secondsToTotalMinutes(seconds))

private fun hoursMinutesText(
    totalMinutes: Int,
    hoursPattern: String,
    minutesPattern: String,
): String {
    val minutes = totalMinutes.coerceAtLeast(0)
    val hours = minutes / MINUTES_PER_HOUR
    return if (hours > 0) {
        String.format(Locale.ROOT, hoursPattern, hours, minutes % MINUTES_PER_HOUR)
    } else {
        String.format(Locale.ROOT, minutesPattern, minutes)
    }
}
