package com.makd.afinity.ui.music.library

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import com.makd.afinity.R
import com.makd.afinity.data.manager.AdminChangeBroadcaster
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.download.DownloadStatus
import com.makd.afinity.data.models.music.AfinityAlbum
import com.makd.afinity.data.models.music.AfinityArtist
import com.makd.afinity.data.models.music.AfinityMusicGenre
import com.makd.afinity.data.models.music.AfinityPlaylist
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.data.models.music.MusicBrowsePrefs
import com.makd.afinity.data.models.music.MusicFilterOptions
import com.makd.afinity.data.models.music.MusicFilters
import com.makd.afinity.data.paging.MusicAlbumsPagingSource
import com.makd.afinity.data.paging.MusicArtistsPagingSource
import com.makd.afinity.data.paging.MusicGenresPagingSource
import com.makd.afinity.data.paging.MusicTracksPagingSource
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.download.DownloadRepository
import com.makd.afinity.data.repository.music.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

enum class MusicSortField(@StringRes val labelRes: Int, val sortBy: ItemSortBy) {
    Name(R.string.music_sort_field_name, ItemSortBy.SORT_NAME),
    Album(R.string.music_sort_field_album, ItemSortBy.ALBUM),
    AlbumArtist(R.string.music_sort_field_album_artist, ItemSortBy.ALBUM_ARTIST),
    Artist(R.string.music_sort_field_artist, ItemSortBy.ARTIST),
    CommunityRating(R.string.music_sort_field_community_rating, ItemSortBy.COMMUNITY_RATING),
    CriticsRating(R.string.music_sort_field_critics_rating, ItemSortBy.CRITIC_RATING),
    DateAdded(R.string.music_sort_field_date_added, ItemSortBy.DATE_CREATED),
    DatePlayed(R.string.music_sort_field_date_played, ItemSortBy.DATE_PLAYED),
    PlayCount(R.string.music_sort_field_play_count, ItemSortBy.PLAY_COUNT),
    ReleaseDate(R.string.music_sort_field_release_date, ItemSortBy.PREMIERE_DATE),
    Runtime(R.string.music_sort_field_runtime, ItemSortBy.RUNTIME),
    Random(R.string.music_sort_field_random, ItemSortBy.RANDOM),
}

val ALBUM_SORT_FIELDS =
    listOf(
        MusicSortField.Name,
        MusicSortField.Album,
        MusicSortField.AlbumArtist,
        MusicSortField.CommunityRating,
        MusicSortField.CriticsRating,
        MusicSortField.DateAdded,
        MusicSortField.ReleaseDate,
        MusicSortField.Random,
    )

val TRACK_SORT_FIELDS =
    listOf(
        MusicSortField.Name,
        MusicSortField.Album,
        MusicSortField.AlbumArtist,
        MusicSortField.Artist,
        MusicSortField.DateAdded,
        MusicSortField.DatePlayed,
        MusicSortField.PlayCount,
        MusicSortField.ReleaseDate,
        MusicSortField.Runtime,
        MusicSortField.Random,
    )

data class MadeForYouSnapshot(
    val randomTracks: List<AfinityTrack>,
    val radioSections: List<Pair<String, List<AfinityTrack>>>,
    val songsByGenreSections: List<Pair<String, List<AfinityTrack>>>,
    val randomAlbums: List<AfinityAlbum>,
)

