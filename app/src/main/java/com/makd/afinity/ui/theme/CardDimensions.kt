package com.makd.afinity.ui.theme

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object CardDimensions {

    const val ASPECT_RATIO_PORTRAIT = 2f / 3f
    const val ASPECT_RATIO_LANDSCAPE = 16f / 9f

    private object Values {
        val PortraitCompact = 140.dp
        val PortraitMedium = 150.dp
        val PortraitExpanded = 180.dp

        val LandscapeCompact = 240.dp
        val LandscapeMedium = 260.dp
        val LandscapeExpanded = 320.dp

        val GridCompact = 140.dp
        val GridMedium = 160.dp
        val GridExpanded = 180.dp
    }

    val WindowWidthSizeClass.portraitWidth: Dp
        get() =
            when (this) {
                WindowWidthSizeClass.Compact -> Values.PortraitCompact
                WindowWidthSizeClass.Medium -> Values.PortraitMedium
                WindowWidthSizeClass.Expanded -> Values.PortraitExpanded
                else -> Values.PortraitCompact
            }

    val WindowWidthSizeClass.landscapeWidth: Dp
        get() =
            when (this) {
                WindowWidthSizeClass.Compact -> Values.LandscapeCompact
                WindowWidthSizeClass.Medium -> Values.LandscapeMedium
                WindowWidthSizeClass.Expanded -> Values.LandscapeExpanded
                else -> Values.LandscapeCompact
            }

    val WindowWidthSizeClass.gridMinSize: Dp
        get() =
            when (this) {
                WindowWidthSizeClass.Compact -> Values.GridCompact
                WindowWidthSizeClass.Medium -> Values.GridMedium
                WindowWidthSizeClass.Expanded -> Values.GridExpanded
                else -> Values.GridCompact
            }

    fun calculateHeight(width: Dp, aspectRatio: Float): Dp = width / aspectRatio
}
