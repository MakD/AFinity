package com.makd.afinity.ui.music.player

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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.music.AfinityLyricLine

@Composable
fun MusicLyricsView(
    lyrics: List<AfinityLyricLine>,
    positionMs: Long,
    isLoading: Boolean,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    val listState = rememberLazyListState()

    val currentLineIndex by
        remember(positionMs, lyrics) {
            derivedStateOf {
                if (lyrics.isEmpty()) return@derivedStateOf -1
                val posSeconds = positionMs / 1000.0
                var last = 0
                for (i in lyrics.indices) {
                    if (lyrics[i].startSeconds <= posSeconds) last = i else break
                }
                last
            }
        }

    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex >= 0) {
            val target = (currentLineIndex - 2).coerceAtLeast(0)
            listState.animateScrollToItem(target)
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
                        LyricLine(
                            line = line,
                            isCurrent = index == currentLineIndex,
                            isPast = index < currentLineIndex,
                            accentColor = accentColor,
                            onClick = { onSeek((line.startSeconds * 1000).toLong()) },
                        )
                    }
                    item { Spacer(Modifier.height(48.dp)) }
                }
            }
        }
    }
}

@Composable
private fun LyricLine(
    line: AfinityLyricLine,
    isCurrent: Boolean,
    isPast: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
) {
    val alpha by
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

    val scale by
        animateFloatAsState(
            targetValue = if (isCurrent) 1.05f else 1f,
            animationSpec = tween(durationMillis = 300),
            label = "lyricScale",
        )

    Text(
        text = line.text,
        style =
            MaterialTheme.typography.titleMedium.copy(
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                fontSize = MaterialTheme.typography.titleMedium.fontSize * scale,
            ),
        color = if (isCurrent) accentColor else Color.White,
        textAlign = TextAlign.Center,
        modifier =
            Modifier.fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onClick,
                )
                .padding(horizontal = 24.dp, vertical = 10.dp)
                .alpha(alpha),
    )
}
