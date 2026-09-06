package com.makd.afinity.ui.player.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs

@Composable
fun rememberSleepTimerRemainingMs(endTimeMs: Long?): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(endTimeMs) {
        if (endTimeMs == null) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            if (now >= endTimeMs) break
            delay(1_000L)
        }
    }

    return if (endTimeMs == null) 0L else (endTimeMs - now).coerceAtLeast(0L)
}

fun formatSleepCountdown(timeMs: Long): String {
    val totalSeconds = abs(timeMs / 1000).toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}
