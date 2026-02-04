package com.makd.afinity.ui.audiobookshelf.item.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.makd.afinity.data.models.audiobookshelf.PodcastEpisode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun EpisodeList(
    episodes: List<PodcastEpisode>,
    onEpisodeClick: (PodcastEpisode) -> Unit,
    onEpisodePlay: (PodcastEpisode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Episodes (${episodes.size})",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        episodes.forEachIndexed { index, episode ->
            EpisodeItem(
                episode = episode,
                onClick = { onEpisodeClick(episode) },
                onPlay = { onEpisodePlay(episode) }
            )

            if (index < episodes.size - 1) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}

@Composable
private fun EpisodeItem(
    episode: PodcastEpisode,
    onClick: () -> Unit,
    onPlay: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row {
                episode.publishedAt?.let { timestamp ->
                    Text(
                        text = formatDate(timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                episode.duration?.let { duration ->
                    Text(
                        text = " \u2022 ${formatDuration(duration)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            episode.description?.let { description ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        IconButton(onClick = onPlay) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Play episode",
                tint = MaterialTheme.colorScheme.primary
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
