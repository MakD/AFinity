package com.makd.afinity.ui.music.library

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.makd.afinity.R
import com.makd.afinity.data.models.music.AfinityAlbum
import com.makd.afinity.data.models.music.AfinityArtist
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.data.models.music.MusicFilters
import com.makd.afinity.navigation.Destination
import com.makd.afinity.player.audiobookshelf.AudiobookshelfPlayer
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.FullScreenLoading
import com.makd.afinity.ui.home.components.ArtistAlbumsCarousel
import com.makd.afinity.ui.home.components.LatestAlbumsSection
import com.makd.afinity.ui.home.components.MusicAlbumRowSection
import com.makd.afinity.ui.home.components.MusicGenreHomeSection
import com.makd.afinity.ui.home.components.MusicTrackRowSection
import com.makd.afinity.ui.music.components.AddToPlaylistDialog
import com.makd.afinity.ui.music.components.AddToPlaylistResult
import com.makd.afinity.ui.music.components.AddToPlaylistViewModel
import com.makd.afinity.ui.music.components.MusicAlbumCard
import com.makd.afinity.ui.music.components.MusicArtistCard
import com.makd.afinity.ui.music.components.MusicTrackRow
import com.makd.afinity.ui.music.player.MusicPlayerViewModel
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.launch
import java.util.UUID

