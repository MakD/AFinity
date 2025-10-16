package com.makd.afinity.ui.episode

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import com.makd.afinity.data.models.extensions.backdropBlurHash
import com.makd.afinity.data.models.extensions.backdropImageUrl
import com.makd.afinity.data.models.extensions.primaryBlurHash
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.extensions.showBackdropBlurHash
import com.makd.afinity.data.models.extensions.showBackdropImageUrl
import com.makd.afinity.data.models.extensions.showLogoBlurHash
import com.makd.afinity.data.models.extensions.showLogoImageUrl
import com.makd.afinity.data.models.extensions.thumbBlurHash
import com.makd.afinity.data.models.extensions.thumbImageUrl
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.ui.components.ContinueWatchingCard
import com.makd.afinity.ui.components.OptimizedAsyncImage
import com.makd.afinity.ui.item.components.shared.CastSection
import com.makd.afinity.ui.item.components.shared.ExternalLinksSection
import com.makd.afinity.ui.item.components.shared.SpecialFeaturesSection
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.theme.calculateCardHeight
import com.makd.afinity.ui.theme.rememberLandscapeCardWidth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeListContent(
    season: AfinitySeason,
    lazyEpisodeItems: LazyPagingItems<AfinityEpisode>,
    specialFeatures: List<AfinityItem>,
    isLoading: Boolean,
    onBackClick: () -> Unit,
    onEpisodeClick: (AfinityEpisode) -> Unit,
    onSpecialFeatureClick: (AfinityItem) -> Unit,
    navController: NavController,
    ) {
    val cardWidth = rememberLandscapeCardWidth()
    val cardHeight = calculateCardHeight(cardWidth, CardDimensions.ASPECT_RATIO_LANDSCAPE)
    val fixedRowHeight = cardHeight + 8.dp + 20.dp + 22.dp
    BackHandler {
        onBackClick()
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val overlayOffset = if (isLandscape) (-200).dp else (-110).dp

    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            SeasonHeroSection(
                season = season
            )
        }

        item {
            SeasonDetailsSection(
                season = season,
                specialFeatures = specialFeatures,
                onSpecialFeatureClick = onSpecialFeatureClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-110).dp)
                    .padding(start = 16.dp, end = 16.dp, top = 0.dp, bottom = 16.dp)
            )
        }

        item {
            Text(
                text = "Episodes",
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .offset(y = (-110).dp)
                    .padding(bottom = 16.dp)
            )
        }

        if (lazyEpisodeItems.loadState.refresh is LoadState.Loading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }

        item {
            LazyRow(
                modifier = Modifier
                    .height(fixedRowHeight)
                    .offset(y = (-110).dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                items(
                    count = lazyEpisodeItems.itemCount,
                    key = { index -> lazyEpisodeItems[index]?.id ?: index }
                ) { index ->
                    lazyEpisodeItems[index]?.let { episode ->
                        ContinueWatchingCard(
                            item = episode,
                            onClick = { onEpisodeClick(episode) },
                            modifier = Modifier.width(cardWidth)
                        )
                    }
                }
            }
        }

        if (lazyEpisodeItems.loadState.append is LoadState.Loading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SeasonHeroSection(
    season: AfinitySeason
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(screenHeight * 0.5f)
    ) {
        OptimizedAsyncImage(
            imageUrl = season.images.showBackdropImageUrl ?: season.images.backdropImageUrl,
            contentDescription = "${season.seriesName} backdrop",
            blurHash = season.images.showBackdropBlurHash ?: season.images.backdropBlurHash,
            targetWidth = LocalConfiguration.current.screenWidthDp.dp,
            targetHeight = 300.dp,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 0.99f }
                .drawWithCache {
                    val gradient = Brush.verticalGradient(
                        colors = listOf(
                            Color.Black,
                            Color.Transparent
                        ),
                        startY = size.height * 0.75f,
                        endY = size.height
                    )
                    onDrawWithContent {
                        drawContent()
                        drawRect(gradient, blendMode = BlendMode.DstIn)
                    }
                }
        )
    }
}

