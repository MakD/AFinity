package com.makd.afinity.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class RatingMetadataScale(val textScale: Float, val imdbIconSize: Dp, val rtIconSize: Dp)

private val DefaultRatingMetadataScale =
    RatingMetadataScale(
        textScale = 1f,
        imdbIconSize = 18.dp,
        rtIconSize = 12.dp,
    )

@Composable fun rememberRatingMetadataScale(): RatingMetadataScale = DefaultRatingMetadataScale
