package com.makd.afinity.ui.player.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import timber.log.Timber
import kotlin.math.abs

private enum class GestureType {
    BRIGHTNESS, VOLUME, SEEK
}

@Composable
fun GestureHandler(
    modifier: Modifier = Modifier,
    onSingleTap: () -> Unit,
    onDoubleTap: (isForward: Boolean) -> Unit,
    onBrightnessGesture: (percent: Float, isActive: Boolean) -> Unit,
    onVolumeGesture: (percent: Float, isActive: Boolean) -> Unit,
    onSeekGesture: (delta: Float) -> Unit,
    onSeekPreview: (isActive: Boolean) -> Unit = {},
    content: @Composable () -> Unit
) {
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }
    val exclusionHorizontal = with(density) { 32.dp.toPx() }
    val exclusionVertical = with(density) { 48.dp.toPx() }

    val leftZoneWidth = screenWidth * 0.3f
    val rightZoneStart = screenWidth * 0.7f

    val currentOnSingleTap by rememberUpdatedState(onSingleTap)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val currentOnBrightnessGesture by rememberUpdatedState(onBrightnessGesture)
    val currentOnVolumeGesture by rememberUpdatedState(onVolumeGesture)
    val currentOnSeekGesture by rememberUpdatedState(onSeekGesture)
    val currentOnSeekPreview by rememberUpdatedState(onSeekPreview)

    var isDragging by remember { mutableStateOf(false) }
    var gestureType by remember { mutableStateOf<GestureType?>(null) }
    var totalHorizontalDelta by remember { mutableFloatStateOf(0f) }
    var totalVerticalDelta by remember { mutableFloatStateOf(0f) }

    var isSeeking by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(screenWidth) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        val isForward = offset.x > screenWidth / 2
                        currentOnDoubleTap(isForward)
                    },
                    onTap = { currentOnSingleTap() }
                )
            }
            .pointerInput(screenWidth, screenHeight) {
                detectDragGestures(
                    onDragStart = { offset ->
                        if (offset.y < exclusionVertical ||
                            offset.y > screenHeight - exclusionVertical ||
                            offset.x < exclusionHorizontal ||
                            offset.x > screenWidth - exclusionHorizontal
                        ) {
                            return@detectDragGestures
                        }

                        isDragging = true
                        totalHorizontalDelta = 0f
                        totalVerticalDelta = 0f
                        gestureType = null
                        isSeeking = false
                        Timber.d("Drag started")
                    },
                    onDragEnd = {
                        isDragging = false
                        if (gestureType == GestureType.VOLUME) currentOnVolumeGesture(0f, false)
                        if (gestureType == GestureType.BRIGHTNESS) currentOnBrightnessGesture(0f, false)
                        if (isSeeking) currentOnSeekPreview(false)

                        gestureType = null
                        isSeeking = false
                        Timber.d("Drag ended")
                    },
                    onDragCancel = {
                        isDragging = false
                        if (gestureType == GestureType.VOLUME) currentOnVolumeGesture(0f, false)
                        if (gestureType == GestureType.BRIGHTNESS) currentOnBrightnessGesture(0f, false)
                        if (isSeeking) currentOnSeekPreview(false)

                        gestureType = null
                        isSeeking = false
                        Timber.d("Drag cancelled")
                    },
                    onDrag = { change, dragAmount ->
                        if (!isDragging) return@detectDragGestures

                        val verticalDrag = -dragAmount.y
                        val horizontalDrag = dragAmount.x

                        if (gestureType == null) {
                            val minimumDragDistance = 10f
                            if (abs(verticalDrag) > minimumDragDistance || abs(horizontalDrag) > minimumDragDistance) {
                                gestureType = when {
                                    abs(verticalDrag) > abs(horizontalDrag) -> {
                                        val startX = change.position.x - dragAmount.x
                                        when {
                                            startX < leftZoneWidth -> GestureType.BRIGHTNESS
                                            startX > rightZoneStart -> GestureType.VOLUME
                                            else -> null
                                        }
                                    }
                                    else -> GestureType.SEEK
                                }

                                if (gestureType == GestureType.VOLUME) currentOnVolumeGesture(0f, true)
                                if (gestureType == GestureType.BRIGHTNESS) currentOnBrightnessGesture(0f, true)
                            }
                        }

                        when (gestureType) {
                            GestureType.BRIGHTNESS -> {
                                totalVerticalDelta += verticalDrag
                                val distanceFull = screenHeight * 0.5f
                                val accumulatedPercent = totalVerticalDelta / distanceFull
                                currentOnBrightnessGesture(accumulatedPercent, true)
                            }

                            GestureType.VOLUME -> {
                                totalVerticalDelta += verticalDrag
                                val distanceFull = screenHeight * 0.5f
                                val accumulatedPercent = totalVerticalDelta / distanceFull
                                currentOnVolumeGesture(accumulatedPercent, true)
                            }

                            GestureType.SEEK -> {
                                totalHorizontalDelta += horizontalDrag
                                val seekActivationThreshold = 20f

                                if (abs(totalHorizontalDelta) > seekActivationThreshold) {
                                    if (!isSeeking) {
                                        isSeeking = true
                                        currentOnSeekPreview(true)
                                    }
                                    val normalizedDelta = totalHorizontalDelta / screenWidth
                                    currentOnSeekGesture(normalizedDelta)
                                }
                            }
                            null -> { }
                        }
                    }
                )
            }
    ) {
        content()
    }
}