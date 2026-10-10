package com.makd.afinity.ui.downloads

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.makd.afinity.R
import com.makd.afinity.data.models.audiobookshelf.AbsDownloadInfo
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.music.AfinityAlbum
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.data.models.music.MusicFilterOptions
import com.makd.afinity.data.models.music.MusicFilters
import com.makd.afinity.navigation.Destination
import com.makd.afinity.navigation.LocalPlayerOffset
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.AppBarProfile
import com.makd.afinity.ui.components.FullScreenEmpty
import com.makd.afinity.ui.components.MediaItemCard
import com.makd.afinity.ui.home.HomeViewModel
import com.makd.afinity.ui.home.components.SquareMediaTile
import com.makd.afinity.ui.main.MainUiState
import com.makd.afinity.ui.music.components.MusicAlbumCard
import com.makd.afinity.ui.music.components.SortFilterHeaderActions
import com.makd.afinity.ui.music.components.musicFiltersSummary
import com.makd.afinity.ui.music.components.musicSortSummary
import com.makd.afinity.ui.music.components.musicTotalRuntimeLabel
import com.makd.afinity.ui.music.library.MusicFilterBottomSheet
import com.makd.afinity.ui.music.library.MusicSortDialog
import com.makd.afinity.ui.music.library.MusicSortField
import com.makd.afinity.ui.music.library.TracksList
import com.makd.afinity.ui.music.library.startMusicService
import com.makd.afinity.ui.music.player.MusicPlayerViewModel
import com.makd.afinity.ui.theme.CardDimensions
import com.makd.afinity.ui.theme.CardDimensions.gridMinSize
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.flow.flowOf

private const val TICKS_PER_SECOND = 10_000_000L
private const val DAYS_PER_YEAR = 366L

enum class DownloadedCategory(
    @StringRes val titleRes: Int,
    val sortFields: List<MusicSortField>,
    val canFilter: Boolean,
) {
    MOVIES(
        R.string.home_downloaded_movies,
        listOf(
            MusicSortField.Name,
            MusicSortField.ReleaseDate,
            MusicSortField.DateAdded,
            MusicSortField.CommunityRating,
            MusicSortField.Runtime,
        ),
        true,
    ),
    SHOWS(
        R.string.home_downloaded_shows,
        listOf(
            MusicSortField.Name,
            MusicSortField.ReleaseDate,
            MusicSortField.DateAdded,
            MusicSortField.CommunityRating,
        ),
        true,
    ),
    AUDIOBOOKS(
        R.string.home_downloaded_audiobooks,
        listOf(MusicSortField.Name, MusicSortField.DateAdded, MusicSortField.Runtime),
        false,
    ),
    PODCASTS(
        R.string.home_downloaded_episodes,
        listOf(
            MusicSortField.Name,
            MusicSortField.ReleaseDate,
            MusicSortField.DateAdded,
            MusicSortField.Runtime,
        ),
        false,
    ),
    ALBUMS(
        R.string.home_downloaded_albums,
        listOf(
            MusicSortField.Name,
            MusicSortField.AlbumArtist,
            MusicSortField.ReleaseDate,
            MusicSortField.Runtime,
        ),
        true,
    ),
    TRACKS(
        R.string.home_downloaded_tracks,
        listOf(
            MusicSortField.Name,
            MusicSortField.Album,
            MusicSortField.Artist,
            MusicSortField.ReleaseDate,
            MusicSortField.PlayCount,
            MusicSortField.Runtime,
        ),
        true,
    ),
}

private data class SortKeys(
    val name: String,
    val creator: String? = null,
    val album: String? = null,
    val released: Long? = null,
    val added: Long? = null,
    val runtime: Long = 0L,
    val rating: Float? = null,
    val playCount: Int? = null,
)

private data class FilterKeys(
    val favorite: Boolean,
    val played: Boolean,
    val genres: List<String>,
    val year: Int?,
)

