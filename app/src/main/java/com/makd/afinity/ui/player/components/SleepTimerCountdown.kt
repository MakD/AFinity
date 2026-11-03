package com.makd.afinity.ui.player.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

@Composable
fun rememberSleepTimerRemainingMs(endTimeMs: Long?): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(endTimeMs) {
        if (endTimeMs == null) return@LaunchedEffect
        while (true) {
            now = System.currentTimeMillis()
            if (now >= endTimeMs) break
            delay(1.seconds)
        }
    }

    return if (endTimeMs == null) 0L else (endTimeMs - now).coerceAtLeast(0L)
}
