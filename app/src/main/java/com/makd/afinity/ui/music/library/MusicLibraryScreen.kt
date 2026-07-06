package com.makd.afinity.ui.music.library

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import com.makd.afinity.R
import com.makd.afinity.data.models.music.AfinityAlbum
import com.makd.afinity.data.models.music.AfinityArtist
import com.makd.afinity.data.models.music.AfinityMusicGenre
import com.makd.afinity.data.models.music.AfinityPlaylist
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.data.models.music.MusicFilters
import com.makd.afinity.navigation.Destination
import com.makd.afinity.player.AudioService
import com.makd.afinity.player.audiobookshelf.AudiobookshelfPlayer
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.AlphabetScroller
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.components.FullScreenLoading
import com.makd.afinity.ui.home.components.ArtistAlbumsCarousel
import com.makd.afinity.ui.home.components.LatestAlbumsSection
import com.makd.afinity.ui.home.components.MostPlayedAlbumsSection
import com.makd.afinity.ui.home.components.MusicAlbumRowSection
import com.makd.afinity.ui.home.components.MusicGenreHomeSection
import com.makd.afinity.ui.music.components.MusicAlbumCard
import com.makd.afinity.ui.music.components.MusicArtistCard
import com.makd.afinity.ui.music.components.MusicGenreCard
import com.makd.afinity.ui.music.components.MusicTrackRow
import com.makd.afinity.ui.music.player.MusicPlayerViewModel
import com.makd.afinity.ui.theme.CardDimensions
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.UUID

enum class LibraryFilter(@StringRes val displayNameRes: Int) {
    Home(R.string.music_tab_home),
    Playlists(R.string.music_tab_playlists),
    Artists(R.string.music_tab_artists),
    Albums(R.string.music_tab_albums),
    Tracks(R.string.music_tab_tracks),
    Genres(R.string.music_tab_genres),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicLibraryScreen(
    navController: NavController,
    viewModel: MusicLibraryViewModel = hiltViewModel(),
    playerViewModel: MusicPlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val userProfileImageUrl by viewModel.userProfileImageUrl.collectAsStateWithLifecycle()
    val context = LocalContext.current

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

    val topBarOpacity by remember {
        derivedStateOf {
            if (homeListState.firstVisibleItemIndex > 1) 1f
            else (homeListState.firstVisibleItemScrollOffset / 300f).coerceIn(0f, 1f)
        }
    }

    Scaffold(
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
        }
    ) { innerPadding ->
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
                navController.navigate(Destination.createMusicAlbumRoute(album.id.toString()))
            },
            onArtistClick = { artist ->
                navController.navigate(Destination.createMusicArtistRoute(artist.id.toString()))
            },
            onBrowse = { filter ->
                navController.navigate(
                    Destination.createMusicBrowseRoute(
                        libraryId = viewModel.libraryId.toString(),
                        libraryName = viewModel.libraryName,
                        tab = filter.name,
                    )
                )
            },
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        )
    }
}

@Composable
internal fun TracksList(
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
    onInstantMix: ((AfinityTrack) -> Unit)?,
    onStartRadio: (AfinityTrack) -> Unit,
    onAddNext: (AfinityTrack) -> Unit,
    onAddLast: (AfinityTrack) -> Unit,
    onFavorite: (AfinityTrack, Boolean) -> Unit,
    onAddToPlaylist: ((AfinityTrack) -> Unit)?,
    onDownload: ((AfinityTrack) -> Unit)? = null,
    downloadedTrackIds: Set<UUID> = emptySet(),
    modifier: Modifier = Modifier,
) {
    val isInitialLoading = tracks.loadState.refresh is LoadState.Loading && tracks.itemCount == 0
    if (isInitialLoading) {
        FullScreenLoading()
        return
    }

    LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
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
                onInstantMix = onInstantMix?.let { mix -> { mix(track) } },
                onStartRadio = { onStartRadio(track) },
                onAddNext = { onAddNext(track) },
                onAddLast = { onAddLast(track) },
                onFavorite = { onFavorite(track, effectiveFavorite) },
                onAddToPlaylist = onAddToPlaylist?.let { atp -> { atp(track) } },
                onDownload = onDownload?.let { dl -> { dl(track) } },
                isDownloaded = downloadedTrackIds.contains(track.id),
            )
        }
    }
}

