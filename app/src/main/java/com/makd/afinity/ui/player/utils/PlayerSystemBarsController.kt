package com.makd.afinity.ui.player.utils

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

@Composable
fun PlayerSystemBarsController(
    isControlsVisible: Boolean = false
) {
    val context = LocalContext.current
    val activity = context as? Activity

    LaunchedEffect(isControlsVisible) {
        activity?.let { act ->
            val window = act.window
            val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)

            if (isControlsVisible) {
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            } else {
                windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
                windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            activity?.let { act ->
                val window = act.window
                val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)

                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
                windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            }
        }
    }
}