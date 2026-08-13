package com.makd.afinity.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.makd.afinity.R
import kotlin.math.ceil

fun ticksToTotalMinutes(ticks: Long): Int =
    if (ticks <= 0L) 0 else ceil(ticks / 600_000_000.0).toInt()

@Composable
fun formatRuntimeTicks(ticks: Long): String {
    val totalMinutes = ticksToTotalMinutes(ticks)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) stringResource(R.string.meta_runtime_hours_minutes, hours, minutes)
    else stringResource(R.string.meta_runtime_minutes, minutes)
}
