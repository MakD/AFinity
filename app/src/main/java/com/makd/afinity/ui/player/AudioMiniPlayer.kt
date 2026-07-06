package com.makd.afinity.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.audiobookshelf.BookChapter
import com.makd.afinity.ui.audiobookshelf.player.util.rememberDominantColor
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.player.components.BufferingIndicator
import timber.log.Timber

sealed class AudioMiniPlayerState {
    abstract val isPlaying: Boolean
    abstract val isBuffering: Boolean
    abstract val coverUrl: String?

    data class Abs(
        val title: String,
        val author: String?,
        val currentChapter: BookChapter?,
        override val coverUrl: String?,
        val currentTime: Double,
        val duration: Double,
        override val isPlaying: Boolean,
        override val isBuffering: Boolean,
    ) : AudioMiniPlayerState()

    data class Music(
        val title: String,
        val artist: String?,
        override val coverUrl: String?,
        val blurHash: String?,
        val positionMs: Long,
        val durationMs: Long,
        override val isPlaying: Boolean,
        override val isBuffering: Boolean,
    ) : AudioMiniPlayerState()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.AudioMiniPlayer(
    state: AudioMiniPlayerState,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onPlayPauseClick: () -> Unit,
    onSkipNext: (() -> Unit)?,
    onCloseClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress =
        when (state) {
            is AudioMiniPlayerState.Abs -> {
                val ch = state.currentChapter
                if (ch != null) {
                    val dur = ch.end - ch.start
                    if (dur > 0) ((state.currentTime - ch.start) / dur).toFloat().coerceIn(0f, 1f)
                    else 0f
                } else {
                    if (state.duration > 0)
                        (state.currentTime / state.duration).toFloat().coerceIn(0f, 1f)
                    else 0f
                }
            }
            is AudioMiniPlayerState.Music ->
                if (state.durationMs > 0)
                    (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
                else 0f
        }
    val animatedProgress by animateFloatAsState(targetValue = progress, label = "progress")

    val defaultColor = MaterialTheme.colorScheme.primaryContainer
    val dominantColor = rememberDominantColor(url = state.coverUrl, defaultColor = defaultColor)
    val animatedDominantColor by animateColorAsState(targetValue = dominantColor, label = "color")

    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.currentValue) {
        when (dismissState.currentValue) {
            SwipeToDismissBoxValue.StartToEnd,
            SwipeToDismissBoxValue.EndToStart -> {
                Timber.tag("ABS-MiniPlayer")
                    .d(
                        "MiniPlayer: DISMISSED — value=${dismissState.currentValue} stateType=${state::class.simpleName}"
                    )
                onCloseClick()
            }
            SwipeToDismissBoxValue.Settled -> {}
        }
    }
    val sharedElementKey =
        when (state) {
            is AudioMiniPlayerState.Abs -> "cover-${state.coverUrl ?: "default"}"
            is AudioMiniPlayerState.Music -> "music-cover-${state.coverUrl ?: "default"}"
        }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {},
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Surface(
            modifier =
                Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp))
                    .clickable(onClick = onClick),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
            tonalElevation = 6.dp,
            shadowElevation = 4.dp,
        ) {
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .drawBehind {
                            drawRect(
                                color = animatedDominantColor.copy(alpha = 0.35f),
                                size =
                                    Size(
                                        width = size.width * animatedProgress,
                                        height = size.height,
                                    ),
                            )
                        }
                        .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier.sharedElement(
                                sharedContentState =
                                    rememberSharedContentState(key = sharedElementKey),
                                animatedVisibilityScope = animatedVisibilityScope,
                            )
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (state.coverUrl != null || state is AudioMiniPlayerState.Music) {
                        val title =
                            when (state) {
                                is AudioMiniPlayerState.Abs -> state.title
                                is AudioMiniPlayerState.Music -> state.title
                            }
                        AsyncImage(
                            imageUrl = state.coverUrl,
                            contentDescription = title,
                            blurHash = (state as? AudioMiniPlayerState.Music)?.blurHash,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize(),
                            targetWidth = 48.dp,
                            targetHeight = 48.dp,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    when (state) {
                        is AudioMiniPlayerState.Abs -> AbsTextContent(state)
                        is AudioMiniPlayerState.Music -> MusicTextContent(state)
                    }
                }

                IconButton(onClick = onPlayPauseClick) {
                    if (state.isBuffering) {
                        BufferingIndicator(
                            size = 22.dp,
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        AnimatedContent(targetState = state.isPlaying, label = "play_pause") {
                            playing ->
                            Icon(
                                painter =
                                    if (playing) painterResource(R.drawable.ic_player_pause_filled)
                                    else painterResource(R.drawable.ic_player_play_filled),
                                contentDescription =
                                    if (playing) stringResource(R.string.cd_pause)
                                    else stringResource(R.string.cd_play),
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                if (onSkipNext != null) {
                    IconButton(onClick = onSkipNext) {
                        Icon(
                            painter = painterResource(R.drawable.ic_player_skip_forward),
                            contentDescription = stringResource(R.string.cd_next),
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AbsTextContent(state: AudioMiniPlayerState.Abs) {
    val chapter = state.currentChapter
    if (chapter != null && chapter.title.isNotBlank()) {
        Text(
            text = chapter.title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            modifier =
                Modifier.basicMarquee(
                    iterations = if (state.isPlaying) Int.MAX_VALUE else 0,
                    velocity = 30.dp,
                ),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = state.title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    } else {
        Text(
            text = state.title,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            modifier =
                Modifier.basicMarquee(
                    iterations = if (state.isPlaying) Int.MAX_VALUE else 0,
                    velocity = 30.dp,
                ),
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (state.author != null) {
            Text(
                text = state.author,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MusicTextContent(state: AudioMiniPlayerState.Music) {
    Text(
        text = state.title,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
        modifier =
            Modifier.basicMarquee(
                iterations = if (state.isPlaying) Int.MAX_VALUE else 0,
                velocity = 30.dp,
            ),
        color = MaterialTheme.colorScheme.onSurface,
    )
    if (state.artist != null) {
        Text(
            text = state.artist,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
