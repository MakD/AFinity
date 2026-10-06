package com.makd.afinity.ui.player.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.sign
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class SwipeCoverItem<T>(val key: Any, val value: T)

private class SwipeSettle {
    var job: Job? = null
}

private const val SWIPE_RESISTANCE = 0.3f
private const val SLIDE_COMMIT_FRACTION = 0.33f
private const val SLIDE_SETTLE_TIMEOUT_MS = 500L
private const val UNCLIPPED_EXTENT_PX = 100_000f
private val NudgeCommitDistance = 72.dp
private val FlingCommitVelocity = 600.dp
private val SlideSpec = spring<Float>(stiffness = Spring.StiffnessMedium, visibilityThreshold = 1f)
private val ReturnSpec =
    spring<Float>(stiffness = Spring.StiffnessMedium, visibilityThreshold = 0.5f)

@Composable
fun <T> SwipeableCover(
    current: SwipeCoverItem<T>,
    enabled: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    horizontalOverflow: Dp,
    modifier: Modifier = Modifier,
    previous: SwipeCoverItem<T>? = null,
    next: SwipeCoverItem<T>? = null,
    cover: @Composable (value: T, isCurrent: Boolean) -> Unit,
) {
    val density = LocalDensity.current
    val overflowPx = with(density) { horizontalOverflow.toPx() }
    val nudgeCommitPx = with(density) { NudgeCommitDistance.toPx() }
    val flingCommitPx = with(density) { FlingCommitVelocity.toPx() }

    val dragOffset = remember(current.key) { mutableFloatStateOf(0f) }
    var containerWidth by remember { mutableIntStateOf(0) }
    var coverWidth by remember { mutableIntStateOf(0) }
    val settle = remember { SwipeSettle() }
    val scope = rememberCoroutineScope()

    val latestOnPrevious by rememberUpdatedState(onPrevious)
    val latestOnNext by rememberUpdatedState(onNext)

    val previousSlot = previous.takeIf { enabled && hasPrevious }
    val nextSlot = next.takeIf { enabled && hasNext }
    val slidesToPrevious = previousSlot != null
    val slidesToNext = nextSlot != null

    val travel = { (containerWidth + coverWidth) / 2f + overflowPx }
    val displayedOffset = {
        val raw = dragOffset.floatValue
        when {
            raw < 0f && slidesToNext -> raw
            raw > 0f && slidesToPrevious -> raw
            else -> raw * SWIPE_RESISTANCE
        }
    }

    val draggableState = rememberDraggableState { delta ->
        val limit = travel()
        dragOffset.floatValue = (dragOffset.floatValue + delta).coerceIn(-limit, limit)
    }

    val clipModifier =
        if (slidesToPrevious || slidesToNext) {
            Modifier.drawWithContent {
                clipRect(
                    left = -overflowPx,
                    top = -UNCLIPPED_EXTENT_PX,
                    right = size.width + overflowPx,
                    bottom = size.height + UNCLIPPED_EXTENT_PX,
                ) {
                    this@drawWithContent.drawContent()
                }
            }
        } else {
            Modifier
        }

    Box(
        modifier =
            modifier
                .onSizeChanged { containerWidth = it.width }
                .then(clipModifier)
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Horizontal,
                    enabled = enabled,
                    onDragStarted = { settle.job?.cancel() },
                    onDragStopped = { velocity ->
                        val state = dragOffset
                        val raw = state.floatValue
                        val towardNext = raw < 0f
                        val canCommit = if (towardNext) hasNext else raw > 0f && hasPrevious
                        val slides = if (towardNext) slidesToNext else slidesToPrevious
                        val commitDistance =
                            if (slides) coverWidth * SLIDE_COMMIT_FRACTION else nudgeCommitPx
                        val commit =
                            canCommit &&
                                if (abs(velocity) >= flingCommitPx) sign(velocity) == sign(raw)
                                else abs(raw) >= commitDistance
                        val action = if (towardNext) latestOnNext else latestOnPrevious
                        val limit = travel()

                        settle.job = scope.launch {
                            if (commit && slides) {
                                val target = if (towardNext) -limit else limit
                                animate(
                                    initialValue = raw,
                                    targetValue = target,
                                    initialVelocity = velocity,
                                    animationSpec = SlideSpec,
                                ) { value, _ ->
                                    state.floatValue = value.coerceIn(-limit, limit)
                                }
                                state.floatValue = target
                                action()
                                delay(SLIDE_SETTLE_TIMEOUT_MS)
                                animate(
                                    initialValue = state.floatValue,
                                    targetValue = 0f,
                                    animationSpec = ReturnSpec,
                                ) { value, _ ->
                                    state.floatValue = value
                                }
                            } else {
                                if (commit) action()
                                animate(
                                    initialValue = raw,
                                    targetValue = 0f,
                                    initialVelocity = velocity,
                                    animationSpec = ReturnSpec,
                                ) { value, _ ->
                                    state.floatValue = value.coerceIn(-limit, limit)
                                }
                            }
                            state.floatValue = 0f
                        }
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        val slots =
            listOfNotNull(previousSlot?.let { -1 to it }, nextSlot?.let { 1 to it }, 0 to current)
        for ((position, slot) in slots) {
            key(slot.key) {
                Box(
                    modifier =
                        Modifier.onSizeChanged { coverWidth = it.width }
                            .graphicsLayer {
                                val offset = displayedOffset()
                                translationX = offset + position * travel()
                                alpha = if (position == 0 || position * offset < 0f) 1f else 0f
                            }
                ) {
                    cover(slot.value, position == 0)
                }
            }
        }
    }
}
