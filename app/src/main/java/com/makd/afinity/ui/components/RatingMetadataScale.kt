package com.makd.afinity.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class RatingMetadataScale(val textScale: Float, val imdbIconSize: Dp, val rtIconSize: Dp)

@Composable
fun rememberRatingMetadataScale(): RatingMetadataScale {
    val fontScale = LocalDensity.current.fontScale
    return remember(fontScale) {
        RatingMetadataScale(
            textScale = if (fontScale > 1.3f) 0.8f else if (fontScale > 1.15f) 0.9f else 1f,
            imdbIconSize = if (fontScale > 1.3f) 14.dp else if (fontScale > 1.15f) 16.dp else 18.dp,
            rtIconSize = if (fontScale > 1.3f) 10.dp else if (fontScale > 1.15f) 11.dp else 12.dp,
        )
    }
}