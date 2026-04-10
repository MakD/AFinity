package com.makd.afinity.ui.player.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sign

private enum class GestureType {
    BRIGHTNESS,
    VOLUME,
    SEEK,
}

@Composable
fun GestureHandler(
    modifier: Modifier = Modifier,
    isSeekEnabled: Boolean = true,
    onSingleTap: () -> Unit,
    onDoubleTap: (isForward: Boolean) -> Unit,
    onLongPressStart: () -> Unit = {},
    onLongPressEnd: () -> Unit = {},
    onBrightnessGesture: (percent: Float, isActive: Boolean) -> Unit,
    onVolumeGesture: (percent: Float, isActive: Boolean) -> Unit,
    onSeekGesture: (delta: Float) -> Unit,
    onSeekPreview: (isActive: Boolean) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val viewConfiguration = LocalViewConfiguration.current
    val touchSlop = viewConfiguration.touchSlop
    val layoutDirection = LocalLayoutDirection.current

    var componentWidth by remember { mutableFloatStateOf(0f) }
    var componentHeight by remember { mutableFloatStateOf(0f) }

    val systemGestures = WindowInsets.systemGestures
    val leftGestureInset = systemGestures.getLeft(density, layoutDirection).toFloat()
    val rightGestureInset = systemGestures.getRight(density, layoutDirection).toFloat()

    val exclusionLeft = max(with(density) { 48.dp.toPx() }, leftGestureInset)
    val exclusionRight = max(with(density) { 48.dp.toPx() }, rightGestureInset)
    val exclusionVertical = with(density) { 48.dp.toPx() }

    val currentOnSingleTap by rememberUpdatedState(onSingleTap)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    val currentOnLongPressStart by rememberUpdatedState(onLongPressStart)
    val currentOnLongPressEnd by rememberUpdatedState(onLongPressEnd)
    val currentOnBrightnessGesture by rememberUpdatedState(onBrightnessGesture)
    val currentOnVolumeGesture by rememberUpdatedState(onVolumeGesture)
    val currentOnSeekGesture by rememberUpdatedState(onSeekGesture)
    val currentOnSeekPreview by rememberUpdatedState(onSeekPreview)
    val currentIsSeekEnabled by rememberUpdatedState(isSeekEnabled)

    var isDragging by remember { mutableStateOf(false) }
    var isLongPressActive by remember { mutableStateOf(false) }
    var gestureType by remember { mutableStateOf<GestureType?>(null) }

    var dragStartOffset by remember { mutableStateOf(Offset.Zero) }

    var totalHorizontalDelta by remember { mutableFloatStateOf(0f) }
    var totalVerticalDelta by remember { mutableFloatStateOf(0f) }
    var isSeeking by remember { mutableStateOf(false) }
    var isGestureRejected by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .onSizeChanged { size ->
                    componentWidth = size.width.toFloat()
                    componentHeight = size.height.toFloat()
                }
                .pointerInput(currentIsSeekEnabled) {
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            if (currentIsSeekEnabled) {
                                val isForward = offset.x > componentWidth / 2
                                currentOnDoubleTap(isForward)
                            }
                        },
                        onTap = { currentOnSingleTap() },
                        onPress = { _ ->
                            val job = coroutineScope.launch {
                                delay(500)
                                if (!isDragging && gestureType == null) {
                                    isLongPressActive = true
                                    currentOnLongPressStart()
                                }
                            }

                            tryAwaitRelease()
                            job.cancel()

                            if (isLongPressActive) {
                                isLongPressActive = false
                                currentOnLongPressEnd()
                            }
                        },
                    )
                }
                .pointerInput(currentIsSeekEnabled) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            if (
                                offset.y < exclusionVertical ||
                                    offset.y > componentHeight - exclusionVertical ||
                                    offset.x < exclusionLeft ||
                                    offset.x > componentWidth - exclusionRight
                            ) {
                                isGestureRejected = true
                                return@detectDragGestures
                            }
                            isGestureRejected = false
                            dragStartOffset = offset
                            totalHorizontalDelta = 0f
                            totalVerticalDelta = 0f
                            gestureType = null
                            isSeeking = false
                        },
                        onDragEnd = {
                            isGestureRejected = false
                            isDragging = false
                            if (gestureType == GestureType.VOLUME) currentOnVolumeGesture(0f, false)
                            if (gestureType == GestureType.BRIGHTNESS)
                                currentOnBrightnessGesture(0f, false)
                            if (isSeeking) currentOnSeekPreview(false)

                            gestureType = null
                            isSeeking = false
                        },
                        onDragCancel = {
                            isGestureRejected = false
                            isDragging = false
                            if (gestureType == GestureType.VOLUME) currentOnVolumeGesture(0f, false)
                            if (gestureType == GestureType.BRIGHTNESS)
                                currentOnBrightnessGesture(0f, false)
                            if (isSeeking) currentOnSeekPreview(false)

                            gestureType = null
                            isSeeking = false
                        },
                        onDrag = { change, dragAmount ->
                            if (isGestureRejected) return@detectDragGestures
                            val verticalDrag = -dragAmount.y
                            val horizontalDrag = dragAmount.x
                            totalHorizontalDelta += horizontalDrag
                            totalVerticalDelta += verticalDrag

                            val leftZoneWidth = componentWidth * 0.35f
                            val rightZoneStart = componentWidth * 0.65f

                            if (gestureType == null) {
                                if (
                                    abs(totalVerticalDelta) > touchSlop ||
                                        abs(totalHorizontalDelta) > touchSlop
                                ) {

                                    isDragging = true
                                    change.consume()

                                    if (isLongPressActive) {
                                        isLongPressActive = false
                                        currentOnLongPressEnd()
                                    }

                                    gestureType =
                                        when {
                                            abs(totalVerticalDelta) > abs(totalHorizontalDelta) -> {
                                                when {
                                                    dragStartOffset.x < leftZoneWidth ->
                                                        GestureType.BRIGHTNESS
                                                    dragStartOffset.x > rightZoneStart ->
                                                        GestureType.VOLUME
                                                    else -> null
                                                }
                                            }
                                            currentIsSeekEnabled -> GestureType.SEEK
                                            else -> null
                                        }

                                    if (abs(totalVerticalDelta) > touchSlop) {
                                        totalVerticalDelta -= sign(totalVerticalDelta) * touchSlop
                                    }
                                    if (abs(totalHorizontalDelta) > touchSlop) {
                                        totalHorizontalDelta -=
                                            sign(totalHorizontalDelta) * touchSlop
                                    }

                                    if (gestureType == GestureType.VOLUME)
                                        currentOnVolumeGesture(0f, true)
                                    if (gestureType == GestureType.BRIGHTNESS)
                                        currentOnBrightnessGesture(0f, true)
                                }
                            } else {
                                change.consume()
                            }

                            if (!isDragging) return@detectDragGestures

                            when (gestureType) {
                                GestureType.BRIGHTNESS -> {
                                    val distanceFull = componentHeight * 0.5f
                                    val accumulatedPercent = totalVerticalDelta / distanceFull
                                    currentOnBrightnessGesture(accumulatedPercent, true)
                                }

                                GestureType.VOLUME -> {
                                    val distanceFull = componentHeight * 0.5f
                                    val accumulatedPercent = totalVerticalDelta / distanceFull
                                    currentOnVolumeGesture(accumulatedPercent, true)
                                }

                                GestureType.SEEK -> {
                                    if (!isSeeking) {
                                        isSeeking = true
                                        currentOnSeekPreview(true)
                                    }
                                    val normalizedDelta = totalHorizontalDelta / componentWidth
                                    currentOnSeekGesture(normalizedDelta)
                                }
                                null -> {}
                            }
                        },
                    )
                }
    ) {
        content()
    }
}