@Composable
internal fun AlbumsGrid(
    gridState: LazyGridState,
    albums: LazyPagingItems<AfinityAlbum>,
    sortOption: MusicSortOption,
    onSortChange: (MusicSortOption) -> Unit,
    filters: MusicFilters,
    onFiltersChange: (MusicFilters) -> Unit,
    letterFilter: String?,
    onLetterSelected: (String) -> Unit,
    onAlbumClick: (AfinityAlbum) -> Unit,
    modifier: Modifier = Modifier,
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

    Row(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            state = gridState,
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 16.dp,
                ),
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
            AlphabetScroller(
                selectedLetter = letterFilter,
                onLetterSelected = onLetterSelected,
            )
        }
    }
}

@Composable
internal fun ArtistsGrid(
    gridState: LazyGridState,
    artists: LazyPagingItems<AfinityArtist>,
    sortOption: MusicSortOption,
    onSortChange: (MusicSortOption) -> Unit,
    filters: MusicFilters,
    onFiltersChange: (MusicFilters) -> Unit,
    letterFilter: String?,
    onLetterSelected: (String) -> Unit,
    onArtistClick: (AfinityArtist) -> Unit,
    modifier: Modifier = Modifier,
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

    Row(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(140.dp),
            state = gridState,
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 16.dp,
                ),
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
            AlphabetScroller(
                selectedLetter = letterFilter,
                onLetterSelected = onLetterSelected,
            )
        }
    }
}

@Composable
internal fun PlaylistsGrid(
    gridState: LazyGridState,
    playlists: List<AfinityPlaylist>,
    onPlaylistClick: (AfinityPlaylist) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        state = gridState,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        items(playlists, key = { it.id }) { playlist ->
            MusicAlbumCard(
                album =
                    AfinityAlbum(
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
internal fun GenresGrid(
    gridState: LazyGridState,
    genres: LazyPagingItems<AfinityMusicGenre>,
    onGenreClick: (AfinityMusicGenre) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isInitialLoading = genres.loadState.refresh is LoadState.Loading && genres.itemCount == 0
    if (isInitialLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            FullScreenLoading()
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        state = gridState,
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        items(count = genres.itemCount, key = genres.itemKey { it.id }) { index ->
            val genre = genres[index] ?: return@items
            MusicGenreCard(
                genre = genre,
                index = index,
                onClick = { onGenreClick(genre) },
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
                        painter = painterResource(R.drawable.ic_arrows_sort),
                        contentDescription = stringResource(R.string.cd_sort_fab),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(currentSort.labelRes),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                    sortOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(stringResource(option.labelRes)) },
                            onClick = {
                                onSortChange(option)
                                showSortMenu = false
                            },
                            leadingIcon =
                                if (currentSort == option) {
                                    {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_check),
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
                        painter = painterResource(R.drawable.ic_filter),
                        contentDescription = stringResource(R.string.cd_music_filter),
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
                            painter = painterResource(R.drawable.ic_arrows_shuffle),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.music_action_shuffle),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                if (onPlayAll != null) {
                    Button(
                        onClick = onPlayAll,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_player_play_filled),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.music_action_play_all),
                            style = MaterialTheme.typography.labelLarge,
                        )
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
                        text = stringResource(R.string.music_filter_status),
                        style =
                            MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                    if (currentFilters.isActive) {
                        TextButton(onClick = { onFiltersChange(MusicFilters()) }) {
                            Text(stringResource(R.string.music_filter_clear))
                        }
                    }
                }
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.music_filter_status),
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
                        label = { Text(stringResource(R.string.filter_favorites)) },
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
                        label = { Text(stringResource(R.string.music_filter_unplayed)) },
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
                        label = { Text(stringResource(R.string.music_filter_played)) },
                    )
                }
            }
        }
    }
}

private sealed class MadeForYouItem {
    data class TrackMix(val title: String, val subtitle: String, val tracks: List<AfinityTrack>) :
        MadeForYouItem()

    data class AlbumItem(val album: AfinityAlbum) : MadeForYouItem()
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
    onAlbumClick: (AfinityAlbum) -> Unit,
    onArtistClick: (AfinityArtist) -> Unit,
    onBrowse: (LibraryFilter) -> Unit,
    modifier: Modifier = Modifier,
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

    val surpriseMeLabel = stringResource(R.string.music_mix_surprise_me)
    val randomMixLabel = stringResource(R.string.music_mix_random)
    val radioLabel = stringResource(R.string.music_mix_radio)
    val instantMixLabel = stringResource(R.string.music_action_instant_mix)
    val songsFmt = stringResource(R.string.music_mix_instant_mix_genre_fmt)

