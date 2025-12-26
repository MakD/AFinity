package com.makd.afinity.ui.player.utils

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposableTarget
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

@ComposableTarget("androidx.compose.ui.UiComposable")
@Composable
fun KeepScreenOn(keepOn: Boolean) {
    val context = LocalContext.current

    DisposableEffect(keepOn) {
        val activity = context as? Activity
        if (keepOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}