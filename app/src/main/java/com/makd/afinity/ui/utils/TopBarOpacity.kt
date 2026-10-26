package com.makd.afinity.ui.utils

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember

private const val LIST_SCROLL_THRESHOLD = 1500f

@Composable
fun rememberTopBarOpacity(
    state: LazyListState,
    scrollThreshold: Float = LIST_SCROLL_THRESHOLD,
): State<Float> =
    remember(state, scrollThreshold) {
        derivedStateOf {
            val firstIndex = state.firstVisibleItemIndex
            val offset = state.firstVisibleItemScrollOffset
            if (firstIndex > 0 || offset > scrollThreshold) 1f else 0f
        }
    }

@Composable
fun rememberTopBarOpacity(state: LazyGridState, scrollThreshold: Float = 200f): State<Float> =
    remember(state) {
        derivedStateOf {
            val offset = state.firstVisibleItemScrollOffset
            (offset / scrollThreshold).coerceIn(0f, 1f)
        }
    }
