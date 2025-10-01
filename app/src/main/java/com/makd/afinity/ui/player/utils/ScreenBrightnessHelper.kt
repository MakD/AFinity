package com.makd.afinity.ui.player.utils

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

@Composable
fun ScreenBrightnessController(brightness: Float) {
    val context = LocalContext.current

    LaunchedEffect(brightness) {
        if (brightness >= 0f) {
            val activity = context as? Activity
            activity?.let {
                val layoutParams = it.window.attributes
                layoutParams.screenBrightness = brightness
                it.window.attributes = layoutParams
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val activity = context as? Activity
            activity?.let {
                val layoutParams = it.window.attributes
                layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                it.window.attributes = layoutParams
            }
        }
    }
}