    val madeForYouItems =
        remember(uiState.madeForYouSnapshot) {
            val snapshot = uiState.madeForYouSnapshot ?: return@remember emptyList()

            val randomMix =
                if (snapshot.randomTracks.isNotEmpty()) {
                    listOf(
                        MadeForYouItem.TrackMix(
                            surpriseMeLabel,
                            randomMixLabel,
                            snapshot.randomTracks,
                        )
                    )
                } else {
                    emptyList()
                }

            val others = buildList {
                snapshot.radioSections.forEach { (title, tracks) ->
                    add(MadeForYouItem.TrackMix(title, radioLabel, tracks))
                }
                snapshot.songsByGenreSections.forEach { (genre, tracks) ->
                    add(MadeForYouItem.TrackMix(songsFmt.format(genre), instantMixLabel, tracks))
                }
                snapshot.randomAlbums.forEach { album ->
                    add(MadeForYouItem.AlbumItem(album))
                }
            }
                .shuffled()

            randomMix + others
        }

    val shuffledRows =
        uiState.homeRowOrder
            .mapNotNull { allRows.getOrNull(it) }
            .filterNot { row ->
                row is HomeRow.Playlists ||
                    row is HomeRow.MostPlayed ||
                    row is HomeRow.Favorites ||
                    row is HomeRow.RandomSongs ||
                    row is HomeRow.Radio ||
                    row is HomeRow.SongsByGenre
            }
            .filter { row ->
                when (row) {
                    HomeRow.FavoriteArtists -> uiState.favoriteArtists.isNotEmpty()
                    HomeRow.TopArtists -> uiState.topArtists.isNotEmpty()
                    HomeRow.TopRated -> uiState.topRatedAlbums.isNotEmpty()
                    HomeRow.ArtistsToExplore -> uiState.randomArtists.isNotEmpty()
                    HomeRow.Decades -> uiState.albumsByDecade != null
                    is HomeRow.MoreFromArtist -> uiState.moreFromArtistSections.size > row.index
                    is HomeRow.TopTracks -> uiState.topTracksSections.size > row.index
                    is HomeRow.Genre -> uiState.musicGenreSections.size > row.index
                    is HomeRow.NewGenreRelease -> uiState.newGenreReleases.size > row.index
                    else -> false
                }
            }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        item(key = "top_spacer") { Spacer(Modifier.height(16.dp)) }

        if (madeForYouItems.isNotEmpty()) {
            item(key = "made_for_you_carousel") {
                MadeForYouCarousel(
                    title = stringResource(R.string.music_section_made_for_you),
                    items = madeForYouItems,
                    onPlayMix = onQueuePlay,
                    onAlbumClick = onAlbumClick,
                    modifier = Modifier.padding(bottom = 28.dp),
                )
            }
        }

        item(key = "library_shortcuts") {
            LibraryShortcutsRow(
                onBrowse = onBrowse,
                modifier = Modifier.padding(bottom = 28.dp),
            )
        }

        if (uiState.recentlyPlayedTracks.isNotEmpty()) {
            item(key = "recently_played_tracks") {
                CompactTrackGridSection(
                    title = stringResource(R.string.music_section_jump_back_in),
                    tracks = uiState.recentlyPlayedTracks,
                    onTrackClick = { track ->
                        onTrackClickWithQueue(track, uiState.recentlyPlayedTracks)
                    },
                    modifier = Modifier.padding(bottom = 28.dp),
                )
            }
        }

        if (uiState.recentlyPlayedAlbums.isNotEmpty()) {
            item(key = "recently_played") {
                MusicAlbumRowSection(
                    title = stringResource(R.string.music_section_recently_played_albums),
                    albums = uiState.recentlyPlayedAlbums,
                    onAlbumClick = onAlbumClick,
                    modifier = Modifier.padding(bottom = 28.dp),
                )
            }
        }

        if (uiState.latestAlbums.isNotEmpty()) {
            item(key = "latest_albums") {
                MusicAlbumRowSection(
                    title = stringResource(R.string.music_section_recently_added),
                    albums = uiState.latestAlbums,
                    onAlbumClick = onAlbumClick,
                    modifier = Modifier.padding(bottom = 28.dp),
                )
            }
        }

        if (uiState.mostPlayedAlbums.isNotEmpty()) {
            item(key = "pinned_most_played") {
                MostPlayedAlbumsSection(
                    albums = uiState.mostPlayedAlbums,
                    onAlbumClick = onAlbumClick,
                    modifier = Modifier.padding(bottom = 28.dp),
                )
            }
        }

