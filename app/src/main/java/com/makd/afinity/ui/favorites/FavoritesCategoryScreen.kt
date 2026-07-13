@file:UnstableApi

package com.makd.afinity.ui.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import com.makd.afinity.ui.music.components.MusicAlbumCard
import com.makd.afinity.ui.music.components.MusicArtistCard
import com.makd.afinity.ui.music.components.MusicPlaylistCard
import com.makd.afinity.ui.music.library.startMusicService
import com.makd.afinity.ui.music.player.MusicPlayerViewModel
import com.makd.afinity.ui.player.PlayerLauncher
import com.makd.afinity.ui.theme.CardDimensions.landscapeWidth
import com.makd.afinity.ui.theme.CardDimensions.portraitWidth

enum class FavoritesCategory {
    BOXSETS,
    MOVIES,
    SHOWS,
    SEASONS,
    EPISODES,
    CHANNELS,
    PEOPLE,
    ALBUMS,
    ARTISTS,
    TRACKS,
    PLAYLISTS,
}

@Composable
fun FavoritesCategoryScreen(
    category: FavoritesCategory,
    mainUiState: MainUiState,
    onItemClick: (AfinityItem) -> Unit,
    onPersonClick: (String) -> Unit,
    navController: NavController,
    widthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
    viewModel: FavoritesViewModel = hiltViewModel(),
    playerViewModel: MusicPlayerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playbackState by playerViewModel.playbackState.collectAsStateWithLifecycle()
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

    LaunchedEffect(Unit) { viewModel.loadFavorites() }

    val contentPadding =
        PaddingValues(start = 14.dp, top = 16.dp, end = 14.dp, bottom = 16.dp + playerOffset)

    val isEmpty =
        when (category) {
            FavoritesCategory.BOXSETS -> uiState.boxSets.isEmpty()
            FavoritesCategory.MOVIES -> uiState.movies.isEmpty()
            FavoritesCategory.SHOWS -> uiState.shows.isEmpty()
            FavoritesCategory.SEASONS -> uiState.seasons.isEmpty()
            FavoritesCategory.EPISODES -> uiState.episodes.isEmpty()
            FavoritesCategory.CHANNELS -> uiState.channels.isEmpty()
            FavoritesCategory.PEOPLE -> uiState.people.isEmpty()
            FavoritesCategory.ALBUMS -> uiState.favoriteAlbums.isEmpty()
            FavoritesCategory.ARTISTS -> uiState.favoriteArtists.isEmpty()
            FavoritesCategory.TRACKS -> uiState.favoriteTracks.isEmpty()
            FavoritesCategory.PLAYLISTS -> uiState.favoritePlaylists.isEmpty()
        }

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
        if (isEmpty) {
            FullScreenEmpty(
                title = stringResource(R.string.favorites_empty_title),
                message = stringResource(R.string.favorites_empty_message),
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            val gridModifier = Modifier.padding(innerPadding)
            when (category) {
                FavoritesCategory.BOXSETS,
                FavoritesCategory.MOVIES,
                FavoritesCategory.SHOWS,
                FavoritesCategory.SEASONS -> {
                    val items: List<AfinityItem> =
                        when (category) {
                            FavoritesCategory.BOXSETS -> uiState.boxSets
                            FavoritesCategory.MOVIES -> uiState.movies
                            FavoritesCategory.SHOWS -> uiState.shows
                            else -> uiState.seasons
                        }
                    MediaCategoryGrid(
                        items = items,
                        landscape = false,
                        widthSizeClass = widthSizeClass,
                        playerOffset = playerOffset,
                        onItemClick = onItemClick,
                        modifier = gridModifier,
                    )
                }

                FavoritesCategory.EPISODES ->
                    MediaCategoryGrid(
                        items = uiState.episodes,
                        landscape = true,
                        widthSizeClass = widthSizeClass,
                        playerOffset = playerOffset,
                        onItemClick = { item -> viewModel.selectEpisode(item as AfinityEpisode) },
                        modifier = gridModifier,
                    )

                FavoritesCategory.CHANNELS ->
                    FavoritesGrid(
                        cardWidth = widthSizeClass.landscapeWidth,
                        contentPadding = contentPadding,
                        modifier = gridModifier,
                        items = uiState.channels,
                        key = { it.id.toString() },
                    ) { channel ->
                        FavoriteChannelCard(
                            channel = channel,
                            onClick = {
                                PlayerLauncher.launchLiveChannel(
                                    context,
                                    channel.id,
                                    channel.name,
                                )
                            },
                            cardWidth = widthSizeClass.landscapeWidth,
                        )
                    }

                FavoritesCategory.PEOPLE ->
                    FavoritesGrid(
                        cardWidth = widthSizeClass.portraitWidth,
                        contentPadding = contentPadding,
                        modifier = gridModifier,
                        items = uiState.people,
                        key = { it.id.toString() },
                    ) { person ->
                        FavoritePersonCard(
                            person = person,
                            onClick = { onPersonClick(person.id.toString()) },
                            cardWidth = widthSizeClass.portraitWidth,
                        )
                    }

                FavoritesCategory.ALBUMS ->
                    FavoritesGrid(
                        cardWidth = widthSizeClass.portraitWidth,
                        contentPadding = contentPadding,
                        modifier = gridModifier,
                        items = uiState.favoriteAlbums,
                        key = { it.id },
                    ) { album ->
                        MusicAlbumCard(
                            album = album,
                            onClick = {
                                navController.navigate(
                                    Destination.createMusicAlbumRoute(album.id.toString())
                                )
                            },
                        )
                    }

                FavoritesCategory.ARTISTS ->
                    FavoritesGrid(
                        cardWidth = widthSizeClass.portraitWidth,
                        contentPadding = contentPadding,
                        modifier = gridModifier,
                        items = uiState.favoriteArtists,
                        key = { it.id },
                    ) { artist ->
                        MusicArtistCard(
                            name = artist.name,
                            imageUrl = artist.images.primary?.toString(),
                            blurHash = artist.images.primaryImageBlurHash,
                            onClick = {
                                navController.navigate(
                                    Destination.createMusicArtistRoute(artist.id.toString())
                                )
                            },
                            size = widthSizeClass.portraitWidth,
                        )
                    }

                FavoritesCategory.TRACKS ->
                    FavoritesGrid(
                        cardWidth = 140.dp,
                        contentPadding = contentPadding,
                        modifier = gridModifier,
                        items = uiState.favoriteTracks,
                        key = { it.id },
                    ) { track ->
                        FavoriteTrackCard(
                            track = track,
                            isPlaying = track.id == playbackState.currentTrack?.id,
                            onClick = {
                                startMusicService(context)
                                playerViewModel.playQueue(
                                    uiState.favoriteTracks,
                                    uiState.favoriteTracks.indexOf(track),
                                )
                            },
                            onInstantMix = {
                                startMusicService(context)
                                playerViewModel.playInstantMix(track.id)
                            },
                            onAddNext = { playerViewModel.addNext(listOf(track)) },
                            onAddLast = { playerViewModel.addLast(listOf(track)) },
                            onFavorite = { viewModel.toggleTrackFavorite(track) },
                        )
                    }

                FavoritesCategory.PLAYLISTS ->
                    FavoritesGrid(
                        cardWidth = widthSizeClass.portraitWidth,
                        contentPadding = contentPadding,
                        modifier = gridModifier,
                        items = uiState.favoritePlaylists,
                        key = { it.id },
                    ) { playlist ->
                        MusicPlaylistCard(
                            playlist = playlist,
                            onClick = {
                                navController.navigate(
                                    Destination.createMusicPlaylistRoute(playlist.id.toString())
                                )
                            },
                        )
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

@Composable
private fun <T> FavoritesGrid(
    cardWidth: Dp,
    contentPadding: PaddingValues,
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    itemContent: @Composable (T) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(cardWidth),
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(items = items, key = { key(it) }) { item -> itemContent(item) }
    }
}

private fun FavoritesCategory.titleRes(): Int =
    when (this) {
        FavoritesCategory.BOXSETS -> R.string.section_boxset
        FavoritesCategory.MOVIES -> R.string.section_movies
        FavoritesCategory.SHOWS -> R.string.section_tv_shows
        FavoritesCategory.SEASONS -> R.string.section_seasons
        FavoritesCategory.EPISODES -> R.string.section_episodes
        FavoritesCategory.CHANNELS -> R.string.section_tv_channels
        FavoritesCategory.PEOPLE -> R.string.section_people
        FavoritesCategory.ALBUMS -> R.string.section_favorite_albums
        FavoritesCategory.ARTISTS -> R.string.section_favorite_artists
        FavoritesCategory.TRACKS -> R.string.section_favorite_songs
        FavoritesCategory.PLAYLISTS -> R.string.section_favorite_playlists
    }