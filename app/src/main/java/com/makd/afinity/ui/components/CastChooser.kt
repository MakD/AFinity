package com.makd.afinity.ui.components

import android.view.View
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import timber.log.Timber

@Composable
fun rememberCastChooserLauncher(): () -> Unit {
    val context = LocalContext.current
    val button =
        remember(context) {
            MediaRouteButton(context).apply {
                runCatching { CastButtonFactory.setUpMediaRouteButton(context, this) }
                    .onFailure { Timber.w(it, "Failed to set up MediaRouteButton") }
                visibility = View.GONE
            }
        }

    AndroidView(factory = { button }, modifier = Modifier.size(0.dp))

    return remember(button) { { button.performClick() } }
}