enum class LibraryFilter(val displayName: String) {
    Home("Home"),
    Playlists("Playlists"),
    Artists("Artists"),
    Albums("Albums"),
    Tracks("Tracks"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicLibraryScreen(
    navController: NavController,
    viewModel: MusicLibraryViewModel = hiltViewModel(),
    playerViewModel: MusicPlayerViewModel = hiltViewModel(),
    addToPlaylistViewModel: AddToPlaylistViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playbackState by playerViewModel.playbackState.collectAsStateWithLifecycle()
    val userProfileImageUrl by viewModel.userProfileImageUrl.collectAsStateWithLifecycle()
    val trackSort by viewModel.trackSort.collectAsStateWithLifecycle()
    val albumSort by viewModel.albumSort.collectAsStateWithLifecycle()
    val artistSort by viewModel.artistSort.collectAsStateWithLifecycle()
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

    val lazyTracks = viewModel.tracksPagingFlow.collectAsLazyPagingItems()
    val lazyAlbums = viewModel.albumsPagingFlow.collectAsLazyPagingItems()
    val lazyArtists = viewModel.artistsPagingFlow.collectAsLazyPagingItems()

    var selectedFilter by rememberSaveable { mutableStateOf(LibraryFilter.Home) }

    val navBackStackEntry = navController.currentBackStackEntry
    DisposableEffect(navBackStackEntry) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPlaylists()
            }
        }
        navBackStackEntry?.lifecycle?.addObserver(observer)
        onDispose { navBackStackEntry?.lifecycle?.removeObserver(observer) }
    }

    val homeListState = rememberLazyListState()
    val tracksListState = rememberLazyListState()
    val albumsGridState = rememberLazyGridState()
    val artistsGridState = rememberLazyGridState()
    val playlistsGridState = rememberLazyGridState()

    val topBarOpacity by
        remember(selectedFilter) {
            derivedStateOf {
                when (selectedFilter) {
                    LibraryFilter.Home -> {
                        if (homeListState.firstVisibleItemIndex > 1) 1f
                        else (homeListState.firstVisibleItemScrollOffset / 300f).coerceIn(0f, 1f)
                    }
                    LibraryFilter.Tracks -> {
                        if (tracksListState.firstVisibleItemIndex > 0) 1f
                        else (tracksListState.firstVisibleItemScrollOffset / 300f).coerceIn(0f, 1f)
                    }
                    LibraryFilter.Albums -> {
                        if (albumsGridState.firstVisibleItemIndex > 0) 1f
                        else (albumsGridState.firstVisibleItemScrollOffset / 300f).coerceIn(0f, 1f)
                    }
                    LibraryFilter.Artists -> {
                        if (artistsGridState.firstVisibleItemIndex > 0) 1f
                        else (artistsGridState.firstVisibleItemScrollOffset / 300f).coerceIn(0f, 1f)
                    }
                    LibraryFilter.Playlists -> {
                        if (playlistsGridState.firstVisibleItemIndex > 0) 1f
                        else
                            (playlistsGridState.firstVisibleItemScrollOffset / 300f).coerceIn(
                                0f,
                                1f,
                            )
                    }
                }
            }
        }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            AfinityTopAppBar(
                title = {
                    Text(
                        text = viewModel.libraryName,
                        style =
                            MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
                backgroundOpacity = { topBarOpacity },
                userProfileImageUrl = userProfileImageUrl,
                onSearchClick = { navController.navigate(Destination.createSearchRoute()) },
                onProfileClick = { navController.navigate(Destination.createSettingsRoute()) },
            )
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(LibraryFilter.entries.toTypedArray()) { filter ->
                    val isSelected = selectedFilter == filter
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter.displayName) },
                        shape = CircleShape,
                        colors =
                            FilterChipDefaults.filterChipColors(
                                containerColor = Color.Transparent,
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        border =
                            FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = isSelected,
                                borderColor = MaterialTheme.colorScheme.outlineVariant,
                            ),
                    )
                }
            }

            AnimatedContent(
                targetState = selectedFilter,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "library_content_transition",
                modifier = Modifier.fillMaxSize(),
            ) { filter ->
                when (filter) {
                    LibraryFilter.Home ->
                        MusicHomeContent(
                            uiState = uiState,
                            listState = homeListState,
                            onTrackClick = { track ->
                                startMusicService(context)
                                playerViewModel.playQueue(
                                    uiState.recentlyPlayedTracks,
                                    uiState.recentlyPlayedTracks
                                        .indexOfFirst { it.id == track.id }
                                        .coerceAtLeast(0),
                                )
                                navController.navigate(Destination.MUSIC_PLAYER_ROUTE)
                            },
                            onTrackClickWithQueue = { track, queue ->
                                startMusicService(context)
                                playerViewModel.playQueue(
                                    queue,
                                    queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0),
                                )
                                navController.navigate(Destination.MUSIC_PLAYER_ROUTE)
                            },
                            onQueuePlay = { tracks ->
                                startMusicService(context)
                                playerViewModel.playQueue(tracks, 0)
                            },
                            onInstantMixByTrack = { track ->
                                startMusicService(context)
                                playerViewModel.playInstantMix(track.id)
                                navController.navigate(Destination.MUSIC_PLAYER_ROUTE)
                            },
                            onAlbumClick = { album ->
                                navController.navigate(
                                    Destination.createMusicAlbumRoute(album.id.toString())
                                )
                            },
                            onArtistClick = { artist ->
                                navController.navigate(
                                    Destination.createMusicArtistRoute(artist.id.toString())
                                )
                            },
                            onPlaylistClick = { playlistId ->
                                navController.navigate(
                                    Destination.createMusicPlaylistRoute(playlistId.toString())
                                )
                            },
                        )
                    LibraryFilter.Tracks ->
                        TracksList(
                            listState = tracksListState,
                            tracks = lazyTracks,
                            favoriteOverrides = uiState.trackFavoriteOverrides,
                            currentTrackId = playbackState.currentTrack?.id?.toString(),
                            sortOption = trackSort,
                            onSortChange = viewModel::setTrackSort,
                            filters = trackFilters,
                            onFiltersChange = viewModel::setTrackFilters,
                            onPlayAll = { queue ->
                                startMusicService(context)
                                playerViewModel.playQueue(queue, 0)
                            },
                            onShuffleAll = { queue ->
                                startMusicService(context)
                                playerViewModel.playQueue(queue.shuffled(), 0)
                            },
                            onTrackClick = { track, queue, index ->
                                startMusicService(context)
                                playerViewModel.playQueue(queue, index)
                            },
                            onInstantMix = { track ->
                                startMusicService(context)
                                playerViewModel.playInstantMix(track.id)
                            },
                            onStartRadio = { track ->
                                track.artistId?.let { artistId ->
                                    startMusicService(context)
                                    playerViewModel.playArtistRadio(artistId)
                                }
                            },
                            onAddNext = { track -> playerViewModel.addNext(listOf(track)) },
                            onAddLast = { track -> playerViewModel.addLast(listOf(track)) },
                            onFavorite = { track, isFavorite ->
                                viewModel.toggleTrackFavorite(track.id, isFavorite)
                            },
                            onAddToPlaylist = { track ->
                                addToPlaylistTrackIds = listOf(track.id)
                                addToPlaylistViewModel.reset()
                                showAddToPlaylist = true
                            },
                        )
                    LibraryFilter.Albums ->
                        AlbumsGrid(
                            gridState = albumsGridState,
                            albums = lazyAlbums,
                            sortOption = albumSort,
                            onSortChange = viewModel::setAlbumSort,
                            filters = albumFilters,
                            onFiltersChange = viewModel::setAlbumFilters,
                            letterFilter = albumLetterFilter,
                            onLetterSelected = viewModel::filterAlbumsByLetter,
                            onAlbumClick = { album ->
                                navController.navigate(
                                    Destination.createMusicAlbumRoute(album.id.toString())
                                )
                            },
                        )
                    LibraryFilter.Artists ->
                        ArtistsGrid(
                            gridState = artistsGridState,
                            artists = lazyArtists,
                            sortOption = artistSort,
                            onSortChange = viewModel::setArtistSort,
                            filters = artistFilters,
                            onFiltersChange = viewModel::setArtistFilters,
                            letterFilter = artistLetterFilter,
                            onLetterSelected = viewModel::filterArtistsByLetter,
                            onArtistClick = { artist ->
                                navController.navigate(
                                    Destination.createMusicArtistRoute(artist.id.toString())
                                )
                            },
                        )
                    LibraryFilter.Playlists ->
                        PlaylistsGrid(
                            gridState = playlistsGridState,
                            playlists = uiState.playlists,
                            onPlaylistClick = { playlist ->
                                navController.navigate(
                                    Destination.createMusicPlaylistRoute(playlist.id.toString())
                                )
                            },
                        )
                }
            }
        }
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
private fun TracksList(
    listState: LazyListState,
    tracks: LazyPagingItems<AfinityTrack>,
    favoriteOverrides: Map<UUID, Boolean>,
    currentTrackId: String?,
    sortOption: MusicSortOption,
    onSortChange: (MusicSortOption) -> Unit,
    filters: MusicFilters,
    onFiltersChange: (MusicFilters) -> Unit,
    onPlayAll: (List<AfinityTrack>) -> Unit,
    onShuffleAll: (List<AfinityTrack>) -> Unit,
    onTrackClick: (AfinityTrack, List<AfinityTrack>, Int) -> Unit,
    onInstantMix: (AfinityTrack) -> Unit,
    onStartRadio: (AfinityTrack) -> Unit,
    onAddNext: (AfinityTrack) -> Unit,
    onAddLast: (AfinityTrack) -> Unit,
    onFavorite: (AfinityTrack, Boolean) -> Unit,
    onAddToPlaylist: (AfinityTrack) -> Unit,
) {
    val isInitialLoading = tracks.loadState.refresh is LoadState.Loading && tracks.itemCount == 0
    if (isInitialLoading) {
        FullScreenLoading()
        return
    }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        item(key = "tracks_controls") {
            TabControlsRow(
                sortOptions = TRACK_SORT_OPTIONS,
                currentSort = sortOption,
                onSortChange = onSortChange,
                currentFilters = filters,
                onFiltersChange = onFiltersChange,
                onPlayAll = {
                    val queue = tracks.itemSnapshotList.items
                    if (queue.isNotEmpty()) onPlayAll(queue)
                },
                onShuffleAll = {
                    val queue = tracks.itemSnapshotList.items
                    if (queue.isNotEmpty()) onShuffleAll(queue)
                },
            )
        }
        items(count = tracks.itemCount, key = tracks.itemKey { it.id }) { index ->
            val track = tracks[index] ?: return@items
            val effectiveFavorite = favoriteOverrides[track.id] ?: track.favorite
            val displayTrack =
                if (effectiveFavorite != track.favorite) track.copy(favorite = effectiveFavorite)
                else track
            MusicTrackRow(
                track = displayTrack,
                isPlaying = track.id.toString() == currentTrackId,
                showAlbumArt = true,
                onClick = {
                    val queue = tracks.itemSnapshotList.items
                    val queueIndex = queue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
                    onTrackClick(track, queue, queueIndex)
                },
                onInstantMix = { onInstantMix(track) },
                onStartRadio = { onStartRadio(track) },
                onAddNext = { onAddNext(track) },
                onAddLast = { onAddLast(track) },
                onFavorite = { onFavorite(track, effectiveFavorite) },
                onAddToPlaylist = { onAddToPlaylist(track) },
            )
        }
    }
}

