@file:UnstableApi

package com.makd.afinity.ui.watchlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.makd.afinity.R
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.navigation.Destination
import com.makd.afinity.navigation.LocalPlayerOffset
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.EpisodeOverlayHandler
import com.makd.afinity.ui.components.FullScreenEmpty
import com.makd.afinity.ui.components.FullScreenError
import com.makd.afinity.ui.components.FullScreenLoading
import com.makd.afinity.ui.components.MediaRowSection
import com.makd.afinity.ui.main.MainUiState
import com.makd.afinity.ui.theme.CardDimensions.landscapeWidth
import com.makd.afinity.ui.theme.CardDimensions.portraitWidth

@Composable
fun WatchlistScreen(
    modifier: Modifier = Modifier,
    mainUiState: MainUiState,
    onItemClick: (AfinityItem) -> Unit = {},
    navController: NavController,
    viewModel: WatchlistViewModel = hiltViewModel(),
    widthSizeClass: WindowWidthSizeClass,
    onMenuClick: (() -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedEpisode by viewModel.selectedEpisode.collectAsStateWithLifecycle()
    val selectedEpisodeWatchlistStatus by
        viewModel.selectedEpisodeWatchlistStatus.collectAsStateWithLifecycle()
    val selectedEpisodeDownloadInfo by
        viewModel.selectedEpisodeDownloadInfo.collectAsStateWithLifecycle()
    val canDownload by viewModel.canDownload.collectAsStateWithLifecycle()
    val playerOffset = LocalPlayerOffset.current
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onScreenResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) { viewModel.loadWatchlist() }

    val portraitWidth = widthSizeClass.portraitWidth
    val landscapeWidth = widthSizeClass.landscapeWidth

    Scaffold(
        topBar = {
            AfinityTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.watchlist_title),
                        style =
                            MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
                onMenuClick = onMenuClick,
                onSearchClick = { navController.navigate(Destination.createSearchRoute()) },
                onProfileClick = { navController.navigate(Destination.createSettingsRoute()) },
                userProfileImageUrl = mainUiState.userProfileImageUrl,
                userName = mainUiState.userName,
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        val customPadding =
            PaddingValues(
                top = innerPadding.calculateTopPadding(),
                start = innerPadding.calculateStartPadding(layoutDirection),
                end = innerPadding.calculateEndPadding(layoutDirection),
                bottom = max(innerPadding.calculateBottomPadding(), playerOffset),
            )
        when {
            uiState.isLoading -> FullScreenLoading(modifier = Modifier.padding(customPadding))

            uiState.error != null ->
                FullScreenError(message = uiState.error, modifier = Modifier.padding(customPadding))

            uiState.boxSets.isEmpty() &&
                uiState.movies.isEmpty() &&
                uiState.shows.isEmpty() &&
                uiState.seasons.isEmpty() &&
                uiState.episodes.isEmpty() -> {
                FullScreenEmpty(
                    title = stringResource(R.string.watchlist_empty_title),
                    message = stringResource(R.string.watchlists_empty_message),
                    actionText = "Browse Media",
                    onActionClick = {
                        navController.navigate(Destination.HOME.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(customPadding),
                    contentPadding = PaddingValues(all = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    if (uiState.boxSets.isNotEmpty()) {
                        item {
                            MediaRowSection(
                                title = stringResource(R.string.section_boxset),
                                items = uiState.boxSets,
                                onItemClick = onItemClick,
                                cardWidth = portraitWidth,
                            )
                        }
                    }

                    if (uiState.movies.isNotEmpty()) {
                        item {
                            MediaRowSection(
                                title = stringResource(R.string.section_movies),
                                items = uiState.movies,
                                onItemClick = onItemClick,
                                cardWidth = portraitWidth,
                            )
                        }
                    }

                    if (uiState.shows.isNotEmpty()) {
                        item {
                            MediaRowSection(
                                title = stringResource(R.string.section_tv_shows),
                                items = uiState.shows,
                                onItemClick = onItemClick,
                                cardWidth = portraitWidth,
                            )
                        }
                    }

                    if (uiState.seasons.isNotEmpty()) {
                        item {
                            MediaRowSection(
                                title = stringResource(R.string.section_seasons),
                                items = uiState.seasons,
                                onItemClick = onItemClick,
                                cardWidth = portraitWidth,
                            )
                        }
                    }

                    if (uiState.episodes.isNotEmpty()) {
                        item {
                            MediaRowSection(
                                title = stringResource(R.string.section_episodes),
                                items = uiState.episodes,
                                onItemClick = { episode ->
                                    viewModel.selectEpisode(
                                        episode as com.makd.afinity.data.models.media.AfinityEpisode
                                    )
                                },
                                cardWidth = landscapeWidth,
                            )
                        }
                    }
                }
            }
        }
    }

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
