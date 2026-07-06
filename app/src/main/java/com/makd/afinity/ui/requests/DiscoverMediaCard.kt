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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.jellyseerr.SearchResultItem
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.theme.CardDimensions

@Composable
fun DiscoverMediaCard(
    item: SearchResultItem,
    onClick: () -> Unit,
    cardWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val fontScale = density.fontScale

    Column(modifier = modifier.width(cardWidth)) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(CardDimensions.ASPECT_RATIO_PORTRAIT),
            colors =
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    imageUrl = item.getPosterUrl(),
                    contentDescription = item.getDisplayTitle(),
                    blurHash = null,
                    targetWidth = cardWidth,
                    targetHeight = cardWidth * 3f / 2f,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )

                if (item.hasExistingRequest()) {
                    item.getDisplayStatus()?.let { status ->
                        StatusChip(
                            attributes = mediaStatusAttributes(status),
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = item.getDisplayTitle(),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
                        style =
                            MaterialTheme.typography.bodySmall.copy(
                                fontSize =
                                    MaterialTheme.typography.bodySmall.fontSize *
                                        if (fontScale > 1.3f) 0.8f
                                        else if (fontScale > 1.15f) 0.9f else 1f
                            ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item.getRating()?.let { rating ->
                metadataItems.add {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_tmdb_short),
                            contentDescription = stringResource(R.string.cd_tmdb_rating),
                            tint = Color.Unspecified,
                            modifier =
                                Modifier.size(
                                    if (fontScale > 1.3f) 12.dp
                                    else if (fontScale > 1.15f) 14.dp else 16.dp
                                ),
                        )
                        Text(
                            text = rating,
                            style =
                                MaterialTheme.typography.bodySmall.copy(
                                    fontSize =
                                        MaterialTheme.typography.bodySmall.fontSize *
                                            if (fontScale > 1.3f) 0.8f
                                            else if (fontScale > 1.15f) 0.9f else 1f
                                ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
