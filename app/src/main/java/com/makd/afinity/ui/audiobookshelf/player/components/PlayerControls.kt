package com.makd.afinity.ui.audiobookshelf.player.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay30
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
    modifier: Modifier = Modifier
) {
    var sliderPosition by remember(currentTime) { mutableFloatStateOf(currentTime.toFloat()) }
    var isDragging by remember { mutableFloatStateOf(0f) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
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
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(currentTime),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "-${formatTime(duration - currentTime)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onPreviousChapter?.invoke() },
                enabled = onPreviousChapter != null
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipPrevious,
                    contentDescription = "Previous chapter",
                    modifier = Modifier.size(32.dp)
                )
            }

            IconButton(onClick = onSkipBackward) {
                Icon(
                    imageVector = Icons.Filled.Replay30,
                    contentDescription = "Skip backward 30 seconds",
                    modifier = Modifier.size(40.dp)
                )
            }

            FilledIconButton(
                onClick = onPlayPauseClick,
                modifier = Modifier.size(72.dp)
            ) {
                if (isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 3.dp
                    )
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            IconButton(onClick = onSkipForward) {
                Icon(
                    imageVector = Icons.Filled.Forward30,
                    contentDescription = "Skip forward 30 seconds",
                    modifier = Modifier.size(40.dp)
                )
            }

            IconButton(
                onClick = { onNextChapter?.invoke() },
                enabled = onNextChapter != null
            ) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "Next chapter",
                    modifier = Modifier.size(32.dp)
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

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%d:%02d", minutes, secs)
    }
}