@Composable
private fun AlbumsGrid(
    gridState: LazyGridState,
    albums: LazyPagingItems<AfinityAlbum>,
    sortOption: MusicSortOption,
    onSortChange: (MusicSortOption) -> Unit,
    filters: MusicFilters,
    onFiltersChange: (MusicFilters) -> Unit,
    letterFilter: String?,
    onLetterSelected: (String) -> Unit,
    onAlbumClick: (AfinityAlbum) -> Unit,
) {
    val isInitialLoading = albums.loadState.refresh is LoadState.Loading && albums.itemCount == 0
    if (isInitialLoading) {
        FullScreenLoading()
        return
    }

    var previousLetterFilter by rememberSaveable { mutableStateOf(letterFilter) }
    LaunchedEffect(letterFilter) {
        if (letterFilter != previousLetterFilter) {
            gridState.scrollToItem(0)
            previousLetterFilter = letterFilter
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            state = gridState,
            contentPadding = PaddingValues(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "albums_controls") {
                TabControlsRow(
                    sortOptions = ALBUM_SORT_OPTIONS,
                    currentSort = sortOption,
                    onSortChange = onSortChange,
                    currentFilters = filters,
                    onFiltersChange = onFiltersChange,
                )
            }
            items(count = albums.itemCount, key = albums.itemKey { it.id }) { index ->
                val album = albums[index] ?: return@items
                MusicAlbumCard(album = album, onClick = { onAlbumClick(album) })
            }
        }
        Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
            MusicAlphabetScroller(
                selectedLetter = letterFilter,
                onLetterSelected = onLetterSelected,
            )
        }
    }
}

