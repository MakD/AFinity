package com.makd.afinity.ui.livetv.components

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import java.time.LocalDateTime

const val CURRENT_TIME_TICK_MS = 30_000L

private val currentTimeTicker = flow {
    while (true) {
        emit(LocalDateTime.now())
        delay(CURRENT_TIME_TICK_MS - System.currentTimeMillis() % CURRENT_TIME_TICK_MS)
    }
}

@Composable
fun rememberCurrentTime(): LocalDateTime =
    currentTimeTicker.collectAsStateWithLifecycle(initialValue = LocalDateTime.now()).value
