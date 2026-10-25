package com.makd.afinity.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.view.WindowInsetsControllerCompat

@Stable
class StatusBarIconsState {
    var darkStatusBarRequests by mutableIntStateOf(0)
}

val LocalStatusBarIcons = staticCompositionLocalOf { StatusBarIconsState() }

@Composable
fun RequestDarkStatusBar(enabled: Boolean) {
    val state = LocalStatusBarIcons.current
    DisposableEffect(state, enabled) {
        if (enabled) state.darkStatusBarRequests++
        onDispose { if (enabled) state.darkStatusBarRequests-- }
    }
}

@Composable
fun StatusBarIconsEffect(
    controller: WindowInsetsControllerCompat,
    isLightTheme: Boolean,
    state: StatusBarIconsState,
) {
    val darkStatusBar = state.darkStatusBarRequests > 0
    SideEffect { controller.isAppearanceLightStatusBars = isLightTheme && !darkStatusBar }
}
