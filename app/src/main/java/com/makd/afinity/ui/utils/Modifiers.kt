package com.makd.afinity.ui.utils

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp

@Composable
fun Modifier.shimmerEffect(): Modifier {
    val progress =
        rememberInfiniteTransition(label = "shimmer")
            .animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(durationMillis = 1400, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                    ),
                label = "shimmerProgress",
            )

    val base = MaterialTheme.colorScheme.surfaceVariant
    val highlight = lerp(base, MaterialTheme.colorScheme.onSurfaceVariant, 0.12f)

    return this.drawWithCache {
        val band = size.width * 0.5f
        val travel = size.width + size.height + band * 2f
        val brush =
            if (band > 0f) {
                Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = Offset.Zero,
                    end = Offset(band, band),
                )
            } else {
                null
            }

        onDrawBehind {
            drawRect(color = base)
            if (brush == null) return@onDrawBehind

            val offset = progress.value * travel - band * 2f
            translate(left = offset) {
                drawRect(brush = brush, topLeft = Offset(-offset, 0f), size = size)
            }
        }
    }
}

fun Modifier.verticalLayoutOffset(yOffset: Dp) =
    this.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val yOffsetPx = yOffset.roundToPx()

        layout(placeable.width, (placeable.height + yOffsetPx).coerceAtLeast(0)) {
            placeable.placeRelative(0, yOffsetPx)
        }
    }

fun Modifier.horizontalBleed(amount: Dp) =
    this.layout { measurable, constraints ->
        if (!constraints.hasBoundedWidth) {
            val placeable = measurable.measure(constraints)
            return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
        }

        val extraPx = amount.roundToPx() * 2
        val placeable =
            measurable.measure(
                constraints.copy(
                    minWidth = constraints.minWidth + extraPx,
                    maxWidth = constraints.maxWidth + extraPx,
                )
            )

        layout((placeable.width - extraPx).coerceAtLeast(0), placeable.height) {
            placeable.placeRelative(-amount.roundToPx(), 0)
        }
    }

fun Modifier.bottomOverlap(overlap: Dp) =
    this.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val overlapPx = overlap.roundToPx()

        layout(placeable.width, (placeable.height - overlapPx).coerceAtLeast(0)) {
            placeable.place(0, 0)
        }
    }
