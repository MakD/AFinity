package com.makd.afinity.ui.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makd.afinity.R
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

private const val REGION_WIDTH_FRACTION = 0.4f
private const val REGION_BULGE_FRACTION = 0.25f
private const val HIDE_DELAY_MS = 800L
private const val RIPPLE_DURATION_MS = 450
private const val CHEVRON_CYCLE_MS = 900

@Immutable
data class DoubleTapSeekFeedback(
    val isForward: Boolean,
    val totalSeconds: Int,
    val tapOffset: Offset,
    val tapCount: Int,
)

@Stable
class DoubleTapSeekFeedbackState {
    var feedback by mutableStateOf<DoubleTapSeekFeedback?>(null)
        private set

    var visible by mutableStateOf(false)
        private set

    fun onSeek(isForward: Boolean, seconds: Int, tapOffset: Offset) {
        val previous = feedback?.takeIf { visible && it.isForward == isForward }
        feedback =
            previous?.copy(
                totalSeconds = previous.totalSeconds + seconds,
                tapOffset = tapOffset,
                tapCount = previous.tapCount + 1,
            ) ?: DoubleTapSeekFeedback(isForward, seconds, tapOffset, tapCount = 1)
        visible = true
    }

    fun hide() {
        visible = false
    }
}

@Composable
fun rememberDoubleTapSeekFeedbackState(): DoubleTapSeekFeedbackState = remember {
    DoubleTapSeekFeedbackState()
}

@Composable
fun DoubleTapSeekOverlay(state: DoubleTapSeekFeedbackState, modifier: Modifier = Modifier) {
    LaunchedEffect(state.feedback, state.visible) {
        if (state.visible) {
            delay(HIDE_DELAY_MS.milliseconds)
            state.hide()
        }
    }

    AnimatedVisibility(
        visible = state.visible,
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(300)),
        modifier = modifier,
    ) {
        val feedback = state.feedback ?: return@AnimatedVisibility
        val ripple = remember { Animatable(0f) }
        LaunchedEffect(feedback) {
            ripple.snapTo(0f)
            ripple.animateTo(1f, tween(RIPPLE_DURATION_MS))
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val regionWidth = size.width * REGION_WIDTH_FRACTION
                clipPath(seekRegionPath(size, regionWidth, feedback.isForward)) {
                    drawRect(Color.White.copy(alpha = 0.12f))
                    val progress = ripple.value
                    drawCircle(
                        color = Color.White.copy(alpha = 0.25f * (1f - progress)),
                        radius = regionWidth * 1.2f * progress,
                        center = feedback.tapOffset,
                    )
                }
            }

            Column(
                modifier =
                    Modifier.fillMaxHeight()
                        .fillMaxWidth(REGION_WIDTH_FRACTION)
                        .align(
                            if (feedback.isForward) AbsoluteAlignment.CenterRight
                            else AbsoluteAlignment.CenterLeft
                        ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            ) {
                SeekChevrons(isForward = feedback.isForward)
                Text(
                    text = stringResource(R.string.seek_step_seconds_fmt, feedback.totalSeconds),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun SeekChevrons(isForward: Boolean) {
    val phase by
        rememberInfiniteTransition(label = "SeekChevrons")
            .animateFloat(
                initialValue = 0f,
                targetValue = 3f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(CHEVRON_CYCLE_MS, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                    ),
                label = "SeekChevronsPhase",
            )

    Row(modifier = Modifier.graphicsLayer { scaleX = if (isForward) 1f else -1f }) {
        repeat(3) { index ->
            Icon(
                painter = painterResource(id = R.drawable.ic_player_play_filled),
                contentDescription = null,
                tint = Color.White,
                modifier =
                    Modifier.size(16.dp).graphicsLayer {
                        alpha = 0.3f + 0.7f * (1f - abs(phase - (index + 0.5f))).coerceIn(0f, 1f)
                    },
            )
        }
    }
}

private fun seekRegionPath(size: Size, regionWidth: Float, isForward: Boolean): Path {
    val bulge = regionWidth * REGION_BULGE_FRACTION
    val edge = regionWidth - bulge
    return Path().apply {
        if (isForward) {
            moveTo(size.width, 0f)
            lineTo(size.width - edge, 0f)
            quadraticTo(
                size.width - regionWidth - bulge,
                size.height / 2f,
                size.width - edge,
                size.height,
            )
            lineTo(size.width, size.height)
        } else {
            moveTo(0f, 0f)
            lineTo(edge, 0f)
            quadraticTo(regionWidth + bulge, size.height / 2f, edge, size.height)
            lineTo(0f, size.height)
        }
        close()
    }
}
