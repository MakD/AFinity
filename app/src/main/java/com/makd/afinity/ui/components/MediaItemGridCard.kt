package com.makd.afinity.ui.components

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.common.CardSize
import com.makd.afinity.data.models.extensions.landscapeBlurHash
import com.makd.afinity.data.models.extensions.landscapeImageUrl
import com.makd.afinity.data.models.extensions.posterBlurHash
import com.makd.afinity.data.models.extensions.posterImageUrl
import com.makd.afinity.data.models.extensions.primaryBlurHash
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.media.AfinityAudiobook
import com.makd.afinity.data.models.media.AfinityBoxSet
import com.makd.afinity.data.models.media.AfinityFolder
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityMusicVideo
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.media.AfinityVideo
import com.makd.afinity.data.models.media.AfinityVideoPlaylist
import com.makd.afinity.navigation.LocalShowRatings
import com.makd.afinity.ui.preview.CardSizePreviewProvider
import com.makd.afinity.ui.preview.FontScalePreviews
import com.makd.afinity.ui.preview.PreviewData
import com.makd.afinity.ui.preview.PreviewHost
import com.makd.afinity.ui.preview.StressPreviews
import com.makd.afinity.ui.preview.WidthPreviews
import com.makd.afinity.ui.theme.CardDimensions
import java.util.Locale

enum class AudiobookCardFrame {
    SQUARE,
    LETTERBOX,
}

