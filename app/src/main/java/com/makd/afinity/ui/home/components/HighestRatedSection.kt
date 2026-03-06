package com.makd.afinity.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.sp
import com.makd.afinity.R
import com.makd.afinity.data.models.extensions.backdropBlurHash
import com.makd.afinity.data.models.extensions.backdropImageUrl
import com.makd.afinity.data.models.extensions.primaryBlurHash
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.theme.CardDimensions.portraitWidth
import java.util.Locale

@Composable
fun HighestRatedSection(
    items: List<AfinityItem>,
    onItemClick: (AfinityItem) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
) {
    val cardWidth = widthSizeClass.portraitWidth
    val cardHeight = CardDimensions.calculateHeight(cardWidth, CardDimensions.ASPECT_RATIO_PORTRAIT)
    val fixedRowHeight = cardHeight + 8.dp + 20.dp + 22.dp

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = stringResource(R.string.home_critics_choice),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        val uniqueItems = items.distinctBy { it.id }

        LazyRow(
            modifier = Modifier.height(fixedRowHeight),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            contentPadding = PaddingValues(start = 4.dp, end = 24.dp),
        ) {
            itemsIndexed(items = uniqueItems, key = { _, item -> item.id }) { index, item ->
                HighestRatedCard(
                    item = item,
                    ranking = index + 1,
                    onClick = { onItemClick(item) },
                    cardWidth = cardWidth,
                )
            }
        }
    }
}

@Composable
fun HighestRatedCard(item: AfinityItem, ranking: Int, onClick: () -> Unit, cardWidth: Dp) {
    val density = LocalDensity.current
    val fontScale = density.fontScale

    val baseFontSize = MaterialTheme.typography.bodySmall.fontSize
    val metadataFontSize =
        remember(fontScale, baseFontSize) {
            baseFontSize * if (fontScale > 1.3f) 0.8f else if (fontScale > 1.15f) 0.9f else 1f
        }

    val imdbIconSize =
        remember(fontScale) {
            if (fontScale > 1.3f) 14.dp else if (fontScale > 1.15f) 16.dp else 18.dp
        }

    val rtIconSize =
        remember(fontScale) {
            if (fontScale > 1.3f) 10.dp else if (fontScale > 1.15f) 11.dp else 12.dp
        }

    Column(modifier = Modifier.width(cardWidth)) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(CardDimensions.ASPECT_RATIO_PORTRAIT)) {
            Card(
                onClick = onClick,
                modifier = Modifier.fillMaxSize().offset(x = 16.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        imageUrl = item.images.primaryImageUrl ?: item.images.backdropImageUrl,
                        contentDescription = item.name,
                        blurHash = item.images.primaryBlurHash ?: item.images.backdropBlurHash,
                        targetWidth = cardWidth,
                        targetHeight = cardWidth * 3f / 2f,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )

                    when {
                        item.played -> {
                            Box(
                                modifier =
                                    Modifier.align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .size(24.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_check),
                                    contentDescription = stringResource(R.string.cd_watched_status),
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }

                        item is AfinityShow -> {
                            val displayCount = item.unplayedItemCount ?: item.episodeCount
                            displayCount?.let { count ->
                                if (count > 0) {
                                    Surface(
                                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                    ) {
                                        Text(
                                            text =
                                                if (count > 99)
                                                    stringResource(R.string.home_episode_count_plus)
                                                else
                                                    stringResource(
                                                        R.string.home_episode_count_fmt,
                                                        count,
                                                    ),
                                            style =
                                                MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold
                                                ),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier =
                                                Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Text(
                text = ranking.toString(),
                style =
                    MaterialTheme.typography.displayLarge.copy(
                        fontSize = 82.sp,
                        fontWeight = FontWeight.Black,
                    ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.BottomStart).offset(y = 12.dp),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Column(modifier = Modifier.offset(x = 16.dp)) {
            Text(
                text = item.name,
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

                when (item) {
                    is AfinityMovie -> item.productionYear
                    is AfinityShow -> item.productionYear
                    else -> null
                }?.let { year ->
                    metadataItems.add {
                        Text(
                            text = year.toString(),
                            style =
                                MaterialTheme.typography.bodySmall.copy(
                                    fontSize = metadataFontSize
                                ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_imdb_logo),
                                contentDescription = stringResource(R.string.cd_imdb),
                                tint = Color.Unspecified,
                                modifier = Modifier.size(imdbIconSize),
                            )
                            Text(
                                text = String.format(Locale.US, "%.1f", rating),
                                style =
                                    MaterialTheme.typography.bodySmall.copy(
                                        fontSize = metadataFontSize
                                    ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (item is AfinityMovie) {
                    item.criticRating?.let { rtRating ->
                        metadataItems.add {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Icon(
                                    painter =
                                        painterResource(
                                            id =
                                                if (rtRating > 60) {
                                                    R.drawable.ic_rotten_tomato_fresh
                                                } else {
                                                    R.drawable.ic_rotten_tomato_rotten
                                                }
                                        ),
                                    contentDescription =
                                        stringResource(R.string.cd_rotten_tomatoes),
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(rtIconSize),
                                )
                                Text(
                                    text = "${rtRating.toInt()}%",
                                    style =
                                        MaterialTheme.typography.bodySmall.copy(
                                            fontSize = metadataFontSize
                                        ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                metadataItems.forEachIndexed { index, item ->
                    item()
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
}
