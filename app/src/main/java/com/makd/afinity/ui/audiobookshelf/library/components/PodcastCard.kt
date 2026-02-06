package com.makd.afinity.ui.audiobookshelf.library.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.makd.afinity.R
import com.makd.afinity.data.models.audiobookshelf.LibraryItem

@Composable
fun PodcastCard(
    item: LibraryItem,
    serverUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val coverUrl =
                if (serverUrl != null && item.media.coverPath != null) {
                    "$serverUrl/api/items/${item.id}/cover"
                } else null

            if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = "Cover for ${item.media.metadata.title}",
                    modifier = Modifier.size(64.dp).clip(MaterialTheme.shapes.small),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Card(
                    modifier = Modifier.size(64.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        ),
                ) {}
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.media.metadata.title ?: "Unknown Podcast",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                item.media.metadata.authorName?.let { author ->
                    Text(
                        text = author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                item.media.episodes?.size?.let { count ->
                    Text(
                        text = "$count episodes",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Icon(
                painterResource(id = R.drawable.ic_chevron_right),
                contentDescription = "Open podcast",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