@Composable
private fun ArtistsGrid(
    gridState: LazyGridState,
    artists: LazyPagingItems<AfinityArtist>,
    sortOption: MusicSortOption,
    onSortChange: (MusicSortOption) -> Unit,
    filters: MusicFilters,
    onFiltersChange: (MusicFilters) -> Unit,
    letterFilter: String?,
    onLetterSelected: (String) -> Unit,
    onArtistClick: (AfinityArtist) -> Unit,
) {
    val isInitialLoading = artists.loadState.refresh is LoadState.Loading && artists.itemCount == 0
    if (isInitialLoading) {
        FullScreenLoading()
        return
    }

    var previousLetterFilter by rememberSaveable { mutableStateOf(letterFilter) }
    LaunchedEffect(letterFilter) {
        if (letterFilter != previousLetterFilter) {
            gridState.scrollToItem(0)
            previousLetterFilter = letterFilter
        }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(140.dp),
            state = gridState,
            contentPadding = PaddingValues(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "artists_controls") {
                TabControlsRow(
                    sortOptions = ARTIST_SORT_OPTIONS,
                    currentSort = sortOption,
                    onSortChange = onSortChange,
                    currentFilters = filters,
                    onFiltersChange = onFiltersChange,
                )
            }
            items(count = artists.itemCount, key = artists.itemKey { it.id }) { index ->
                val artist = artists[index] ?: return@items
                MusicArtistCard(
                    name = artist.name,
                    imageUrl = artist.images.primary?.toString(),
                    blurHash = artist.images.primaryImageBlurHash,
                    onClick = { onArtistClick(artist) },
                )
            }
        }
        Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
            MusicAlphabetScroller(
                selectedLetter = letterFilter,
                onLetterSelected = onLetterSelected,
            )
        }
    }
}

@Composable
private fun PlaylistsGrid(
    gridState: LazyGridState,
    playlists: List<com.makd.afinity.data.models.music.AfinityPlaylist>,
    onPlaylistClick: (com.makd.afinity.data.models.music.AfinityPlaylist) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        state = gridState,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(playlists, key = { it.id }) { playlist ->
            MusicAlbumCard(
                album =
                    com.makd.afinity.data.models.music.AfinityAlbum(
                        id = playlist.id,
                        name = playlist.name,
                        artistId = null,
                        artist = null,
                        artists = emptyList(),
                        productionYear = null,
                        songCount = playlist.songCount,
                        runtimeTicks = playlist.runtimeTicks,
                        genres = emptyList(),
                        overview = playlist.overview,
                        favorite = playlist.favorite,
                        played = false,
                        playCount = null,
                        images = playlist.images,
                    ),
                onClick = { onPlaylistClick(playlist) },
            )
        }
    }
}

