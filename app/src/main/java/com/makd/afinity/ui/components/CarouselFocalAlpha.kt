package com.makd.afinity.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.carousel.CarouselItemDrawInfo

@OptIn(ExperimentalMaterial3Api::class)
val CarouselItemDrawInfo.focalAlpha: Float
    get() {
        val range = maxSize - minSize
        return if (range <= 0f) 1f else ((size - minSize) / range).coerceIn(0f, 1f)
    }
