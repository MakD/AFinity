package com.makd.afinity.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import com.makd.afinity.data.models.extensions.showBackdropBlurHash
import com.makd.afinity.data.models.extensions.showBackdropImageUrl
import com.makd.afinity.data.models.extensions.showThumbBlurHash
import com.makd.afinity.data.models.extensions.showThumbImageUrl
import com.makd.afinity.data.models.extensions.thumbBlurHash
import com.makd.afinity.data.models.extensions.thumbImageUrl
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.navigation.LocalShowRatings
import com.makd.afinity.ui.theme.CardDimensions
import java.util.Locale

@Composable
fun ContinueWatchingCard(
    item: AfinityItem,
    onClick: () -> Unit,
    cardWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val ratingScale = rememberRatingMetadataScale()

    Column(modifier = modifier.width(cardWidth)) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(CardDimensions.ASPECT_RATIO_LANDSCAPE),
            colors =
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val blurHash =
                    if (item is AfinityMovie) {
                        item.images.thumbBlurHash
                            ?: item.images.backdropBlurHash
                            ?: item.images.primaryBlurHash
                    } else {
                        item.images.primaryBlurHash
                            ?: item.images.thumbBlurHash
                            ?: item.images.backdropBlurHash
                            ?: item.images.showThumbBlurHash
                            ?: item.images.showBackdropBlurHash
                    }

                val imageUrl =
                    if (item is AfinityMovie) {
                        item.images.thumbImageUrl
                            ?: item.images.backdropImageUrl
                            ?: item.images.primaryImageUrl
                    } else {
                        item.images.primaryImageUrl
                            ?: item.images.thumbImageUrl
                            ?: item.images.backdropImageUrl
                            ?: item.images.showThumbImageUrl
                            ?: item.images.showBackdropImageUrl
                    }

                val isMissing = item is AfinityEpisode && item.missing
                val isUpcoming =
                    isMissing &&
                        (item as AfinityEpisode)
                            .premiereDate
                            ?.isAfter(java.time.LocalDateTime.now()) == true
                val isMissingAndAired = isMissing && !isUpcoming

                AsyncImage(
                    imageUrl = imageUrl,
                    blurHash = blurHash,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize().alpha(if (isMissingAndAired) 0.5f else 1f),
                    contentScale = ContentScale.Crop,
                    targetWidth = cardWidth,
                    targetHeight = cardWidth / CardDimensions.ASPECT_RATIO_LANDSCAPE,
                )

                val progressPercentage =
                    if (item.runtimeTicks > 0) {
                        (item.playbackPositionTicks.toFloat() / item.runtimeTicks.toFloat())
                            .coerceIn(0f, 1f)
                    } else 0f

                if (progressPercentage > 0f) {
                    LinearProgressIndicator(
                        progress = { progressPercentage },
                        modifier =
                            Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Black.copy(alpha = 0.3f),
                    )
                }

                if (isMissingAndAired) {
                    Box(
                        modifier =
                            Modifier.align(Alignment.TopEnd)
                                .padding(8.dp)
                                .background(Color.Red.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.episode_missing),
                            color = Color.White,
                            style =
                                MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                ),
                        )
                    }
                } else if (isUpcoming) {
                    Box(
                        modifier =
                            Modifier.align(Alignment.TopEnd)
                                .padding(8.dp)
                                .background(
                                    Color(0xFF2E7D32).copy(alpha = 0.9f),
                                    RoundedCornerShape(4.dp),
                                )
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.episode_upcoming),
                            color = Color.White,
                            style =
                                MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                ),
                        )
                    }
                } else if (item.played) {
                    PlayedBadge(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (item) {
            is AfinityEpisode -> {
                Text(
                    text = item.name,
                    style =
                        MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            else -> {
                Text(
                    text = item.name,
                    style =
                        MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        when (item) {
            is AfinityMovie -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val metadataItems = mutableListOf<@Composable () -> Unit>()
                    val showRatings = LocalShowRatings.current

                    item.productionYear?.let { year ->
                        metadataItems.add {
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
                    }

                    if (showRatings) {
                        item.communityRating?.let { imdbRating ->
                            metadataItems.add {
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
                                        text = String.format(Locale.US, "%.1f", imdbRating),
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

                        item.criticRating?.let { rtRating ->
                            metadataItems.add {
                                Row(verticalAlignment = Alignment.CenterVertically) {
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
                                            stringResource(R.string.cd_rotten_tomatoes_rating),
                                        modifier = Modifier.size(ratingScale.rtIconSize),
                                        tint = Color.Unspecified,
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
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

            is AfinityEpisode -> {
                val showRatings = LocalShowRatings.current
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text =
                            "S${item.parentIndexNumber}:E" +
                                (if (
                                    item.indexNumberEnd != null &&
                                        item.indexNumberEnd != item.indexNumber
                                )
                                    "${item.indexNumber}-E${item.indexNumberEnd}"
                                else "${item.indexNumber}") +
                                " • ${item.seriesName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )

                    if (showRatings) {
                        item.communityRating?.let { imdbRating ->
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
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
                                    text = String.format(Locale.US, "%.1f", imdbRating),
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
                if (LocalShowRatings.current) {
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
