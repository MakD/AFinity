package com.makd.afinity.ui.components

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.core.content.res.ResourcesCompat
import com.makd.afinity.R
import com.makd.afinity.ui.theme.CardDimensions

private const val DIGIT_STROKE_FRACTION = 0.022f
private const val DIGIT_STROKE_ALPHA = 0.78f
private const val DIGIT_FILL_TINT_ALPHA = 0.08f
private const val DOUBLE_DIGIT_OVERLAP_FRACTION = 0.12f

private const val NARROW_DIGIT_TUCK_FRACTION = 0.30f
private const val SINGLE_DIGIT_TUCK_FRACTION = 0.33f
private const val DOUBLE_DIGIT_TUCK_FRACTION = 0.34f

private class RankGlyph(val paths: List<ComposePath>, val visibleWidthPx: Float)

@Composable
fun RankedCardFrame(
    ranking: Int,
    cardWidth: Dp,
    aspectRatio: Float,
    modifier: Modifier = Modifier,
    details: @Composable () -> Unit,
    card: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val cardHeight = CardDimensions.calculateHeight(cardWidth, aspectRatio)
    val digitHeight = CardDimensions.rankedDigitHeight(cardHeight, aspectRatio)
    val digitHeightPx = with(density) { digitHeight.toPx() }
    val netflixTypeface =
        remember(context) {
            ResourcesCompat.getFont(context, R.font.netflix_sans_medium) ?: Typeface.DEFAULT
        }
    val glyph =
        remember(ranking, netflixTypeface, digitHeightPx) {
            rankGlyph(ranking, netflixTypeface, digitHeightPx)
        }
    val numberZone = with(density) { glyph.visibleWidthPx.toDp() }

    Column(modifier = modifier.width(numberZone + cardWidth)) {
        Box(modifier = Modifier.fillMaxWidth().height(cardHeight)) {
            RankDigits(
                glyph = glyph,
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(digitHeight),
            )
            Box(modifier = Modifier.align(Alignment.TopEnd).size(cardWidth, cardHeight)) { card() }
        }

        Spacer(modifier = Modifier.height(CardDimensions.CardTextSpacing))

        Box(modifier = Modifier.padding(start = numberZone)) { details() }
    }
}

@Composable
private fun RankDigits(glyph: RankGlyph, modifier: Modifier = Modifier) {
    val strokeColor = MaterialTheme.colorScheme.onBackground.copy(alpha = DIGIT_STROKE_ALPHA)

    val fillColor =
        MaterialTheme.colorScheme.onBackground
            .copy(alpha = DIGIT_FILL_TINT_ALPHA)
            .compositeOver(MaterialTheme.colorScheme.background)

    Box(
        modifier =
            modifier.drawWithCache {
                val outerStroke =
                    Stroke(
                        width = size.height * DIGIT_STROKE_FRACTION * 2f,
                        join = StrokeJoin.Miter,
                        miter = 4f,
                    )

                onDrawBehind {
                    glyph.paths.forEach { path ->
                        drawPath(path = path, color = strokeColor, style = outerStroke)
                        drawPath(path = path, color = fillColor, style = Fill)
                    }
                }
            }
    )
}

private fun rankGlyph(ranking: Int, font: Typeface, digitHeightPx: Float): RankGlyph {
    val text = ranking.toString()
    val isDoubleDigit = ranking >= 10
    val strokeWidthPx = digitHeightPx * DIGIT_STROKE_FRACTION

    val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = font
            textSize = digitHeightPx * 2f
        }

    val digitPaths = ArrayList<Path>(text.length)
    val digitWidths = FloatArray(text.length)
    val charBounds = RectF()

    for (i in text.indices) {
        val charPath = Path()
        paint.getTextPath(text, i, i + 1, 0f, 0f, charPath)
        charPath.computeBounds(charBounds, true)
        digitWidths[i] = charBounds.width()
        charPath.offset(-charBounds.left, 0f)
        digitPaths.add(charPath)
    }

    var currentX = 0f

    for (i in digitPaths.indices) {
        digitPaths[i].offset(currentX, 0f)

        if (i < digitPaths.lastIndex) {
            val overlapPx =
                if (isDoubleDigit) digitWidths[i + 1] * DOUBLE_DIGIT_OVERLAP_FRACTION else 0f
            currentX += digitWidths[i] - overlapPx
        }
    }

    val boundsPath = Path()
    digitPaths.forEach { path -> boundsPath.addPath(path) }

    val totalBounds = RectF()
    boundsPath.computeBounds(totalBounds, true)

    digitPaths.forEach { path -> path.offset(-totalBounds.left, -totalBounds.top) }

    val targetInkHeight = (digitHeightPx - strokeWidthPx * 2f).coerceAtLeast(1f)
    val scale = targetInkHeight / totalBounds.height().coerceAtLeast(1f)
    val scaleMatrix = Matrix().apply { setScale(scale, scale) }

    digitPaths.forEach { path -> path.transform(scaleMatrix) }

    val scaledInkWidth = totalBounds.width() * scale

    val tuckFraction =
        when {
            isDoubleDigit -> DOUBLE_DIGIT_TUCK_FRACTION
            ranking == 1 -> NARROW_DIGIT_TUCK_FRACTION
            else -> SINGLE_DIGIT_TUCK_FRACTION
        }

    val translateY = digitHeightPx - targetInkHeight - strokeWidthPx
    digitPaths.forEach { path -> path.offset(strokeWidthPx, translateY) }

    return RankGlyph(
        paths = digitPaths.map { path -> path.asComposePath() },
        visibleWidthPx = strokeWidthPx + scaledInkWidth * (1f - tuckFraction),
    )
}
