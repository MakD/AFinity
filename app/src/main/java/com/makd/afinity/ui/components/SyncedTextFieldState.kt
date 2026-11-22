package com.makd.afinity.ui.components

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow

private const val MAX_PENDING_ECHOES = 32

private class TextSyncTracker(var lastKnown: String) {
    val pendingEchoes = ArrayDeque<String>()
}

@Composable
fun rememberSyncedTextFieldState(value: String, onValueChange: (String) -> Unit): TextFieldState {
    val state = rememberTextFieldState(value)
    val tracker = remember { TextSyncTracker(state.text.toString()) }
    val currentOnValueChange by rememberUpdatedState(onValueChange)

    LaunchedEffect(value) {
        val echoIndex = tracker.pendingEchoes.indexOf(value)
        if (echoIndex >= 0) {
            repeat(echoIndex + 1) { tracker.pendingEchoes.removeFirst() }
            return@LaunchedEffect
        }
        if (state.text.toString() != value) {
            tracker.pendingEchoes.clear()
            tracker.lastKnown = value
            state.setTextAndPlaceCursorAtEnd(value)
        }
    }

    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }
            .collect { text ->
                if (text != tracker.lastKnown) {
                    tracker.lastKnown = text
                    tracker.pendingEchoes.addLast(text)
                    if (tracker.pendingEchoes.size > MAX_PENDING_ECHOES) {
                        tracker.pendingEchoes.removeFirst()
                    }
                    currentOnValueChange(text)
                }
            }
    }

    return state
}