@Composable
private fun SeasonDetailsSection(
    season: AfinitySeason,
    specialFeatures: List<AfinityItem>,
    onSpecialFeatureClick: (AfinityItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
            season.images.showLogo?.let { logoUri ->
                OptimizedAsyncImage(
                    imageUrl = season.images.showLogoImageUrl,
                    contentDescription = "${season.seriesName} logo",
                    blurHash = season.images.showLogoBlurHash,
                    targetWidth = (LocalConfiguration.current.screenWidthDp * 0.8f).dp,
                    targetHeight = 120.dp,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .height(120.dp)
                        .align(Alignment.CenterHorizontally)
                )
            }

        Column {
            Text(
                text = season.name,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (season.seriesName.isNotBlank()) {
                Text(
                    text = season.seriesName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        SeasonMetadataRow(season = season)

        if (season.overview.isNotBlank()) {
            var isExpanded by remember { mutableStateOf(false) }
            var isEllipsized by remember { mutableStateOf(false) }

            Column {
                Text(
                    text = season.overview,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 3,
                    overflow = if (isExpanded) TextOverflow.Visible else TextOverflow.Ellipsis,
                    modifier = Modifier.animateContentSize(),
                    onTextLayout = { result ->
                        if (!isExpanded) {
                            isEllipsized = result.hasVisualOverflow
                        }
                    }
                )

                if (isEllipsized || isExpanded) {
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { isExpanded = !isExpanded }
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isExpanded) "See less" else "See more",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        ExternalLinksSection(item = season)

        SpecialFeaturesSection(
            specialFeatures = specialFeatures,
            onItemClick = onSpecialFeatureClick
        )

        CastSection(item = season)
    }
}

@Composable
private fun SeasonMetadataRow(season: AfinitySeason) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        season.episodeCount?.let { episodeCount ->
            if (episodeCount > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = "Episodes",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "$episodeCount Episodes",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        season.productionYear?.let { year ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarToday,
                    contentDescription = "Year",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = year.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (season.played) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Watched",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Watched",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            season.unplayedItemCount?.let { unwatchedCount ->
                if (unwatchedCount > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Unwatched",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "$unwatchedCount Unwatched",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeCard(
    episode: AfinityEpisode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isOverviewExpanded by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    var isLoading by remember { mutableStateOf(true) }
                    var hasError by remember { mutableStateOf(false) }

                    if (isLoading || hasError) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(shimmerBrush())
                        )
                    }

                    OptimizedAsyncImage(
                        imageUrl = episode.images.primaryImageUrl ?: episode.images.thumbImageUrl,
                        contentDescription = episode.name,
                        blurHash = episode.images.primaryBlurHash ?: episode.images.thumbBlurHash,
                        targetWidth = 200.dp,
                        targetHeight = 300.dp,
                        onLoading = { loading -> isLoading = loading },
                        onSuccess = {
                            isLoading = false
                            hasError = false
                        },
                        onError = {
                            isLoading = false
                            hasError = true
                        },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                val watchProgress = if (episode.runtimeTicks > 0 && episode.playbackPositionTicks > 0) {
                    (episode.playbackPositionTicks.toDouble() / episode.runtimeTicks.toDouble()).toFloat()
                } else 0f

                if (watchProgress > 0f) {
                    LinearProgressIndicator(
                        progress = { watchProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .height(4.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }

                if (episode.played) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Watched",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier
                                .padding(4.dp)
                                .size(16.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = "Episode ${episode.indexNumber}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = episode.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (episode.overview.isNotBlank()) {
                    var isEllipsized by remember { mutableStateOf(false) }

                    Column {
                        Text(
                            text = episode.overview,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (isOverviewExpanded) Int.MAX_VALUE else 2,
                            overflow = if (isOverviewExpanded) TextOverflow.Visible else TextOverflow.Ellipsis,
                            lineHeight = 20.sp,
                            modifier = Modifier.animateContentSize(),
                            onTextLayout = { result ->
                                if (!isOverviewExpanded) {
                                    isEllipsized = result.hasVisualOverflow
                                }
                            }
                        )

                        if (isEllipsized || isOverviewExpanded) {
                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { isOverviewExpanded = !isOverviewExpanded }
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (isOverviewExpanded) "See less" else "See more",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                Icon(
                                    imageVector = if (isOverviewExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (isOverviewExpanded) "Collapse" else "Expand",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (episode.runtimeTicks > 0) {
                        val minutes = (episode.runtimeTicks / 10_000_000 / 60).toInt()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = "Runtime",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "${minutes}m",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    episode.communityRating?.let { rating ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Star,
                                contentDescription = "Rating",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = String.format("%.1f", rating),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    episode.premiereDate?.let { premiereDate ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = "Air Date",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = premiereDate.format(
                                    java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy")
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnimation = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )
    return Brush.linearGradient(
        colors = listOf(
            Color.Transparent,
            Color.White.copy(alpha = 0.3f),
            Color.Transparent
        ),
        start = Offset.Zero,
        end = Offset(x = translateAnimation.value, y = translateAnimation.value)
    )
}