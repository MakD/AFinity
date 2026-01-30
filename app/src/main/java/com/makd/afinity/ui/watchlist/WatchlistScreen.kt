package com.makd.afinity.ui.watchlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.makd.afinity.R
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.navigation.Destination
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.ContinueWatchingCard
import com.makd.afinity.ui.components.MediaItemCard
import com.makd.afinity.ui.item.components.EpisodeDetailOverlay
import com.makd.afinity.ui.main.MainUiState
import com.makd.afinity.ui.theme.CardDimensions.landscapeWidth
import com.makd.afinity.ui.theme.CardDimensions.portraitWidth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    mainUiState: MainUiState,
    onItemClick: (AfinityItem) -> Unit = {},
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: WatchlistViewModel = hiltViewModel(),
    widthSizeClass: WindowWidthSizeClass
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedEpisode by viewModel.selectedEpisode.collectAsStateWithLifecycle()
    val isLoadingEpisode by viewModel.isLoadingEpisode.collectAsStateWithLifecycle()
    val selectedEpisodeWatchlistStatus by viewModel.selectedEpisodeWatchlistStatus.collectAsStateWithLifecycle()
    val selectedEpisodeDownloadInfo by viewModel.selectedEpisodeDownloadInfo.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadWatchlist()
    }

    val portraitWidth = widthSizeClass.portraitWidth
    val landscapeWidth = widthSizeClass.landscapeWidth

    Scaffold(
        topBar = {
            AfinityTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.watchlist_title),
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                onSearchClick = {
                    val route = Destination.createSearchRoute()
                    navController.navigate(route)
                },
                onProfileClick = {
                    val route = Destination.createSettingsRoute()
                    navController.navigate(route)
                },
                userProfileImageUrl = mainUiState.userProfileImageUrl
            )
        },
        modifier = modifier
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.error ?: stringResource(R.string.error_unknown),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            uiState.movies.isEmpty() && uiState.shows.isEmpty() && uiState.seasons.isEmpty() && uiState.episodes.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.watchlist_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    if (uiState.movies.isNotEmpty()) {
                        item {
                            WatchlistSection(
                                title = stringResource(R.string.section_movies),
                                items = uiState.movies,
                                onItemClick = onItemClick,
                                cardWidth = portraitWidth
                            )
                        }
                    }

                    if (uiState.shows.isNotEmpty()) {
                        item {
                            WatchlistSection(
                                title = stringResource(R.string.section_tv_shows),
                                items = uiState.shows,
                                onItemClick = onItemClick,
                                cardWidth = portraitWidth
                            )
                        }
                    }

                    if (uiState.seasons.isNotEmpty()) {
                        item {
                            WatchlistSection(
                                title = stringResource(R.string.section_seasons),
                                items = uiState.seasons,
                                onItemClick = onItemClick,
                                cardWidth = portraitWidth
                            )
                        }
                    }

                    if (uiState.episodes.isNotEmpty()) {
                        item {
                            WatchlistEpisodesSection(
                                title = stringResource(R.string.section_episodes),
                                episodes = uiState.episodes,
                                onEpisodeClick = { episode ->
                                    viewModel.selectEpisode(episode)
                                },
                                cardWidth = landscapeWidth
                            )
                        }
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
            onDismiss = {
                viewModel.clearSelectedEpisode()
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
            onDownloadClick = {
                viewModel.onDownloadClick()
            },
            onPauseDownload = {
                viewModel.pauseDownload()
            },
            onResumeDownload = {
                viewModel.resumeDownload()
            },
            onCancelDownload = {
                viewModel.cancelDownload()
            },
            onGoToSeries = {
                viewModel.clearSelectedEpisode()
                episode.seriesId?.let { seriesId ->
                    val route = Destination.createItemDetailRoute(seriesId.toString())
                    navController.navigate(route)
                }
            }
        )
    }
}

@Composable
private fun WatchlistSection(
    title: String,
    items: List<AfinityItem>,
    onItemClick: (AfinityItem) -> Unit,
    cardWidth: Dp
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.watchlist_section_header_fmt, title, items.size),
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            items(items) { item ->
                MediaItemCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    cardWidth = cardWidth
                )
            }
        }
    }
}

@Composable
private fun WatchlistEpisodesSection(
    title: String,
    episodes: List<AfinityEpisode>,
    onEpisodeClick: (AfinityEpisode) -> Unit,
    cardWidth: Dp
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.watchlist_section_header_fmt, title, episodes.size),
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            items(episodes) { episode ->
                ContinueWatchingCard(
                    item = episode,
                    onClick = { onEpisodeClick(episode) },
                    cardWidth = cardWidth
                )
            }
        }
    }
}