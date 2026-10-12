package com.makd.afinity.ui.admin.components

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.makd.afinity.ui.player.utils.findActivity

@Composable
fun SecureWindowEffect(enabled: Boolean) {
    val context = LocalContext.current
    val window = remember(context) { context.findActivity()?.window } ?: return
    DisposableEffect(window, enabled) {
        if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (enabled) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}