data class MusicLibraryUiState(
    val playlists: List<AfinityPlaylist> = emptyList(),
    val isLoadingPlaylists: Boolean = false,
    val error: String? = null,
    val recentlyPlayedTracks: List<AfinityTrack> = emptyList(),
    val recentlyPlayedAlbums: List<AfinityAlbum> = emptyList(),
    val latestAlbums: List<AfinityAlbum> = emptyList(),
    val mostPlayedAlbums: List<AfinityAlbum> = emptyList(),
    val favoriteAlbums: List<AfinityAlbum> = emptyList(),
    val moreFromArtistSections: List<Pair<AfinityArtist, List<AfinityAlbum>>> = emptyList(),
    val musicGenreSections: List<Pair<String, List<AfinityAlbum>>> = emptyList(),
    val randomAlbums: List<AfinityAlbum> = emptyList(),
    val randomArtists: List<AfinityArtist> = emptyList(),
    val songsByGenreSections: List<Pair<String, List<AfinityTrack>>> = emptyList(),
    val albumsByDecade: Pair<Int, List<AfinityAlbum>>? = null,
    val randomTracks: List<AfinityTrack> = emptyList(),
    val topRatedAlbums: List<AfinityAlbum> = emptyList(),
    val homePlaylists: List<AfinityPlaylist> = emptyList(),
    val isLoadingHome: Boolean = false,
    val topArtists: List<AfinityArtist> = emptyList(),
    val favoriteArtists: List<AfinityArtist> = emptyList(),
    val topTracksSections: List<Pair<String, List<AfinityTrack>>> = emptyList(),
    val newGenreReleases: List<Pair<String, List<AfinityAlbum>>> = emptyList(),
    val radioSections: List<Pair<String, List<AfinityTrack>>> = emptyList(),
    val homeRowOrder: List<Int> = generateHomeRowOrder(),
    val madeForYouSnapshot: MadeForYouSnapshot? = null,
    val trackFavoriteOverrides: Map<UUID, Boolean> = emptyMap(),
)

private fun generateHomeRowOrder(): List<Int> {
    val buckets =
        listOf(
                (0 until 9).toList(),
                (9 until 14).toList(),
                (14 until 17).toList(),
                (17 until 22).toList(),
                (22 until 25).toList(),
                (25 until 27).toList(),
                (27 until 30).toList(),
            )
            .map { it.shuffled().toMutableList() }
            .shuffled()

    return buildList {
        while (buckets.any { it.isNotEmpty() }) {
            buckets.forEach { bucket -> if (bucket.isNotEmpty()) add(bucket.removeFirst()) }
        }
    }
}

private const val PAGE_SIZE = 50
private const val PREFETCH_DISTANCE = 20
private const val MAX_PLAY_ALL_TRACKS = 5000

private data class AlbumQuery(
    val field: MusicSortField,
    val descending: Boolean,
    val filters: MusicFilters,
    val letter: String?,
)

