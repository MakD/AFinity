package com.makd.afinity.ui.player.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

@Composable
fun ScreenBrightnessController(
    brightness: Float
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() } ?: return

    LaunchedEffect(brightness) {
        val layoutParams = activity.window.attributes

        layoutParams.screenBrightness = if (brightness < 0f) {
            WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        } else {
            brightness.coerceIn(0f, 1f)
        }

        activity.window.attributes = layoutParams
    }

    DisposableEffect(Unit) {
        onDispose {
            val layoutParams = activity.window.attributes
            layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            activity.window.attributes = layoutParams
        }
    }
}

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}