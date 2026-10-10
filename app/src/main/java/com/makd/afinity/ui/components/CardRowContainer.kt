package com.makd.afinity.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.onConsumedWindowInsetsChanged
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.theme.LocalCardContainerWidthPx
import com.makd.afinity.ui.theme.LocalCardRowGutter

@Composable
fun CardRowContainer(
    modifier: Modifier = Modifier,
    gutter: Dp = CardDimensions.RowGutter,
    excludeDisplayCutout: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    var consumedInsets by remember { mutableStateOf(WindowInsets(0, 0, 0, 0)) }
    val containerModifier =
        if (excludeDisplayCutout) {
            modifier.onConsumedWindowInsetsChanged { consumedInsets = it }
        } else {
            modifier
        }

    BoxWithConstraints(modifier = containerModifier) {
        val cutoutPx =
            if (excludeDisplayCutout) {
                val density = LocalDensity.current
                val layoutDirection = LocalLayoutDirection.current
                val cutout = WindowInsets.displayCutout.exclude(consumedInsets)
                cutout.getLeft(density, layoutDirection) + cutout.getRight(density, layoutDirection)
            } else {
                0
            }
        val containerWidthPx =
            if (constraints.hasBoundedWidth) constraints.maxWidth - cutoutPx else -1

        CompositionLocalProvider(
            LocalCardContainerWidthPx provides containerWidthPx,
            LocalCardRowGutter provides gutter,
        ) {
            content()
        }
    }
}
