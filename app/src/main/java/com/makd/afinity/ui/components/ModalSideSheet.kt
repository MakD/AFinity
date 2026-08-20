package com.makd.afinity.ui.components

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val EnterAnimationMillis = 250
private const val ExitAnimationMillis = 200
private const val DismissDistanceFraction = 0.4f
private const val DismissVelocityThreshold = 800f
private const val ScrimAlpha = 0.4f

private val SideSheetMinWidth = 320.dp
private val SideSheetMaxWidth = 400.dp

@Composable
fun isWideWindow(): Boolean {
    val windowInfo = LocalWindowInfo.current
    return windowInfo.containerSize.width > windowInfo.containerSize.height
}

@Composable
fun ModalSideSheet(
    onDismissRequest: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val layoutDirection = LocalLayoutDirection.current
    val scope = rememberCoroutineScope()

    val sheetWidth =
        with(density) { windowInfo.containerSize.width.toDp() * 0.5f }
            .coerceIn(SideSheetMinWidth, SideSheetMaxWidth)
    val sheetWidthPx = with(density) { sheetWidth.toPx() }

    val isEndOnRight = layoutDirection == LayoutDirection.Ltr
    val hiddenOffsetPx = if (isEndOnRight) sheetWidthPx else -sheetWidthPx

    val offsetX = remember { Animatable(hiddenOffsetPx) }

    LaunchedEffect(Unit) { offsetX.animateTo(0f, tween(EnterAnimationMillis)) }

    suspend fun animateOutAndDismiss() {
        offsetX.animateTo(hiddenOffsetPx, tween(ExitAnimationMillis))
        onDismissRequest()
    }

    val scrimAlpha =
        ((1f - abs(offsetX.value) / sheetWidthPx).coerceIn(0f, 1f)) * ScrimAlpha

    Dialog(
        onDismissRequest = { scope.launch { animateOutAndDismiss() } },
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnClickOutside = false,
                dismissOnBackPress = false,
            ),
    ) {
        PredictiveBackHandler { progress ->
            try {
                progress.collect { event ->
                    offsetX.snapTo(
                        hiddenOffsetPx * LinearOutSlowInEasing.transform(event.progress)
                    )
                }
                animateOutAndDismiss()
            } catch (_: CancellationException) {
                offsetX.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) {
                            scope.launch { animateOutAndDismiss() }
                        }
            )

            Surface(
                modifier =
                    modifier
                        .align(Alignment.CenterEnd)
                        .width(sheetWidth)
                        .fillMaxHeight()
                        .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                        .draggable(
                            orientation = Orientation.Horizontal,
                            state =
                                rememberDraggableState { delta ->
                                    scope.launch {
                                        val next = offsetX.value + delta
                                        offsetX.snapTo(
                                            if (isEndOnRight) next.coerceIn(0f, sheetWidthPx)
                                            else next.coerceIn(-sheetWidthPx, 0f)
                                        )
                                    }
                                },
                            onDragStopped = { velocity ->
                                val draggedFraction = abs(offsetX.value) / sheetWidthPx
                                val flungAway =
                                    if (isEndOnRight) velocity > DismissVelocityThreshold
                                    else velocity < -DismissVelocityThreshold

                                if (draggedFraction > DismissDistanceFraction || flungAway) {
                                    animateOutAndDismiss()
                                } else {
                                    offsetX.animateTo(
                                        0f,
                                        spring(stiffness = Spring.StiffnessMediumLow),
                                    )
                                }
                            },
                        )
                        .semantics {
                            paneTitle = title
                            dismiss {
                                scope.launch { animateOutAndDismiss() }
                                true
                            }
                        },
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp),
                tonalElevation = 1.dp,
                shadowElevation = 8.dp,
            ) {
                content()
            }
        }
    }
}