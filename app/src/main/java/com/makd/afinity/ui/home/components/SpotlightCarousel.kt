package com.makd.afinity.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.extensions.backdropBlurHash
import com.makd.afinity.data.models.extensions.backdropImageUrl
import com.makd.afinity.data.models.extensions.logoImageUrlWithTransparency
import com.makd.afinity.data.models.extensions.primaryBlurHash
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.extensions.showLogoImageUrl
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.ui.components.AsyncImage
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotlightCarousel(
    title: String,
    items: List<AfinityItem>,
    onItemClick: (AfinityItem) -> Unit,
    onPlayClick: (AfinityItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    val configuration = LocalConfiguration.current
    val isLandscape =
        configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val preferredItemWidth = (configuration.screenWidthDp * if (isLandscape) 0.58f else 0.82f).dp
    val carouselHeight = if (isLandscape) (configuration.screenHeightDp * 0.62f).dp else 220.dp
    val state = rememberCarouselState { items.size }

    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 14.dp, bottom = 12.dp),
        )
        HorizontalMultiBrowseCarousel(
            state = state,
            preferredItemWidth = preferredItemWidth,
            modifier = Modifier.height(carouselHeight).padding(horizontal = 14.dp),
            itemSpacing = 8.dp,
        ) { index ->
            val item = items[index]
            Box(
                modifier =
                    Modifier.height(carouselHeight)
                        .maskClip(MaterialTheme.shapes.extraLarge)
                        .clickable { onItemClick(item) }
            ) {
                AsyncImage(
                    imageUrl = item.images.backdropImageUrl ?: item.images.primaryImageUrl,
                    contentDescription = item.name,
                    blurHash = item.images.backdropBlurHash ?: item.images.primaryBlurHash,
                    targetWidth = preferredItemWidth,
                    targetHeight = carouselHeight,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(
                    modifier =
                        Modifier.fillMaxSize().drawWithCache {
                            val gradient =
                                Brush.verticalGradient(
                                    colors =
                                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                                    startY = size.height * 0.45f,
                                    endY = size.height,
                                )
                            onDrawWithContent {
                                drawRect(Color.Black.copy(alpha = 0.4f))
                                drawRect(gradient)
                                drawContent()
                            }
                        }
                ) {
                    Row(
                        modifier =
                            Modifier.align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        val logoUrl =
                            item.images.logoImageUrlWithTransparency ?: item.images.showLogoImageUrl
                        val imdbRating =
                            when (item) {
                                is AfinityMovie -> item.communityRating
                                is AfinityShow -> item.communityRating
                                else -> null
                            }
                        val rtRating = (item as? AfinityMovie)?.criticRating

                        Column(
                            modifier = Modifier.weight(1f).padding(end = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (logoUrl != null) {
                                AsyncImage(
                                    imageUrl = logoUrl,
                                    contentDescription = item.name,
                                    blurHash = null,
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    contentScale = ContentScale.Fit,
                                    alignment = Alignment.CenterStart,
                                )
                            } else {
                                Text(
                                    text = item.name,
                                    style =
                                        MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            if (imdbRating != null || rtRating != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    imdbRating?.let { rating ->
                                        Icon(
                                            painter = painterResource(R.drawable.ic_imdb_logo),
                                            contentDescription = null,
                                            tint = Color.Unspecified,
                                            modifier = Modifier.size(18.dp),
                                        )
                                        Text(
                                            text = String.format(Locale.US, "%.1f", rating),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                        )
                                    }
                                    if (imdbRating != null && rtRating != null) {
                                        Text(
                                            text = "•",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.6f),
                                        )
                                    }
                                    rtRating?.let { rt ->
                                        Icon(
                                            painter =
                                                painterResource(
                                                    if (rt > 60) R.drawable.ic_rotten_tomato_fresh
                                                    else R.drawable.ic_rotten_tomato_rotten
                                                ),
                                            contentDescription = null,
                                            tint = Color.Unspecified,
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Text(
                                            text = "${rt.toInt()}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                        )
                                    }
                                }
                            }
                        }

                        Box(
                            modifier =
                                Modifier.size(40.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    .clickable { onPlayClick(item) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_player_play_filled),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
