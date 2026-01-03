package com.makd.afinity.ui.requests

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.makd.afinity.data.models.jellyseerr.MediaStatus
import com.makd.afinity.data.models.jellyseerr.SearchResultItem
import com.makd.afinity.ui.components.OptimizedAsyncImage

@Composable
fun DiscoverMediaCard(
    item: SearchResultItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.width(120.dp),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column {
            Card(
                modifier = Modifier
                    .width(120.dp)
                    .height(180.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    OptimizedAsyncImage(
                        imageUrl = item.getPosterUrl(),
                        contentDescription = item.getDisplayTitle(),
                        blurHash = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    if (item.hasExistingRequest()) {
                        item.getDisplayStatus()?.let { status ->
                            val backgroundColor = when (status) {
                                MediaStatus.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
                                MediaStatus.PENDING -> MaterialTheme.colorScheme.tertiary
                                MediaStatus.PROCESSING -> MaterialTheme.colorScheme.primary
                                MediaStatus.PARTIALLY_AVAILABLE -> MaterialTheme.colorScheme.secondary
                                MediaStatus.AVAILABLE -> MaterialTheme.colorScheme.secondary
                                MediaStatus.DELETED -> MaterialTheme.colorScheme.error
                            }

                            val textColor = when (status) {
                                MediaStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                                MediaStatus.PENDING -> MaterialTheme.colorScheme.onTertiary
                                MediaStatus.PROCESSING -> MaterialTheme.colorScheme.onPrimary
                                MediaStatus.PARTIALLY_AVAILABLE -> MaterialTheme.colorScheme.onSecondary
                                MediaStatus.AVAILABLE -> MaterialTheme.colorScheme.onSecondary
                                MediaStatus.DELETED -> MaterialTheme.colorScheme.onError
                            }

                            Card(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp),
                                shape = RoundedCornerShape(4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = backgroundColor
                                )
                            ) {
                                Text(
                                    text = MediaStatus.getDisplayName(status),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = textColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Card(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp),
                            shape = RoundedCornerShape(4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Text(
                                text = item.getMediaType()?.name ?: "UNKNOWN",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = item.getDisplayTitle(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}