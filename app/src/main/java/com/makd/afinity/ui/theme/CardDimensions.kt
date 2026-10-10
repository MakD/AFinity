package com.makd.afinity.ui.theme

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makd.afinity.data.models.CustomSectionCardStyle
import com.makd.afinity.data.models.common.CardSize
import kotlin.math.floor

val LocalCardSize = compositionLocalOf { CardSize.DEFAULT }

val LocalCardContainerWidthPx = compositionLocalOf { -1 }

val LocalCardRowGutter = compositionLocalOf { CardDimensions.RowGutter }

private const val MIN_SHIFTED_GRID_COLUMNS = 2

private data class ShiftedAdaptiveCells(val minSize: Dp, val columnDelta: Int) : GridCells {
    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
        val baseCount = maxOf((availableSize + spacing) / (minSize.roundToPx() + spacing), 1)
        val count = maxOf(baseCount + columnDelta, minOf(baseCount, MIN_SHIFTED_GRID_COLUMNS))
        val cellsSize = availableSize - spacing * (count - 1)
        val cellSize = cellsSize / count
        val remainder = cellsSize % count
        return List(count) { index -> cellSize + if (index < remainder) 1 else 0 }
    }
}

object CardDimensions {

    const val ASPECT_RATIO_PORTRAIT = 2f / 3f
    const val ASPECT_RATIO_LANDSCAPE = 16f / 9f
    const val ASPECT_RATIO_SQUARE = 1f
    const val ASPECT_RATIO_SPOTLIGHT = 1.85f
    const val ASPECT_RATIO_SPOTLIGHT_PORTRAIT = 1.5f

    private const val LANDSCAPE_HEIGHT_FRACTION = 0.4f
    private const val SQUARE_CAROUSEL_WIDTH_FRACTION = 0.48f
    private const val ROW_COUNT_BIAS = 0.75f
    private const val MAX_LANDSCAPE_ROW_COUNT_DELTA = 1
    private const val MAX_TILE_ROW_COUNT_DELTA = 1
    private const val SPLIT_PANE_FRACTION = 0.5f

    val RowGutter = 14.dp
    val RowGap = 12.dp
    val MusicDetailGutter = 20.dp
    val MusicDetailGap = 16.dp

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

        val MusicCard = 140.dp
        val MusicCardLandscape = 175.dp

