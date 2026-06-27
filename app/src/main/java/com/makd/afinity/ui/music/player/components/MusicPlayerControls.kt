package com.makd.afinity.ui.music.player.components

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
import androidx.compose.material3.CircularProgressIndicator
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
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerControls(
    positionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onPlayPauseClick: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekBackward: () -> Unit = {},
    onSeekForward: () -> Unit = {},
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    var sliderValue by remember(positionMs) { mutableFloatStateOf(positionMs.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(contentAlignment = Alignment.CenterStart) {
            Slider(
                value = if (isDragging) sliderValue else positionMs.toFloat(),
                onValueChange = { value ->
                    sliderValue = value
                    isDragging = true
                },
                onValueChangeFinished = {
                    onSeek(sliderValue.toLong())
                    isDragging = false
                },
                valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
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
                        val rangeSpan = (sliderState.valueRange.endInclusive - sliderState.valueRange.start).coerceAtLeast(1f)
                        val progress = (sliderState.value - sliderState.valueRange.start) / rangeSpan
                        val thumbCenterX = trackStart + progress * trackRange

                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.2f),
                            topLeft = Offset(trackStart, 0f),
                            size = Size(trackRange, h),
                            cornerRadius = cornerR,
                        )

                        if (durationMs > 0) {
                            val bufferedFraction = (bufferedPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                            val bufferedEndX = (trackStart + bufferedFraction * trackRange).coerceAtMost(trackEnd)
                            if (bufferedEndX > thumbCenterX) {
                                drawRoundRect(
                                    color = Color.White.copy(alpha = 0.4f),
                                    topLeft = Offset(thumbCenterX, 0f),
                                    size = Size(bufferedEndX - thumbCenterX, h),
                                    cornerRadius = cornerR,
                                )
                            }
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
                text = formatMs(positionMs),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
            )
            Text(
                text = formatMs(durationMs),
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
            IconButton(onClick = onPrevious) {
                Icon(
                    painter = painterResource(R.drawable.ic_player_skip_back),
                    contentDescription = "Previous",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(28.dp),
                )
            }

            IconButton(onClick = onSeekBackward) {
                Icon(
                    painter = painterResource(R.drawable.ic_rewind_backward_30),
                    contentDescription = "Seek backward",
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
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = accentColor,
                            strokeWidth = 3.dp,
                        )
                    } else {
                        Icon(
                            painter = if (isPlaying) painterResource(R.drawable.ic_player_pause_filled)
                            else painterResource(R.drawable.ic_player_play_filled),
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(38.dp),
                        )
                    }
                }
            }

            IconButton(onClick = onSeekForward) {
                Icon(
                    painter = painterResource(R.drawable.ic_rewind_forward_30),
                    contentDescription = "Seek forward",
                    tint = Color.White,
                    modifier = Modifier.size(34.dp),
                )
            }

            IconButton(onClick = onNext) {
                Icon(
                    painter = painterResource(R.drawable.ic_player_skip_forward),
                    contentDescription = "Next",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val secs = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, secs)
    }
}