package com.makd.afinity.ui.utils

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val DEFAULT_LIST_SCROLL_THRESHOLD = 450.dp
private val DEFAULT_GRID_SCROLL_THRESHOLD = 64.dp

@Composable
fun rememberTopBarOpacity(
    state: LazyListState,
    scrollThreshold: Dp = DEFAULT_LIST_SCROLL_THRESHOLD,
): State<Float> {
    val density = LocalDensity.current
    val thresholdPx =
        remember(density, scrollThreshold) { with(density) { scrollThreshold.toPx() } }
    return remember(state, thresholdPx) {
        derivedStateOf {
            val firstIndex = state.firstVisibleItemIndex
            val offset = state.firstVisibleItemScrollOffset
            if (firstIndex > 0 || offset > thresholdPx) 1f else 0f
        }
    }
}

@Composable
fun rememberTopBarOpacity(
    state: LazyGridState,
    scrollThreshold: Dp = DEFAULT_GRID_SCROLL_THRESHOLD,
): State<Float> {
    val density = LocalDensity.current
    val thresholdPx =
        remember(density, scrollThreshold) { with(density) { scrollThreshold.toPx() } }
    return remember(state, thresholdPx) {
        derivedStateOf {
            val offset = state.firstVisibleItemScrollOffset
            (offset / thresholdPx).coerceIn(0f, 1f)
        }
    }
}
