@file:UnstableApi

package com.makd.afinity.ui.watchlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.makd.afinity.R
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.navigation.Destination
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.FullScreenEmpty
import com.makd.afinity.ui.components.FullScreenError
import com.makd.afinity.ui.components.FullScreenLoading
import com.makd.afinity.ui.components.MediaRowSection
import com.makd.afinity.ui.item.components.EpisodeDetailOverlay
import com.makd.afinity.ui.main.MainUiState
import com.makd.afinity.ui.theme.CardDimensions.landscapeWidth
import com.makd.afinity.ui.theme.CardDimensions.portraitWidth
import kotlinx.coroutines.delay

@Composable
fun WatchlistScreen(
    mainUiState: MainUiState,
    onItemClick: (AfinityItem) -> Unit = {},
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: WatchlistViewModel = hiltViewModel(),
    widthSizeClass: WindowWidthSizeClass,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedEpisode by viewModel.selectedEpisode.collectAsStateWithLifecycle()
    val isLoadingEpisode by viewModel.isLoadingEpisode.collectAsStateWithLifecycle()
    val selectedEpisodeWatchlistStatus by
        viewModel.selectedEpisodeWatchlistStatus.collectAsStateWithLifecycle()
    val selectedEpisodeDownloadInfo by
        viewModel.selectedEpisodeDownloadInfo.collectAsStateWithLifecycle()
    var pendingNavigationSeriesId by remember { mutableStateOf<String?>(null) }

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
                onSearchClick = { navController.navigate(Destination.createSearchRoute()) },
                onProfileClick = { navController.navigate(Destination.createSettingsRoute()) },
                userProfileImageUrl = mainUiState.userProfileImageUrl,
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        when {
            uiState.isLoading -> FullScreenLoading(modifier = Modifier.padding(innerPadding))

            uiState.error != null ->
                FullScreenError(message = uiState.error, modifier = Modifier.padding(innerPadding))

            uiState.movies.isEmpty() &&
                uiState.shows.isEmpty() &&
                uiState.seasons.isEmpty() &&
                uiState.episodes.isEmpty() -> {
                FullScreenEmpty(
                    message = stringResource(R.string.watchlist_empty),
                    modifier = Modifier.padding(innerPadding),
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    item {
                        MediaRowSection(
                            title = stringResource(R.string.section_movies),
                            items = uiState.movies,
                            onItemClick = onItemClick,
                            cardWidth = portraitWidth,
                        )
                    }

                    item {
                        MediaRowSection(
                            title = stringResource(R.string.section_tv_shows),
                            items = uiState.shows,
                            onItemClick = onItemClick,
                            cardWidth = portraitWidth,
                        )
                    }

                    item {
                        MediaRowSection(
                            title = stringResource(R.string.section_seasons),
                            items = uiState.seasons,
                            onItemClick = onItemClick,
                            cardWidth = portraitWidth,
                        )
                    }

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

    selectedEpisode?.let { episode ->
        EpisodeDetailOverlay(
            episode = episode,
            isInWatchlist = selectedEpisodeWatchlistStatus,
            downloadInfo = selectedEpisodeDownloadInfo,
            onDismiss = { viewModel.clearSelectedEpisode() },
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
                pendingNavigationSeriesId = episode.seriesId?.toString()
            },
        )
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
}