        if (uiState.favoriteAlbums.isNotEmpty()) {
            item(key = "pinned_favorites") {
                MusicAlbumRowSection(
                    title = stringResource(R.string.favorites_title),
                    albums = uiState.favoriteAlbums,
                    onAlbumClick = onAlbumClick,
                    modifier = Modifier.padding(bottom = 28.dp),
                )
            }
        }

        items(shuffledRows, key = { it.key() }) { row ->
            val m = Modifier.padding(bottom = 28.dp)
            when (row) {
                HomeRow.FavoriteArtists ->
                    MusicArtistsRow(
                        title = stringResource(R.string.music_section_favorite_artists),
                        artists = uiState.favoriteArtists,
                        onArtistClick = onArtistClick,
                        modifier = m,
                    )
                HomeRow.TopArtists ->
                    MusicArtistsRow(
                        title = stringResource(R.string.music_section_top_artists),
                        artists = uiState.topArtists,
                        onArtistClick = onArtistClick,
                        modifier = m,
                    )
                HomeRow.TopRated ->
                    LatestAlbumsSection(
                        title = stringResource(R.string.music_section_top_rated),
                        albums = uiState.topRatedAlbums,
                        onAlbumClick = onAlbumClick,
                        modifier = m,
                    )
                HomeRow.ArtistsToExplore ->
                    MusicArtistsRow(
                        title = stringResource(R.string.music_section_artists_to_explore),
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
                        CompactTrackGridSection(
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
                else -> Unit
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

private data class LibraryShortcut(
    val filter: LibraryFilter,
    @StringRes val labelRes: Int,
    val iconRes: Int,
    val gradientStart: Color,
    val gradientEnd: Color,
)

@Composable
private fun LibraryShortcutsRow(
    onBrowse: (LibraryFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val cardWidth =
        when {
            configuration.screenWidthDp < 600 -> 240.dp
            configuration.screenWidthDp < 840 -> 260.dp
            else -> 320.dp
        }

    val shortcuts = remember {
        listOf(
            LibraryShortcut(
                filter = LibraryFilter.Playlists,
                labelRes = R.string.music_nav_playlists,
                iconRes = R.drawable.ic_music_playlist,
                gradientStart = Color(0xFFB1AADA),
                gradientEnd = Color(0xFF5A5482),
            ),
            LibraryShortcut(
                filter = LibraryFilter.Artists,
                labelRes = R.string.music_nav_artists,
                iconRes = R.drawable.ic_microphone,
                gradientStart = Color(0xFFE2AE95),
                gradientEnd = Color(0xFF965243),
            ),
            LibraryShortcut(
                filter = LibraryFilter.Albums,
                labelRes = R.string.music_nav_albums,
                iconRes = R.drawable.ic_disc,
                gradientStart = Color(0xFFA4C4D6),
                gradientEnd = Color(0xFF50787A),
            ),
            LibraryShortcut(
                filter = LibraryFilter.Tracks,
                labelRes = R.string.music_nav_tracks,
                iconRes = R.drawable.ic_file_music,
                gradientStart = Color(0xFFAED1AE),
                gradientEnd = Color(0xFF4F7E70),
            ),
            LibraryShortcut(
                filter = LibraryFilter.Genres,
                labelRes = R.string.music_nav_genres,
                iconRes = R.drawable.ic_genre,
                gradientStart = Color(0xFFD4A5C9),
                gradientEnd = Color(0xFF7B4F8C),
            ),
        )
    }

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.music_action_browse),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 14.dp, bottom = 12.dp),
        )
        LazyRow(
            modifier = Modifier.padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(shortcuts, key = { it.filter }) { shortcut ->
                LibraryShortcutCard(
                    label = stringResource(shortcut.labelRes),
                    iconRes = shortcut.iconRes,
                    gradientStart = shortcut.gradientStart,
                    gradientEnd = shortcut.gradientEnd,
                    cardWidth = cardWidth,
                    onClick = { onBrowse(shortcut.filter) },
                )
            }
        }
    }
}

@Composable
private fun LibraryShortcutCard(
    label: String,
    iconRes: Int,
    gradientStart: Color,
    gradientEnd: Color,
    cardWidth: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.width(cardWidth).aspectRatio(CardDimensions.ASPECT_RATIO_LANDSCAPE),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        onClick = onClick,
    ) {
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .background(Brush.linearGradient(colors = listOf(gradientStart, gradientEnd)))
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.25f),
                modifier =
                    Modifier.fillMaxHeight(0.85f)
                        .aspectRatio(1f)
                        .align(Alignment.BottomEnd)
                        .offset(x = 20.dp, y = 20.dp)
                        .rotate(-15f),
            )

            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .fillMaxHeight(0.5f)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                            )
                        )
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = label,
                    style =
                        MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp,
                        ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MadeForYouCarousel(
    title: String,
    items: List<MadeForYouItem>,
    onPlayMix: (List<AfinityTrack>) -> Unit,
    onAlbumClick: (AfinityAlbum) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 14.dp, bottom = 12.dp),
        )

        val configuration = LocalConfiguration.current
        val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val preferredItemWidth =
            (configuration.screenWidthDp * if (isLandscape) 0.58f else 0.82f).dp
        val carouselHeight = if (isLandscape) (configuration.screenHeightDp * 0.45f).dp else 220.dp
        val state = rememberCarouselState { items.size }

        HorizontalMultiBrowseCarousel(
            state = state,
            preferredItemWidth = preferredItemWidth,
            modifier = Modifier.height(carouselHeight).padding(horizontal = 16.dp),
            itemSpacing = 8.dp,
        ) { index ->
            Box(
                modifier =
                    Modifier.height(carouselHeight)
                        .maskClip(MaterialTheme.shapes.extraLarge)
                        .clip(MaterialTheme.shapes.extraLarge)
            ) {
                when (val item = items[index]) {
                    is MadeForYouItem.TrackMix -> {
                        MusicTracksCard(
                            title = item.title,
                            subtitle = item.subtitle,
                            tracks = item.tracks,
                            onPlay = { onPlayMix(item.tracks) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    is MadeForYouItem.AlbumItem -> {
                        HeroDiscoverCard(
                            album = item.album,
                            onAlbumClick = { onAlbumClick(item.album) },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
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
            modifier.height(220.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onPlay)
    ) {
        AsyncImage(
            imageUrl = imageUrl,
            contentDescription = title,
            blurHash = blurHash,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            targetWidth = LocalConfiguration.current.screenWidthDp.dp,
            targetHeight = 220.dp,
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
                Column(
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = title,
                        style =
                            MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                        painter = painterResource(R.drawable.ic_player_play_filled),
                        contentDescription = stringResource(R.string.cd_play),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroDiscoverCard(
    album: AfinityAlbum,
    onAlbumClick: (AfinityAlbum) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier.height(220.dp).clip(RoundedCornerShape(16.dp)).clickable {
                onAlbumClick(album)
            }
    ) {
        AsyncImage(
            imageUrl = album.images.primary?.toString(),
            contentDescription = album.name,
            blurHash = album.images.primaryImageBlurHash,
            targetWidth = 300.dp,
            targetHeight = 220.dp,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier =
                Modifier.fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                            startY = 100f,
                        )
                    )
        )
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(
                text = album.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = album.artist ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun MusicArtistsRow(
    title: String,
    artists: List<AfinityArtist>,
    onArtistClick: (AfinityArtist) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp,
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val cardSize = if (isLandscape) 170.dp else 140.dp
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
                    size = cardSize,
                )
            }
        }
    }
}

@Composable
fun CompactTrackGridSection(
    title: String,
    tracks: List<AfinityTrack>,
    onTrackClick: (AfinityTrack) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val rowsCount = if (tracks.size >= 6) 3 else if (tracks.size >= 3) 2 else 1
    val rowHeight = if (isLandscape) 70 else 64
    val itemWidth = if (isLandscape) 320.dp else 280.dp
    val gridHeight = (rowsCount * rowHeight + (rowsCount - 1) * 12).dp

    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(start = 14.dp, bottom = 12.dp),
        )
        LazyHorizontalGrid(
            rows = GridCells.Fixed(rowsCount),
            modifier = Modifier.height(gridHeight).padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(tracks, key = { it.id }) { track ->
                Row(
                    modifier =
                        Modifier.width(itemWidth).clip(RoundedCornerShape(8.dp)).clickable {
                            onTrackClick(track)
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        imageUrl = track.images.primary?.toString(),
                        contentDescription = null,
                        blurHash = track.images.primaryImageBlurHash,
                        targetWidth = 56.dp,
                        targetHeight = 56.dp,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.name,
                            style =
                                MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = track.artist ?: track.artists.firstOrNull() ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
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
        Intent(context, AudioService::class.java).setAction(AudioService.ACTION_ENGINE_MUSIC)
    )
}
