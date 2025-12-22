package com.makd.afinity.data.models.player

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import com.makd.afinity.R

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

    @Composable
    fun getIconPainter(): Painter {
        return when (this) {
            FIT -> painterResource(id = R.drawable.fullscreen)
            ZOOM -> painterResource(id = R.drawable.fullscreen_exit)
        }
    }


    fun toggle(): VideoZoomMode {
        return when (this) {
            FIT -> ZOOM
            ZOOM -> FIT
        }
    }
}