        val AudiobookGrid = 140.dp
        val ChannelGrid = 160.dp
        val DownloadGrid = 110.dp
        val SquareTile = 100.dp
    }

    data class CarouselItemSize(val width: Dp, val height: Dp)

    private fun basePortraitWidth(widthSizeClass: WindowWidthSizeClass): Dp =
        when (widthSizeClass) {
            WindowWidthSizeClass.Compact -> Values.PortraitCompact
            WindowWidthSizeClass.Medium -> Values.PortraitMedium
            WindowWidthSizeClass.Expanded -> Values.PortraitExpanded
            else -> Values.PortraitCompact
        }

    private fun baseLandscapeWidth(widthSizeClass: WindowWidthSizeClass): Dp =
        when (widthSizeClass) {
            WindowWidthSizeClass.Compact -> Values.LandscapeCompact
            WindowWidthSizeClass.Medium -> Values.LandscapeMedium
            WindowWidthSizeClass.Expanded -> Values.LandscapeExpanded
            else -> Values.LandscapeCompact
        }

    private fun baseSquareWidth(widthSizeClass: WindowWidthSizeClass): Dp =
        when (widthSizeClass) {
            WindowWidthSizeClass.Compact -> Values.SquareCompact
            WindowWidthSizeClass.Medium -> Values.SquareMedium
            WindowWidthSizeClass.Expanded -> Values.SquareExpanded
            else -> Values.SquareCompact
        }

    private fun baseGridMinSize(widthSizeClass: WindowWidthSizeClass): Dp =
        when (widthSizeClass) {
            WindowWidthSizeClass.Compact -> Values.GridCompact
            WindowWidthSizeClass.Medium -> Values.GridMedium
            WindowWidthSizeClass.Expanded -> Values.GridExpanded
            else -> Values.GridCompact
        }

    @Composable
    @ReadOnlyComposable
    private fun Dp.cardScaled(): Dp = this * LocalCardSize.current.scale

    @Composable
    @ReadOnlyComposable
    private fun shiftedGridCells(minSize: Dp): GridCells =
        ShiftedAdaptiveCells(minSize, LocalCardSize.current.gridColumnDelta)

    @Composable
    @ReadOnlyComposable
    private fun rowCountDelta(): Int = LocalCardSize.current.gridColumnDelta

    @Composable
    @ReadOnlyComposable
    private fun landscapeRowCountDelta(): Int =
        rowCountDelta().coerceAtMost(MAX_LANDSCAPE_ROW_COUNT_DELTA)

    @Composable
    @ReadOnlyComposable
    private fun fittedRowWidth(
        base: Dp,
        countDelta: Int,
        gutter: Dp? = null,
        gap: Dp = RowGap,
        reserved: Dp = 0.dp,
        containerFraction: Float = 1f,
    ): Dp {
        val containerPx = (LocalCardContainerWidthPx.current * containerFraction).toInt()
        if (containerPx <= 0) return base.cardScaled()
        val rowGutter = gutter ?: LocalCardRowGutter.current
        return with(LocalDensity.current) {
            val rowPx = containerPx - rowGutter.roundToPx() * 2 - reserved.roundToPx()
            val gapPx = gap.roundToPx()
            val basePx = base.roundToPx()
            if (rowPx <= 0 || basePx <= 0) return@with base.cardScaled()
            val baseCount =
                floor((rowPx + gapPx).toFloat() / (basePx + gapPx) + ROW_COUNT_BIAS).toInt()
            val enlarge = countDelta < 0
            val count = (if (enlarge) baseCount else baseCount + countDelta).coerceAtLeast(1)
            val fittedPx = (rowPx - gapPx * (count - 1)) / count
            val widthPx =
                if (enlarge) {
                    (fittedPx * LocalCardSize.current.scale).toInt().coerceAtMost(rowPx)
                } else {
                    fittedPx
                }
            widthPx.coerceAtLeast(1).toDp()
        }
    }

    val WindowWidthSizeClass.portraitWidth: Dp
        @Composable
        @ReadOnlyComposable
        get() = fittedRowWidth(basePortraitWidth(this), rowCountDelta())

    val WindowWidthSizeClass.landscapeWidth: Dp
        @Composable
        @ReadOnlyComposable
        get() = fittedRowWidth(baseLandscapeWidth(this), landscapeRowCountDelta())

    val WindowWidthSizeClass.squareWidth: Dp
        @Composable
        @ReadOnlyComposable
        get() = fittedRowWidth(baseSquareWidth(this), rowCountDelta())

    val WindowWidthSizeClass.gridMinSize: Dp
        @Composable @ReadOnlyComposable get() = baseGridMinSize(this).cardScaled()

    val WindowWidthSizeClass.scaledPortraitWidth: Dp
        @Composable @ReadOnlyComposable get() = basePortraitWidth(this).cardScaled()

    val WindowWidthSizeClass.scaledLandscapeWidth: Dp
        @Composable @ReadOnlyComposable get() = baseLandscapeWidth(this).cardScaled()

    val scaledMusicCardWidth: Dp
        @Composable @ReadOnlyComposable get() = Values.MusicCard.cardScaled()

    val musicCardWidth: Dp
        @Composable @ReadOnlyComposable get() = fittedRowWidth(Values.MusicCard, rowCountDelta())

    val squareTileWidth: Dp
        @Composable
        @ReadOnlyComposable
        get() =
            fittedRowWidth(
                Values.SquareTile,
                rowCountDelta().coerceIn(-MAX_TILE_ROW_COUNT_DELTA, MAX_TILE_ROW_COUNT_DELTA),
            )

    val squareCarouselWidthFraction: Float
        @Composable
        @ReadOnlyComposable
        get() = SQUARE_CAROUSEL_WIDTH_FRACTION * LocalCardSize.current.scale

    @Composable
    @ReadOnlyComposable
    fun portraitRowWidth(
        widthSizeClass: WindowWidthSizeClass,
        gutter: Dp? = null,
        gap: Dp = RowGap,
        reserved: Dp = 0.dp,
    ): Dp =
        fittedRowWidth(basePortraitWidth(widthSizeClass), rowCountDelta(), gutter, gap, reserved)

    @Composable
    @ReadOnlyComposable
    fun landscapeRowWidth(
        widthSizeClass: WindowWidthSizeClass,
        gutter: Dp? = null,
        gap: Dp = RowGap,
        reserved: Dp = 0.dp,
    ): Dp =
        fittedRowWidth(
            baseLandscapeWidth(widthSizeClass),
            landscapeRowCountDelta(),
            gutter,
            gap,
            reserved,
        )

    @Composable
    @ReadOnlyComposable
    fun musicCardRowWidth(gutter: Dp? = null, gap: Dp = RowGap, reserved: Dp = 0.dp): Dp =
        fittedRowWidth(Values.MusicCard, rowCountDelta(), gutter, gap, reserved)

    @Composable
    @ReadOnlyComposable
    fun musicRowCardWidth(
        isLandscape: Boolean,
        gutter: Dp? = null,
        gap: Dp = RowGap,
        reserved: Dp = 0.dp,
    ): Dp =
        fittedRowWidth(
            if (isLandscape) Values.MusicCardLandscape else Values.MusicCard,
            rowCountDelta(),
            gutter,
            gap,
            reserved,
        )

    @Composable
    @ReadOnlyComposable
    fun musicDetailCardWidth(splitPane: Boolean = false): Dp =
        fittedRowWidth(
            Values.MusicCard,
            rowCountDelta(),
            gutter = MusicDetailGutter,
            gap = MusicDetailGap,
            containerFraction = if (splitPane) SPLIT_PANE_FRACTION else 1f,
        )

    @Composable
    @ReadOnlyComposable
    fun shortcutCardWidth(screenWidthDp: Int): Dp =
        fittedRowWidth(
            when {
                screenWidthDp < 600 -> Values.LandscapeCompact
                screenWidthDp < 840 -> Values.LandscapeMedium
                else -> Values.LandscapeExpanded
            },
            landscapeRowCountDelta(),
        )

    @Composable
    @ReadOnlyComposable
    fun gridCells(widthSizeClass: WindowWidthSizeClass): GridCells =
        shiftedGridCells(baseGridMinSize(widthSizeClass))

    @Composable
    @ReadOnlyComposable
    fun portraitGridCells(widthSizeClass: WindowWidthSizeClass): GridCells =
        shiftedGridCells(basePortraitWidth(widthSizeClass))

    @Composable
    @ReadOnlyComposable
    fun landscapeGridCells(widthSizeClass: WindowWidthSizeClass): GridCells =
        shiftedGridCells(baseLandscapeWidth(widthSizeClass))

    val musicGridCells: GridCells
        @Composable @ReadOnlyComposable get() = shiftedGridCells(Values.MusicCard)

    val audiobookGridCells: GridCells
        @Composable @ReadOnlyComposable get() = shiftedGridCells(Values.AudiobookGrid)

    val channelGridCells: GridCells
        @Composable @ReadOnlyComposable get() = shiftedGridCells(Values.ChannelGrid)

    val downloadGridCells: GridCells
        @Composable @ReadOnlyComposable get() = shiftedGridCells(Values.DownloadGrid)

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

    @Composable
    @ReadOnlyComposable
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
