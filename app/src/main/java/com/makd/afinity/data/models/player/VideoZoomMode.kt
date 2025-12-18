package com.makd.afinity.data.models.player

import androidx.annotation.OptIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout

enum class VideoZoomMode(val value: Int) {
    FIT(0),
    ZOOM(1);

    companion object {
        fun fromInt(value: Int) = entries.firstOrNull { it.value == value } ?: FIT
    }

    @OptIn(UnstableApi::class)
    fun toExoPlayerResizeMode(): Int {
        return when (this) {
            FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        }
    }

    fun getDisplayName(): String {
        return when (this) {
            FIT -> "Fit"
            ZOOM -> "Zoom"
        }
    }

    fun getIconVector(): ImageVector {
        return when (this) {
            FIT -> Icons.Default.Fullscreen
            ZOOM -> Icons.Default.FullscreenExit
        }
    }

    fun toggle(): VideoZoomMode {
        return when (this) {
            FIT -> ZOOM
            ZOOM -> FIT
        }
    }
}