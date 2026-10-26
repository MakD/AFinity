package com.makd.afinity.ui.audiobookshelf.player.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.makd.afinity.R
import com.makd.afinity.data.models.audiobookshelf.BookChapter
import com.makd.afinity.ui.components.TransportControls
import com.makd.afinity.ui.player.cast.formatCastClock
import com.makd.afinity.ui.player.components.AudioFormatBadge

@Composable
fun PlayerControls(
    modifier: Modifier = Modifier,
    currentTime: Double,
    duration: Double,
    bufferedPosition: Double = 0.0,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onPlayPauseClick: () -> Unit,
    onSkipForward: () -> Unit,
    onSkipBackward: () -> Unit,
    onSeek: (Double) -> Unit,
    onPreviousChapter: (() -> Unit)? = null,
    onNextChapter: (() -> Unit)? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    currentChapter: BookChapter? = null,
    audioCodec: String? = null,
    isServerTranscode: Boolean = false,
) {
    val rangeStart = currentChapter?.start?.toFloat() ?: 0f
    val rangeEnd =
        (currentChapter?.end?.toFloat() ?: duration.toFloat()).coerceAtLeast(rangeStart + 1f)
    val displayElapsed =
        if (currentChapter != null) currentTime - currentChapter.start else currentTime
    val displayTotal =
        if (currentChapter != null) currentChapter.end - currentChapter.start else duration

    TransportControls(
        modifier = modifier,
        position = currentTime.toFloat(),
        range = rangeStart..rangeEnd,
        bufferedPosition = bufferedPosition.toFloat(),
        isPlaying = isPlaying,
        isBuffering = isBuffering,
        elapsedLabel = formatCastClock((displayElapsed * 1000).toLong()),
        totalLabel = formatCastClock((displayTotal * 1000).toLong()),
        onSeek = { onSeek(it.toDouble()) },
        onPlayPauseClick = onPlayPauseClick,
        onSkipBackward = onSkipBackward,
        onSkipForward = onSkipForward,
        onPrevious = { onPreviousChapter?.invoke() },
        previousEnabled = onPreviousChapter != null,
        onNext = { onNextChapter?.invoke() },
        nextEnabled = onNextChapter != null,
        accentColor = accentColor,
        previousContentDescription = stringResource(R.string.cd_previous),
        nextContentDescription = stringResource(R.string.cd_next),
        seekBackwardContentDescription = stringResource(R.string.cd_rewind_30),
        seekForwardContentDescription = stringResource(R.string.cd_forward_30),
        playContentDescription = stringResource(R.string.cd_play),
        pauseContentDescription = stringResource(R.string.cd_pause),
        timeRowCenter = {
            AudioFormatBadge(
                codec = audioCodec,
                isTranscoding = isServerTranscode,
                accentColor = accentColor,
            )
        },
    )
}
