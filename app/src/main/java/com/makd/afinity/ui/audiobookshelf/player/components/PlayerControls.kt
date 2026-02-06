package com.makd.afinity.ui.audiobookshelf.player.components

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.makd.afinity.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerControls(
    currentTime: Double,
    duration: Double,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onPlayPauseClick: () -> Unit,
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    onSeek: (Double) -> Unit,
    onPreviousChapter: (() -> Unit)? = null,
    onNextChapter: (() -> Unit)? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    var sliderPosition by remember(currentTime) { mutableFloatStateOf(currentTime.toFloat()) }
    var isDragging by remember { mutableFloatStateOf(0f) }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(contentAlignment = Alignment.CenterStart) {
            Slider(
                value = if (isDragging > 0) sliderPosition else currentTime.toFloat(),
                onValueChange = { value ->
                    sliderPosition = value
                    isDragging = 1f
                },
                onValueChangeFinished = {
                    onSeek(sliderPosition.toDouble())
                    isDragging = 0f
                },
                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
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
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        modifier = Modifier.height(2.dp),
                        thumbTrackGapSize = 0.dp,
                        colors =
                            SliderDefaults.colors(
                                activeTrackColor = Color.White,
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                            ),
                        drawStopIndicator = null,
                    )
                },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTime(currentTime),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
            )
            Text(
                text = formatTime(duration),
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
            IconButton(
                onClick = { onPreviousChapter?.invoke() },
                enabled = onPreviousChapter != null,
            ) {
                Icon(
                    painterResource(id = R.drawable.ic_player_skip_back),
                    null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(28.dp),
                )
            }

            IconButton(onClick = onSkipBackward) {
                Icon(
                    painterResource(id = R.drawable.ic_rewind_backward_30),
                    null,
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
                            painter =
                                if (isPlaying)
                                    painterResource(id = R.drawable.ic_player_pause_filled)
                                else painterResource(id = R.drawable.ic_player_play_filled),
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(38.dp),
                        )
                    }
                }
            }

            IconButton(onClick = onSkipForward) {
                Icon(
                    painterResource(id = R.drawable.ic_rewind_forward_30),
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(34.dp),
                )
            }

            IconButton(onClick = { onNextChapter?.invoke() }, enabled = onNextChapter != null) {
                Icon(
                    painterResource(id = R.drawable.ic_player_skip_forward),
                    null,
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

private fun formatTime(seconds: Double): String {
    val totalSeconds = seconds.toLong().coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val secs = totalSeconds % 60
    return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes, secs)
    else String.format("%d:%02d", minutes, secs)
}
