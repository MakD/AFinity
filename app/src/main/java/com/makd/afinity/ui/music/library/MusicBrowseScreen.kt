package com.makd.afinity.ui.music.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.paging.compose.collectAsLazyPagingItems
import com.makd.afinity.R
import com.makd.afinity.data.models.music.RadioSeed
import com.makd.afinity.navigation.Destination
import com.makd.afinity.navigation.Destination.Companion.createSearchRoute
import com.makd.afinity.navigation.Destination.Companion.createSettingsRoute
import com.makd.afinity.ui.components.AFinitySnackbar
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.music.components.AddToPlaylistDialog
import com.makd.afinity.ui.music.components.AddToPlaylistResult
import com.makd.afinity.ui.music.components.AddToPlaylistViewModel
import com.makd.afinity.ui.music.components.RadioModeBottomSheet
import com.makd.afinity.ui.music.player.MusicPlayerViewModel
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicBrowseScreen(
    tab: LibraryFilter,
    navController: NavController,
    viewModel: MusicLibraryViewModel = hiltViewModel(),
    playerViewModel: MusicPlayerViewModel = hiltViewModel(),
    addToPlaylistViewModel: AddToPlaylistViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val userProfileImageUrl by viewModel.userProfileImageUrl.collectAsStateWithLifecycle()
    val playbackState by playerViewModel.playbackState.collectAsStateWithLifecycle()
    val isOffline by playerViewModel.isOffline.collectAsStateWithLifecycle()
    val trackSortField by viewModel.trackSortField.collectAsStateWithLifecycle()
    val trackSortDescending by viewModel.trackSortDescending.collectAsStateWithLifecycle()
    val albumSortField by viewModel.albumSortField.collectAsStateWithLifecycle()
    val albumSortDescending by viewModel.albumSortDescending.collectAsStateWithLifecycle()
    val albumFilterOptions by viewModel.albumFilterOptions.collectAsStateWithLifecycle()
    val albumLetterFilter by viewModel.albumLetterFilter.collectAsStateWithLifecycle()
    val artistLetterFilter by viewModel.artistLetterFilter.collectAsStateWithLifecycle()
    val trackFilters by viewModel.trackFilters.collectAsStateWithLifecycle()
    val albumFilters by viewModel.albumFilters.collectAsStateWithLifecycle()
    val artistFilters by viewModel.artistFilters.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var addToPlaylistTrackIds by remember { mutableStateOf<List<UUID>>(emptyList()) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var radioSeed by remember { mutableStateOf<RadioSeed?>(null) }
    var showAlbumSortDialog by remember { mutableStateOf(false) }
    var showAlbumFilterSheet by remember { mutableStateOf(false) }
    var showArtistFilterSheet by remember { mutableStateOf(false) }
    var showTrackSortDialog by remember { mutableStateOf(false) }
    var showTrackFilterSheet by remember { mutableStateOf(false) }

    val lazyGenres = viewModel.genresPagingFlow.collectAsLazyPagingItems()

    val lazyTracks = viewModel.tracksPagingFlow.collectAsLazyPagingItems()
    val lazyAlbums = viewModel.albumsPagingFlow.collectAsLazyPagingItems()
    val lazyArtists = viewModel.artistsPagingFlow.collectAsLazyPagingItems()

    val tracksListState = rememberLazyListState()
    val albumsGridState = rememberLazyGridState()
    val artistsGridState = rememberLazyGridState()
    val playlistsGridState = rememberLazyGridState()
    val genresGridState = rememberLazyGridState()

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState, snackbar = { AFinitySnackbar(it) })
        },
        topBar = {
            AfinityTopAppBar(
                title = {
                    Text(
                        text = stringResource(tab.displayNameRes),
                        style =
                            MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
                backgroundOpacity = { 1f },
                userProfileImageUrl = userProfileImageUrl,
                onSearchClick = { navController.navigate(createSearchRoute()) },
                onProfileClick = { navController.navigate(createSettingsRoute()) },
            )
        },
        floatingActionButton = {
            when (tab) {
                LibraryFilter.Albums ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.End,
                    ) {
                        FilterFab(
                            active = albumFilters.isActive,
                            onClick = { showAlbumFilterSheet = true },
                        )
                        FloatingActionButton(onClick = { showAlbumSortDialog = true }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_arrows_sort),
                                contentDescription = stringResource(R.string.cd_sort_fab),
                            )
                        }
                    }
                LibraryFilter.Artists ->
                    FilterFab(
                        active = artistFilters.isActive,
                        onClick = { showArtistFilterSheet = true },
                    )
                LibraryFilter.Tracks ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.End,
                    ) {
                        FilterFab(
                            active = trackFilters.isActive,
                            onClick = { showTrackFilterSheet = true },
                        )
                        FloatingActionButton(onClick = { showTrackSortDialog = true }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_arrows_sort),
                                contentDescription = stringResource(R.string.cd_sort_fab),
                            )
                        }
                    }
                else -> Unit
            }
        },
    ) { innerPadding ->
        when (tab) {
            LibraryFilter.Playlists ->
                PlaylistsGrid(
                    gridState = playlistsGridState,
                    playlists = uiState.playlists,
                    onPlaylistClick = { playlist ->
                        navController.navigate(
                            Destination.createMusicPlaylistRoute(playlist.id.toString())
                        )
                    },
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                )
            LibraryFilter.Albums ->
                AlbumsGrid(
                    gridState = albumsGridState,
                    albums = lazyAlbums,
                    letterFilter = albumLetterFilter,
                    onLetterSelected = viewModel::filterAlbumsByLetter,
                    onAlbumClick = { album ->
                        navController.navigate(
                            Destination.createMusicAlbumRoute(album.id.toString())
                        )
                    },
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                )
            LibraryFilter.Artists ->
                ArtistsGrid(
                    gridState = artistsGridState,
                    artists = lazyArtists,
                    letterFilter = artistLetterFilter,
                    onLetterSelected = viewModel::filterArtistsByLetter,
                    onArtistClick = { artist ->
                        navController.navigate(
                            Destination.createMusicArtistRoute(artist.id.toString())
                        )
                    },
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                )
            LibraryFilter.Tracks ->
                TracksList(
                    listState = tracksListState,
                    tracks = lazyTracks,
                    favoriteOverrides = uiState.trackFavoriteOverrides,
                    currentTrackId = playbackState.currentTrack?.id?.toString(),
                    onPlayAll = {
                        scope.launch {
                            val queue = viewModel.getAllFilteredTracks()
                            if (queue.isNotEmpty()) {
                                startMusicService(context)
                                playerViewModel.playQueue(queue, 0)
                            }
                        }
                    },
                    onShuffleAll = {
                        scope.launch {
                            val queue = viewModel.getAllFilteredTracks()
                            if (queue.isNotEmpty()) {
                                startMusicService(context)
                                playerViewModel.playQueue(queue.shuffled(), 0)
                            }
                        }
                    },
                    onTrackClick = { track, queue, index ->
                        startMusicService(context)
                        playerViewModel.playQueue(queue, index)
                        navController.navigate(Destination.MUSIC_PLAYER_ROUTE)
                    },
                    onInstantMix =
                        if (isOffline) null
                        else
                            ({ track ->
                                startMusicService(context)
                                playerViewModel.playInstantMix(track.id)
                                navController.navigate(Destination.MUSIC_PLAYER_ROUTE)
                            }),
                    onStartRadio = { track ->
                        if (!isOffline) {
                            radioSeed =
                                RadioSeed(
                                    trackId = track.id,
                                    albumId = track.albumId,
                                    sourceTracks = listOf(track),
                                )
                        }
                    },
                    onAddNext = { track -> playerViewModel.addNext(listOf(track)) },
                    onAddLast = { track -> playerViewModel.addLast(listOf(track)) },
                    onFavorite = { track, isFavorite ->
                        viewModel.toggleTrackFavorite(track, isFavorite)
                    },
                    onAddToPlaylist =
                        if (isOffline) null
                        else
                            ({ track ->
                                addToPlaylistTrackIds = listOf(track.id)
                                addToPlaylistViewModel.reset()
                                showAddToPlaylist = true
                            }),
                    onDownload = { track -> viewModel.downloadTrack(track.id) },
                    downloadedTrackIds =
                        viewModel.trackDownloadInfos.collectAsStateWithLifecycle().value.keys,
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                )
            LibraryFilter.Genres ->
                GenresGrid(
                    gridState = genresGridState,
                    genres = lazyGenres,
                    onGenreClick = { genre ->
                        navController.navigate(
                            Destination.createMusicGenreRoute(genre.name, genre.imageUrl, genre.id)
                        )
                    },
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                )
            LibraryFilter.Home -> Unit
        }
    }

    if (showAlbumSortDialog) {
        MusicSortDialog(
            fields = ALBUM_SORT_FIELDS,
            currentField = albumSortField,
            currentDescending = albumSortDescending,
            onDismiss = { showAlbumSortDialog = false },
            onSortSelected = { field, descending -> viewModel.setAlbumSort(field, descending) },
        )
    }

    if (showAlbumFilterSheet) {
        MusicFilterBottomSheet(
            filters = albumFilters,
            options = albumFilterOptions,
            onApply = { viewModel.setAlbumFilters(it) },
            onDismiss = { showAlbumFilterSheet = false },
        )
    }

    if (showArtistFilterSheet) {
        MusicFilterBottomSheet(
            filters = artistFilters,
            options = albumFilterOptions,
            onApply = { viewModel.setArtistFilters(it) },
            onDismiss = { showArtistFilterSheet = false },
        )
    }

    if (showTrackSortDialog) {
        MusicSortDialog(
            fields = TRACK_SORT_FIELDS,
            currentField = trackSortField,
            currentDescending = trackSortDescending,
            onDismiss = { showTrackSortDialog = false },
            onSortSelected = { field, descending -> viewModel.setTrackSort(field, descending) },
        )
    }

    if (showTrackFilterSheet) {
        MusicFilterBottomSheet(
            filters = trackFilters,
            options = albumFilterOptions,
            onApply = { viewModel.setTrackFilters(it) },
            onDismiss = { showTrackFilterSheet = false },
        )
    }

    radioSeed?.let { seed ->
        RadioModeBottomSheet(
            seed = seed,
            onDismiss = { radioSeed = null },
            onSelectMode = { s, mode ->
                startMusicService(context)
                playerViewModel.startRadio(s, mode)
                radioSeed = null
            },
        )
    }

    if (showAddToPlaylist) {
        AddToPlaylistDialog(
            trackIds = addToPlaylistTrackIds,
            viewModel = addToPlaylistViewModel,
            onDismiss = { showAddToPlaylist = false },
            onResult = { result ->
                showAddToPlaylist = false
                val message =
                    when (result) {
                        is AddToPlaylistResult.Added -> "Added to \"${result.playlistName}\""
                        is AddToPlaylistResult.Created -> "Created \"${result.playlistName}\""
                        is AddToPlaylistResult.Error -> result.message
                        else -> null
                    }
                message?.let { scope.launch { snackbarHostState.showSnackbar(it) } }
            },
        )
    }
}

@Composable
private fun FilterFab(active: Boolean, onClick: () -> Unit) {
    FloatingActionButton(onClick = onClick) {
        Icon(
            painter =
                painterResource(
                    id = if (active) R.drawable.ic_filter_active else R.drawable.ic_filter
                ),
            contentDescription = stringResource(R.string.cd_filter_fab),
        )
    }
}
