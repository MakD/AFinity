package com.makd.afinity.ui.audiobookshelf.item.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.audiobookshelf.PodcastEpisode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EpisodeList(
    episodes: List<PodcastEpisode>,
    onEpisodeClick: (PodcastEpisode) -> Unit,
    onEpisodePlay: (PodcastEpisode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            episodes.forEach { episode ->
                EpisodeItem(
                    episode = episode,
                    onClick = { onEpisodeClick(episode) },
                    onPlay = { onEpisodePlay(episode) },
                )
            }
        }
    }
}

@Composable
private fun EpisodeItem(episode: PodcastEpisode, onClick: () -> Unit, onPlay: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 12.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                episode.publishedAt?.let { timestamp ->
                    Text(
                        text = formatDate(timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                episode.duration?.let { duration ->
                    if (episode.publishedAt != null) {
                        Text(
                            text = " • ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = formatDuration(duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            episode.description?.let { description ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = MaterialTheme.typography.bodySmall.lineHeight * 1.2,
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        FilledIconButton(
            onClick = onPlay,
            modifier = Modifier.size(40.dp),
            colors =
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_player_play_filled),
                contentDescription = "Play episode",
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return dateFormat.format(Date(timestamp))
}

private fun formatDuration(seconds: Double): String {
    val totalSeconds = seconds.toLong()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60

    return if (hours > 0) {
        "${hours}h ${minutes}m"
    } else {
        "${minutes}m"
    }
}
