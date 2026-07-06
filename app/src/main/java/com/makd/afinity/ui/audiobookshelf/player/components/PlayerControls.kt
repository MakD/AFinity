package com.makd.afinity.ui.audiobookshelf.player.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.makd.afinity.data.models.audiobookshelf.BookChapter
import com.makd.afinity.ui.components.TransportControls
import java.util.Locale

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
        elapsedLabel = formatTime(displayElapsed),
        totalLabel = formatTime(displayTotal),
        onSeek = { onSeek(it.toDouble()) },
        onPlayPauseClick = onPlayPauseClick,
        onSkipBackward = onSkipBackward,
        onSkipForward = onSkipForward,
        onPrevious = { onPreviousChapter?.invoke() },
        previousEnabled = onPreviousChapter != null,
        onNext = { onNextChapter?.invoke() },
        nextEnabled = onNextChapter != null,
        accentColor = accentColor,
    )
}

private fun formatTime(seconds: Double): String {
    val totalSeconds = seconds.toLong().coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val secs = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, secs)
    }
}