@Composable
fun MediaItemGridCard(
    item: AfinityItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    audiobookFrame: AudiobookCardFrame = AudiobookCardFrame.SQUARE,
) {
    val ratingScale = rememberRatingMetadataScale()
    val letterboxed = item is AfinityAudiobook && audiobookFrame == AudiobookCardFrame.LETTERBOX
    val aspectRatio =
        when {
            item is AfinityMusicVideo -> CardDimensions.ASPECT_RATIO_LANDSCAPE
            item is AfinityVideoPlaylist || item is AfinityVideo ->
                CardDimensions.ASPECT_RATIO_SQUARE
            item is AfinityAudiobook && !letterboxed -> CardDimensions.ASPECT_RATIO_SQUARE
            else -> CardDimensions.ASPECT_RATIO_PORTRAIT
        }
    val imageUrl =
        when (aspectRatio) {
            CardDimensions.ASPECT_RATIO_LANDSCAPE -> item.images.landscapeImageUrl
            CardDimensions.ASPECT_RATIO_PORTRAIT -> item.images.posterImageUrl
            else -> item.images.primaryImageUrl
        }
    val blurHash =
        when (aspectRatio) {
            CardDimensions.ASPECT_RATIO_LANDSCAPE -> item.images.landscapeBlurHash
            CardDimensions.ASPECT_RATIO_PORTRAIT -> item.images.posterBlurHash
            else -> item.images.primaryBlurHash
        }

    Column(modifier = modifier.fillMaxWidth()) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(aspectRatio),
            shape = RoundedCornerShape(12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Box {
                if (letterboxed) {
                    LetterboxedCover(item = item, modifier = Modifier.fillMaxSize())
                    AudiobookBadge(modifier = Modifier.align(Alignment.TopStart))
                } else {
                    AsyncImage(
                        imageUrl = imageUrl,
                        contentDescription = item.name,
                        blurHash = blurHash,
                        targetWidth = 160.dp,
                        targetHeight = 160.dp / aspectRatio,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                }

                if (item is AfinityFolder && imageUrl == null) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_folder),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center).size(48.dp),
                    )
                }

                when {
                    item.played -> {
                        PlayedBadge(modifier = Modifier.align(Alignment.TopEnd))
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
                                modifier = Modifier.align(Alignment.TopEnd),
                            )
                        }
                    }

                    item is AfinityMusicVideo && (item.mediaSourceCount ?: 0) > 1 -> {
                        MediaCountBadge(
                            text = "${item.mediaSourceCount}",
                            modifier = Modifier.align(Alignment.TopEnd),
                        )
                    }

                    item is AfinityVideoPlaylist -> {
                        val displayCount = item.itemCount
                        displayCount?.let { count ->
                            if (count > 0) {
                                MediaCountBadge(
                                    text = "$count",
                                    modifier = Modifier.align(Alignment.TopEnd),
                                )
                            }
                        }
                    }

                    item is AfinityFolder -> {
                        val unplayedCount = item.unplayedItemCount
                        if (unplayedCount != null && unplayedCount > 0) {
                            MediaCountBadge(
                                text = "$unplayedCount",
                                modifier = Modifier.align(Alignment.TopEnd),
                            )
                        }
                    }

                    item is AfinityBoxSet -> {
                        val displayCount = item.unplayedItemCount ?: item.itemCount
                        displayCount?.let { count ->
                            if (count > 0) {
                                MediaCountBadge(
                                    text = "$count",
                                    modifier = Modifier.align(Alignment.TopEnd),
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

        if (item is AfinityMusicVideo) {
            item.artists
                .joinToString(", ")
                .ifBlank { null }
                ?.let { subtitle ->
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start,
                    )
                }
        }

        val showRatings = LocalShowRatings.current
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
                            fontSize =
                                MaterialTheme.typography.bodySmall.fontSize * ratingScale.textScale,
                        ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (showRatings) {
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
                            painter = painterResource(id = R.drawable.ic_community_rating),
                            contentDescription = stringResource(R.string.cd_imdb),
                            tint = Color.Unspecified,
                            modifier = Modifier.size(ratingScale.rtIconSize),
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f", rating),
                            style =
                                MaterialTheme.typography.bodySmall.copy(
                                    fontSize =
                                        MaterialTheme.typography.bodySmall.fontSize *
                                            ratingScale.textScale,
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
                                            },
                                    ),
                                contentDescription = stringResource(R.string.cd_rotten_tomatoes),
                                tint = Color.Unspecified,
                                modifier = Modifier.size(ratingScale.rtIconSize),
                            )
                            Text(
                                text = "${rtRating.toInt()}%",
                                style =
                                    MaterialTheme.typography.bodySmall.copy(
                                        fontSize =
                                            MaterialTheme.typography.bodySmall.fontSize *
                                                ratingScale.textScale,
                                    ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        MetadataOverflowRow(items = metadataItems, spacing = 6.dp, showSeparators = false)
    }
}

@Composable
private fun LetterboxedCover(item: AfinityItem, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        AsyncImage(
            imageUrl = item.images.primaryImageUrl,
            contentDescription = null,
            blurHash = item.images.primaryBlurHash,
            targetWidth = 160.dp,
            targetHeight = 160.dp,
            modifier = Modifier.fillMaxSize().blur(24.dp),
            contentScale = ContentScale.Crop,
        )
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
        AsyncImage(
            imageUrl = item.images.primaryImageUrl,
            contentDescription = item.name,
            blurHash = item.images.primaryBlurHash,
            targetWidth = 160.dp,
            targetHeight = 160.dp,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).align(Alignment.Center),
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun MediaItemGridCardPreviewContent(cardSize: CardSize) {
    PreviewHost(cardSize = cardSize) {
        LazyVerticalGrid(
            columns = CardDimensions.gridCells(),
            modifier = Modifier.fillMaxWidth().height(640.dp),
            contentPadding = PaddingValues(horizontal = CardDimensions.RowGutter, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(CardDimensions.RowGap),
            verticalArrangement = Arrangement.spacedBy(CardDimensions.GridRowGap),
        ) {
            items(PreviewData.posterRow) { item -> MediaItemGridCard(item = item, onClick = {}) }
        }
    }
}

@WidthPreviews
@Composable
private fun MediaItemGridCardWidthPreview(
    @PreviewParameter(CardSizePreviewProvider::class) cardSize: CardSize,
) {
    MediaItemGridCardPreviewContent(cardSize)
}

@FontScalePreviews
@Composable
private fun MediaItemGridCardFontScalePreview(
    @PreviewParameter(CardSizePreviewProvider::class) cardSize: CardSize,
) {
    MediaItemGridCardPreviewContent(cardSize)
}

@StressPreviews
@Composable
private fun MediaItemGridCardStressPreview(
    @PreviewParameter(CardSizePreviewProvider::class) cardSize: CardSize,
) {
    MediaItemGridCardPreviewContent(cardSize)
}
