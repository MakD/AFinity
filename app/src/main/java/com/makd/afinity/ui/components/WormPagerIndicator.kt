package com.makd.afinity.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.math.floor
import kotlin.math.min

private fun visibleDotCount(pageCount: Int, requestedDots: Int): Int =
    when {
        requestedDots >= pageCount -> pageCount
        requestedDots % 2 == 0 -> requestedDots - 1
        else -> requestedDots
    }

private fun dotWindowStart(pageFraction: Float, pageCount: Int, visibleDots: Int): Float =
    (pageFraction - visibleDots / 2).coerceIn(0f, (pageCount - visibleDots).toFloat())

private fun dotScale(
    index: Int,
    windowStart: Int,
    pageCount: Int,
    visibleDots: Int,
    edgeScale: Float,
): Float {
    val windowEnd = windowStart + visibleDots - 1
    return when (index) {
        !in windowStart..windowEnd -> 0f
        windowStart if windowStart > 0 -> edgeScale
        windowEnd if windowEnd < pageCount - 1 -> edgeScale
        else -> 1f
    }
}

private fun wormStart(pageFraction: Float): Float {
    val page = floor(pageFraction)
    return page + ((pageFraction - page) * 2f - 1f).coerceIn(0f, 1f)
}

private fun wormEnd(pageFraction: Float): Float {
    val page = floor(pageFraction)
    return page + ((pageFraction - page) * 2f).coerceIn(0f, 1f)
}

@Composable
fun WormPagerIndicator(
    pageCount: Int,
    pageFractionProvider: () -> Float,
    activeColor: Color,
    inactiveColor: Color,
    modifier: Modifier = Modifier,
    visibleDots: Int = 5,
    dotSize: Dp = 8.dp,
    edgeDotSize: Dp = 4.dp,
    spacing: Dp = 5.dp,
) {
    if (pageCount <= 0) return

    val dots = visibleDotCount(pageCount, visibleDots)

    Spacer(
        modifier =
            modifier
                .size(width = dotSize * dots + spacing * (dots - 1), height = dotSize)
                .drawWithCache {
                    val dotPx = dotSize.toPx()
                    val stepPx = dotPx + spacing.toPx()
                    val edgeScale = edgeDotSize.toPx() / dotPx
                    val wormRadius = CornerRadius(dotPx / 2f)
                    val centerY = dotPx / 2f

                    onDrawBehind {
                        val pageFraction =
                            pageFractionProvider().coerceIn(0f, (pageCount - 1).toFloat())
                        val windowStart = dotWindowStart(pageFraction, pageCount, dots)
                        val windowFloor = windowStart.toInt()
                        val windowProgress = windowStart - windowFloor
                        val lastDrawn = min(windowFloor + dots, pageCount - 1)
                        val isRtl = layoutDirection == LayoutDirection.Rtl

                        withTransform({
                            if (isRtl) scale(-1f, 1f)
                            translate(left = -windowStart * stepPx)
                        }) {
                            for (index in windowFloor..lastDrawn) {
                                val sizeFactor =
                                    lerp(
                                        dotScale(index, windowFloor, pageCount, dots, edgeScale),
                                        dotScale(
                                            index,
                                            windowFloor + 1,
                                            pageCount,
                                            dots,
                                            edgeScale,
                                        ),
                                        windowProgress,
                                    )
                                if (sizeFactor > 0f) {
                                    drawCircle(
                                        color = inactiveColor,
                                        radius = dotPx * sizeFactor / 2f,
                                        center = Offset(index * stepPx + dotPx / 2f, centerY),
                                    )
                                }
                            }

                            val start = wormStart(pageFraction)
                            val end = wormEnd(pageFraction)
                            drawRoundRect(
                                color = activeColor,
                                topLeft = Offset(start * stepPx, 0f),
                                size = Size((end - start) * stepPx + dotPx, dotPx),
                                cornerRadius = wormRadius,
                            )
                        }
                    }
                }
    )
}
