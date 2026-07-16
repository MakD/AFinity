@file:UnstableApi

package com.makd.afinity.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.makd.afinity.R
import com.makd.afinity.R.drawable.ic_launcher_monochrome
import com.makd.afinity.data.models.GenreType
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.navigation.Destination
import com.makd.afinity.navigation.LocalPlayerOffset
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.EpisodeOverlayHandler
import com.makd.afinity.ui.components.FullScreenError
import com.makd.afinity.ui.components.HeroCarousel
import com.makd.afinity.ui.home.components.ContinueWatchingSkeleton
import com.makd.afinity.ui.home.components.DownloadedAudiobooksSection
import com.makd.afinity.ui.home.components.DownloadedMusicAlbumsSection
import com.makd.afinity.ui.home.components.DownloadedMusicTracksSection
import com.makd.afinity.ui.home.components.GenreSection
import com.makd.afinity.ui.home.components.HighestRatedSection
import com.makd.afinity.ui.home.components.LibrariesSection
import com.makd.afinity.ui.home.components.MovieRecommendationSection
import com.makd.afinity.ui.home.components.MoviesSectionSkeleton
import com.makd.afinity.ui.home.components.NextUpSection
import com.makd.afinity.ui.home.components.OptimizedContinueWatchingSection
import com.makd.afinity.ui.home.components.OptimizedLatestMoviesSection
import com.makd.afinity.ui.home.components.OptimizedLatestTvSeriesSection
import com.makd.afinity.ui.home.components.PendingSection
import com.makd.afinity.ui.home.components.PersonFromMovieSection
import com.makd.afinity.ui.home.components.PersonSection
import com.makd.afinity.ui.home.components.PopularStudiosSection
import com.makd.afinity.ui.home.components.ShowGenreSection
import com.makd.afinity.ui.home.components.SpotlightCarousel
import com.makd.afinity.ui.home.components.TvSeriesSectionSkeleton
import com.makd.afinity.ui.home.components.UpcomingEpisodesSection
import com.makd.afinity.ui.main.MainUiState
import com.makd.afinity.ui.music.library.startMusicService
import com.makd.afinity.ui.music.player.MusicPlayerViewModel
import com.makd.afinity.ui.utils.rememberTopBarOpacity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    mainUiState: MainUiState,
    onItemClick: (AfinityItem) -> Unit,
    onPlayClick: (AfinityItem) -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
    navController: NavController,
    snackbarHostState: SnackbarHostState,
    viewModel: HomeViewModel = hiltViewModel(),
    playerViewModel: MusicPlayerViewModel = hiltViewModel(),
    widthSizeClass: WindowWidthSizeClass,
    onAbsItemClick: (String) -> Unit = {},
    onMenuClick: (() -> Unit)? = null,
    hideLibrariesSection: Boolean = false,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isFetchingRandom by viewModel.isFetchingRandomItem.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val playerOffset = LocalPlayerOffset.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val randomNoneMessage = stringResource(R.string.random_item_none)

    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val screenHeight =
        remember(windowInfo.containerSize, density) {
            with(density) { windowInfo.containerSize.height.toDp() }
        }
    val lazyListState = rememberLazyListState()
    val scrollToTopScope = rememberCoroutineScope()
    val showScrollToTop by remember {
        derivedStateOf { lazyListState.firstVisibleItemIndex > 3 }
    }
    val continueWatchingScrollState = rememberLazyListState()
    val continueWatchingItems =
        if (uiState.isOffline) uiState.offlineContinueWatching else uiState.continueWatching
    LaunchedEffect(continueWatchingItems.firstOrNull()?.id) {
        if (continueWatchingItems.isNotEmpty()) {
            if (
                continueWatchingScrollState.firstVisibleItemIndex == 0 &&
                    !continueWatchingScrollState.isScrollInProgress
            ) {
                continueWatchingScrollState.scrollToItem(0)
            }
        }
    }

    val topBarOpacity by rememberTopBarOpacity(lazyListState)

    DisposableEffect(Unit) { onDispose { viewModel.clearSelectedEpisode() } }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onScreenResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isScrolling by remember { derivedStateOf { lazyListState.isScrollInProgress } }

    Box(modifier = modifier.fillMaxSize()) {
        uiState.error?.let { error ->
            FullScreenError(
                message = error,
                modifier = Modifier.background(MaterialTheme.colorScheme.background),
            )
        }
            ?: run {
                Box(modifier = Modifier.fillMaxSize()) {
                    LocalDensity.current
                    val statusBarHeight =
                        WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
                    val bottomPadding =
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                    val showCarousel = !uiState.isOffline && uiState.heroCarouselItems.isNotEmpty()

                    val baseModifier =
                        Modifier.fillMaxWidth()
                            .windowInsetsPadding(
                                WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
                            )

                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding =
                            PaddingValues(
                                top = if (!showCarousel) statusBarHeight + 56.dp else 0.dp,
                                bottom = max(bottomPadding, playerOffset) + 16.dp,
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

                        if (
                            !uiState.isOffline &&
                                uiState.libraries.isNotEmpty() &&
                                !hideLibrariesSection
                        ) {
                            item(key = "libraries_section") {
                                Box(modifier = baseModifier.padding(top = 24.dp)) {
                                    LibrariesSection(
                                        libraries = uiState.libraries,
                                        onLibraryClick = { library ->
                                            val route =
                                                if (library.type == CollectionType.Music) {
                                                    Destination.createMusicLibraryRoute(
                                                        libraryId = library.id.toString(),
                                                        libraryName = library.name,
                                                    )
                                                } else {
                                                    Destination.createLibraryContentRoute(
                                                        libraryId = library.id.toString(),
                                                        libraryName = library.name,
                                                    )
                                                }
                                            navController.navigate(route)
                                        },
                                        widthSizeClass = widthSizeClass,
                                    )
                                }
                            }
                        }

                        if (continueWatchingItems.isNotEmpty()) {
                            item(key = "continue_watching") {
                                Box(modifier = baseModifier.padding(top = 24.dp)) {
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
                                        scrollState = continueWatchingScrollState,
                                    )
                                }
                            }
                        } else if (
                            !uiState.isOffline &&
                                uiState.isLoading &&
                                uiState.latestMedia.isNotEmpty()
                        ) {
                            item(key = "cw_skeleton") {
                                Box(modifier = baseModifier.padding(top = 24.dp)) {
                                    ContinueWatchingSkeleton(widthSizeClass)
                                }
                            }
                        }

                        if (uiState.isOffline && uiState.offlineNextUp.isNotEmpty()) {
                            item(key = "offline_next_up") {
                                Box(modifier = baseModifier.padding(top = 24.dp)) {
                                    NextUpSection(
                                        episodes = uiState.offlineNextUp,
                                        onEpisodeClick = { episode ->
                                            viewModel.selectEpisode(episode)
                                        },
                                        widthSizeClass = widthSizeClass,
                                    )
                                }
                            }
                        }

                        if (uiState.isOffline && uiState.downloadedMovies.isNotEmpty()) {
                            item(key = "downloaded_movies") {
                                Box(modifier = baseModifier.padding(top = 24.dp)) {
                                    OptimizedLatestTvSeriesSection(
                                        title = stringResource(R.string.home_downloaded_movies),
                                        items = uiState.downloadedMovies,
                                        onItemClick = onItemClick,
                                        widthSizeClass = widthSizeClass,
                                        unavailableItemIds = uiState.unavailableDownloadIds,
                                    )
                                }
                            }
                        }

                        if (uiState.isOffline && uiState.downloadedShows.isNotEmpty()) {
                            item(key = "downloaded_shows") {
                                Box(modifier = baseModifier.padding(top = 24.dp)) {
                                    OptimizedLatestTvSeriesSection(
                                        title = stringResource(R.string.home_downloaded_shows),
                                        items = uiState.downloadedShows,
                                        onItemClick = onItemClick,
                                        widthSizeClass = widthSizeClass,
                                        unavailableItemIds = uiState.unavailableDownloadIds,
                                    )
                                }
                            }
                        }

                        if (uiState.isOffline && uiState.downloadedAudiobooks.isNotEmpty()) {
                            item(key = "downloaded_audiobooks") {
                                Box(modifier = baseModifier.padding(top = 24.dp)) {
                                    DownloadedAudiobooksSection(
                                        title = stringResource(R.string.home_downloaded_audiobooks),
                                        items = uiState.downloadedAudiobooks,
                                        onItemClick = { onAbsItemClick(it.libraryItemId) },
                                    )
                                }
                            }
                        }

                        if (uiState.isOffline && uiState.downloadedPodcastEpisodes.isNotEmpty()) {
                            item(key = "downloaded_podcasts") {
                                Box(modifier = baseModifier.padding(top = 24.dp)) {
                                    DownloadedAudiobooksSection(
                                        title = stringResource(R.string.home_downloaded_episodes),
                                        items = uiState.downloadedPodcastEpisodes,
                                        onItemClick = { onAbsItemClick(it.libraryItemId) },
                                    )
                                }
                            }
                        }

                        if (uiState.isOffline && uiState.downloadedMusicAlbums.isNotEmpty()) {
                            item(key = "downloaded_music_albums") {
                                Box(modifier = baseModifier.padding(top = 24.dp)) {
                                    DownloadedMusicAlbumsSection(
                                        title = "Downloaded Albums",
                                        albums = uiState.downloadedMusicAlbums,
                                        onAlbumClick = { album ->
                                            navController.navigate(
                                                Destination.createMusicAlbumRoute(
                                                    album.id.toString()
                                                )
                                            )
                                        },
                                    )
                                }
                            }
                        }

                        if (uiState.isOffline && uiState.downloadedMusicTracks.isNotEmpty()) {
                            item(key = "downloaded_music_tracks") {
                                Box(modifier = baseModifier.padding(top = 24.dp)) {
                                    DownloadedMusicTracksSection(
                                        title = "Downloaded Tracks",
                                        tracks = uiState.downloadedMusicTracks,
                                        onTrackClick = { track ->
                                            val tracks = uiState.downloadedMusicTracks
                                            val index = tracks.indexOf(track).coerceAtLeast(0)
                                            startMusicService(context)
                                            playerViewModel.playQueue(tracks, index)
                                            navController.navigate(Destination.MUSIC_PLAYER_ROUTE)
                                        },
                                    )
                                }
                            }
                        }

                        if (!uiState.isOffline && uiState.nextUp.isNotEmpty()) {
                            item(key = "next_up") {
                                Box(modifier = baseModifier.padding(top = 24.dp)) {
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

                        if (!uiState.isOffline) {
                            if (uiState.combineLibrarySections) {
                                if (uiState.latestMovies.isNotEmpty()) {
                                    item(key = "latest_movies_combined") {
                                        Box(modifier = baseModifier.padding(top = 24.dp)) {
                                            OptimizedLatestMoviesSection(
                                                items = uiState.latestMovies,
                                                onItemClick = onItemClick,
                                                widthSizeClass = widthSizeClass,
                                                unavailableItemIds = uiState.unavailableDownloadIds,
                                            )
                                        }
                                    }
                                } else if (uiState.isLoading && uiState.latestMedia.isNotEmpty()) {
                                    item(key = "movies_skeleton") {
                                        Box(modifier = baseModifier.padding(top = 24.dp)) {
                                            MoviesSectionSkeleton(widthSizeClass)
                                        }
                                    }
                                }
                            } else {
                                items(
                                    items = uiState.separateMovieLibrarySections,
                                    key = { (library, _) -> "movie_lib_${library.id}" },
                                ) { (library, movies) ->
                                    Box(modifier = baseModifier.padding(top = 24.dp)) {
                                        OptimizedLatestMoviesSection(
                                            title = library.name,
                                            items = movies,
                                            onItemClick = onItemClick,
                                            widthSizeClass = widthSizeClass,
                                            unavailableItemIds = uiState.unavailableDownloadIds,
                                        )
                                    }
                                }
                            }
                        }

                        if (!uiState.isOffline) {
                            if (uiState.combineLibrarySections) {
                                if (uiState.latestTvSeries.isNotEmpty()) {
                                    item(key = "latest_tv_combined") {
                                        Box(modifier = baseModifier.padding(top = 24.dp)) {
                                            OptimizedLatestTvSeriesSection(
                                                items = uiState.latestTvSeries,
                                                onItemClick = onItemClick,
                                                widthSizeClass = widthSizeClass,
                                            )
                                        }
                                    }
                                } else if (uiState.isLoading && uiState.latestMedia.isNotEmpty()) {
                                    item(key = "tv_skeleton") {
                                        Box(modifier = baseModifier.padding(top = 24.dp)) {
                                            TvSeriesSectionSkeleton(widthSizeClass)
                                        }
                                    }
                                }
                            } else {
                                items(
                                    items = uiState.separateTvLibrarySections,
                                    key = { (library, _) -> "tv_lib_${library.id}" },
                                ) { (library, shows) ->
                                    Box(modifier = baseModifier.padding(top = 24.dp)) {
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

                        if (!uiState.isOffline && uiState.upcomingEpisodes.isNotEmpty()) {
                            item(key = "upcoming_episodes") {
                                Box(modifier = baseModifier.padding(top = 24.dp)) {
                                    UpcomingEpisodesSection(
                                        items = uiState.upcomingEpisodes,
                                        onItemClick = { episode ->
                                            viewModel.selectEpisode(episode)
                                        },
                                        widthSizeClass = widthSizeClass,
                                    )
                                }
                            }
                        }

                        if (!uiState.isOffline && uiState.highestRated.isNotEmpty()) {
                            item(key = "highest_rated") {
                                Box(modifier = baseModifier.padding(top = 24.dp)) {
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
                                Box(modifier = baseModifier.padding(top = 24.dp)) {
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
                                key = { section -> section.key },
                                contentType = { section -> section::class },
                            ) { section ->
                                Box(modifier = baseModifier.padding(top = 24.dp)) {
                                    when (section) {
                                        is HomeSection.Pending -> {
                                            PendingSection(
                                                title = section.title,
                                                isSpotlight = section.isSpotlight,
                                                onVisible = {
                                                    viewModel.hydrateSection(section.key)
                                                },
                                                widthSizeClass = widthSizeClass,
                                            )
                                        }

                                        is HomeSection.Person -> {
                                            PersonSection(
                                                section = section.section,
                                                onItemClick = onItemClick,
                                                widthSizeClass = widthSizeClass,
                                            )
                                        }

                                        is HomeSection.Movie -> {
                                            MovieRecommendationSection(
                                                section = section.section,
                                                onItemClick = onItemClick,
                                                widthSizeClass = widthSizeClass,
                                            )
                                        }

                                        is HomeSection.PersonFromMovie -> {
                                            PersonFromMovieSection(
                                                section = section.section,
                                                onItemClick = onItemClick,
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
                    }

                    AnimatedVisibility(
                        visible = showScrollToTop,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut(),
                        modifier =
                            Modifier.align(Alignment.BottomEnd)
                                .windowInsetsPadding(
                                    WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal)
                                )
                                .padding(
                                    end = 16.dp,
                                    bottom = max(bottomPadding, playerOffset) + 16.dp,
                                ),
                    ) {
                        FloatingActionButton(
                            onClick = {
                                scrollToTopScope.launch {
                                    if (lazyListState.firstVisibleItemIndex > 3) {
                                        lazyListState.scrollToItem(3)
                                    }
                                    lazyListState.animateScrollToItem(0)
                                }
                            },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_keyboard_arrow_up),
                                contentDescription = stringResource(R.string.cd_scroll_to_top),
                            )
                        }
                    }
                }
            }

        AfinityTopAppBar(
            title = {
                if (onMenuClick == null && widthSizeClass == WindowWidthSizeClass.Compact) {
                    Box(
                        modifier =
                            Modifier.size(42.dp)
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
            onMenuClick = onMenuClick,
            onSearchClick = {
                val route = Destination.createSearchRoute()
                navController.navigate(route)
            },
            onRandomClick = {
                scrollToTopScope.launch {
                    val item = viewModel.getRandomUnwatchedItem()
                    if (item != null) {
                        onItemClick(item)
                    } else {
                        snackbarHostState.showSnackbar(randomNoneMessage)
                    }
                }
            },
            onProfileClick = onProfileClick,
            userName = mainUiState.userName,
            userProfileImageUrl = mainUiState.userProfileImageUrl,
            backgroundOpacity = { topBarOpacity },
            isFetchingRandom = isFetchingRandom,
        )

        val selectedEpisode by viewModel.selectedEpisode.collectAsStateWithLifecycle()
        val selectedEpisodeWatchlistStatus by
            viewModel.selectedEpisodeWatchlistStatus.collectAsStateWithLifecycle()
        val selectedEpisodeDownloadInfo by
            viewModel.selectedEpisodeDownloadInfo.collectAsStateWithLifecycle()
        val canDownload by viewModel.canDownload.collectAsStateWithLifecycle()

        EpisodeOverlayHandler(
            selectedEpisode = selectedEpisode,
            watchlistStatus = selectedEpisodeWatchlistStatus,
            downloadInfo = selectedEpisodeDownloadInfo,
            canDownload = canDownload,
            onClearSelection = { viewModel.clearSelectedEpisode() },
            onToggleFavorite = { episode -> viewModel.toggleEpisodeFavorite(episode) },
            onToggleWatchlist = { episode -> viewModel.toggleEpisodeWatchlist(episode) },
            onToggleWatched = { episode -> viewModel.toggleEpisodeWatched(episode) },
            onNavigateToSeries = { seriesId ->
                navController.navigate(
                    Destination.createItemDetailRoute(itemId = seriesId, itemType = "Series")
                )
            },
        )
    }
}
