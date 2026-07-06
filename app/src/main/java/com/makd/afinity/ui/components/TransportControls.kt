package com.makd.afinity.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.ui.player.components.BufferingIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransportControls(
    position: Float,
    range: ClosedFloatingPointRange<Float>,
    isPlaying: Boolean,
    isBuffering: Boolean,
    elapsedLabel: String,
    totalLabel: String,
    onSeek: (Float) -> Unit,
    onPlayPauseClick: () -> Unit,
    onSkipBackward: () -> Unit,
    onSkipForward: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    bufferedPosition: Float = 0f,
    previousEnabled: Boolean = true,
    nextEnabled: Boolean = true,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    previousContentDescription: String? = null,
    nextContentDescription: String? = null,
    seekBackwardContentDescription: String? = null,
    seekForwardContentDescription: String? = null,
    playContentDescription: String? = null,
    pauseContentDescription: String? = null,
) {
    var sliderValue by remember(position) { mutableFloatStateOf(position) }
    var isDragging by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(contentAlignment = Alignment.CenterStart) {
            Slider(
                value = if (isDragging) sliderValue else position,
                onValueChange = { value ->
                    sliderValue = value
                    isDragging = true
                },
                onValueChangeFinished = {
                    onSeek(sliderValue)
                    isDragging = false
                },
                valueRange = range,
                modifier = Modifier.fillMaxWidth(),
                colors =
                    SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                    ),
                thumb = {
                    Box(modifier = Modifier.size(16.dp).background(Color.White, CircleShape))
                },
                track = { sliderState ->
                    Canvas(modifier = Modifier.fillMaxWidth().height(2.dp)) {
                        val thumbRadiusPx = 8.dp.toPx()
                        val trackStart = thumbRadiusPx
                        val trackEnd = size.width - thumbRadiusPx
                        val trackRange = trackEnd - trackStart
                        val h = size.height
                        val cornerR = CornerRadius(h / 2f, h / 2f)
                        val rangeSpan =
                            (sliderState.valueRange.endInclusive - sliderState.valueRange.start)
                                .coerceAtLeast(1f)
                        val progress =
                            (sliderState.value - sliderState.valueRange.start) / rangeSpan
                        val thumbCenterX = trackStart + progress * trackRange

                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.2f),
                            topLeft = Offset(trackStart, 0f),
                            size = Size(trackRange, h),
                            cornerRadius = cornerR,
                        )

                        val bufferedFraction =
                            ((bufferedPosition - sliderState.valueRange.start) / rangeSpan)
                                .coerceIn(0f, 1f)
                        val bufferedEndX =
                            (trackStart + bufferedFraction * trackRange).coerceAtMost(trackEnd)
                        if (bufferedEndX > thumbCenterX) {
                            drawRoundRect(
                                color = Color.White.copy(alpha = 0.4f),
                                topLeft = Offset(thumbCenterX, 0f),
                                size = Size(bufferedEndX - thumbCenterX, h),
                                cornerRadius = cornerR,
                            )
                        }

                        drawRoundRect(
                            color = Color.White,
                            topLeft = Offset(trackStart, 0f),
                            size = Size((thumbCenterX - trackStart).coerceAtLeast(0f), h),
                            cornerRadius = cornerR,
                        )
                    }
                },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = elapsedLabel,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
            )
            Text(
                text = totalLabel,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onPrevious, enabled = previousEnabled) {
                Icon(
                    painter = painterResource(R.drawable.ic_player_skip_back),
                    contentDescription = previousContentDescription,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(28.dp),
                )
            }

            IconButton(onClick = onSkipBackward) {
                Icon(
                    painter = painterResource(R.drawable.ic_rewind_backward_30),
                    contentDescription = seekBackwardContentDescription,
                    tint = Color.White,
                    modifier = Modifier.size(34.dp),
                )
            }

            Surface(
                onClick = onPlayPauseClick,
                shape = RoundedCornerShape(28.dp),
                color = Color.White,
                modifier = Modifier.size(76.dp),
                shadowElevation = 8.dp,
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    if (isBuffering) {
                        BufferingIndicator(size = 32.dp, strokeWidth = 3.dp, color = accentColor)
                    } else {
                        Icon(
                            painter =
                                if (isPlaying) painterResource(R.drawable.ic_player_pause_filled)
                                else painterResource(R.drawable.ic_player_play_filled),
                            contentDescription =
                                if (isPlaying) pauseContentDescription else playContentDescription,
                            tint = Color.Black,
                            modifier = Modifier.size(38.dp),
                        )
                    }
                }
            }

            IconButton(onClick = onSkipForward) {
                Icon(
                    painter = painterResource(R.drawable.ic_rewind_forward_30),
                    contentDescription = seekForwardContentDescription,
                    tint = Color.White,
                    modifier = Modifier.size(34.dp),
                )
            }

            IconButton(onClick = onNext, enabled = nextEnabled) {
                Icon(
                    painter = painterResource(R.drawable.ic_player_skip_forward),
                    contentDescription = nextContentDescription,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}