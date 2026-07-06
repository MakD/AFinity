package com.makd.afinity.ui.music.player.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.makd.afinity.R
import com.makd.afinity.ui.components.TransportControls
import java.util.Locale

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
    TransportControls(
        modifier = modifier,
        position = positionMs.toFloat(),
        range = 0f..durationMs.toFloat().coerceAtLeast(1f),
        bufferedPosition = bufferedPositionMs.toFloat(),
        isPlaying = isPlaying,
        isBuffering = isBuffering,
        elapsedLabel = formatMs(positionMs),
        totalLabel = formatMs(durationMs),
        onSeek = { onSeek(it.toLong()) },
        onPlayPauseClick = onPlayPauseClick,
        onSkipBackward = onSeekBackward,
        onSkipForward = onSeekForward,
        onPrevious = onPrevious,
        onNext = onNext,
        accentColor = accentColor,
        previousContentDescription = stringResource(R.string.cd_previous),
        nextContentDescription = stringResource(R.string.cd_next),
        seekBackwardContentDescription = stringResource(R.string.cd_music_seek_backward),
        seekForwardContentDescription = stringResource(R.string.cd_music_seek_forward),
        playContentDescription = stringResource(R.string.cd_play),
        pauseContentDescription = stringResource(R.string.cd_pause),
    )
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