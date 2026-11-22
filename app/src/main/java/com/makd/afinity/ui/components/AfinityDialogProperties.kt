package com.makd.afinity.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.isCrossWindowBlurEnabled
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy

private val DialogBlurRadius = 14.dp
private const val BlurredScrimAlpha = 0.18f

@Composable
fun afinityDialogProperties(
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    securePolicy: SecureFlagPolicy = SecureFlagPolicy.Inherit,
    usePlatformDefaultWidth: Boolean = true,
    decorFitsSystemWindows: Boolean = true,
): DialogProperties {
    val blurEnabled = LocalWindowInfo.current.isCrossWindowBlurEnabled
    return remember(
        dismissOnBackPress,
        dismissOnClickOutside,
        securePolicy,
        usePlatformDefaultWidth,
        decorFitsSystemWindows,
        blurEnabled,
    ) {
        DialogProperties(
            dismissOnBackPress = dismissOnBackPress,
            dismissOnClickOutside = dismissOnClickOutside,
            securePolicy = securePolicy,
            usePlatformDefaultWidth = usePlatformDefaultWidth,
            decorFitsSystemWindows = decorFitsSystemWindows,
            blurBehindRadius = if (blurEnabled) DialogBlurRadius else Dp.Unspecified,
            scrimAlpha = if (blurEnabled) BlurredScrimAlpha else Float.NaN,
        )
    }
}