private fun comparatorFor(field: MusicSortField): Comparator<SortKeys> {
    val byName = compareBy<SortKeys, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
    val primary: Comparator<SortKeys> =
        when (field) {
            MusicSortField.Album ->
                compareBy<SortKeys, String>(String.CASE_INSENSITIVE_ORDER) { it.album.orEmpty() }

            MusicSortField.AlbumArtist,
            MusicSortField.Artist ->
                compareBy<SortKeys, String>(String.CASE_INSENSITIVE_ORDER) { it.creator.orEmpty() }

            MusicSortField.ReleaseDate -> compareBy<SortKeys> { it.released ?: 0L }
            MusicSortField.DateAdded -> compareBy<SortKeys> { it.added ?: 0L }
            MusicSortField.Runtime -> compareBy<SortKeys> { it.runtime }
            MusicSortField.CommunityRating -> compareBy<SortKeys> { it.rating ?: 0f }
            MusicSortField.PlayCount -> compareBy<SortKeys> { it.playCount ?: 0 }
            else -> return byName
        }
    return primary.then(byName)
}

private fun FilterKeys.matches(filters: MusicFilters): Boolean {
    if (filters.favoritesOnly && !favorite) return false
    if (filters.playedOnly && !played) return false
    if (filters.unplayedOnly && played) return false
    if (filters.genres.isNotEmpty() && genres.none { it in filters.genres }) return false
    if (filters.years.isNotEmpty() && year !in filters.years) return false
    return true
}

private fun <T> List<T>.sortedAndFiltered(
    field: MusicSortField,
    descending: Boolean,
    filters: MusicFilters,
    sortKeys: (T) -> SortKeys,
    filterKeys: ((T) -> FilterKeys)?,
): List<T> {
    val filtered =
        if (filterKeys == null || !filters.isActive) this
        else filter { filterKeys(it).matches(filters) }
    val comparator = comparatorFor(field)
    val sorted =
        filtered
            .map { it to sortKeys(it) }
            .sortedWith { a, b -> comparator.compare(a.second, b.second) }
            .map { it.first }
    return if (descending) sorted.reversed() else sorted
}

private fun <T> List<T>.filterOptions(filterKeys: (T) -> FilterKeys): MusicFilterOptions {
    val keys = map(filterKeys)
    return MusicFilterOptions(
        genres = keys.flatMap { it.genres }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER),
        years = keys.mapNotNull { it.year }.distinct().sortedDescending(),
    )
}

private fun releasedFrom(year: Int?): Long? = year?.let { it * DAYS_PER_YEAR }

private fun AfinityMovie.sortKeys() =
    SortKeys(
        name = name,
        released = premiereDate?.toLocalDate()?.toEpochDay() ?: releasedFrom(productionYear),
        added = dateCreated?.toEpochSecond(ZoneOffset.UTC),
        runtime = runtimeTicks,
        rating = communityRating,
    )

private fun AfinityMovie.filterKeys() =
    FilterKeys(favorite = favorite, played = played, genres = genres, year = productionYear)

private fun AfinityShow.sortKeys() =
    SortKeys(
        name = name,
        released = premiereDate?.toLocalDate()?.toEpochDay() ?: releasedFrom(productionYear),
        added = dateCreated?.toEpochSecond(ZoneOffset.UTC),
        runtime = runtimeTicks,
        rating = communityRating,
    )

private fun AfinityShow.filterKeys() =
    FilterKeys(favorite = favorite, played = played, genres = genres, year = productionYear)

private fun AbsDownloadInfo.sortKeys() =
    SortKeys(
        name = title,
        creator = authorName,
        released = publishedAt,
        added = createdAt,
        runtime = (duration * TICKS_PER_SECOND).toLong(),
    )

private fun AfinityAlbum.sortKeys() =
    SortKeys(
        name = name,
        creator = artist,
        released = releasedFrom(productionYear),
        runtime = runtimeTicks,
    )

