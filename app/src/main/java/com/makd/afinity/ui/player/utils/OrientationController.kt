package com.makd.afinity.ui.player.utils

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import timber.log.Timber

@Composable
fun PlayerOrientationController() {
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val activity = context as? Activity
        if (activity == null) return@DisposableEffect onDispose { }

        val originalOrientation = activity.requestedOrientation

        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        Timber.d("PlayerOrientationController: Forced sensor landscape orientation")

        onDispose {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            Timber.d("PlayerOrientationController: Set orientation to unspecified for smooth transition")
        }
    }
}

@Composable
fun DetailScreenOrientationController() {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val activity = context as? Activity
        if (activity == null) return@LaunchedEffect

        delay(100)

        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        delay(300)
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        Timber.d("DetailScreenOrientationController: Guided transition to portrait")
    }
}