@Composable
private fun TabControlsRow(
    sortOptions: List<MusicSortOption>,
    currentSort: MusicSortOption,
    onSortChange: (MusicSortOption) -> Unit,
    currentFilters: MusicFilters,
    onFiltersChange: (MusicFilters) -> Unit,
    onPlayAll: (() -> Unit)? = null,
    onShuffleAll: (() -> Unit)? = null,
) {
    var showSortMenu by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                TextButton(onClick = { showSortMenu = true }) {
                    Icon(
                        painter =
                            androidx.compose.ui.res.painterResource(R.drawable.ic_arrows_sort),
                        contentDescription = "Sort",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = currentSort.label,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                    sortOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.label) },
                            onClick = {
                                onSortChange(option)
                                showSortMenu = false
                            },
                            leadingIcon =
                                if (currentSort == option) {
                                    {
                                        Icon(
                                            painter =
                                                androidx.compose.ui.res.painterResource(
                                                    R.drawable.ic_check
                                                ),
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                } else null,
                        )
                    }
                }
            }

            BadgedBox(
                badge = {
                    if (currentFilters.isActive) Badge()
                }
            ) {
                IconButton(onClick = { showFilterSheet = true }) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_filter),
                        contentDescription = "Filter",
                        modifier = Modifier.size(20.dp),
                        tint =
                            if (currentFilters.isActive) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (onPlayAll != null || onShuffleAll != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onShuffleAll != null) {
                    OutlinedButton(
                        onClick = onShuffleAll,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            painter =
                                androidx.compose.ui.res.painterResource(
                                    R.drawable.ic_arrows_shuffle
                                ),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Shuffle", style = MaterialTheme.typography.labelLarge)
                    }
                }
                if (onPlayAll != null) {
                    Button(
                        onClick = onPlayAll,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            painter =
                                androidx.compose.ui.res.painterResource(
                                    R.drawable.ic_player_play_filled
                                ),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Play All", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = sheetState,
        ) {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 32.dp)
                        .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Filter",
                        style =
                            MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                    if (currentFilters.isActive) {
                        TextButton(onClick = { onFiltersChange(MusicFilters()) }) {
                            Text("Clear")
                        }
                    }
                }
                HorizontalDivider()
                Text(
                    text = "Status",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = currentFilters.favoritesOnly,
                        onClick = {
                            onFiltersChange(
                                currentFilters.copy(favoritesOnly = !currentFilters.favoritesOnly)
                            )
                        },
                        label = { Text("Favorites") },
                    )
                    FilterChip(
                        selected = currentFilters.unplayedOnly,
                        onClick = {
                            onFiltersChange(
                                currentFilters.copy(
                                    unplayedOnly = !currentFilters.unplayedOnly,
                                    playedOnly = false,
                                )
                            )
                        },
                        label = { Text("Unplayed") },
                    )
                    FilterChip(
                        selected = currentFilters.playedOnly,
                        onClick = {
                            onFiltersChange(
                                currentFilters.copy(
                                    playedOnly = !currentFilters.playedOnly,
                                    unplayedOnly = false,
                                )
                            )
                        },
                        label = { Text("Played") },
                    )
                }
            }
        }
    }
}

