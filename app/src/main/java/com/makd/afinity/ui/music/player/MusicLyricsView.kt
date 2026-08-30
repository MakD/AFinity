package com.makd.afinity.ui.music.player

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.music.AfinityLyricLine

@Composable
fun MusicLyricsView(
    lyrics: List<AfinityLyricLine>,
    positionMs: Long,
    isPlaying: Boolean,
    isLoading: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    val listState = rememberLazyListState()
    val currentLineIndex = lineIndexAt(lyrics, positionMs / 1000.0)
    val positionState = rememberUpdatedState(positionMs)
    val playingState = rememberUpdatedState(isPlaying)
    val seekState = rememberUpdatedState(onSeek)

    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex >= 0) {
            listState.animateScrollToItem((currentLineIndex - 2).coerceAtLeast(0))
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            }
            lyrics.isEmpty() -> {
                Text(
                    text = stringResource(R.string.music_player_no_lyrics),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    item { Spacer(Modifier.height(48.dp)) }
                    itemsIndexed(lyrics) { index, line ->
                        LyricLineRow(
                            line = line,
                            nextLineStartSeconds = lyrics.getOrNull(index + 1)?.startSeconds,
                            isCurrent = index == currentLineIndex,
                            isPast = index < currentLineIndex,
                            positionState = positionState,
                            playingState = playingState,
                            seekState = seekState,
                            accentColor = accentColor,
                        )
                    }
                    item { Spacer(Modifier.height(48.dp)) }
                }
            }
        }
    }
}

@Composable
private fun LyricLineRow(
    line: AfinityLyricLine,
    nextLineStartSeconds: Double?,
    isCurrent: Boolean,
    isPast: Boolean,
    positionState: State<Long>,
    playingState: State<Boolean>,
    seekState: State<(Long) -> Unit>,
    accentColor: Color,
) {
    val lineAlpha =
        animateFloatAsState(
            targetValue =
                when {
                    isCurrent -> 1f
                    isPast -> 0.45f
                    else -> 0.35f
                },
            animationSpec = tween(durationMillis = 300),
            label = "lyricAlpha",
        )

    val lineScale =
        animateFloatAsState(
            targetValue = if (isCurrent) 1.05f else 1f,
            animationSpec = tween(durationMillis = 300),
            label = "lyricScale",
        )

    val textStyle =
        MaterialTheme.typography.titleMedium.copy(
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
        )

    val rowModifier =
        Modifier.fillMaxWidth()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) {
                seekState.value((line.startSeconds * 1000).toLong())
            }
            .padding(horizontal = 24.dp, vertical = 10.dp)
            .graphicsLayer {
                alpha = lineAlpha.value
                scaleX = lineScale.value
                scaleY = lineScale.value
            }

    if (isCurrent && line.cues.isNotEmpty()) {
        val charProgress = remember(line) { mutableFloatStateOf(0f) }
        val layoutResult = remember(line) { mutableStateOf<TextLayoutResult?>(null) }

        LaunchedEffect(line, nextLineStartSeconds) {
            var anchorMs = positionState.value
            var anchorAt = SystemClock.elapsedRealtime()
            while (true) {
                withFrameMillis {
                    if (positionState.value != anchorMs) {
                        anchorMs = positionState.value
                        anchorAt = SystemClock.elapsedRealtime()
                    }
                    val drift =
                        if (playingState.value) SystemClock.elapsedRealtime() - anchorAt else 0L
                    charProgress.floatValue =
                        charProgressAt(line, (anchorMs + drift) / 1000.0, nextLineStartSeconds)
                }
            }
        }

        Box(modifier = rowModifier) {
            Text(
                text = line.text,
                style = textStyle,
                color = Color.White,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = line.text,
                style = textStyle,
                color = accentColor,
                onTextLayout = { layoutResult.value = it },
                modifier =
                    Modifier.fillMaxWidth().drawWithContent {
                        drawKaraokeFill(layoutResult.value, charProgress.floatValue)
                    },
            )
        }
    } else {
        Text(
            text = line.text,
            style = textStyle,
            color = if (isCurrent) accentColor else Color.White,
            modifier = rowModifier,
        )
    }
}

private fun ContentDrawScope.drawKaraokeFill(layout: TextLayoutResult?, progress: Float) {
    if (layout == null) return
    val length = layout.layoutInput.text.length
    if (length == 0 || progress <= 0f) return

    val content = this
    val clamped = progress.coerceIn(0f, length.toFloat())
    val index = clamped.toInt().coerceIn(0, length)
    val lineIndex = layout.getLineForOffset(index.coerceAtMost(length - 1))

    if (lineIndex > 0) {
        clipRect(0f, 0f, size.width, layout.getLineBottom(lineIndex - 1)) {
            content.drawContent()
        }
    }

    val start = layout.getHorizontalPosition(index, usePrimaryDirection = true)
    val end =
        if (index < length && layout.getLineForOffset(index + 1) == lineIndex) {
            layout.getHorizontalPosition(index + 1, usePrimaryDirection = true)
        } else {
            start
        }
    val x = start + (end - start) * (clamped - index)

    clipRect(0f, layout.getLineTop(lineIndex), x, layout.getLineBottom(lineIndex)) {
        content.drawContent()
    }
}

private fun lineIndexAt(lyrics: List<AfinityLyricLine>, seconds: Double): Int {
    if (lyrics.isEmpty()) return -1
    var low = 0
    var high = lyrics.lastIndex
    var result = 0
    while (low <= high) {
        val mid = (low + high) / 2
        if (lyrics[mid].startSeconds <= seconds) {
            result = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return result
}

private fun charProgressAt(
    line: AfinityLyricLine,
    seconds: Double,
    nextLineStartSeconds: Double?,
): Float {
    val cues = line.cues
    if (cues.isEmpty()) return 0f

    for (index in cues.indices) {
        val cue = cues[index]
        if (seconds < cue.startSeconds) return cue.position.toFloat()

        val end =
            cue.endSeconds
                ?: cues.getOrNull(index + 1)?.startSeconds
                ?: nextLineStartSeconds
                ?: (cue.startSeconds + 0.5)

        if (seconds < end) {
            val span = (end - cue.startSeconds).coerceAtLeast(0.001)
            val fraction = ((seconds - cue.startSeconds) / span).coerceIn(0.0, 1.0)
            return cue.position + (cue.endPosition - cue.position) * fraction.toFloat()
        }
    }
    return line.text.length.toFloat()
}
