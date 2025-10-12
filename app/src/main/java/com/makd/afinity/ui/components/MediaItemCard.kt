package com.makd.afinity.ui.components

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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.extensions.backdropBlurHash
import com.makd.afinity.data.models.extensions.backdropImageUrl
import com.makd.afinity.data.models.extensions.primaryBlurHash
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.theme.rememberPortraitCardWidth

@Composable
fun MediaItemCard(
    item: AfinityItem,
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
                    imageUrl = item.images.primaryImageUrl ?: item.images.backdropImageUrl,
                    contentDescription = item.name,
                    blurHash = item.images.primaryBlurHash ?: item.images.backdropBlurHash,
                    targetWidth = cardWidth,
                    targetHeight = cardWidth * 3f / 2f,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                if (item is AfinityShow) {
                    item.episodeCount?.let { episodeCount ->
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp),
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
                        ) {
                            Text(
                                text = if (episodeCount > 99) "99+ EP" else "$episodeCount EP",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = item.name,
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

            when (item) {
                is AfinityMovie -> item.productionYear
                is AfinityShow -> item.productionYear
                else -> null
            }?.let { year ->
                metadataItems.add {
                    Text(
                        text = year.toString(),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = MaterialTheme.typography.bodySmall.fontSize *
                                    if (fontScale > 1.3f) 0.8f else if (fontScale > 1.15f) 0.9f else 1f
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            when (item) {
                is AfinityMovie -> item.communityRating
                is AfinityShow -> item.communityRating
                else -> null
            }?.let { rating ->
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
                            text = String.format("%.1f", rating),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = MaterialTheme.typography.bodySmall.fontSize *
                                        if (fontScale > 1.3f) 0.8f else if (fontScale > 1.15f) 0.9f else 1f
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (item is AfinityMovie) {
                item.criticRating?.let { rtRating ->
                    metadataItems.add {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                painter = painterResource(
                                    id = if (rtRating > 60) {
                                        R.drawable.ic_rotten_tomato_fresh
                                    } else {
                                        R.drawable.ic_rotten_tomato_rotten
                                    }
                                ),
                                contentDescription = "Rotten Tomatoes",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(
                                    if (fontScale > 1.3f) 10.dp
                                    else if (fontScale > 1.15f) 11.dp
                                    else 12.dp
                                )
                            )
                            Text(
                                text = "${rtRating.toInt()}%",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = MaterialTheme.typography.bodySmall.fontSize *
                                            if (fontScale > 1.3f) 0.8f else if (fontScale > 1.15f) 0.9f else 1f
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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