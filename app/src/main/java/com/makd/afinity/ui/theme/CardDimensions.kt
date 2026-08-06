package com.makd.afinity.ui.theme

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makd.afinity.data.models.CustomSectionCardStyle

object CardDimensions {

    const val ASPECT_RATIO_PORTRAIT = 2f / 3f
    const val ASPECT_RATIO_LANDSCAPE = 16f / 9f
    const val ASPECT_RATIO_SQUARE = 1f
    const val ASPECT_RATIO_SPOTLIGHT = 1.85f
    const val ASPECT_RATIO_SPOTLIGHT_PORTRAIT = 1.5f

    private const val LANDSCAPE_HEIGHT_FRACTION = 0.4f

    val CardTextSpacing = 8.dp
    val TitleLine = 20.dp
    val MetadataLine = 22.dp

    private object Values {
        val PortraitCompact = 140.dp
        val PortraitMedium = 150.dp
        val PortraitExpanded = 180.dp

        val LandscapeCompact = 240.dp
        val LandscapeMedium = 260.dp
        val LandscapeExpanded = 320.dp

        val SquareCompact = 150.dp
        val SquareMedium = 160.dp
        val SquareExpanded = 190.dp

        val GridCompact = 140.dp
        val GridMedium = 160.dp
        val GridExpanded = 180.dp

        val SpotlightCompact = 230.dp
        val SpotlightMedium = 270.dp
        val SpotlightExpanded = 330.dp
    }

    data class CarouselItemSize(val width: Dp, val height: Dp)

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

    val WindowWidthSizeClass.squareWidth: Dp
        get() =
            when (this) {
                WindowWidthSizeClass.Compact -> Values.SquareCompact
                WindowWidthSizeClass.Medium -> Values.SquareMedium
                WindowWidthSizeClass.Expanded -> Values.SquareExpanded
                else -> Values.SquareCompact
            }

    val WindowWidthSizeClass.gridMinSize: Dp
        get() =
            when (this) {
                WindowWidthSizeClass.Compact -> Values.GridCompact
                WindowWidthSizeClass.Medium -> Values.GridMedium
                WindowWidthSizeClass.Expanded -> Values.GridExpanded
                else -> Values.GridCompact
            }

    fun spotlightAspectRatio(isLandscape: Boolean): Float =
        if (isLandscape) ASPECT_RATIO_SPOTLIGHT else ASPECT_RATIO_SPOTLIGHT_PORTRAIT

    fun spotlightMaxHeight(windowWidth: Dp): Dp =
        when {
            windowWidth < 600.dp -> Values.SpotlightCompact
            windowWidth < 840.dp -> Values.SpotlightMedium
            else -> Values.SpotlightExpanded
        }

    fun carouselItemSize(
        availableWidth: Dp,
        windowHeight: Dp,
        isLandscape: Boolean,
        widthFraction: Float,
        aspectRatio: Float,
        maxHeight: Dp,
    ): CarouselItemSize {
        val heightCap =
            if (isLandscape) minOf(maxHeight, windowHeight * LANDSCAPE_HEIGHT_FRACTION)
            else maxHeight
        val width = (availableWidth * widthFraction).coerceAtMost(heightCap * aspectRatio)
        return CarouselItemSize(width = width, height = width / aspectRatio)
    }

    fun calculateHeight(width: Dp, aspectRatio: Float): Dp = width / aspectRatio

    fun rowHeight(
        cardWidth: Dp,
        aspectRatio: Float,
        titleHeight: Dp = TitleLine,
        metadataHeight: Dp = MetadataLine,
    ): Dp = calculateHeight(cardWidth, aspectRatio) + CardTextSpacing + titleHeight + metadataHeight

    fun cardWidthFor(style: CustomSectionCardStyle, widthSizeClass: WindowWidthSizeClass): Dp =
        when (style) {
            CustomSectionCardStyle.LANDSCAPE -> widthSizeClass.landscapeWidth
            CustomSectionCardStyle.SQUARE -> widthSizeClass.squareWidth
            else -> widthSizeClass.portraitWidth
        }

    fun aspectRatioFor(style: CustomSectionCardStyle): Float =
        when (style) {
            CustomSectionCardStyle.LANDSCAPE -> ASPECT_RATIO_LANDSCAPE
            CustomSectionCardStyle.SQUARE -> ASPECT_RATIO_SQUARE
            else -> ASPECT_RATIO_PORTRAIT
        }
}
