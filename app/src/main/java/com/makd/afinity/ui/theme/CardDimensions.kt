package com.makd.afinity.ui.theme

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makd.afinity.data.models.CustomSectionCardStyle
import com.makd.afinity.data.models.common.CardSize

val LocalCardSize = compositionLocalOf { CardSize.DEFAULT }

val LocalCardContainerWidthPx = compositionLocalOf { -1 }

val LocalCardRowGutter = compositionLocalOf { CardDimensions.RowGutter }

private const val PEEK_FRACTION = 0.25f
private const val GRID_CONTAINER_FRACTION = 0.7f

private val PosterLadder = intArrayOf(2, 3, 4, 6, 8, 12)
private val LandscapeLadder = intArrayOf(1, 2, 3, 4, 6, 8)

private enum class CardTrack(val ladder: IntArray) {
    POSTER(PosterLadder),
    LANDSCAPE(LandscapeLadder),
}

private fun peekedPosterWidthPx(spanPx: Float, marginPx: Float, gapPx: Float, count: Int): Float =
    (spanPx - marginPx - gapPx * count) / (count + PEEK_FRACTION)

private fun Density.ladderIndex(spanPx: Float, step: Int): Int {
    val marginPx = CardDimensions.RowGutter.toPx()
    val gapPx = CardDimensions.RowGap.toPx()
    val minPosterPx = CardDimensions.MinPosterWidth.toPx()
    val defaultIndex =
        PosterLadder.indices.lastOrNull { index ->
            peekedPosterWidthPx(spanPx, marginPx, gapPx, PosterLadder[index]) >= minPosterPx
        } ?: 0
    return (defaultIndex + step).coerceIn(0, PosterLadder.lastIndex)
}

private fun Density.rowCardWidthPx(spanPx: Float, step: Int, track: CardTrack): Float {
    val gapPx = CardDimensions.RowGap.toPx()
    val index = ladderIndex(spanPx, step)
    val posters = PosterLadder[index]
    val posterPx = peekedPosterWidthPx(spanPx, CardDimensions.RowGutter.toPx(), gapPx, posters)
    return when (track) {
        CardTrack.POSTER -> posterPx
        CardTrack.LANDSCAPE -> {
            val areaPx = posterPx * posters + gapPx * (posters - 1)
            val count = LandscapeLadder[index]
            (areaPx - gapPx * (count - 1)) / count
        }
    }
}

private fun gridCellSizes(availableSize: Int, spacing: Int, count: Int): List<Int> {
    val cellsSize = availableSize - spacing * (count - 1)
    val cellSize = cellsSize / count
    val remainder = cellsSize % count
    return List(count) { index -> cellSize + if (index < remainder) 1 else 0 }
}

private data class LadderGridCells(val track: CardTrack, val step: Int, val containerPx: Int) :
    GridCells {
    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
        val spansContainer =
            availableSize <= containerPx && availableSize >= containerPx * GRID_CONTAINER_FRACTION
        val spanPx =
            if (spansContainer) containerPx.toFloat()
            else availableSize + CardDimensions.RowGutter.toPx() * 2
        val count = track.ladder[ladderIndex(spanPx, step)]
        return gridCellSizes(availableSize, spacing, count)
    }
}

private data class ShiftedAdaptiveCells(val minSize: Dp, val columnDelta: Int) : GridCells {
    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
        val baseCount = maxOf((availableSize + spacing) / (minSize.roundToPx() + spacing), 1)
        return gridCellSizes(availableSize, spacing, maxOf(baseCount + columnDelta, 1))
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
    private const val SPLIT_PANE_FRACTION = 0.5f
    private const val RANKED_NUMBER_ZONE_FRACTION = 0.5f
    private const val RANKED_DIGIT_HEIGHT_FRACTION = 0.7f

    val RowGutter = 16.dp
    val RowGap = 12.dp
    val MinPosterWidth = 90.dp

    val CardTextSpacing = 8.dp

    private val MetadataLineSlack = 6.dp
    private val SubtitleLineSlack = 2.dp