@Composable
private fun MusicAlphabetScroller(
    selectedLetter: String?,
    onLetterSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val letters = listOf("#") + ('A'..'Z').map { it.toString() }
    Column(
        modifier =
            modifier.width(32.dp).padding(vertical = 8.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        letters.forEach { letter ->
            val isSelected = selectedLetter == letter
            Text(
                text = letter,
                style =
                    MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    ),
                color =
                    if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier.clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) {
                            onLetterSelected(letter)
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MusicHomeContent(
    uiState: MusicLibraryUiState,
    listState: LazyListState,
    onTrackClick: (AfinityTrack) -> Unit,
    onTrackClickWithQueue:
        (
            track: AfinityTrack,
            queue: List<AfinityTrack>,
        ) -> Unit,
    onQueuePlay: (List<AfinityTrack>) -> Unit,
    onInstantMixByTrack: (AfinityTrack) -> Unit,
    onAlbumClick: (com.makd.afinity.data.models.music.AfinityAlbum) -> Unit,
    onArtistClick: (com.makd.afinity.data.models.music.AfinityArtist) -> Unit,
    onPlaylistClick: (java.util.UUID) -> Unit,
) {
    val isEmpty =
        uiState.recentlyPlayedTracks.isEmpty() &&
            uiState.recentlyPlayedAlbums.isEmpty() &&
            uiState.latestAlbums.isEmpty() &&
            uiState.mostPlayedAlbums.isEmpty() &&
            uiState.randomAlbums.isEmpty()
    if (uiState.isLoadingHome && isEmpty) {
        FullScreenLoading()
        return
    }

    val allRows = remember {
        buildList {
            add(HomeRow.MostPlayed)
            add(HomeRow.Favorites)
            add(HomeRow.FavoriteArtists)
            add(HomeRow.TopArtists)
            add(HomeRow.RandomSongs)
            add(HomeRow.Playlists)
            add(HomeRow.TopRated)
            add(HomeRow.ArtistsToExplore)
            add(HomeRow.Decades)
            repeat(5) { add(HomeRow.MoreFromArtist(it)) }
            repeat(3) { add(HomeRow.TopTracks(it)) }
            repeat(5) { add(HomeRow.Genre(it)) }
            repeat(3) { add(HomeRow.NewGenreRelease(it)) }
            repeat(2) { add(HomeRow.SongsByGenre(it)) }
            repeat(3) { add(HomeRow.Radio(it)) }
        }
    }

    val shuffledRows =
        uiState.homeRowOrder
            .mapNotNull { allRows.getOrNull(it) }
            .filter { row ->
                when (row) {
                    HomeRow.MostPlayed -> uiState.mostPlayedAlbums.isNotEmpty()
                    HomeRow.Favorites -> uiState.favoriteAlbums.isNotEmpty()
                    HomeRow.FavoriteArtists -> uiState.favoriteArtists.isNotEmpty()
                    HomeRow.TopArtists -> uiState.topArtists.isNotEmpty()
                    HomeRow.RandomSongs -> uiState.randomTracks.isNotEmpty()
                    HomeRow.Playlists -> uiState.homePlaylists.isNotEmpty()
                    HomeRow.TopRated -> uiState.topRatedAlbums.isNotEmpty()
                    HomeRow.ArtistsToExplore -> uiState.randomArtists.isNotEmpty()
                    HomeRow.Decades -> uiState.albumsByDecade != null
                    is HomeRow.MoreFromArtist -> uiState.moreFromArtistSections.size > row.index
                    is HomeRow.TopTracks -> uiState.topTracksSections.size > row.index
                    is HomeRow.Genre -> uiState.musicGenreSections.size > row.index
                    is HomeRow.NewGenreRelease -> uiState.newGenreReleases.size > row.index
                    is HomeRow.SongsByGenre -> uiState.songsByGenreSections.size > row.index
                    is HomeRow.Radio -> uiState.radioSections.size > row.index
                }
            }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item(key = "top_spacer") { Spacer(Modifier.height(16.dp)) }

        if (uiState.randomAlbums.isNotEmpty()) {
            item(key = "discover") {
                LatestAlbumsSection(
                    title = "Discover",
                    albums = uiState.randomAlbums,
                    onAlbumClick = onAlbumClick,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            }
        }
        if (uiState.latestAlbums.isNotEmpty()) {
            item(key = "latest_albums") {
                MusicAlbumRowSection(
                    title = "Recently Added",
                    albums = uiState.latestAlbums,
                    onAlbumClick = onAlbumClick,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            }
        }
        if (uiState.recentlyPlayedAlbums.isNotEmpty()) {
            item(key = "recently_played") {
                MusicAlbumRowSection(
                    title = "Recently Played Albums",
                    albums = uiState.recentlyPlayedAlbums,
                    onAlbumClick = onAlbumClick,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            }
        }
        if (uiState.recentlyPlayedTracks.isNotEmpty()) {
            item(key = "recently_played_tracks") {
                MusicTrackRowSection(
                    title = "Recently Played",
                    tracks = uiState.recentlyPlayedTracks,
                    onTrackClick = onTrackClick,
                    modifier = Modifier.padding(bottom = 24.dp),
                )
            }
        }

        items(shuffledRows, key = { it.key() }) { row ->
            val m = Modifier.padding(bottom = 24.dp)
            when (row) {
                HomeRow.MostPlayed ->
                    MusicAlbumRowSection(
                        title = "Most Played",
                        albums = uiState.mostPlayedAlbums,
                        onAlbumClick = onAlbumClick,
                        modifier = m,
                    )
                HomeRow.Favorites ->
                    MusicAlbumRowSection(
                        title = "Favorites",
                        albums = uiState.favoriteAlbums,
                        onAlbumClick = onAlbumClick,
                        modifier = m,
                    )
                HomeRow.FavoriteArtists ->
                    MusicArtistsRow(
                        title = "Favorite Artists",
                        artists = uiState.favoriteArtists,
                        onArtistClick = onArtistClick,
                        modifier = m,
                    )
                HomeRow.TopArtists ->
                    MusicArtistsRow(
                        title = "Your Top Artists",
                        artists = uiState.topArtists,
                        onArtistClick = onArtistClick,
                        modifier = m,
                    )
                HomeRow.RandomSongs ->
                    MusicTrackRowSection(
                        title = "Random Songs",
                        tracks = uiState.randomTracks,
                        onTrackClick = { track ->
                            onTrackClickWithQueue(track, uiState.randomTracks)
                        },
                        modifier = m,
                    )
                HomeRow.Playlists ->
                    MusicAlbumRowSection(
                        title = "Playlists",
                        albums =
                            uiState.homePlaylists.map { p ->
                                com.makd.afinity.data.models.music.AfinityAlbum(
                                    id = p.id,
                                    name = p.name,
                                    artistId = null,
                                    artist = null,
                                    artists = emptyList(),
                                    productionYear = null,
                                    songCount = p.songCount,
                                    runtimeTicks = p.runtimeTicks,
                                    genres = emptyList(),
                                    overview = p.overview,
                                    favorite = p.favorite,
                                    played = false,
                                    playCount = null,
                                    images = p.images,
                                )
                            },
                        onAlbumClick = { album -> onPlaylistClick(album.id) },
                        modifier = m,
                    )
                HomeRow.TopRated ->
                    LatestAlbumsSection(
                        title = "Top Rated",
                        albums = uiState.topRatedAlbums,
                        onAlbumClick = onAlbumClick,
                        modifier = m,
                    )
                HomeRow.ArtistsToExplore ->
                    MusicArtistsRow(
                        title = "Artists to Explore",
                        artists = uiState.randomArtists,
                        onArtistClick = onArtistClick,
                        modifier = m,
                    )
                HomeRow.Decades ->
                    uiState.albumsByDecade?.let { (decade, albums) ->
                        LatestAlbumsSection(
                            title = "From the ${decade}s",
                            albums = albums,
                            onAlbumClick = onAlbumClick,
                            modifier = m,
                        )
                    }
                is HomeRow.MoreFromArtist ->
                    uiState.moreFromArtistSections.getOrNull(row.index)?.let { (artist, albums) ->
                        ArtistAlbumsCarousel(
                            artist = artist,
                            albums = albums,
                            onAlbumClick = onAlbumClick,
                            modifier = m,
                        )
                    }
                is HomeRow.TopTracks ->
                    uiState.topTracksSections.getOrNull(row.index)?.let { (artistName, tracks) ->
                        MusicTrackRowSection(
                            title = "Top Tracks: $artistName",
                            tracks = tracks,
                            onTrackClick = { track -> onTrackClickWithQueue(track, tracks) },
                            modifier = m,
                        )
                    }
                is HomeRow.Genre ->
                    uiState.musicGenreSections.getOrNull(row.index)?.let { (genre, albums) ->
                        MusicGenreHomeSection(
                            name = genre,
                            albums = albums,
                            onAlbumClick = onAlbumClick,
                            modifier = m,
                        )
                    }
                is HomeRow.NewGenreRelease ->
                    uiState.newGenreReleases.getOrNull(row.index)?.let { (genre, albums) ->
                        MusicAlbumRowSection(
                            title = "New $genre Releases",
                            albums = albums,
                            onAlbumClick = onAlbumClick,
                            modifier = m,
                        )
                    }
                is HomeRow.SongsByGenre ->
                    uiState.songsByGenreSections.getOrNull(row.index)?.let { (genre, tracks) ->
                        MusicTracksCard(
                            title = "$genre Songs",
                            tracks = tracks,
                            subtitle = "Instant Mix",
                            onPlay = { tracks.firstOrNull()?.let { onInstantMixByTrack(it) } },
                            modifier = m,
                        )
                    }
                is HomeRow.Radio ->
                    uiState.radioSections.getOrNull(row.index)?.let { (title, tracks) ->
                        MusicTracksCard(
                            title = title,
                            tracks = tracks,
                            subtitle = "Radio",
                            onPlay = { onQueuePlay(tracks) },
                            modifier = m,
                        )
                    }
            }
        }
    }
}

private sealed class HomeRow {
    data object MostPlayed : HomeRow()

    data object Favorites : HomeRow()

    data object FavoriteArtists : HomeRow()

    data object TopArtists : HomeRow()

    data object RandomSongs : HomeRow()

    data object Playlists : HomeRow()

    data object TopRated : HomeRow()

    data object ArtistsToExplore : HomeRow()

    data object Decades : HomeRow()

    data class MoreFromArtist(val index: Int) : HomeRow()

    data class TopTracks(val index: Int) : HomeRow()

    data class Genre(val index: Int) : HomeRow()

    data class NewGenreRelease(val index: Int) : HomeRow()

    data class SongsByGenre(val index: Int) : HomeRow()

    data class Radio(val index: Int) : HomeRow()

    fun key(): String =
        when (this) {
            is MostPlayed -> "most_played"
            is Favorites -> "favorites"
            is FavoriteArtists -> "favorite_artists"
            is TopArtists -> "top_artists"
            is RandomSongs -> "random_songs"
            is Playlists -> "home_playlists"
            is TopRated -> "top_rated"
            is ArtistsToExplore -> "random_artists"
            is Decades -> "decades"
            is MoreFromArtist -> "more_from_$index"
            is TopTracks -> "top_tracks_$index"
            is Genre -> "genre_$index"
            is NewGenreRelease -> "new_genre_$index"
            is SongsByGenre -> "songs_genre_$index"
            is Radio -> "radio_$index"
        }
}

@Composable
private fun MusicTracksCard(
    title: String,
    tracks: List<AfinityTrack>,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String = "${tracks.size} tracks",
) {
    val imageUrl = tracks.firstOrNull()?.images?.primary?.toString()
    val blurHash = tracks.firstOrNull()?.images?.primaryImageBlurHash
    Box(
        modifier =
            modifier
                .padding(horizontal = 14.dp)
                .fillMaxWidth()
                .height(220.dp)
                .clip(MaterialTheme.shapes.extraLarge)
                .clickable(onClick = onPlay)
    ) {
        com.makd.afinity.ui.components.AsyncImage(
            imageUrl = imageUrl,
            contentDescription = title,
            blurHash = blurHash,
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
        )
        Box(
            modifier =
                Modifier.fillMaxSize().drawWithCache {
                    val gradient =
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                            startY = size.height * 0.45f,
                            endY = size.height,
                        )
                    onDrawWithContent {
                        drawRect(Color.Black.copy(alpha = 0.4f))
                        drawRect(gradient)
                        drawContent()
                    }
                }
        ) {
            Row(
                modifier =
                    Modifier.align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = title,
                        style =
                            MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
                Box(
                    modifier =
                        Modifier.size(40.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                            .clickable(onClick = onPlay),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter =
                            androidx.compose.ui.res.painterResource(
                                R.drawable.ic_player_play_filled
                            ),
                        contentDescription = "Play",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun MusicArtistsRow(
    title: String,
    artists: List<com.makd.afinity.data.models.music.AfinityArtist>,
    onArtistClick: (com.makd.afinity.data.models.music.AfinityArtist) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: androidx.compose.ui.unit.Dp = 14.dp,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = horizontalPadding, bottom = 12.dp),
        )
        LazyRow(
            modifier = Modifier.padding(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(artists.take(15), key = { it.id }) { artist ->
                MusicArtistCard(
                    name = artist.name,
                    imageUrl = artist.images.primary?.toString(),
                    blurHash = artist.images.primaryImageBlurHash,
                    onClick = { onArtistClick(artist) },
                )
            }
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
private interface AbsPlayerEntryPoint {
    fun audiobookshelfPlayer(): AudiobookshelfPlayer
}

@androidx.annotation.OptIn(UnstableApi::class)
internal fun startMusicService(context: Context) {
    EntryPointAccessors.fromApplication(
            context.applicationContext,
            AbsPlayerEntryPoint::class.java,
        )
        .audiobookshelfPlayer()
        .release()
    context.startService(
        Intent(context, com.makd.afinity.player.AudioService::class.java)
            .setAction(com.makd.afinity.player.AudioService.ACTION_ENGINE_MUSIC)
    )
}
