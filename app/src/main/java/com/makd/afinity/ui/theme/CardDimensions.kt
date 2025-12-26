package com.makd.afinity.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object CardDimensions {

    const val ASPECT_RATIO_PORTRAIT = 2f / 3f
    const val ASPECT_RATIO_LANDSCAPE = 16f / 9f

    object Portrait {
        val COMPACT = 140.dp
        val MEDIUM = 150.dp
        val EXPANDED = 180.dp
    }

    object Landscape {
        val COMPACT = 240.dp
        val MEDIUM = 260.dp
        val EXPANDED = 320.dp
    }

    object Grid {
        val COMPACT = 140.dp
        val MEDIUM = 160.dp
        val EXPANDED = 180.dp
    }

    object WindowSize {
        const val COMPACT_MAX_WIDTH = 600
        const val MEDIUM_MAX_WIDTH = 840
    }
}

@Composable
fun rememberPortraitCardWidth(): Dp {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    return when {
        screenWidthDp < CardDimensions.WindowSize.COMPACT_MAX_WIDTH ->
            CardDimensions.Portrait.COMPACT

        screenWidthDp < CardDimensions.WindowSize.MEDIUM_MAX_WIDTH ->
            CardDimensions.Portrait.MEDIUM

        else ->
            CardDimensions.Portrait.EXPANDED
    }
}

@Composable
fun rememberLandscapeCardWidth(): Dp {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    return when {
        screenWidthDp < CardDimensions.WindowSize.COMPACT_MAX_WIDTH ->
            CardDimensions.Landscape.COMPACT

        screenWidthDp < CardDimensions.WindowSize.MEDIUM_MAX_WIDTH ->
            CardDimensions.Landscape.MEDIUM

        else ->
            CardDimensions.Landscape.EXPANDED
    }
}

@Composable
fun rememberGridMinColumnSize(): Dp {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    return when {
        screenWidthDp < CardDimensions.WindowSize.COMPACT_MAX_WIDTH ->
            CardDimensions.Grid.COMPACT

        screenWidthDp < CardDimensions.WindowSize.MEDIUM_MAX_WIDTH ->
            CardDimensions.Grid.MEDIUM

        else ->
            CardDimensions.Grid.EXPANDED
    }
}

fun calculateCardHeight(width: Dp, aspectRatio: Float): Dp {
    return width / aspectRatio
}

enum class WindowSizeClass {
    COMPACT,
    MEDIUM,
    EXPANDED;

    companion object {
        @Composable
        fun current(): WindowSizeClass {
            val screenWidthDp = LocalConfiguration.current.screenWidthDp
            return when {
                screenWidthDp < CardDimensions.WindowSize.COMPACT_MAX_WIDTH -> COMPACT
                screenWidthDp < CardDimensions.WindowSize.MEDIUM_MAX_WIDTH -> MEDIUM
                else -> EXPANDED
            }
        }
    }
}