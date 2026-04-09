@file:UnstableApi

package com.makd.afinity.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.makd.afinity.R
import com.makd.afinity.R.drawable.ic_launcher_monochrome
import com.makd.afinity.data.models.GenreType
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.navigation.Destination
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.HeroCarousel
import com.makd.afinity.ui.home.components.ContinueWatchingSkeleton
import com.makd.afinity.ui.home.components.SpotlightCarousel
import com.makd.afinity.ui.home.components.DownloadedAudiobooksSection
import com.makd.afinity.ui.home.components.GenreSection
import com.makd.afinity.ui.home.components.HighestRatedSection
import com.makd.afinity.ui.home.components.LibrariesSection
import com.makd.afinity.ui.home.components.MovieRecommendationSection
import com.makd.afinity.ui.home.components.MoviesSectionSkeleton
import com.makd.afinity.ui.home.components.NextUpSection
import com.makd.afinity.ui.home.components.OptimizedContinueWatchingSection
import com.makd.afinity.ui.home.components.OptimizedLatestMoviesSection
import com.makd.afinity.ui.home.components.OptimizedLatestTvSeriesSection
import com.makd.afinity.ui.home.components.PersonFromMovieSection
import com.makd.afinity.ui.home.components.PersonSection
import com.makd.afinity.ui.home.components.PopularStudiosSection
import com.makd.afinity.ui.home.components.ShowGenreSection
import com.makd.afinity.ui.home.components.TvSeriesSectionSkeleton
import com.makd.afinity.ui.item.components.EpisodeDetailOverlay
import com.makd.afinity.ui.item.components.QualitySelectionDialog
import com.makd.afinity.ui.main.MainUiState
import com.makd.afinity.ui.utils.verticalLayoutOffset
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    mainUiState: MainUiState,
    onItemClick: (AfinityItem) -> Unit,
    onPlayClick: (AfinityItem) -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
    navController: NavController,
    viewModel: HomeViewModel = hiltViewModel(),
    widthSizeClass: WindowWidthSizeClass,
    onAbsItemClick: (String) -> Unit = {},
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

    DisposableEffect(Unit) { onDispose { viewModel.clearSelectedEpisode() } }

    val isScrolling by remember { derivedStateOf { lazyListState.isScrollInProgress } }

    Box(modifier = modifier.fillMaxSize()) {
        uiState.error?.let { error ->
            Box(
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.home_error_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.home_error_message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
            ?: run {
                val isLandscape =
                    configuration.orientation ==
                        android.content.res.Configuration.ORIENTATION_LANDSCAPE

                Box(modifier = Modifier.fillMaxSize()) {
                    val density = LocalDensity.current
                    val statusBarHeight = WindowInsets.statusBars.getTop(density)
                    val showCarousel = !uiState.isOffline && uiState.heroCarouselItems.isNotEmpty()

                    val continueWatchingItems =
                        if (uiState.isOffline) {
                            uiState.offlineContinueWatching
                        } else {
                            uiState.continueWatching
                        }

                    val firstContentKey =
                        when {
                            !uiState.isOffline && uiState.libraries.isNotEmpty() ->
                                "libraries_section"
                            continueWatchingItems.isNotEmpty() -> "continue_watching"
                            !uiState.isOffline &&
                                uiState.isLoading &&
                                uiState.latestMedia.isNotEmpty() -> "cw_skeleton"
                            uiState.isOffline && uiState.downloadedMovies.isNotEmpty() -> "downloaded_movies"
                            uiState.isOffline && uiState.downloadedShows.isNotEmpty() -> "downloaded_shows"
                            uiState.isOffline && uiState.downloadedAudiobooks.isNotEmpty() -> "downloaded_audiobooks"
                            uiState.isOffline && uiState.downloadedPodcastEpisodes.isNotEmpty() -> "downloaded_podcasts"
                            !uiState.isOffline && uiState.nextUp.isNotEmpty() -> "next_up"
                            else -> null
                        }

                    fun getItemModifier(key: String): Modifier {
                        return if (isLandscape && key == firstContentKey) {
                            Modifier.fillMaxWidth().verticalLayoutOffset((-70).dp)
                        } else {
                            Modifier.fillMaxWidth()
                        }
                    }

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding =
                            PaddingValues(
                                top =
                                    if (!showCarousel)
                                        with(density) { statusBarHeight.toDp() + 56.dp }
                                    else 0.dp,
                                bottom = 16.dp,
                            ),
                    ) {
                        if (showCarousel) {
                            item(key = "hero_carousel") {
                                HeroCarousel(
                                    items = uiState.heroCarouselItems,
                                    height = screenHeight * 0.65f,
                                    isScrolling = isScrolling,
                                    onWatchNowClick = onPlayClick,
                                    onPlayTrailerClick = { item ->
                                        viewModel.onPlayTrailerClick(context, item)
                                    },
                                    onMoreInformationClick = onItemClick,
                                )
                            }
                        }

                        if (!uiState.isOffline && uiState.libraries.isNotEmpty()) {
                            item(key = "libraries_section") {
                                Box(modifier = getItemModifier("libraries_section")) {
                                    Column {
                                        Spacer(modifier = Modifier.height(24.dp))
                                        LibrariesSection(
                                            libraries = uiState.libraries,
                                            onLibraryClick = { library ->
                                                val route =
                                                    Destination.createLibraryContentRoute(
                                                        libraryId = library.id.toString(),
                                                        libraryName = library.name,
                                                    )
                                                navController.navigate(route)
                                            },
                                            widthSizeClass = widthSizeClass,
                                        )
                                    }
                                }
                            }
                        }

                        if (continueWatchingItems.isNotEmpty()) {
                            item(key = "continue_watching") {
                                Box(modifier = getItemModifier("continue_watching")) {
                                    Column {
                                        Spacer(modifier = Modifier.height(24.dp))
                                        OptimizedContinueWatchingSection(
                                            items = continueWatchingItems,
                                            onItemClick = { item ->
                                                if (item is AfinityEpisode) {
                                                    viewModel.selectEpisode(item)
                                                } else {
                                                    onItemClick(item)
                                                }
                                            },
                                            widthSizeClass = widthSizeClass,
                                        )
                                    }
                                }
                            }
                        } else if (
                            !uiState.isOffline &&
                                uiState.isLoading &&
                                uiState.latestMedia.isNotEmpty()
                        ) {
                            item(key = "cw_skeleton") {
                                Box(modifier = getItemModifier("cw_skeleton")) {
                                    Column {
                                        Spacer(modifier = Modifier.height(24.dp))
                                        ContinueWatchingSkeleton(widthSizeClass)
                                    }
                                }
                            }
                        }

                        if (uiState.isOffline && uiState.downloadedMovies.isNotEmpty()) {
                            item(key = "downloaded_movies") {
                                Box(modifier = getItemModifier("downloaded_movies")) {
                                    Column {
                                        Spacer(modifier = Modifier.height(24.dp))
                                        OptimizedLatestTvSeriesSection(
                                            title = stringResource(R.string.home_downloaded_movies),
                                            items = uiState.downloadedMovies,
                                            onItemClick = onItemClick,
                                            widthSizeClass = widthSizeClass,
                                        )
                                    }
                                }
                            }
                        }

                        if (uiState.isOffline && uiState.downloadedShows.isNotEmpty()) {
                            item(key = "downloaded_shows") {
                                Box(modifier = getItemModifier("downloaded_shows")) {
                                    Column {
                                        Spacer(modifier = Modifier.height(24.dp))
                                        OptimizedLatestTvSeriesSection(
                                            title = stringResource(R.string.home_downloaded_shows),
                                            items = uiState.downloadedShows,
                                            onItemClick = onItemClick,
                                            widthSizeClass = widthSizeClass,
                                        )
                                    }
                                }
                            }
                        }

                        if (uiState.isOffline && uiState.downloadedAudiobooks.isNotEmpty()) {
                            item(key = "downloaded_audiobooks") {
                                Box(modifier = getItemModifier("downloaded_audiobooks")) {
                                    Column {
                                        Spacer(modifier = Modifier.height(24.dp))
                                        DownloadedAudiobooksSection(
                                            title = "Downloaded Audiobooks",
                                            items = uiState.downloadedAudiobooks,
                                            onItemClick = { onAbsItemClick(it.libraryItemId) },
                                        )
                                    }
                                }
                            }
                        }

                        if (uiState.isOffline && uiState.downloadedPodcastEpisodes.isNotEmpty()) {
                            item(key = "downloaded_podcasts") {
                                Box(modifier = getItemModifier("downloaded_podcasts")) {
                                    Column {
                                        Spacer(modifier = Modifier.height(24.dp))
                                        DownloadedAudiobooksSection(
                                            title = "Downloaded Episodes",
                                            items = uiState.downloadedPodcastEpisodes,
                                            onItemClick = { onAbsItemClick(it.libraryItemId) },
                                        )
                                    }
                                }
                            }
                        }

                        if (!uiState.isOffline && uiState.nextUp.isNotEmpty()) {
                            item(key = "next_up") {
                                Box(modifier = getItemModifier("next_up")) {
                                    Column {
                                        Spacer(modifier = Modifier.height(24.dp))
                                        NextUpSection(
                                            episodes = uiState.nextUp,
                                            onEpisodeClick = { episode ->
                                                viewModel.selectEpisode(episode)
                                            },
                                            widthSizeClass = widthSizeClass,
                                        )
                                    }
                                }
                            }
                        }

                        if (!uiState.isOffline) {
                            if (uiState.combineLibrarySections) {
                                if (uiState.latestMovies.isNotEmpty()) {
                                    item(key = "latest_movies_combined") {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Spacer(modifier = Modifier.height(24.dp))
                                            OptimizedLatestMoviesSection(
                                                items = uiState.latestMovies,
                                                onItemClick = onItemClick,
                                                widthSizeClass = widthSizeClass,
                                            )
                                        }
                                    }
                                } else if (uiState.isLoading && uiState.latestMedia.isNotEmpty()) {
                                    item(key = "movies_skeleton") {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Spacer(modifier = Modifier.height(24.dp))
                                            MoviesSectionSkeleton(widthSizeClass)
                                        }
                                    }
                                }
                            } else {
                                items(
                                    items = uiState.separateMovieLibrarySections,
                                    key = { (library, _) -> "movie_lib_${library.id}" },
                                ) { (library, movies) ->
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Spacer(modifier = Modifier.height(24.dp))
                                        OptimizedLatestMoviesSection(
                                            title = library.name,
                                            items = movies,
                                            onItemClick = onItemClick,
                                            widthSizeClass = widthSizeClass,
                                        )
                                    }
                                }
                            }
                        }

                        if (!uiState.isOffline) {
                            if (uiState.combineLibrarySections) {
                                if (uiState.latestTvSeries.isNotEmpty()) {
                                    item(key = "latest_tv_combined") {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Spacer(modifier = Modifier.height(24.dp))
                                            OptimizedLatestTvSeriesSection(
                                                items = uiState.latestTvSeries,
                                                onItemClick = onItemClick,
                                                widthSizeClass = widthSizeClass,
                                            )
                                        }
                                    }
                                } else if (uiState.isLoading && uiState.latestMedia.isNotEmpty()) {
                                    item(key = "tv_skeleton") {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Spacer(modifier = Modifier.height(24.dp))
                                            TvSeriesSectionSkeleton(widthSizeClass)
                                        }
                                    }
                                }
                            } else {
                                items(
                                    items = uiState.separateTvLibrarySections,
                                    key = { (library, _) -> "tv_lib_${library.id}" },
                                ) { (library, shows) ->
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Spacer(modifier = Modifier.height(24.dp))
                                        OptimizedLatestTvSeriesSection(
                                            title = library.name,
                                            items = shows,
                                            onItemClick = onItemClick,
                                            widthSizeClass = widthSizeClass,
                                        )
                                    }
                                }
                            }
                        }

                        if (!uiState.isOffline && uiState.highestRated.isNotEmpty()) {
                            item(key = "highest_rated") {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Spacer(modifier = Modifier.height(24.dp))
                                    HighestRatedSection(
                                        items = uiState.highestRated,
                                        onItemClick = onItemClick,
                                        widthSizeClass = widthSizeClass,
                                    )
                                }
                            }
                        }

                        if (!uiState.isOffline && uiState.studios.isNotEmpty()) {
                            item(key = "popular_studios") {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Spacer(modifier = Modifier.height(24.dp))
                                    PopularStudiosSection(
                                        studios = uiState.studios,
                                        onStudioClick = { studio ->
                                            viewModel.onStudioClick(studio, navController)
                                        },
                                        widthSizeClass = widthSizeClass,
                                    )
                                }
                            }
                        }

                        if (!uiState.isOffline && uiState.combinedSections.isNotEmpty()) {
                            items(
                                items = uiState.combinedSections,
                                key = { section ->
                                    when (section) {
                                        is HomeSection.Person ->
                                            "person_${section.section.hashCode()}"
                                        is HomeSection.Movie ->
                                            "movie_rec_${section.section.hashCode()}"
                                        is HomeSection.PersonFromMovie ->
                                            "person_movie_${section.section.hashCode()}"
                                        is HomeSection.Genre ->
                                            "genre_${section.genreItem.name}_${section.genreItem.type}"
                                        is HomeSection.Spotlight ->
                                            "spotlight_${section.title}"
                                    }
                                },
                            ) { section ->
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Spacer(modifier = Modifier.height(24.dp))
                                    when (section) {
                                        is HomeSection.Person -> {
                                            PersonSection(
                                                section = section.section,
                                                onItemClick = { movie -> onItemClick(movie) },
                                                widthSizeClass = widthSizeClass,
                                            )
                                        }

                                        is HomeSection.Movie -> {
                                            MovieRecommendationSection(
                                                section = section.section,
                                                onItemClick = { movie -> onItemClick(movie) },
                                                widthSizeClass = widthSizeClass,
                                            )
                                        }

                                        is HomeSection.PersonFromMovie -> {
                                            PersonFromMovieSection(
                                                section = section.section,
                                                onItemClick = { movie -> onItemClick(movie) },
                                                widthSizeClass = widthSizeClass,
                                            )
                                        }

                                        is HomeSection.Spotlight -> {
                                            SpotlightCarousel(
                                                title = section.title,
                                                items = section.items,
                                                onItemClick = onItemClick,
                                                onPlayClick = onPlayClick,
                                            )
                                        }

                                        is HomeSection.Genre -> {
                                            when (section.genreItem.type) {
                                                GenreType.MOVIE -> {
                                                    GenreSection(
                                                        genre = section.genreItem.name,
                                                        movies =
                                                            uiState.genreMovies[
                                                                    section.genreItem.name]
                                                                ?: emptyList(),
                                                        isLoading =
                                                            uiState.genreLoadingStates[
                                                                    section.genreItem.name]
                                                                ?: false,
                                                        onVisible = {
                                                            if (
                                                                uiState.genreMovies[
                                                                        section.genreItem.name] ==
                                                                    null
                                                            ) {
                                                                viewModel.loadMoviesForGenre(
                                                                    section.genreItem.name
                                                                )
                                                            }
                                                        },
                                                        onItemClick = onItemClick,
                                                        widthSizeClass = widthSizeClass,
                                                    )
                                                }

                                                GenreType.SHOW -> {
                                                    ShowGenreSection(
                                                        genre = section.genreItem.name,
                                                        shows =
                                                            uiState.genreShows[
                                                                    section.genreItem.name]
                                                                ?: emptyList(),
                                                        isLoading =
                                                            uiState.genreLoadingStates[
                                                                    section.genreItem.name]
                                                                ?: false,
                                                        onVisible = {
                                                            if (
                                                                uiState.genreShows[
                                                                        section.genreItem.name] ==
                                                                    null
                                                            ) {
                                                                viewModel.loadShowsForGenre(
                                                                    section.genreItem.name
                                                                )
                                                            }
                                                        },
                                                        onItemClick = onItemClick,
                                                        widthSizeClass = widthSizeClass,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item(key = "bottom_padding") { Spacer(modifier = Modifier.height(32.dp)) }
                    }
                }
            }

        AfinityTopAppBar(
            title = {
                IconButton(
                    onClick = { /* TODO: Handle app icon click if needed */ },
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(
                        modifier =
                            Modifier.fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                                .clip(CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(id = ic_launcher_monochrome),
                            contentDescription = stringResource(R.string.cd_app_logo),
                            modifier = Modifier.size(60.dp).fillMaxSize(),
                            colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            },
            onSearchClick = {
                val route = Destination.createSearchRoute()
                navController.navigate(route)
            },
            onProfileClick = onProfileClick,
            userProfileImageUrl = mainUiState.userProfileImageUrl,
            backgroundOpacity = topBarOpacity,
        )
        val selectedEpisode by viewModel.selectedEpisode.collectAsStateWithLifecycle()
        val isLoadingEpisode by viewModel.isLoadingEpisode.collectAsStateWithLifecycle()
        val selectedEpisodeWatchlistStatus by
            viewModel.selectedEpisodeWatchlistStatus.collectAsStateWithLifecycle()
        val selectedEpisodeDownloadInfo by
            viewModel.selectedEpisodeDownloadInfo.collectAsStateWithLifecycle()
        val canDownload by viewModel.canDownload.collectAsStateWithLifecycle()

        var pendingNavigationSeriesId by remember { mutableStateOf<String?>(null) }

        var episodeForOverlay by remember { mutableStateOf<AfinityEpisode?>(null) }
        if (selectedEpisode != null) {
            episodeForOverlay = selectedEpisode
        }

        AnimatedVisibility(
            visible = selectedEpisode != null,
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            episodeForOverlay?.let { episode ->
                EpisodeDetailOverlay(
                    episode = episode,
                    isInWatchlist = selectedEpisodeWatchlistStatus,
                    downloadInfo = selectedEpisodeDownloadInfo,
                    canDownload = canDownload,
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
                            startPositionMs = selection.startPositionMs,
                        )
                    },
                    onToggleFavorite = { viewModel.toggleEpisodeFavorite(episode) },
                    onToggleWatchlist = { viewModel.toggleEpisodeWatchlist(episode) },
                    onToggleWatched = { viewModel.toggleEpisodeWatched(episode) },
                    onDownloadClick = { viewModel.onDownloadClick() },
                    onPauseDownload = { viewModel.pauseDownload() },
                    onResumeDownload = { viewModel.resumeDownload() },
                    onCancelDownload = { viewModel.cancelDownload() },
                    onGoToSeries = {
                        viewModel.clearSelectedEpisode()
                        pendingNavigationSeriesId = episode.seriesId.toString()
                    },
                )
            }
        }

        LaunchedEffect(selectedEpisode, pendingNavigationSeriesId) {
            if (selectedEpisode == null && pendingNavigationSeriesId != null) {
                delay(300)
                val route =
                    Destination.createItemDetailRoute(
                        itemId = pendingNavigationSeriesId!!,
                        itemType = "Series",
                    )
                navController.navigate(route)
                pendingNavigationSeriesId = null
            }
        }

        if (uiState.showQualityDialog) {
            val currentEpisode = selectedEpisode
            val remoteSources =
                currentEpisode?.sources?.filter {
                    it.type == com.makd.afinity.data.models.media.AfinitySourceType.REMOTE
                } ?: emptyList()

            if (remoteSources.isNotEmpty()) {
                QualitySelectionDialog(
                    sources = remoteSources,
                    onSourceSelected = { source -> viewModel.onQualitySelected(source.id) },
                    onDismiss = { viewModel.dismissQualityDialog() },
                )
            }
        }
    }
}
