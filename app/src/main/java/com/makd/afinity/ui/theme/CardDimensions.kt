package com.makd.afinity.ui.theme

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.makd.afinity.data.models.CustomSectionCardStyle
import com.makd.afinity.data.models.common.CardSize
import kotlin.math.roundToInt

val LocalCardSize = compositionLocalOf { CardSize.DEFAULT }

private data class SteppedGridCells(
    val baseCardWidth: Dp,
    val cardSize: CardSize,
    val minCount: Int = 2,
    val minCardWidth: Dp = 56.dp,
) : GridCells {
    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
        if (availableSize <= 0) return List(minCount) { 0 }

        val basePx = baseCardWidth.roundToPx()
        val baseCount =
            ((availableSize + spacing).toFloat() / (basePx + spacing))
                .roundToInt()
                .coerceAtLeast(minCount)

        val columnDelta =
            when (cardSize) {
                CardSize.LARGE -> -1
                CardSize.DEFAULT -> 0
                CardSize.SMALL -> 1
                CardSize.EXTRA_SMALL -> 2
            }

        var count = (baseCount + columnDelta).coerceAtLeast(minCount)

        val minCardPx = minCardWidth.roundToPx()
        while (count > minCount) {
            val cardPx = (availableSize - spacing * (count - 1)) / count
            if (cardPx >= minCardPx) break
            count--
        }

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
    const val SPOTLIGHT_WIDTH_FRACTION_PORTRAIT = 0.72f
    const val SPOTLIGHT_WIDTH_FRACTION_LANDSCAPE = 0.58f
    val spotlightMaxSmallItemWidth: Dp = 120.dp

    private const val LANDSCAPE_HEIGHT_FRACTION = 0.4f
    private const val SQUARE_CAROUSEL_WIDTH_FRACTION = 0.48f
    private const val RANKED_PORTRAIT_DIGIT_HEIGHT_FRACTION = 0.48f
    private const val RANKED_SQUARE_DIGIT_HEIGHT_FRACTION = 0.54f
    private const val LANDSCAPE_POSTER_SPAN = 5
    private const val LANDSCAPE_CARD_SPAN = 3

    val RowGutter = 16.dp
    val RowGap = 12.dp

    val CardTextSpacing = 8.dp

    private val PosterWidthMin = 140.dp
    private val PosterWidthMax = 180.dp
    private val PosterGrowthStart = 411.dp
    private val PosterGrowthEnd = 1200.dp

    private val EpgChannelWidthMin = 100.dp
    private val EpgChannelWidthMax = 140.dp
    private val EpgHourWidthMin = 180.dp
    private val EpgHourWidthMax = 220.dp

    private val MetadataLineSlack = 6.dp
    private val SubtitleLineSlack = 2.dp

    private val InfoTilePadding = 16.dp
    private val RatingTileContentWidth = 124.dp
    private val RatingTileLineGap = 12.dp
    private val ReviewCardHeaderGap = 12.dp
    private val ReviewCardFooterGap = 8.dp
    private val ReviewCardSlack = 4.dp
    private val CompactTrackRowPadding = 8.dp
    private val EpgRowChrome = 38.dp

    const val REVIEW_CARD_BODY_LINES = 5

    private object Values {
        val SpotlightCompact = 230.dp
        val SpotlightExpanded = 330.dp

        val DownloadGrid = 110.dp
    }

    data class CarouselItemSize(val width: Dp, val height: Dp)

    @Composable
    @ReadOnlyComposable
    private fun defaultPosterWidth(): Dp {
        val windowWidth =
            with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
        val growth =
            ((windowWidth - PosterGrowthStart) / (PosterGrowthEnd - PosterGrowthStart)).coerceIn(
                0f,
                1f,
            )
        return lerp(PosterWidthMin, PosterWidthMax, growth)
    }

    private fun landscapeWidthFor(posterWidth: Dp): Dp =
        (posterWidth * LANDSCAPE_POSTER_SPAN +
            RowGap * (LANDSCAPE_POSTER_SPAN - LANDSCAPE_CARD_SPAN)) / LANDSCAPE_CARD_SPAN

    @Composable
    @ReadOnlyComposable
    private fun Dp.cardScaled(): Dp = this * LocalCardSize.current.scale

    val posterCardWidth: Dp
        @Composable @ReadOnlyComposable get() = defaultPosterWidth().cardScaled()

    val landscapeCardWidth: Dp
        @Composable @ReadOnlyComposable get() = landscapeWidthFor(posterCardWidth)

    val WindowWidthSizeClass.portraitWidth: Dp
        @Composable @ReadOnlyComposable get() = posterCardWidth

    val WindowWidthSizeClass.landscapeWidth: Dp
        @Composable @ReadOnlyComposable get() = landscapeCardWidth

    val WindowWidthSizeClass.gridMinSize: Dp
        @Composable @ReadOnlyComposable get() = posterCardWidth

    val WindowWidthSizeClass.scaledPortraitWidth: Dp
        @Composable @ReadOnlyComposable get() = posterCardWidth

    val WindowWidthSizeClass.scaledLandscapeWidth: Dp
        @Composable @ReadOnlyComposable get() = landscapeCardWidth

    val scaledMusicCardWidth: Dp
        @Composable @ReadOnlyComposable get() = posterCardWidth

    val squareCarouselWidthFraction: Float
        @Composable
        @ReadOnlyComposable
        get() = SQUARE_CAROUSEL_WIDTH_FRACTION * LocalCardSize.current.scale

    fun rankedDigitHeight(
        cardHeight: Dp,
        aspectRatio: Float = ASPECT_RATIO_PORTRAIT,
    ): Dp {
        val fraction =
            if (aspectRatio >= ASPECT_RATIO_SQUARE) {
                RANKED_SQUARE_DIGIT_HEIGHT_FRACTION
            } else {
                RANKED_PORTRAIT_DIGIT_HEIGHT_FRACTION
            }
        return cardHeight * fraction
    }

    @Composable
    @ReadOnlyComposable
    fun gridCells(): GridCells =
        SteppedGridCells(
            baseCardWidth = 120.dp,
            cardSize = LocalCardSize.current,
            minCount = 2,
            minCardWidth = 56.dp,
        )

    @Composable
    @ReadOnlyComposable
    fun landscapeGridCells(followCardSize: Boolean = true): GridCells =
        SteppedGridCells(
            baseCardWidth = 200.dp,
            cardSize = if (followCardSize) LocalCardSize.current else CardSize.DEFAULT,
            minCount = 1,
            minCardWidth = 120.dp,
        )

    val downloadGridCells: GridCells
        @Composable @ReadOnlyComposable get() = GridCells.Adaptive(Values.DownloadGrid.cardScaled())

    fun spotlightAspectRatio(isLandscape: Boolean): Float =
        if (isLandscape) ASPECT_RATIO_SPOTLIGHT else ASPECT_RATIO_SPOTLIGHT_PORTRAIT

    fun spotlightWidthFraction(isLandscape: Boolean): Float =
        if (isLandscape) SPOTLIGHT_WIDTH_FRACTION_LANDSCAPE else SPOTLIGHT_WIDTH_FRACTION_PORTRAIT

    fun spotlightMaxHeight(windowWidth: Dp): Dp {
        val growth =
            ((windowWidth - PosterGrowthStart) / (PosterGrowthEnd - PosterGrowthStart)).coerceIn(
                0f,
                1f,
            )
        return lerp(Values.SpotlightCompact, Values.SpotlightExpanded, growth)
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

    fun epgChannelCellWidth(windowWidth: Dp): Dp {
        val growth =
            ((windowWidth - PosterGrowthStart) / (PosterGrowthEnd - PosterGrowthStart)).coerceIn(
                0f,
                1f,
            )
        return lerp(EpgChannelWidthMin, EpgChannelWidthMax, growth)
    }

    val epgChannelCellWidth: Dp
        @Composable
        @ReadOnlyComposable
        get() {
            val windowWidth =
                with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
            return epgChannelCellWidth(windowWidth)
        }

    fun epgHourWidth(windowWidth: Dp): Dp {
        val growth =
            ((windowWidth - PosterGrowthStart) / (PosterGrowthEnd - PosterGrowthStart)).coerceIn(
                0f,
                1f,
            )
        return lerp(EpgHourWidthMin, EpgHourWidthMax, growth)
    }

    val epgHourWidth: Dp
        @Composable
        @ReadOnlyComposable
        get() {
            val windowWidth =
                with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
            return epgHourWidth(windowWidth)
        }

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
