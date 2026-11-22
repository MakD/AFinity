package com.makd.afinity.ui.player.utils

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposableTarget
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalWindow

@ComposableTarget("androidx.compose.ui.UiComposable")
@Composable
fun KeepScreenOn(keepOn: Boolean) {
    val window = LocalWindow.current

    DisposableEffect(window, keepOn) {
        if (keepOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}
