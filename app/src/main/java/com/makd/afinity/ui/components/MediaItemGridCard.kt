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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.extensions.primaryBlurHash
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.media.AfinityBoxSet
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.navigation.LocalShowRatings
import java.util.Locale

@Composable
fun MediaItemGridCard(item: AfinityItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ratingScale = rememberRatingMetadataScale()

    Column(modifier = modifier.fillMaxWidth()) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Box {
                AsyncImage(
                    imageUrl = item.images.primaryImageUrl,
                    contentDescription = item.name,
                    blurHash = item.images.primaryBlurHash,
                    targetWidth = 160.dp,
                    targetHeight = 240.dp,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )

                when {
                    item.played -> {
                        PlayedBadge(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
                    }

                    item is AfinityShow -> {
                        val episodeText =
                            when {
                                item.unplayedItemCount != null && item.unplayedItemCount > 0 ->
                                    "${item.unplayedItemCount}"

                                item.episodeCount != null && item.episodeCount > 0 ->
                                    "${item.episodeCount}"

                                else -> null
                            }

                        episodeText?.let { text ->
                            MediaCountBadge(
                                text =
                                    if (text.toIntOrNull() != null && text.toInt() > 99)
                                        stringResource(R.string.home_episode_count_plus)
                                    else
                                        stringResource(
                                            R.string.home_episode_count_fmt,
                                            text.toIntOrNull() ?: 0,
                                        ),
                                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                            )
                        }
                    }

                    item is AfinityBoxSet -> {
                        val displayCount = item.unplayedItemCount ?: item.itemCount
                        displayCount?.let { count ->
                            if (count > 0) {
                                MediaCountBadge(
                                    text = "$count",
                                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = item.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
        )

        val showRatings = LocalShowRatings.current

        when (item) {
            is AfinityMovie -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item.productionYear?.let { year ->
                        Text(
                            text = year.toString(),
                            style =
                                MaterialTheme.typography.bodySmall.copy(
                                    fontSize =
                                        MaterialTheme.typography.bodySmall.fontSize *
                                            ratingScale.textScale
                                ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (showRatings) {
                        item.communityRating?.let { rating ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_imdb_logo),
                                    contentDescription = stringResource(R.string.cd_imdb),
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(ratingScale.imdbIconSize),
                                )
                                Text(
                                    text = String.format(Locale.US, "%.1f", rating),
                                    style =
                                        MaterialTheme.typography.bodySmall.copy(
                                            fontSize =
                                                MaterialTheme.typography.bodySmall.fontSize *
                                                    ratingScale.textScale
                                        ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        item.criticRating?.let { rtRating ->
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
                                    modifier = Modifier.size(ratingScale.rtIconSize),
                                )
                                Text(
                                    text = "${rtRating.toInt()}%",
                                    style =
                                        MaterialTheme.typography.bodySmall.copy(
                                            fontSize =
                                                MaterialTheme.typography.bodySmall.fontSize *
                                                    ratingScale.textScale
                                        ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            is AfinityShow -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item.productionYear?.let { year ->
                        Text(
                            text = year.toString(),
                            style =
                                MaterialTheme.typography.bodySmall.copy(
                                    fontSize =
                                        MaterialTheme.typography.bodySmall.fontSize *
                                            ratingScale.textScale
                                ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (showRatings) {
                        item.communityRating?.let { rating ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_imdb_logo),
                                    contentDescription = stringResource(R.string.cd_imdb),
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(ratingScale.imdbIconSize),
                                )
                                Text(
                                    text = String.format(Locale.US, "%.1f", rating),
                                    style =
                                        MaterialTheme.typography.bodySmall.copy(
                                            fontSize =
                                                MaterialTheme.typography.bodySmall.fontSize *
                                                    ratingScale.textScale
                                        ),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
