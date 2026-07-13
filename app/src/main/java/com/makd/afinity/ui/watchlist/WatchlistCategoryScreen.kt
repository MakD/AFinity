@file:UnstableApi

package com.makd.afinity.ui.watchlist

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.makd.afinity.R
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.navigation.Destination
import com.makd.afinity.navigation.LocalPlayerOffset
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.EpisodeOverlayHandler
import com.makd.afinity.ui.components.FullScreenEmpty
import com.makd.afinity.ui.components.MediaCategoryGrid
import com.makd.afinity.ui.main.MainUiState

enum class WatchlistCategory {
    BOXSETS,
    MOVIES,
    SHOWS,
    SEASONS,
    EPISODES,
}

@Composable
fun WatchlistCategoryScreen(
    category: WatchlistCategory,
    mainUiState: MainUiState,
    onItemClick: (AfinityItem) -> Unit,
    navController: NavController,
    widthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
    viewModel: WatchlistViewModel = hiltViewModel(),
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

    val items: List<AfinityItem> =
        when (category) {
            WatchlistCategory.BOXSETS -> uiState.boxSets
            WatchlistCategory.MOVIES -> uiState.movies
            WatchlistCategory.SHOWS -> uiState.shows
            WatchlistCategory.SEASONS -> uiState.seasons
            WatchlistCategory.EPISODES -> uiState.episodes
        }
    val isEpisodes = category == WatchlistCategory.EPISODES

    Scaffold(
        topBar = {
            AfinityTopAppBar(
                title = {
                    Text(
                        text = stringResource(category.titleRes()),
                        style =
                            MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
                onSearchClick = { navController.navigate(Destination.createSearchRoute()) },
                onProfileClick = { navController.navigate(Destination.createSettingsRoute()) },
                userProfileImageUrl = mainUiState.userProfileImageUrl,
                userName = mainUiState.userName,
            )
        },
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        if (items.isEmpty()) {
            FullScreenEmpty(
                title = stringResource(R.string.watchlist_empty_title),
                message = stringResource(R.string.watchlists_empty_message),
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            MediaCategoryGrid(
                items = items,
                landscape = isEpisodes,
                widthSizeClass = widthSizeClass,
                playerOffset = playerOffset,
                onItemClick = { item ->
                    if (isEpisodes) {
                        viewModel.selectEpisode(item as AfinityEpisode)
                    } else {
                        onItemClick(item)
                    }
                },
                modifier = Modifier.padding(innerPadding),
            )
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

private fun WatchlistCategory.titleRes(): Int =
    when (this) {
        WatchlistCategory.BOXSETS -> R.string.section_boxset
        WatchlistCategory.MOVIES -> R.string.section_movies
        WatchlistCategory.SHOWS -> R.string.section_tv_shows
        WatchlistCategory.SEASONS -> R.string.section_seasons
        WatchlistCategory.EPISODES -> R.string.section_episodes
    }