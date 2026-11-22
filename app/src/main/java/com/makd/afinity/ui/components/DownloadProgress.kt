package com.makd.afinity.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val RingStrokeWidth = 2.dp
private val BarThickness = 8.dp
private val BarWavelength = 20.dp
private const val PROGRESS_GLIDE_MS = 500

@Composable
private fun rememberGlidingProgress(progress: () -> Float): () -> Float {
    val animated =
        animateFloatAsState(
            targetValue = progress().coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = PROGRESS_GLIDE_MS, easing = LinearEasing),
            label = "downloadProgress",
        )
    return remember(animated) { { animated.value } }
}

@Composable
fun DownloadProgressRing(
    progress: () -> Float,
    size: Dp,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val glidingProgress = rememberGlidingProgress(progress)
    if (active) {
        val density = LocalDensity.current
        val stroke =
            remember(density) {
                Stroke(width = with(density) { RingStrokeWidth.toPx() }, cap = StrokeCap.Round)
            }
        CircularWavyProgressIndicator(
            progress = glidingProgress,
            modifier = modifier.size(size),
            trackColor = ProgressIndicatorDefaults.circularDeterminateTrackColor,
            stroke = stroke,
            trackStroke = stroke,
            wavelength =
                WavyProgressIndicatorDefaults.CircularWavelength *
                    (size / WavyProgressIndicatorDefaults.CircularContainerSize),
        )
    } else {
        CircularProgressIndicator(
            progress = glidingProgress,
            modifier = modifier.size(size),
            strokeWidth = RingStrokeWidth,
        )
    }
}

@Composable
fun DownloadProgressBar(
    progress: () -> Float,
    active: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val glidingProgress = rememberGlidingProgress(progress)
    Box(
        modifier =
            modifier.fillMaxWidth().height(WavyProgressIndicatorDefaults.LinearContainerHeight),
        contentAlignment = Alignment.Center,
    ) {
        if (active) {
            LinearWavyProgressIndicator(
                progress = glidingProgress,
                modifier = Modifier.fillMaxWidth(),
                color = color,
                trackColor = trackColor,
                wavelength = BarWavelength,
            )
        } else {
            LinearProgressIndicator(
                progress = glidingProgress,
                modifier =
                    Modifier.fillMaxWidth()
                        .height(BarThickness)
                        .clip(RoundedCornerShape(BarThickness / 2)),
                color = color,
                trackColor = trackColor,
                strokeCap = StrokeCap.Round,
            )
        }
    }
}
