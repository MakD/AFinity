package com.makd.afinity.ui.audiobookshelf.player.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.makd.afinity.R

@Composable
fun MiniPlayer(
    title: String,
    author: String?,
    coverUrl: String?,
    currentTime: Double,
    duration: Double,
    isPlaying: Boolean,
    isBuffering: Boolean,
    onPlayPauseClick: () -> Unit,
    onCloseClick: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = if (duration > 0) (currentTime / duration).toFloat().coerceIn(0f, 1f) else 0f

    Surface(
        modifier =
            modifier
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
        tonalElevation = 4.dp,
        shadowElevation = 2.dp,
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier.size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (coverUrl != null) {
                        AsyncImage(
                            model = coverUrl,
                            contentDescription = title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (author != null) {
                        Text(
                            text = author,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                IconButton(onClick = onPlayPauseClick) {
                    if (isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        AnimatedContent(targetState = isPlaying, label = "play_pause") { playing ->
                            Icon(
                                painter =
                                    if (playing) painterResource(R.drawable.ic_player_pause_filled)
                                    else painterResource(R.drawable.ic_player_play_filled),
                                contentDescription = if (playing) "Pause" else "Play",
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }

                IconButton(onClick = onCloseClick) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = "Close",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent,
                strokeCap = StrokeCap.Round,
            )
        }
    }
}
