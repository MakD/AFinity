package com.makd.afinity.ui.admin.components

import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalWindow

@Composable
fun SecureWindowEffect(enabled: Boolean) {
    val window = LocalWindow.current ?: return
    DisposableEffect(window, enabled) {
        if (enabled) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (enabled) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}
