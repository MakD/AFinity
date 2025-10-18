package com.makd.afinity.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.makd.afinity.R
import com.makd.afinity.R.drawable.ic_launcher_monochrome
import com.makd.afinity.data.models.extensions.backdropBlurHash
import com.makd.afinity.data.models.extensions.backdropImageUrl
import com.makd.afinity.data.models.extensions.primaryBlurHash
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.navigation.Destination
import com.makd.afinity.ui.components.OptimizedAsyncImage
import com.makd.afinity.ui.components.OptimizedHeroCarousel
import com.makd.afinity.ui.home.components.NextUpSection
import com.makd.afinity.ui.home.components.OptimizedContinueWatchingSection
import com.makd.afinity.ui.home.components.OptimizedLatestMoviesSection
import com.makd.afinity.ui.home.components.OptimizedLatestTvSeriesSection
import com.makd.afinity.ui.home.components.OptimizedRecommendationCategorySection
import com.makd.afinity.ui.item.components.EpisodeDetailOverlay
import com.makd.afinity.ui.main.MainUiState
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.theme.calculateCardHeight
import com.makd.afinity.ui.theme.rememberPortraitCardWidth
import kotlinx.coroutines.delay
import com.makd.afinity.data.models.media.AfinityEpisode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    mainUiState: MainUiState,
    onItemClick: (AfinityItem) -> Unit,
    onPlayClick: (AfinityItem) -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val configuration = LocalConfiguration.current
    val screenHeight = configuration.screenHeightDp.dp
    val lazyListState = rememberLazyListState()

    val topBarOpacity by remember {
        derivedStateOf {
            val firstVisibleItemIndex = lazyListState.firstVisibleItemIndex
            val scrollOffset = lazyListState.firstVisibleItemScrollOffset
            if (firstVisibleItemIndex > 0 || scrollOffset > 1500) {
                1f
            } else {
                0f
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.clearSelectedEpisode()
        }
    }

    val isScrolling by remember {
        derivedStateOf {
            lazyListState.isScrollInProgress
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        uiState.error?.let { error ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Something went wrong",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Data will refresh automatically",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } ?: run {
            val configuration = LocalConfiguration.current
            val isLandscape =
                configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    if (uiState.heroCarouselItems.isNotEmpty()) {
                        item(key = "hero_carousel") {
                            OptimizedHeroCarousel(
                                items = uiState.heroCarouselItems,
                                height = screenHeight * 0.65f,
                                isScrolling = isScrolling,
                                onWatchNowClick = onPlayClick,
                                onPlayTrailerClick = { item ->
                                    viewModel.onPlayTrailerClick(context, item)
                                },
                                onMoreInformationClick = onItemClick
                            )
                        }
                    }

                    item(key = "content_wrapper") {
                        Column(
                            modifier = if (isLandscape) {
                                Modifier
                                    .fillMaxWidth()
                                    .offset(y = (-70).dp)
                            } else {
                                Modifier.fillMaxWidth()
                            }
                        ) {
                            if (uiState.continueWatching.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(24.dp))
                                OptimizedContinueWatchingSection(
                                    items = uiState.continueWatching,
                                    onItemClick = { item ->
                                        if (item is com.makd.afinity.data.models.media.AfinityEpisode) {
                                            viewModel.selectEpisode(item)
                                        } else {
                                            onItemClick(item)
                                        }
                                    }
                                )
                            } else if (uiState.isLoading && uiState.latestMedia.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(24.dp))
                                ContinueWatchingSkeleton()
                            }

                            if (uiState.nextUp.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(24.dp))
                                NextUpSection(
                                    episodes = uiState.nextUp,
                                    onEpisodeClick = { episode ->
                                        viewModel.selectEpisode(episode)
                                    }
                                )
                            }

                            if (uiState.combineLibrarySections) {
                                if (uiState.latestMovies.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(24.dp))
                                    OptimizedLatestMoviesSection(
                                        items = uiState.latestMovies,
                                        onItemClick = onItemClick
                                    )
                                } else if (uiState.isLoading && uiState.latestMedia.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(24.dp))
                                    MoviesSectionSkeleton()
                                }
                            } else {
                                uiState.separateMovieLibrarySections.forEachIndexed { index, (library, movies) ->
                                    Spacer(modifier = Modifier.height(24.dp))
                                    OptimizedLatestMoviesSection(
                                        title = library.name,
                                        items = movies,
                                        onItemClick = onItemClick
                                    )
                                }
                            }

                            if (uiState.combineLibrarySections) {
                                if (uiState.latestTvSeries.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(24.dp))
                                    OptimizedLatestTvSeriesSection(
                                        items = uiState.latestTvSeries,
                                        onItemClick = onItemClick
                                    )
                                } else if (uiState.isLoading && uiState.latestMedia.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(24.dp))
                                    TvSeriesSectionSkeleton()
                                }
                            } else {
                                uiState.separateTvLibrarySections.forEachIndexed { index, (library, shows) ->
                                    Spacer(modifier = Modifier.height(24.dp))
                                    OptimizedLatestTvSeriesSection(
                                        title = library.name,
                                        items = shows,
                                        onItemClick = onItemClick
                                    )
                                }
                            }

                            if (uiState.highestRated.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(24.dp))
                                HighestRatedSection(
                                    items = uiState.highestRated,
                                    onItemClick = onItemClick
                                )
                            }

                            uiState.recommendationCategories.forEach { category ->
                                Spacer(modifier = Modifier.height(24.dp))
                                OptimizedRecommendationCategorySection(
                                    category = category,
                                    onItemClick = onItemClick
                                )
                            }

                            if (uiState.recommendationCategories.isEmpty() &&
                                uiState.latestMovies.isNotEmpty() &&
                                uiState.latestTvSeries.isNotEmpty() &&
                                uiState.isLoading
                            ) {
                                Spacer(modifier = Modifier.height(24.dp))
                                RecommendationsSkeleton()
                            }

                            Spacer(modifier = Modifier.height(32.dp))
                        }
                    }
                }
            }
        }

        TransparentTopBar(
            onSearchClick = {
                val route = Destination.createSearchRoute()
                navController.navigate(route)
            },
            onProfileClick = onProfileClick,
            userProfileImageUrl = mainUiState.userProfileImageUrl,
            backgroundOpacity = topBarOpacity
        )
        val selectedEpisode by viewModel.selectedEpisode.collectAsStateWithLifecycle()
        val isLoadingEpisode by viewModel.isLoadingEpisode.collectAsStateWithLifecycle()
        val selectedEpisodeWatchlistStatus by viewModel.selectedEpisodeWatchlistStatus.collectAsStateWithLifecycle()

        var pendingNavigationSeriesId by remember { mutableStateOf<String?>(null) }

        var episodeForOverlay by remember { mutableStateOf<AfinityEpisode?>(null) }
        if (selectedEpisode != null) {
            episodeForOverlay = selectedEpisode
        }

        AnimatedVisibility(
            visible = selectedEpisode != null,
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            episodeForOverlay?.let { episode ->
                EpisodeDetailOverlay(
                    episode = episode,
                    isLoading = isLoadingEpisode,
                    isInWatchlist = selectedEpisodeWatchlistStatus,
                    onDismiss = {
                        viewModel.clearSelectedEpisode()
                        pendingNavigationSeriesId = null
                    },
                    onPlayClick = { episodeToPlay, selection ->
                        viewModel.clearSelectedEpisode()

                        com.makd.afinity.ui.player.PlayerLauncher.launch(
                            context = context,
                            itemId = episodeToPlay.id,
                            mediaSourceId = selection.mediaSourceId,
                            audioStreamIndex = selection.audioStreamIndex,
                            subtitleStreamIndex = selection.subtitleStreamIndex,
                            startPositionMs = selection.startPositionMs
                        )
                    },
                    onToggleFavorite = {
                        viewModel.toggleEpisodeFavorite(episode)
                    },
                    onToggleWatchlist = {
                        viewModel.toggleEpisodeWatchlist(episode)
                    },
                    onToggleWatched = {
                        viewModel.toggleEpisodeWatched(episode)
                    },
                    onGoToSeries = {
                        viewModel.clearSelectedEpisode()
                        pendingNavigationSeriesId = episode.seriesId.toString()
                    }
                )
            }
        }


        LaunchedEffect(selectedEpisode, pendingNavigationSeriesId) {
            if (selectedEpisode == null && pendingNavigationSeriesId != null) {
                delay(300)
                val route = Destination.createItemDetailRoute(pendingNavigationSeriesId!!)
                navController.navigate(route)
                pendingNavigationSeriesId = null
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransparentTopBar(
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    userProfileImageUrl: String? = null,
    backgroundOpacity: Float = 0f
) {
    TopAppBar(
        title = {
            IconButton(
                onClick = { /* TODO: Handle app icon click if needed */ },
                modifier = Modifier.size(48.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Color.Black.copy(alpha = 0.3f),
                            CircleShape
                        )
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = ic_launcher_monochrome),
                        contentDescription = "App Logo",
                        modifier = Modifier
                            .size(60.dp)
                            .fillMaxSize(),
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        },
        actions = {
            Button(
                onClick = onSearchClick,
                modifier = Modifier
                    .height(48.dp)
                    .width(120.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(24.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Search",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onProfileClick,
                modifier = Modifier.size(48.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Color.Black.copy(alpha = 0.3f),
                            CircleShape
                        )
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (userProfileImageUrl != null) {
                        OptimizedAsyncImage(
                            imageUrl = userProfileImageUrl,
                            contentDescription = "Profile Picture",
                            targetWidth = 48.dp,
                            targetHeight = 48.dp,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profile",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = backgroundOpacity)
        ),
        modifier = Modifier.zIndex(1f)
    )
}

@Composable
private fun HighestRatedSection(
    items: List<AfinityItem>,
    onItemClick: (AfinityItem) -> Unit
) {
    val cardWidth = rememberPortraitCardWidth()
    val cardHeight = calculateCardHeight(cardWidth, CardDimensions.ASPECT_RATIO_PORTRAIT)
    val fixedRowHeight = cardHeight + 8.dp + 20.dp + 22.dp
    Column(
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        Text(
            text = "Critics' Choice",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        val uniqueItems = items.distinctBy { it.id }

        LazyRow(
            modifier = Modifier.height(fixedRowHeight),
            horizontalArrangement = Arrangement.spacedBy(22.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            itemsIndexed(
                items = uniqueItems,
                key = { _, item -> item.id }
            ) { index, item ->
                HighestRatedCard(
                    item = item,
                    ranking = index + 1,
                    onClick = { onItemClick(item) }
                )
            }
        }
    }
}

@Composable
private fun HighestRatedCard(
    item: AfinityItem,
    ranking: Int,
    onClick: () -> Unit
) {
    val cardWidth = rememberPortraitCardWidth()
    val density = LocalDensity.current
    val fontScale = density.fontScale

    Column(
        modifier = Modifier.width(cardWidth)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(CardDimensions.ASPECT_RATIO_PORTRAIT)
        ) {
            Card(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxSize()
                    .offset(x = 16.dp),
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

            Text(
                text = ranking.toString(),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 82.sp,
                    fontWeight = FontWeight.Black
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(y = 12.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier.offset(x = 16.dp)
        ) {
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

                metadataItems.forEachIndexed { index, item ->
                    item()
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
}

@Composable
private fun ContinueWatchingSkeleton() {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .width(200.dp)
                .height(24.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .shimmerEffect()
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(5) {
                val cardWidth = rememberPortraitCardWidth()

                Column(modifier = Modifier.width(cardWidth)) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(CardDimensions.ASPECT_RATIO_PORTRAIT),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .shimmerEffect()
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .width(cardWidth * 0.8f)
                            .height(16.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(4.dp)
                            )
                            .shimmerEffect()
                    )
                }
            }
        }
    }
}

@Composable
private fun MoviesSectionSkeleton() {
    val cardWidth = rememberPortraitCardWidth()

    Column(
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .width(150.dp)
                .height(24.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    RoundedCornerShape(4.dp)
                )
                .shimmerEffect()
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(6) {
                Column(modifier = Modifier.width(cardWidth)) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(CardDimensions.ASPECT_RATIO_PORTRAIT),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .shimmerEffect()
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .width(cardWidth * 0.9f)
                            .height(16.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(4.dp)
                            )
                            .shimmerEffect()
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Box(
                        modifier = Modifier
                            .width(cardWidth * 0.6f)
                            .height(12.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                RoundedCornerShape(4.dp)
                            )
                            .shimmerEffect()
                    )
                }
            }
        }
    }
}

@Composable
private fun TvSeriesSectionSkeleton() {
    val cardWidth = rememberPortraitCardWidth()

    Column(
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .width(180.dp)
                .height(24.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    RoundedCornerShape(4.dp)
                )
                .shimmerEffect()
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(6) {
                Column(modifier = Modifier.width(cardWidth)) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(CardDimensions.ASPECT_RATIO_PORTRAIT),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .shimmerEffect()
                            )

                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .width(50.dp)
                                    .height(20.dp)
                                    .background(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .shimmerEffect()
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .width(cardWidth * 0.9f)
                            .height(16.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                RoundedCornerShape(4.dp)
                            )
                            .shimmerEffect()
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Box(
                        modifier = Modifier
                            .width(cardWidth * 0.7f)
                            .height(12.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                RoundedCornerShape(4.dp)
                            )
                            .shimmerEffect()
                    )
                }
            }
        }
    }
}

@Composable
private fun RecommendationsSkeleton() {
    val cardWidth = rememberPortraitCardWidth()

    Column(
        modifier = Modifier.padding(horizontal = 16.dp)
    ) {
        repeat(2) {
            Box(
                modifier = Modifier
                    .width(250.dp)
                    .height(24.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        RoundedCornerShape(4.dp)
                    )
                    .shimmerEffect()
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(4) {
                    Column(modifier = Modifier.width(cardWidth)) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(CardDimensions.ASPECT_RATIO_PORTRAIT),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .shimmerEffect()
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .width(cardWidth * 0.8f)
                                .height(14.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    RoundedCornerShape(4.dp)
                                )
                                .shimmerEffect()
                        )
                    }
                }
            }

            if (it == 0) Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Modifier.shimmerEffect(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha = transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    background(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha.value)
    )
}