    private val InfoTilePadding = 16.dp
    private val RatingTileContentWidth = 100.dp
    private val RatingTileLineGap = 12.dp
    private val ReviewCardHeaderGap = 12.dp
    private val ReviewCardFooterGap = 8.dp
    private val ReviewCardSlack = 4.dp
    private val CompactTrackRowPadding = 8.dp
    private val EpgRowChrome = 38.dp

    const val REVIEW_CARD_BODY_LINES = 5

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

        val SpotlightCompact = 230.dp
        val SpotlightMedium = 270.dp
        val SpotlightExpanded = 330.dp

        val MusicCard = 140.dp

        val DownloadGrid = 110.dp
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
    private fun cardSizeStep(): Int = LocalCardSize.current.ladderStep

    @Composable
    @ReadOnlyComposable
    private fun ladderGridCells(track: CardTrack, step: Int = cardSizeStep()): GridCells =
        LadderGridCells(track, step, LocalCardContainerWidthPx.current)

    @Composable
    @ReadOnlyComposable
    private fun rowCardWidth(
        track: CardTrack,
        gutter: Dp? = null,
        containerFraction: Float = 1f,
    ): Dp {
        val containerPx = LocalCardContainerWidthPx.current * containerFraction
        val fallback =
            when (track) {
                CardTrack.POSTER -> Values.PortraitCompact
                CardTrack.LANDSCAPE -> Values.LandscapeCompact
            }
        if (containerPx <= 0f) return fallback.cardScaled()
        val rowGutter = gutter ?: LocalCardRowGutter.current
        val step = cardSizeStep()
        return with(LocalDensity.current) {
            val spanPx = containerPx + (RowGutter - rowGutter).toPx() * 2
            val widthPx = rowCardWidthPx(spanPx, step, track)
            if (widthPx < 1f) fallback.cardScaled() else widthPx.toInt().toDp()
        }
    }

    val posterCardWidth: Dp
        @Composable @ReadOnlyComposable get() = rowCardWidth(CardTrack.POSTER)

    val landscapeCardWidth: Dp
        @Composable @ReadOnlyComposable get() = rowCardWidth(CardTrack.LANDSCAPE)

    val WindowWidthSizeClass.portraitWidth: Dp
        @Composable @ReadOnlyComposable get() = posterCardWidth

    val WindowWidthSizeClass.landscapeWidth: Dp
        @Composable @ReadOnlyComposable get() = landscapeCardWidth

    val WindowWidthSizeClass.gridMinSize: Dp
        @Composable @ReadOnlyComposable get() = baseGridMinSize(this).cardScaled()

    val WindowWidthSizeClass.scaledPortraitWidth: Dp
        @Composable @ReadOnlyComposable get() = basePortraitWidth(this).cardScaled()

    val WindowWidthSizeClass.scaledLandscapeWidth: Dp
        @Composable @ReadOnlyComposable get() = baseLandscapeWidth(this).cardScaled()

    val scaledMusicCardWidth: Dp
        @Composable @ReadOnlyComposable get() = Values.MusicCard.cardScaled()

    val squareCarouselWidthFraction: Float
        @Composable
        @ReadOnlyComposable
        get() = SQUARE_CAROUSEL_WIDTH_FRACTION * LocalCardSize.current.scale

    @Composable
    @ReadOnlyComposable
    fun portraitRowWidth(gutter: Dp? = null): Dp = rowCardWidth(CardTrack.POSTER, gutter)

    @Composable
    @ReadOnlyComposable
    fun landscapeRowWidth(gutter: Dp? = null): Dp = rowCardWidth(CardTrack.LANDSCAPE, gutter)

    @Composable
    @ReadOnlyComposable
    fun musicDetailCardWidth(splitPane: Boolean = false): Dp =
        rowCardWidth(
            CardTrack.POSTER,
            containerFraction = if (splitPane) SPLIT_PANE_FRACTION else 1f,
        )

    fun rankedNumberZone(posterWidth: Dp): Dp = (posterWidth + RowGap) * RANKED_NUMBER_ZONE_FRACTION

    fun rankedDigitHeight(cardHeight: Dp): Dp = cardHeight * RANKED_DIGIT_HEIGHT_FRACTION

