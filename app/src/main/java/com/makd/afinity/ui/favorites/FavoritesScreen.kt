package com.makd.afinity.ui.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.makd.afinity.data.models.extensions.primaryBlurHash
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.media.AfinityBoxSet
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityPersonDetail
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.navigation.Destination
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.ContinueWatchingCard
import com.makd.afinity.ui.components.MediaItemCard
import com.makd.afinity.ui.components.OptimizedAsyncImage
import com.makd.afinity.ui.item.components.EpisodeDetailOverlay
import com.makd.afinity.ui.main.MainUiState
import com.makd.afinity.ui.theme.CardDimensions.landscapeWidth
import com.makd.afinity.ui.theme.CardDimensions.portraitWidth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    modifier: Modifier = Modifier,
    mainUiState: MainUiState,
    onItemClick: (AfinityItem) -> Unit = {},
    onPersonClick: (String) -> Unit = {},
    navController: NavController,
    viewModel: FavoritesViewModel = hiltViewModel(),
    widthSizeClass: WindowWidthSizeClass
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedEpisode by viewModel.selectedEpisode.collectAsStateWithLifecycle()
    val isLoadingEpisode by viewModel.isLoadingEpisode.collectAsStateWithLifecycle()
    val selectedEpisodeWatchlistStatus by viewModel.selectedEpisodeWatchlistStatus.collectAsStateWithLifecycle()
    val selectedEpisodeDownloadInfo by viewModel.selectedEpisodeDownloadInfo.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadFavorites()
    }

    val portraitWidth = widthSizeClass.portraitWidth
    val landscapeWidth = widthSizeClass.landscapeWidth

    Scaffold(
        topBar = {
            AfinityTopAppBar(
                title = {
                    Text(
                        text = "Favorites",
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
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                uiState.error != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Something went wrong",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = uiState.error ?: "Unknown error",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {
                    val hasAnyFavorites = uiState.movies.isNotEmpty() ||
                            uiState.shows.isNotEmpty() ||
                            uiState.seasons.isNotEmpty() ||
                            uiState.episodes.isNotEmpty() ||
                            uiState.boxSets.isNotEmpty() ||
                            uiState.people.isNotEmpty()

                    if (!hasAnyFavorites) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "No Favorites Yet",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Items you mark as favorites will appear here",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            if (uiState.movies.isNotEmpty()) {
                                item {
                                    FavoriteSection(
                                        title = "Movies (${uiState.movies.size})"
                                    ) {
                                        FavoriteMoviesRow(
                                            movies = uiState.movies,
                                            onItemClick = onItemClick,
                                            cardWidth = portraitWidth
                                        )
                                    }
                                }
                            }

                            if (uiState.boxSets.isNotEmpty()) {
                                item {
                                    FavoriteSection(
                                        title = "Box Sets (${uiState.boxSets.size})"
                                    ) {
                                        FavoriteBoxSetsRow(
                                            boxSets = uiState.boxSets,
                                            onItemClick = onItemClick,
                                            cardWidth = portraitWidth
                                        )
                                    }
                                }
                            }

                            if (uiState.shows.isNotEmpty()) {
                                item {
                                    FavoriteSection(
                                        title = "TV Shows (${uiState.shows.size})"
                                    ) {
                                        FavoriteShowsRow(
                                            shows = uiState.shows,
                                            onItemClick = onItemClick,
                                            cardWidth = portraitWidth
                                        )
                                    }
                                }
                            }

                            if (uiState.seasons.isNotEmpty()) {
                                item {
                                    FavoriteSection(
                                        title = "Seasons (${uiState.seasons.size})"
                                    ) {
                                        FavoriteSeasonsRow(
                                            seasons = uiState.seasons,
                                            onItemClick = onItemClick,
                                            cardWidth = portraitWidth
                                        )
                                    }
                                }
                            }

                            if (uiState.episodes.isNotEmpty()) {
                                item {
                                    FavoriteSection(
                                        title = "Episodes (${uiState.episodes.size})"
                                    ) {
                                        FavoriteEpisodesRow(
                                            episodes = uiState.episodes,
                                            onEpisodeClick = { episode ->
                                                viewModel.selectEpisode(episode)
                                            },
                                            cardWidth = landscapeWidth
                                        )
                                    }
                                }
                            }

                            if (uiState.people.isNotEmpty()) {
                                item {
                                    FavoriteSection(
                                        title = "People (${uiState.people.size})"
                                    ) {
                                        FavoritePeopleRow(
                                            people = uiState.people,
                                            onPersonClick = onPersonClick
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedEpisode?.let { episode ->
        EpisodeDetailOverlay(
            episode = episode,
            isLoading = isLoadingEpisode,
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
private fun FavoriteSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
        content()
    }
}

@Composable
private fun FavoriteMoviesRow(
    movies: List<AfinityMovie>,
    onItemClick: (AfinityItem) -> Unit,
    cardWidth: Dp
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 0.dp)
    ) {
        items(movies) { movie ->
            MediaItemCard(
                item = movie,
                onClick = { onItemClick(movie) },
                cardWidth = cardWidth
            )
        }
    }
}

@Composable
private fun FavoriteShowsRow(
    shows: List<AfinityShow>,
    onItemClick: (AfinityItem) -> Unit,
    cardWidth: Dp
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 0.dp)
    ) {
        items(shows) { show ->
            MediaItemCard(
                item = show,
                onClick = { onItemClick(show) },
                cardWidth = cardWidth
            )
        }
    }
}

@Composable
private fun FavoriteBoxSetsRow(
    boxSets: List<AfinityBoxSet>,
    onItemClick: (AfinityItem) -> Unit,
    cardWidth: Dp
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 0.dp)
    ) {
        items(boxSets) { boxSet ->
            MediaItemCard(
                item = boxSet,
                onClick = { onItemClick(boxSet) },
                cardWidth = cardWidth
            )
        }
    }
}

@Composable
private fun FavoriteSeasonsRow(
    seasons: List<AfinitySeason>,
    onItemClick: (AfinityItem) -> Unit,
    cardWidth: Dp
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 0.dp)
    ) {
        items(seasons) { season ->
            MediaItemCard(
                item = season,
                onClick = { onItemClick(season) },
                cardWidth = cardWidth
            )
        }
    }
}

@Composable
private fun FavoriteEpisodesRow(
    episodes: List<AfinityEpisode>,
    onEpisodeClick: (AfinityEpisode) -> Unit,
    cardWidth: Dp
) {
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

@Composable
private fun FavoritePeopleRow(
    people: List<AfinityPersonDetail>,
    onPersonClick: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 0.dp)
    ) {
        items(people) { person ->
            FavoritePersonCard(
                person = person,
                onClick = { onPersonClick(person.id.toString()) }
            )
        }
    }
}

@Composable
private fun FavoritePersonCard(
    person: AfinityPersonDetail,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(80.dp)
            .clip(RoundedCornerShape(8.dp))
    ) {
        Card(
            onClick = onClick,
            shape = CircleShape,
            modifier = Modifier.size(64.dp)
        ) {
            OptimizedAsyncImage(
                imageUrl = person.images.primaryImageUrl,
                blurHash = person.images.primaryBlurHash,
                contentDescription = person.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = person.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}