private fun AfinityAlbum.filterKeys() =
    FilterKeys(favorite = favorite, played = played, genres = genres, year = productionYear)

private fun AfinityTrack.sortKeys() =
    SortKeys(
        name = name,
        creator = artist ?: artists.firstOrNull(),
        album = album,
        released = releasedFrom(productionYear),
        runtime = runtimeTicks,
        playCount = playCount,
    )

private fun AfinityTrack.filterKeys() =
    FilterKeys(favorite = favorite, played = played, genres = emptyList(), year = productionYear)

@Composable
fun DownloadedCategoryScreen(
    category: DownloadedCategory,
    mainUiState: MainUiState,
    homeViewModel: HomeViewModel,
    onItemClick: (AfinityItem) -> Unit,
    onAbsItemClick: (String) -> Unit,
    navController: NavController,
    widthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier,
    playerViewModel: MusicPlayerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val playbackState by playerViewModel.playbackState.collectAsStateWithLifecycle()
    val playerOffset = LocalPlayerOffset.current

    var sortField by rememberSaveable { mutableStateOf(MusicSortField.Name) }
    var sortDescending by rememberSaveable { mutableStateOf(false) }
    var filters by remember { mutableStateOf(MusicFilters()) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }

    val movies =
        remember(uiState.downloadedMovies, sortField, sortDescending, filters) {
            uiState.downloadedMovies.sortedAndFiltered(
                sortField,
                sortDescending,
                filters,
                AfinityMovie::sortKeys,
                AfinityMovie::filterKeys,
            )
        }
    val shows =
        remember(uiState.downloadedShows, sortField, sortDescending, filters) {
            uiState.downloadedShows.sortedAndFiltered(
                sortField,
                sortDescending,
                filters,
                AfinityShow::sortKeys,
                AfinityShow::filterKeys,
            )
        }
    val audiobooks =
        remember(uiState.downloadedAudiobooks, sortField, sortDescending) {
            uiState.downloadedAudiobooks.sortedAndFiltered(
                sortField,
                sortDescending,
                filters,
                AbsDownloadInfo::sortKeys,
                null,
            )
        }
    val podcastEpisodes =
        remember(uiState.downloadedPodcastEpisodes, sortField, sortDescending) {
            uiState.downloadedPodcastEpisodes.sortedAndFiltered(
                sortField,
                sortDescending,
                filters,
                AbsDownloadInfo::sortKeys,
                null,
            )
        }
    val albums =
        remember(uiState.downloadedMusicAlbums, sortField, sortDescending, filters) {
            uiState.downloadedMusicAlbums.sortedAndFiltered(
                sortField,
                sortDescending,
                filters,
                AfinityAlbum::sortKeys,
                AfinityAlbum::filterKeys,
            )
        }
    val tracks =
        remember(uiState.downloadedMusicTracks, sortField, sortDescending, filters) {
            uiState.downloadedMusicTracks.sortedAndFiltered(
                sortField,
                sortDescending,
                filters,
                AfinityTrack::sortKeys,
                AfinityTrack::filterKeys,
            )
        }

    val filterOptions =
        remember(
            category,
            uiState.downloadedMovies,
            uiState.downloadedShows,
            uiState.downloadedMusicAlbums,
            uiState.downloadedMusicTracks,
        ) {
            when (category) {
                DownloadedCategory.MOVIES ->
                    uiState.downloadedMovies.filterOptions(AfinityMovie::filterKeys)

                DownloadedCategory.SHOWS ->
                    uiState.downloadedShows.filterOptions(AfinityShow::filterKeys)

                DownloadedCategory.ALBUMS ->
                    uiState.downloadedMusicAlbums.filterOptions(AfinityAlbum::filterKeys)

                DownloadedCategory.TRACKS ->
                    uiState.downloadedMusicTracks.filterOptions(AfinityTrack::filterKeys)

                else -> MusicFilterOptions()
            }
        }

    val totalCount =
        when (category) {
            DownloadedCategory.MOVIES -> uiState.downloadedMovies.size
            DownloadedCategory.SHOWS -> uiState.downloadedShows.size
            DownloadedCategory.AUDIOBOOKS -> uiState.downloadedAudiobooks.size
            DownloadedCategory.PODCASTS -> uiState.downloadedPodcastEpisodes.size
            DownloadedCategory.ALBUMS -> uiState.downloadedMusicAlbums.size
            DownloadedCategory.TRACKS -> uiState.downloadedMusicTracks.size
        }
    val visibleCount =
        when (category) {
            DownloadedCategory.MOVIES -> movies.size
            DownloadedCategory.SHOWS -> shows.size
            DownloadedCategory.AUDIOBOOKS -> audiobooks.size
            DownloadedCategory.PODCASTS -> podcastEpisodes.size
            DownloadedCategory.ALBUMS -> albums.size
            DownloadedCategory.TRACKS -> tracks.size
        }

    val gridPadding =
        PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 80.dp + playerOffset)

    Scaffold(
        topBar = {
            AfinityTopAppBar(
                title = {
                    Text(
                        text = stringResource(category.titleRes),
                        style =
                            MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
                backgroundOpacity = { 1f },
                profile =
                    AppBarProfile(
                        onClick = { navController.navigate(Destination.createSettingsRoute()) },
                        name = mainUiState.userName,
                        imageUrl = mainUiState.userProfileImageUrl,
                    ),
            )
        },
        floatingActionButton = {
            if (category != DownloadedCategory.TRACKS && totalCount > 0) {
                Box(modifier = Modifier.padding(bottom = playerOffset)) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.End,
                    ) {
                        if (category.canFilter) {
                            FloatingActionButton(onClick = { showFilterSheet = true }) {
                                Icon(
                                    painter =
                                        painterResource(
                                            id =
                                                if (filters.isActive) R.drawable.ic_filter_active
                                                else R.drawable.ic_filter
                                        ),
                                    contentDescription = stringResource(R.string.cd_filter_fab),
                                )
                            }
                        }
                        FloatingActionButton(onClick = { showSortDialog = true }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_arrows_sort),
                                contentDescription = stringResource(R.string.cd_sort_fab),
                            )
                        }
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        val contentModifier = Modifier.fillMaxSize().padding(innerPadding)

        when {
            totalCount == 0 ->
                FullScreenEmpty(
                    message = stringResource(R.string.library_empty_title),
                    modifier = Modifier.padding(innerPadding),
                )

            visibleCount == 0 ->
                FullScreenEmpty(
                    title = stringResource(R.string.library_empty_title),
                    message = stringResource(R.string.filter_empty_filtered),
                    actionText = stringResource(R.string.action_clear_filter),
                    onActionClick = { filters = MusicFilters() },
                    modifier = Modifier.padding(innerPadding),
                )

            category == DownloadedCategory.MOVIES || category == DownloadedCategory.SHOWS -> {
                val items: List<AfinityItem> =
                    if (category == DownloadedCategory.MOVIES) movies else shows
                DownloadedGrid(
                    columns = CardDimensions.gridCells(widthSizeClass),
                    contentPadding = gridPadding,
                    items = items,
                    key = { it.id },
                    modifier = contentModifier,
                ) { item ->
                    MediaItemCard(
                        item = item,
                        onClick = { onItemClick(item) },
                        cardWidth = widthSizeClass.gridMinSize,
                        modifier = Modifier.fillMaxWidth(),
                        isUnavailable = item.id in uiState.unavailableDownloadIds,
                    )
                }
            }

            category == DownloadedCategory.AUDIOBOOKS ||
                category == DownloadedCategory.PODCASTS -> {
                val items =
                    if (category == DownloadedCategory.AUDIOBOOKS) audiobooks else podcastEpisodes
                DownloadedGrid(
                    columns = CardDimensions.audiobookGridCells,
                    contentPadding = gridPadding,
                    items = items,
                    key = { it.id },
                    modifier = contentModifier,
                ) { download ->
                    SquareMediaTile(
                        imageUrl = download.coverUrl,
                        contentDescription = download.title,
                        title = download.title,
                        subtitle = download.authorName,
                        onClick = { onAbsItemClick(download.libraryItemId) },
                    )
                }
            }

            category == DownloadedCategory.ALBUMS ->
                DownloadedGrid(
                    columns = CardDimensions.musicGridCells,
                    contentPadding = gridPadding,
                    items = albums,
                    key = { it.id },
                    modifier = contentModifier,
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

            else -> {
                val pagingFlow =
                    remember(tracks) {
                        val loaded = LoadState.NotLoading(endOfPaginationReached = true)
                        flowOf(
                            PagingData.from(
                                tracks,
                                sourceLoadStates =
                                    LoadStates(
                                        refresh = loaded,
                                        prepend = loaded,
                                        append = loaded,
                                    ),
                            )
                        )
                    }
                val lazyTracks = pagingFlow.collectAsLazyPagingItems()
                val durationLabel = musicTotalRuntimeLabel(tracks.sumOf { it.runtimeTicks })
                val headerSubtitle =
                    listOfNotNull(
                            durationLabel,
                            musicSortSummary(sortField, sortDescending),
                            musicFiltersSummary(filters),
                        )
                        .joinToString(" · ")

                TracksList(
                    listState = rememberLazyListState(),
                    tracks = lazyTracks,
                    favoriteOverrides = emptyMap<UUID, Boolean>(),
                    currentTrackId = playbackState.currentTrack?.id?.toString(),
                    headerTitle =
                        pluralStringResource(
                            R.plurals.download_count_tracks,
                            tracks.size,
                            tracks.size,
                        ),
                    headerSubtitle = headerSubtitle,
                    headerActions = {
                        SortFilterHeaderActions(
                            onSortClick = { showSortDialog = true },
                            onFilterClick = { showFilterSheet = true },
                            filterActive = filters.isActive,
                        )
                    },
                    onPlayAll = {
                        startMusicService(context)
                        playerViewModel.playQueue(tracks, 0)
                    },
                    onShuffleAll = {
                        startMusicService(context)
                        playerViewModel.playQueue(tracks.shuffled(), 0)
                    },
                    onTrackClick = { _, queue, index ->
                        startMusicService(context)
                        playerViewModel.playQueue(queue, index)
                        navController.navigate(Destination.MUSIC_PLAYER_ROUTE)
                    },
                    onInstantMix = null,
                    onStartRadio = null,
                    onAddNext = { track -> playerViewModel.addNext(listOf(track)) },
                    onAddLast = { track -> playerViewModel.addLast(listOf(track)) },
                    onFavorite = null,
                    onAddToPlaylist = null,
                    modifier = contentModifier,
                )
            }
        }
    }

    if (showSortDialog) {
        MusicSortDialog(
            fields = category.sortFields,
            currentField = sortField,
            currentDescending = sortDescending,
            onDismiss = { showSortDialog = false },
            onSortSelected = { field, descending ->
                sortField = field
                sortDescending = descending
            },
        )
    }

    if (showFilterSheet) {
        MusicFilterBottomSheet(
            filters = filters,
            options = filterOptions,
            onApply = { filters = it },
            onDismiss = { showFilterSheet = false },
        )
    }
}

@Composable
private fun <T> DownloadedGrid(
    columns: GridCells,
    contentPadding: PaddingValues,
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    itemContent: @Composable (T) -> Unit,
) {
    LazyVerticalGrid(
        columns = columns,
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(items = items, key = { key(it) }) { item -> itemContent(item) }
    }
}
