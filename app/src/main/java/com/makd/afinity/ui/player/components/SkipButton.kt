package com.makd.afinity.ui.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.media.AfinitySegment

@Composable
fun SkipButton(
    segment: AfinitySegment,
    skipButtonText: String,
    onClick: (AfinitySegment) -> Unit,
    modifier: Modifier = Modifier,
) {
    val timerProgress = remember { Animatable(1f) }

    LaunchedEffect(segment) {
        timerProgress.snapTo(1f)
        timerProgress.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 8000, easing = LinearEasing),
        )
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)

    AnimatedVisibility(
        visible = true,
        enter =
            slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)) +
                fadeIn(animationSpec = tween(300)),
        exit =
            slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300)) +
                fadeOut(animationSpec = tween(300)),
        modifier = modifier,
    ) {
        ElevatedButton(
            onClick = { onClick(segment) },
            modifier =
                modifier.drawWithCache {
                    val strokeWidthPx = 3.dp.toPx()
                    val inset = strokeWidthPx / 2f
                    val cornerRadius = CornerRadius((size.height - strokeWidthPx) / 2f)

                    val borderPath =
                        Path().apply {
                            addRoundRect(
                                RoundRect(
                                    left = inset,
                                    top = inset,
                                    right = size.width - inset,
                                    bottom = size.height - inset,
                                    cornerRadius = cornerRadius,
                                )
                            )
                        }

                    val pathMeasure =
                        PathMeasure().apply { setPath(borderPath, forceClosed = false) }

                    val totalLength = pathMeasure.length
                    val halfLength = totalLength / 2f

                    val segment1 = Path()
                    val segment2 = Path()

                    onDrawWithContent {
                        drawContent()
                        drawPath(
                            path = borderPath,
                            color = trackColor,
                            style = Stroke(width = strokeWidthPx),
                        )

                        segment1.reset()
                        segment2.reset()

                        val fillProgress = 1f - timerProgress.value
                        val currentSegmentLength = halfLength * fillProgress
                        val growOffset = (halfLength - currentSegmentLength) / 2f

                        pathMeasure.getSegment(
                            startDistance = growOffset,
                            stopDistance = growOffset + currentSegmentLength,
                            destination = segment1,
                            startWithMoveTo = true,
                        )

                        pathMeasure.getSegment(
                            startDistance = halfLength + growOffset,
                            stopDistance = halfLength + growOffset + currentSegmentLength,
                            destination = segment2,
                            startWithMoveTo = true,
                        )

                        drawPath(
                            path = segment1,
                            color = primaryColor,
                            style = Stroke(width = strokeWidthPx),
                        )
                        drawPath(
                            path = segment2,
                            color = primaryColor,
                            style = Stroke(width = strokeWidthPx),
                        )
                    }
                },
            shape = CircleShape,
            colors =
                ButtonDefaults.elevatedButtonColors(
                    containerColor =
                        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            elevation =
                ButtonDefaults.elevatedButtonElevation(
                    defaultElevation = 6.dp,
                    pressedElevation = 2.dp,
                ),
            contentPadding = PaddingValues(start = 18.dp, end = 22.dp, top = 12.dp, bottom = 12.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_skip_next),
                contentDescription = stringResource(R.string.cd_skip_button_icon),
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = skipButtonText,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
