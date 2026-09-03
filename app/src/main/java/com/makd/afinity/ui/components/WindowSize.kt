package com.makd.afinity.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalWindowInfo

@Composable
@ReadOnlyComposable
fun isLandscapeWindow(): Boolean {
    val size = LocalWindowInfo.current.containerSize
    return size.width > size.height
}