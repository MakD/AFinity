package com.makd.afinity.ui.player.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import timber.log.Timber
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sqrt


private enum class GestureType {
    BRIGHTNESS, VOLUME, SEEK
}

@Composable
fun GestureHandler(
    modifier: Modifier = Modifier,
    onSingleTap: () -> Unit,
    onDoubleTap: (isForward: Boolean) -> Unit,
    onBrightnessGesture: (delta: Float) -> Unit,
    onVolumeGesture: (delta: Float) -> Unit,
    onSeekGesture: (delta: Float) -> Unit,
    onSeekPreview: (isActive: Boolean) -> Unit = {},
    content: @Composable () -> Unit
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }

    val leftZoneWidth = screenWidth * 0.3f
    val rightZoneStart = screenWidth * 0.7f
    val centerZoneStart = screenWidth * 0.3f
    val centerZoneEnd = screenWidth * 0.7f

    var lastTapTime by remember { mutableLongStateOf(0L) }
    var lastTapPosition by remember { mutableStateOf(Offset.Zero) }
    val doubleTapThreshold = 300L
    val tapRadius = 100f

    var isDragging by remember { mutableStateOf(false) }
    var dragStartPosition by remember { mutableStateOf(Offset.Zero) }
    var totalDragDelta by remember { mutableFloatStateOf(0f) }

    var gestureType by remember { mutableStateOf<GestureType?>(null) }
    var totalHorizontalDelta by remember { mutableFloatStateOf(0f) }
    var isSeeking by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        val currentTime = System.currentTimeMillis()
                        val timeDiff = currentTime - lastTapTime
                        val distance = (offset - lastTapPosition).getDistance()

                        if (timeDiff < doubleTapThreshold && distance < tapRadius) {
                            val isForward = offset.x > screenWidth / 2
                            onDoubleTap(isForward)
                            Timber.d("Double tap detected: ${if (isForward) "forward" else "backward"}")
                        } else {
                            onSingleTap()
                            Timber.d("Single tap detected")
                        }

                        lastTapTime = currentTime
                        lastTapPosition = offset
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragStartPosition = offset
                        totalDragDelta = 0f
                        totalHorizontalDelta = 0f
                        gestureType = null
                        isSeeking = false
                        Timber.d("Drag started at: $offset")
                    },
                    onDragEnd = {
                        isDragging = false
                        totalDragDelta = 0f
                        totalHorizontalDelta = 0f
                        if (isSeeking) {
                            onSeekPreview(false)
                        }
                        gestureType = null
                        Timber.d("Drag ended")
                    },
                    onDrag = { _, dragAmount ->
                        if (!isDragging) return@detectDragGestures

                        val exclusionVertical = 48f
                        val exclusionHorizontal = 24f
                        if (dragStartPosition.y < exclusionVertical ||
                            dragStartPosition.y > screenHeight - exclusionVertical ||
                            dragStartPosition.x < exclusionHorizontal ||
                            dragStartPosition.x > screenWidth - exclusionHorizontal) {
                            return@detectDragGestures
                        }

                        val verticalDrag = -dragAmount.y
                        val horizontalDrag = dragAmount.x
                        val minimumDragDistance = 15f

                        if (gestureType == null && (abs(verticalDrag) > minimumDragDistance || abs(horizontalDrag) > minimumDragDistance)) {
                            gestureType = when {
                                abs(verticalDrag) > abs(horizontalDrag) -> {
                                    when {
                                        dragStartPosition.x < leftZoneWidth -> GestureType.BRIGHTNESS
                                        dragStartPosition.x > rightZoneStart -> GestureType.VOLUME
                                        else -> null
                                    }
                                }
                                abs(horizontalDrag) > abs(verticalDrag) -> GestureType.SEEK
                                else -> null
                            }
                        }

                        when (gestureType) {
                            GestureType.BRIGHTNESS -> {
                                if (abs(verticalDrag) >= minimumDragDistance) {
                                    totalDragDelta += verticalDrag
                                    if (abs(totalDragDelta) >= 30f) {
                                        val distanceFull = screenHeight * 0.66f
                                        val gestureStrength = totalDragDelta / distanceFull
                                        onBrightnessGesture(gestureStrength)
                                        totalDragDelta = 0f
                                    }
                                }
                            }
                            GestureType.VOLUME -> {
                                if (abs(verticalDrag) >= minimumDragDistance) {
                                    totalDragDelta += verticalDrag
                                    if (abs(totalDragDelta) >= 15f) {
                                        val distanceFull = screenHeight * 0.35f
                                        val gestureStrength = totalDragDelta / distanceFull
                                        onVolumeGesture(gestureStrength)
                                        totalDragDelta = 0f
                                    }
                                 }
                            }
                            GestureType.SEEK -> {
                                totalHorizontalDelta += horizontalDrag
                                val seekActivationThreshold = 30f
                                if (abs(totalHorizontalDelta) > seekActivationThreshold) {
                                    if (!isSeeking) {
                                        isSeeking = true
                                        onSeekPreview(true)
                                    }

                                    val normalizedDelta = totalHorizontalDelta / screenWidth
                                    val dampenedStrength = normalizedDelta.sign * sqrt(abs(normalizedDelta))
                                    val seekStrength = dampenedStrength * 2f
                                    onSeekGesture(seekStrength)

                                    Timber.d("Seek gesture: strength=$seekStrength")
                                }
                            }
                            null -> {
                            }
                        }
                    }
                )
            }
    ) {
        content()
    }
}