    @Composable @ReadOnlyComposable fun gridCells(): GridCells = ladderGridCells(CardTrack.POSTER)

    @Composable
    @ReadOnlyComposable
    fun landscapeGridCells(followCardSize: Boolean = true): GridCells =
        ladderGridCells(CardTrack.LANDSCAPE, if (followCardSize) cardSizeStep() else 0)

    val downloadGridCells: GridCells
        @Composable
        @ReadOnlyComposable
        get() = ShiftedAdaptiveCells(Values.DownloadGrid, cardSizeStep())

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

    @Composable
    @ReadOnlyComposable
    fun textHeight(style: TextStyle, lines: Int = 1): Dp =
        with(LocalDensity.current) { style.lineHeight.toDp() } * lines

    @Composable
    @ReadOnlyComposable
    fun fontScaled(height: Dp): Dp = with(LocalDensity.current) { height.value.sp.toDp() }

    val titleLine: Dp
        @Composable @ReadOnlyComposable get() = textHeight(MaterialTheme.typography.bodyMedium)

    val metadataLine: Dp
        @Composable
        @ReadOnlyComposable
        get() = textHeight(MaterialTheme.typography.bodySmall) + MetadataLineSlack

    val subtitleLine: Dp
        @Composable
        @ReadOnlyComposable
        get() = textHeight(MaterialTheme.typography.bodySmall) + SubtitleLineSlack

    @Composable
    @ReadOnlyComposable
    fun titleLines(lines: Int): Dp = textHeight(MaterialTheme.typography.bodyMedium, lines)

    val ratingTileWidth: Dp
        @Composable
        @ReadOnlyComposable
        get() {
            val fontSize = MaterialTheme.typography.headlineMedium.fontSize
            val fontScale = with(LocalDensity.current) { fontSize.toDp() } / fontSize.value.dp
            return InfoTilePadding * 2 + RatingTileContentWidth * fontScale
        }

    val ratingTileHeight: Dp
        @Composable
        @ReadOnlyComposable
        get() =
            InfoTilePadding * 2 +
                textHeight(MaterialTheme.typography.labelSmall) +
                RatingTileLineGap +
                textHeight(MaterialTheme.typography.headlineMedium)

    val reviewCardHeight: Dp
        @Composable
        @ReadOnlyComposable
        get() =
            InfoTilePadding * 2 +
                textHeight(MaterialTheme.typography.titleMedium) +
                ReviewCardHeaderGap +
                textHeight(MaterialTheme.typography.bodyMedium, REVIEW_CARD_BODY_LINES) +
                ReviewCardFooterGap +
                textHeight(MaterialTheme.typography.labelLarge) +
                ReviewCardSlack

    @Composable
    @ReadOnlyComposable
    fun compactTrackRowHeight(isLandscape: Boolean): Dp =
        maxOf(
            if (isLandscape) 70.dp else 64.dp,
            titleLine + textHeight(MaterialTheme.typography.bodySmall) + CompactTrackRowPadding,
        )

    val epgRowHeight: Dp
        @Composable
        @ReadOnlyComposable
        get() =
            EpgRowChrome +
                textHeight(MaterialTheme.typography.bodySmall) +
                textHeight(MaterialTheme.typography.labelSmall)

    @Composable
    @ReadOnlyComposable
    fun rowHeight(
        cardWidth: Dp,
        aspectRatio: Float,
        titleHeight: Dp = titleLine,
        metadataHeight: Dp = metadataLine,
    ): Dp = calculateHeight(cardWidth, aspectRatio) + CardTextSpacing + titleHeight + metadataHeight

    @Composable
    @ReadOnlyComposable
    fun cardWidthFor(style: CustomSectionCardStyle): Dp =
        when (style) {
            CustomSectionCardStyle.LANDSCAPE -> landscapeCardWidth
            else -> posterCardWidth
        }

    fun aspectRatioFor(style: CustomSectionCardStyle): Float =
        when (style) {
            CustomSectionCardStyle.LANDSCAPE -> ASPECT_RATIO_LANDSCAPE
            CustomSectionCardStyle.SQUARE -> ASPECT_RATIO_SQUARE
            else -> ASPECT_RATIO_PORTRAIT
        }
}