@HiltViewModel
class MusicLibraryViewModel
@Inject
constructor(
    private val musicRepository: MusicRepository,
    private val appDataRepository: AppDataRepository,
    private val downloadRepository: DownloadRepository,
    private val preferencesRepository: PreferencesRepository,
    private val adminChangeBroadcaster: AdminChangeBroadcaster,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val libraryId: UUID = UUID.fromString(savedStateHandle.get<String>("libraryId")!!)
    val libraryName: String = savedStateHandle.get<String>("libraryName") ?: ""

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val browsePrefsKey = "music_browse_prefs_$libraryId"

    private val _refreshTrigger = MutableStateFlow(0)

    val userProfileImageUrl: StateFlow<String?> = appDataRepository.userProfileImageUrl

    private val _uiState = MutableStateFlow(MusicLibraryUiState())
    val uiState: StateFlow<MusicLibraryUiState> = _uiState.asStateFlow()

    private val _trackDownloadInfos = MutableStateFlow<Map<UUID, DownloadInfo>>(emptyMap())
    val trackDownloadInfos: StateFlow<Map<UUID, DownloadInfo>> = _trackDownloadInfos.asStateFlow()

    private val _trackSortField = MutableStateFlow(MusicSortField.Name)
    val trackSortField: StateFlow<MusicSortField> = _trackSortField.asStateFlow()

    private val _trackSortDescending = MutableStateFlow(false)
    val trackSortDescending: StateFlow<Boolean> = _trackSortDescending.asStateFlow()

    private val _albumSortField = MutableStateFlow(MusicSortField.Name)
    val albumSortField: StateFlow<MusicSortField> = _albumSortField.asStateFlow()

    private val _albumSortDescending = MutableStateFlow(false)
    val albumSortDescending: StateFlow<Boolean> = _albumSortDescending.asStateFlow()

    private val _albumFilterOptions = MutableStateFlow(MusicFilterOptions())
    val albumFilterOptions: StateFlow<MusicFilterOptions> = _albumFilterOptions.asStateFlow()

    private val _albumLetterFilter = MutableStateFlow<String?>(null)
    val albumLetterFilter: StateFlow<String?> = _albumLetterFilter.asStateFlow()

    private val _artistLetterFilter = MutableStateFlow<String?>(null)
    val artistLetterFilter: StateFlow<String?> = _artistLetterFilter.asStateFlow()

    private val _trackFilters = MutableStateFlow(MusicFilters())
    val trackFilters: StateFlow<MusicFilters> = _trackFilters.asStateFlow()

    private val _albumFilters = MutableStateFlow(MusicFilters())
    val albumFilters: StateFlow<MusicFilters> = _albumFilters.asStateFlow()

    private val _artistFilters = MutableStateFlow(MusicFilters())
    val artistFilters: StateFlow<MusicFilters> = _artistFilters.asStateFlow()

    val genresPagingFlow: Flow<PagingData<AfinityMusicGenre>> =
        Pager(
                PagingConfig(
                    pageSize = MusicGenresPagingSource.PAGE_SIZE,
                    prefetchDistance = PREFETCH_DISTANCE,
                )
            ) {
                MusicGenresPagingSource(musicRepository = musicRepository, libraryId = libraryId)
            }
            .flow
            .cachedIn(viewModelScope)

    private val trackFavoriteOverridesFlow: Flow<Map<UUID, Boolean>> =
        uiState.map { it.trackFavoriteOverrides }.distinctUntilChanged()

    @OptIn(ExperimentalCoroutinesApi::class)
    val tracksPagingFlow: Flow<PagingData<AfinityTrack>> =
        combine(_trackSortField, _trackSortDescending, _trackFilters, _refreshTrigger) {
                field,
                descending,
                filters,
                _ ->
                Triple(field, descending, filters)
            }
            .flatMapLatest { (field, descending, filters) ->
                Pager(PagingConfig(pageSize = PAGE_SIZE, prefetchDistance = PREFETCH_DISTANCE)) {
                        MusicTracksPagingSource(
                            musicRepository = musicRepository,
                            libraryId = libraryId,
                            sortBy = field.sortBy,
                            sortOrder =
                                if (descending) SortOrder.DESCENDING else SortOrder.ASCENDING,
                            filters = filters,
                        )
                    }
                    .flow
            }
            .cachedIn(viewModelScope)
            .combine(trackFavoriteOverridesFlow) { pagingData, overrides ->
                if (!_trackFilters.value.favoritesOnly) pagingData
                else pagingData.filter { track -> overrides[track.id] ?: track.favorite }
            }

    @OptIn(ExperimentalCoroutinesApi::class)
    val albumsPagingFlow: Flow<PagingData<AfinityAlbum>> =
        combine(
                _albumSortField,
                _albumSortDescending,
                _albumFilters,
                _albumLetterFilter,
                _refreshTrigger,
            ) { field, descending, filters, letter, _ ->
                AlbumQuery(field, descending, filters, letter)
            }
            .flatMapLatest { query ->
                Pager(PagingConfig(pageSize = PAGE_SIZE, prefetchDistance = PREFETCH_DISTANCE)) {
                        MusicAlbumsPagingSource(
                            musicRepository = musicRepository,
                            libraryId = libraryId,
                            sortBy = query.field.sortBy,
                            sortOrder =
                                if (query.descending) SortOrder.DESCENDING else SortOrder.ASCENDING,
                            filters = query.filters,
                            nameStartsWith = query.letter?.let { if (it == "#") "0" else it },
                        )
                    }
                    .flow
            }
            .cachedIn(viewModelScope)

    @OptIn(ExperimentalCoroutinesApi::class)
    val artistsPagingFlow: Flow<PagingData<AfinityArtist>> =
        combine(_artistFilters, _artistLetterFilter, _refreshTrigger) { filters, letter, _ ->
                Pair(filters, letter)
            }
            .flatMapLatest { (filters, letter) ->
                Pager(PagingConfig(pageSize = PAGE_SIZE, prefetchDistance = PREFETCH_DISTANCE)) {
                        MusicArtistsPagingSource(
                            musicRepository = musicRepository,
                            libraryId = libraryId,
                            sortBy = ItemSortBy.SORT_NAME,
                            sortOrder = SortOrder.ASCENDING,
                            filters = filters,
                            nameStartsWith = letter?.let { if (it == "#") "0" else it },
                        )
                    }
                    .flow
            }
            .cachedIn(viewModelScope)

    init {
        loadPersistedPrefs()
        observePlaylists()
        viewModelScope.launch { loadMusicHomeSections() }
        observeDownloads()
        loadAlbumFilterOptions()
        viewModelScope.launch {
            adminChangeBroadcaster.itemChanged.collect { _refreshTrigger.value++ }
        }
    }

    private fun loadAlbumFilterOptions() {
        viewModelScope.launch {
            runCatching {
                musicRepository.getMusicFilterOptions(libraryId, BaseItemKind.MUSIC_ALBUM)
            }
                .onSuccess { options -> _albumFilterOptions.value = options }
        }
    }

    private fun observePlaylists() {
        viewModelScope.launch {
            musicRepository.getPlaylistsFlow().collect { playlists ->
                _uiState.update { it.copy(playlists = playlists) }
            }
        }
    }

    private fun observeDownloads() {
        viewModelScope.launch {
            downloadRepository.getAllDownloadsFlow().collect { allDownloads ->
                _trackDownloadInfos.value =
                    allDownloads
                        .filter { it.itemType == "Audio" && it.status == DownloadStatus.COMPLETED }
                        .associateBy { it.itemId }
            }
        }
    }

    fun setTrackSort(field: MusicSortField, descending: Boolean) {
        _trackSortField.value = field
        _trackSortDescending.value = descending
        persistBrowsePrefs()
    }

    suspend fun getAllFilteredTracks(): List<AfinityTrack> =
        musicRepository.getTracks(
            libraryId = libraryId,
            sortBy = _trackSortField.value.sortBy,
            sortOrder =
                if (_trackSortDescending.value) SortOrder.DESCENDING else SortOrder.ASCENDING,
            filters = _trackFilters.value,
            startIndex = 0,
            limit = MAX_PLAY_ALL_TRACKS,
        )

    fun setAlbumSort(field: MusicSortField, descending: Boolean) {
        _albumSortField.value = field
        _albumSortDescending.value = descending
        persistBrowsePrefs()
    }

    fun filterAlbumsByLetter(letter: String) {
        _albumLetterFilter.value = if (_albumLetterFilter.value == letter) null else letter
    }

    fun filterArtistsByLetter(letter: String) {
        _artistLetterFilter.value = if (_artistLetterFilter.value == letter) null else letter
    }

    fun setTrackFilters(filters: MusicFilters) {
        _trackFilters.value = filters
        persistBrowsePrefs()
    }

    fun setAlbumFilters(filters: MusicFilters) {
        _albumFilters.value = filters
        persistBrowsePrefs()
    }

    fun setArtistFilters(filters: MusicFilters) {
        _artistFilters.value = filters
        persistBrowsePrefs()
    }

    private fun parseSortField(name: String): MusicSortField = runCatching {
        MusicSortField.valueOf(name)
    }
        .getOrDefault(MusicSortField.Name)

    private fun loadPersistedPrefs() {
        viewModelScope.launch {
            val stored = preferencesRepository.getStringPreference(browsePrefsKey) ?: return@launch
            val prefs =
                runCatching { json.decodeFromString(MusicBrowsePrefs.serializer(), stored) }
                    .getOrNull() ?: return@launch
            _albumSortField.value = parseSortField(prefs.albumSortField)
            _albumSortDescending.value = prefs.albumSortDescending
            _albumFilters.value = prefs.albumFilters
            _artistFilters.value = prefs.artistFilters
            _trackSortField.value = parseSortField(prefs.trackSortField)
            _trackSortDescending.value = prefs.trackSortDescending
            _trackFilters.value = prefs.trackFilters
        }
    }

    private fun persistBrowsePrefs() {
        viewModelScope.launch {
            val prefs =
                MusicBrowsePrefs(
                    albumSortField = _albumSortField.value.name,
                    albumSortDescending = _albumSortDescending.value,
                    albumFilters = _albumFilters.value,
                    artistFilters = _artistFilters.value,
                    trackSortField = _trackSortField.value.name,
                    trackSortDescending = _trackSortDescending.value,
                    trackFilters = _trackFilters.value,
                )
            preferencesRepository.setStringPreference(
                browsePrefsKey,
                json.encodeToString(MusicBrowsePrefs.serializer(), prefs),
            )
        }
    }

    fun toggleTrackFavorite(track: AfinityTrack, currentFavorite: Boolean) {
        val newFavorite = !currentFavorite
        _uiState.update {
            it.copy(trackFavoriteOverrides = it.trackFavoriteOverrides + (track.id to newFavorite))
        }
        viewModelScope.launch {
            runCatching { musicRepository.setFavorite(track.id, newFavorite) }
                .onSuccess { appDataRepository.updateTrackFavoriteStatus(track, newFavorite) }
                .onFailure {
                    _uiState.update {
                        it.copy(trackFavoriteOverrides = it.trackFavoriteOverrides - track.id)
                    }
                }
        }
    }

    fun downloadTrack(trackId: UUID) {
        viewModelScope.launch {
            downloadRepository.startDownload(trackId, "").onFailure {
                Timber.e(it, "Failed to download track $trackId")
            }
        }
    }

    private fun updateMadeForYouSnapshot() {
        _uiState.update { state ->
            state.copy(
                madeForYouSnapshot =
                    MadeForYouSnapshot(
                        randomTracks = state.randomTracks,
                        radioSections = state.radioSections,
                        songsByGenreSections = state.songsByGenreSections,
                        randomAlbums = state.randomAlbums,
                    )
            )
        }
    }

    private suspend fun loadMusicHomeSections() {
        val hasMusicLibrary =
            appDataRepository.libraries.value.any { it.type == CollectionType.Music }
        if (!hasMusicLibrary) return

        _uiState.update { it.copy(isLoadingHome = true) }

        val seedRecentTracks: List<AfinityTrack>
        val seedRecentAlbums: List<AfinityAlbum>
        val seedGenres: List<AfinityMusicGenre>

        coroutineScope {
            val recentTracksJob = async {
                runCatching { musicRepository.getRecentlyPlayedTracks(limit = 20) }
                    .getOrDefault(emptyList())
            }
            val recentPlayedJob = async {
                runCatching { musicRepository.getRecentlyPlayedAlbums(limit = 15) }
                    .getOrDefault(emptyList())
            }
            val newestJob = async {
                runCatching { musicRepository.getRecentlyAddedAlbums(limit = 15) }
                    .getOrDefault(emptyList())
            }
            val mostPlayedJob = async {
                runCatching { musicRepository.getMostPlayedAlbums(limit = 15) }
                    .getOrDefault(emptyList())
            }
            val genresJob = async {
                runCatching { musicRepository.getMusicGenres(limit = 20) }.getOrDefault(emptyList())
            }
            val randomAlbumsJob = async {
                runCatching { musicRepository.getRandomAlbums(limit = 15) }
                    .getOrDefault(emptyList())
            }
            val randomTracksJob = async {
                runCatching { musicRepository.getRandomTracks(limit = 15) }
                    .getOrDefault(emptyList())
            }

            seedRecentTracks = recentTracksJob.await()
            seedRecentAlbums = recentPlayedJob.await()
            seedGenres = genresJob.await()

            if (seedRecentTracks.isNotEmpty())
                _uiState.update { it.copy(recentlyPlayedTracks = seedRecentTracks) }
            val recentPlayed = recentPlayedJob.await()
            if (recentPlayed.isNotEmpty())
                _uiState.update { it.copy(recentlyPlayedAlbums = recentPlayed) }
            val newest = newestJob.await()
            if (newest.isNotEmpty()) _uiState.update { it.copy(latestAlbums = newest) }
            val mostPlayed = mostPlayedJob.await()
            if (mostPlayed.isNotEmpty()) _uiState.update { it.copy(mostPlayedAlbums = mostPlayed) }
            val randomAlbums = randomAlbumsJob.await()
            if (randomAlbums.isNotEmpty()) {
                _uiState.update { it.copy(randomAlbums = randomAlbums) }
                updateMadeForYouSnapshot()
            }
            val randomTracks = randomTracksJob.await()
            if (randomTracks.isNotEmpty()) {
                _uiState.update { it.copy(randomTracks = randomTracks) }
                updateMadeForYouSnapshot()
            }
        }

        coroutineScope {
            launch {
                try {
                    val artistsFromAlbums = seedRecentAlbums.mapNotNull { a ->
                        a.artistId?.let { id -> id to (a.artist ?: "") }
                    }
                    val artistsFromTracks = seedRecentTracks.mapNotNull { t ->
                        t.artistId?.let { id -> id to (t.artist ?: "") }
                    }
                    val pickedArtists =
                        (artistsFromAlbums + artistsFromTracks)
                            .distinctBy { it.first }
                            .shuffled()
                            .take(5)
                    val sections =
                        pickedArtists
                            .map { (artistId, _) ->
                                async {
                                    runCatching {
                                        val artist =
                                            musicRepository.getArtistById(artistId)
                                                ?: return@runCatching null
                                        val albums =
                                            musicRepository.getArtistAlbums(artistId).take(12)
                                        if (albums.size >= 3) artist to albums else null
                                    }
                                        .getOrNull()
                                }
                            }
                            .awaitAll()
                            .filterNotNull()
                    if (sections.isNotEmpty())
                        _uiState.update { it.copy(moreFromArtistSections = sections) }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load More From sections")
                }
            }
            launch {
                try {
                    val sections =
                        seedGenres
                            .shuffled()
                            .take(5)
                            .map { genre ->
                                async {
                                    runCatching {
                                        val albums =
                                            musicRepository.getAlbumsByGenre(genre.name, limit = 15)
                                        if (albums.isNotEmpty()) genre.name to albums else null
                                    }
                                        .getOrNull()
                                }
                            }
                            .awaitAll()
                            .filterNotNull()
                    if (sections.isNotEmpty())
                        _uiState.update { it.copy(musicGenreSections = sections) }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load genre sections")
                }
            }
            launch {
                runCatching {
                    val a = musicRepository.getFavoriteAlbums(limit = 15)
                    if (a.isNotEmpty()) _uiState.update { it.copy(favoriteAlbums = a) }
                }
            }
            launch {
                runCatching {
                    val a = musicRepository.getRandomArtists(limit = 20)
                    if (a.isNotEmpty()) _uiState.update { it.copy(randomArtists = a) }
                }
            }
            launch {
                try {
                    val sections =
                        seedGenres
                            .shuffled()
                            .take(2)
                            .map { genre ->
                                async {
                                    runCatching {
                                        val tracks =
                                            musicRepository.getTracksByGenre(genre.name, limit = 15)
                                        if (tracks.isNotEmpty()) genre.name to tracks else null
                                    }
                                        .getOrNull()
                                }
                            }
                            .awaitAll()
                            .filterNotNull()
                    if (sections.isNotEmpty()) {
                        _uiState.update { it.copy(songsByGenreSections = sections) }
                        updateMadeForYouSnapshot()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load songs by genre")
                }
            }
            launch {
                runCatching {
                    val a = musicRepository.getTopRatedAlbums(limit = 15)
                    if (a.isNotEmpty()) _uiState.update { it.copy(topRatedAlbums = a) }
                }
            }
            launch {
                try {
                    val trackYears = seedRecentTracks.mapNotNull { it.productionYear }
                    val albumYears = seedRecentAlbums.mapNotNull { it.productionYear }
                    val decade =
                        (trackYears + albumYears)
                            .map { (it / 10) * 10 }
                            .groupingBy { it }
                            .eachCount()
                            .maxByOrNull { it.value }
                            ?.key
                    if (decade != null) {
                        val albums = musicRepository.getAlbumsByDecade(decade, limit = 15)
                        if (albums.isNotEmpty())
                            _uiState.update { it.copy(albumsByDecade = decade to albums) }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load albums by decade")
                }
            }
            launch {
                runCatching {
                    val p = musicRepository.getPlaylists()
                    if (p.isNotEmpty()) _uiState.update { it.copy(homePlaylists = p) }
                }
            }
            launch {
                runCatching {
                    val a = musicRepository.getTopArtists(limit = 15)
                    if (a.isNotEmpty()) _uiState.update { it.copy(topArtists = a) }
                }
            }
            launch {
                runCatching {
                    val a = musicRepository.getFavoriteArtists(limit = 15)
                    if (a.isNotEmpty()) _uiState.update { it.copy(favoriteArtists = a) }
                }
            }
            launch {
                try {
                    val seedArtists = buildList {
                        seedRecentAlbums.forEach { a ->
                            a.artistId?.let { id -> add(id to (a.artist ?: "")) }
                        }
                        seedRecentTracks.forEach { t ->
                            t.artistId?.let { id -> add(id to (t.artist ?: "")) }
                        }
                    }
                        .distinctBy { it.first }
                        .take(3)
                    val sections =
                        seedArtists
                            .map { (artistId, artistName) ->
                                async {
                                    runCatching {
                                        val tracks =
                                            musicRepository.getArtistTopTracks(
                                                artistId,
                                                limit = 8,
                                            )
                                        if (tracks.isNotEmpty()) artistName to tracks else null
                                    }
                                        .getOrNull()
                                }
                            }
                            .awaitAll()
                            .filterNotNull()
                    if (sections.isNotEmpty())
                        _uiState.update { it.copy(topTracksSections = sections) }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load top tracks sections")
                }
            }
            launch {
                try {
                    val sections =
                        seedGenres
                            .shuffled()
                            .take(3)
                            .map { genre ->
                                async {
                                    runCatching {
                                        val albums =
                                            musicRepository.getRecentlyAddedAlbumsByGenre(
                                                genre.name,
                                                limit = 12,
                                            )
                                        if (albums.isNotEmpty()) genre.name to albums else null
                                    }
                                        .getOrNull()
                                }
                            }
                            .awaitAll()
                            .filterNotNull()
                    if (sections.isNotEmpty())
                        _uiState.update { it.copy(newGenreReleases = sections) }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load new genre releases")
                }
            }
            launch {
                try {
                    val sections = mutableListOf<Pair<String, List<AfinityTrack>>>()
                    val recentSeed =
                        seedRecentAlbums.firstOrNull()
                            ?: _uiState.value.mostPlayedAlbums.firstOrNull()
                            ?: _uiState.value.latestAlbums.firstOrNull()
                    if (recentSeed != null) {
                        val tracks = musicRepository.getInstantMix(recentSeed.id, limit = 25)
                        if (tracks.isNotEmpty())
                            sections.add("${recentSeed.artist ?: recentSeed.name} Radio" to tracks)
                    }
                    seedGenres.take(2).forEach { genre ->
                        runCatching {
                            val genreAlbum =
                                musicRepository
                                    .getAlbumsByGenre(genre.name, limit = 5)
                                    .randomOrNull()
                            if (genreAlbum != null) {
                                val tracks =
                                    musicRepository.getInstantMix(genreAlbum.id, limit = 25)
                                if (tracks.isNotEmpty())
                                    sections.add("${genre.name} Radio" to tracks)
                            }
                        }
                    }
                    if (sections.isNotEmpty()) {
                        _uiState.update { it.copy(radioSections = sections) }
                        updateMadeForYouSnapshot()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load radio sections")
                }
            }
        }

        _uiState.update { it.copy(isLoadingHome = false) }
    }
}
