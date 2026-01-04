package com.makd.afinity.ui.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.jellyseerr.MediaStatus
import com.makd.afinity.data.models.jellyseerr.SearchResultItem
import com.makd.afinity.ui.components.OptimizedAsyncImage
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.theme.rememberPortraitCardWidth

@Composable
fun DiscoverMediaCard(
    item: SearchResultItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val fontScale = density.fontScale
    val cardWidth = rememberPortraitCardWidth()

    Column(
        modifier = modifier.width(cardWidth)
    ) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(CardDimensions.ASPECT_RATIO_PORTRAIT),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                OptimizedAsyncImage(
                    imageUrl = item.getPosterUrl(),
                    contentDescription = item.getDisplayTitle(),
                    blurHash = null,
                    targetWidth = cardWidth,
                    targetHeight = cardWidth * 3f / 2f,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
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
                                .padding(8.dp),
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
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = item.getDisplayTitle(),
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val metadataItems = mutableListOf<@Composable () -> Unit>()

            item.getReleaseYear()?.let { year ->
                metadataItems.add {
                    Text(
                        text = year,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = MaterialTheme.typography.bodySmall.fontSize *
                                    if (fontScale > 1.3f) 0.8f else if (fontScale > 1.15f) 0.9f else 1f
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item.getRating()?.let { rating ->
                metadataItems.add {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_imdb_logo),
                            contentDescription = "IMDB",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(
                                if (fontScale > 1.3f) 14.dp
                                else if (fontScale > 1.15f) 16.dp
                                else 18.dp
                            )
                        )
                        Text(
                            text = rating,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = MaterialTheme.typography.bodySmall.fontSize *
                                        if (fontScale > 1.3f) 0.8f else if (fontScale > 1.15f) 0.9f else 1f
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            metadataItems.forEachIndexed { index, metadataItem ->
                metadataItem()
                if (index < metadataItems.size - 1) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}