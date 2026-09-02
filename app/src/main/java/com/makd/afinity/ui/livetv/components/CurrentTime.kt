package com.makd.afinity.ui.livetv.components

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDateTime

const val CURRENT_TIME_TICK_MS = 30_000L

private val tickerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

private val currentTimeTicker: StateFlow<LocalDateTime> =
    flow {
            while (true) {
                emit(LocalDateTime.now())
                delay(CURRENT_TIME_TICK_MS - System.currentTimeMillis() % CURRENT_TIME_TICK_MS)
            }
        }
        .stateIn(
            scope = tickerScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LocalDateTime.now(),
        )

@Composable
fun rememberCurrentTime(): LocalDateTime =
    currentTimeTicker.collectAsStateWithLifecycle().value