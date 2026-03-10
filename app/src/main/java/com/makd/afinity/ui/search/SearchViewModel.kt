package com.makd.afinity.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.manager.PlaybackEvent
import com.makd.afinity.data.manager.PlaybackStateManager
import com.makd.afinity.data.models.audiobookshelf.LibraryItem
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.extensions.toAfinityItem
import com.makd.afinity.data.models.jellyseerr.JellyseerrUser
import com.makd.afinity.data.models.jellyseerr.MediaStatus
import com.makd.afinity.data.models.jellyseerr.MediaType
import com.makd.afinity.data.models.jellyseerr.Permissions
import com.makd.afinity.data.models.jellyseerr.QualityProfile
import com.makd.afinity.data.models.jellyseerr.RatingsCombined
import com.makd.afinity.data.models.jellyseerr.SearchResultItem
import com.makd.afinity.data.models.jellyseerr.ServiceSettings
import com.makd.afinity.data.models.jellyseerr.hasPermission
import com.makd.afinity.data.models.media.AfinityBoxSet
import com.makd.afinity.data.models.media.AfinityCollection
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.media.toAfinityEpisode
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.AudiobookshelfRepository
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.JellyfinRepository
import com.makd.afinity.data.repository.JellyseerrRepository
import com.makd.afinity.data.repository.auth.AuthRepository
import com.makd.afinity.data.repository.download.DownloadRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.userdata.UserDataRepository
import com.makd.afinity.ui.item.delegates.ItemDownloadDelegate
import com.makd.afinity.ui.item.delegates.ItemUserDataDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import timber.log.Timber
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class SearchViewModel
@Inject
constructor(
    private val mediaRepository: MediaRepository,
    private val jellyfinRepository: JellyfinRepository,
    private val jellyseerrRepository: JellyseerrRepository,
    private val appDataRepository: AppDataRepository,
    private val audiobookshelfRepository: AudiobookshelfRepository,
    private val playbackStateManager: PlaybackStateManager,
    private val downloadRepository: DownloadRepository,
    private val userDataRepository: UserDataRepository,
    private val authRepository: AuthRepository,
    private val databaseRepository: DatabaseRepository,
    private val itemDownloadDelegate: ItemDownloadDelegate,
    private val itemUserDataDelegate: ItemUserDataDelegate,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    val isJellyseerrAuthenticated = jellyseerrRepository.isAuthenticated
    val isAudiobookshelfAuthenticated = audiobookshelfRepository.isAuthenticated

    private val _currentUser = MutableStateFlow<JellyseerrUser?>(null)
    val currentUser: StateFlow<JellyseerrUser?> = _currentUser.asStateFlow()

    private val _selectedEpisode = MutableStateFlow<AfinityEpisode?>(null)
    val selectedEpisode: StateFlow<AfinityEpisode?> = _selectedEpisode.asStateFlow()

    private val _isLoadingEpisode = MutableStateFlow(false)
    val isLoadingEpisode: StateFlow<Boolean> = _isLoadingEpisode.asStateFlow()

    private val _selectedEpisodeWatchlistStatus = MutableStateFlow(false)
    val selectedEpisodeWatchlistStatus: StateFlow<Boolean> =
        _selectedEpisodeWatchlistStatus.asStateFlow()

    private val _selectedEpisodeDownloadInfo = MutableStateFlow<DownloadInfo?>(null)
    val selectedEpisodeDownloadInfo: StateFlow<DownloadInfo?> =
        _selectedEpisodeDownloadInfo.asStateFlow()

    private var searchJob: Job? = null
    private var jellyseerrSearchJob: Job? = null
    private var audiobookshelfSearchJob: Job? = null

    private val searchQueryFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)

    init {
        viewModelScope.launch {
            searchQueryFlow.debounce(300).distinctUntilChanged().collectLatest { query ->
                if (query.length >= 2) {
                    when {
                        _uiState.value.isJellyseerrSearchMode -> performJellyseerrSearch()
                        _uiState.value.isAudiobookshelfSearchMode -> performAudiobookshelfSearch()
                        else -> {
                            launch { performSearch() }
                            launch { performAudiobookshelfSearch() }
                            launch { performJellyseerrSearch() }
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            appDataRepository.isInitialDataLoaded.collect { isLoaded ->
                if (isLoaded) {
                    loadLibraries()
                    loadGenres()
                    if (_uiState.value.searchQuery.isNotEmpty()) {
                        searchQueryFlow.tryEmit(_uiState.value.searchQuery)
                    }
                } else {
                    _uiState.value = SearchUiState()
                }
            }
        }

        viewModelScope.launch {
            audiobookshelfRepository.currentConfig.collect { config ->
                _uiState.update { it.copy(audiobookshelfServerUrl = config?.serverUrl) }
            }
        }

        viewModelScope.launch {
            playbackStateManager.playbackEvents.collect { event ->
                if (event is PlaybackEvent.Synced) {
                    val syncedItem = jellyfinRepository.getItemById(event.itemId) ?: return@collect
                    updateItemInSearchResults(syncedItem)
                    val parentItem =
                        when (syncedItem) {
                            is AfinityEpisode ->
                                syncedItem.seriesId?.let { jellyfinRepository.getItemById(it) }
                            is AfinitySeason -> jellyfinRepository.getItemById(syncedItem.seriesId)
                            else -> null
                        }

                    if (parentItem != null) {
                        updateItemInSearchResults(parentItem)
                    }
                }
            }
        }
    }

    fun selectEpisode(episode: AfinityEpisode) {
        viewModelScope.launch {
            try {
                _isLoadingEpisode.value = true
                val fullEpisode =
                    try {
                        jellyfinRepository
                            .getItem(episode.id, fields = FieldSets.ITEM_DETAIL)
                            ?.toAfinityEpisode(jellyfinRepository, null)
                    } catch (e: Exception) {
                        try {
                            authRepository.currentUser.value?.id?.let {
                                databaseRepository.getEpisode(episode.id, it)
                            }
                        } catch (dbError: Exception) {
                            null
                        }
                    }
                _selectedEpisode.value = fullEpisode ?: episode
                _selectedEpisodeWatchlistStatus.value = episode.liked

                try {
                    _selectedEpisodeDownloadInfo.value =
                        downloadRepository.getDownloadByItemId(episode.id)
                } catch (e: Exception) {
                    _selectedEpisodeDownloadInfo.value = null
                }

                launch {
                    downloadRepository.getAllDownloadsFlow().collect { downloads ->
                        _selectedEpisode.value?.id?.let { id ->
                            _selectedEpisodeDownloadInfo.value = downloads.find { it.itemId == id }
                        }
                    }
                }
                _isLoadingEpisode.value = false
            } catch (e: Exception) {
                _selectedEpisode.value = episode
                _selectedEpisodeWatchlistStatus.value = false
                _isLoadingEpisode.value = false
            }
        }
    }

    fun clearSelectedEpisode() {
        _selectedEpisode.value = null
        _selectedEpisodeWatchlistStatus.value = false
        _selectedEpisodeDownloadInfo.value = null
    }

    fun onDownloadClick() {
        _selectedEpisode.value?.let { episode ->
            itemDownloadDelegate.onDownloadClick(viewModelScope, episode) {}
        }
    }

    fun pauseDownload() =
        itemDownloadDelegate.pauseDownload(viewModelScope, _selectedEpisodeDownloadInfo.value)

    fun resumeDownload() =
        itemDownloadDelegate.resumeDownload(viewModelScope, _selectedEpisodeDownloadInfo.value)

    fun cancelDownload() =
        itemDownloadDelegate.cancelDownload(viewModelScope, _selectedEpisodeDownloadInfo.value)

    fun toggleEpisodeFavorite(episode: AfinityEpisode) {
        itemUserDataDelegate.toggleEpisodeFavorite(viewModelScope, episode) {
            _selectedEpisode.value = episode.copy(favorite = !episode.favorite)
            updateItemInSearchResults(episode.copy(favorite = !episode.favorite))
        }
    }

    fun toggleEpisodeWatchlist(episode: AfinityEpisode) {
        val isLiked = _selectedEpisodeWatchlistStatus.value
        itemUserDataDelegate.toggleWatchlist(
            scope = viewModelScope,
            item = episode,
            updateOptimisticUI = {
                _selectedEpisodeWatchlistStatus.value = !isLiked
                _selectedEpisode.value = _selectedEpisode.value?.copy(liked = !isLiked)
                updateItemInSearchResults(episode.copy(liked = !isLiked))
            },
            revertUI = {
                _selectedEpisodeWatchlistStatus.value = isLiked
                _selectedEpisode.value = _selectedEpisode.value?.copy(liked = isLiked)
                updateItemInSearchResults(episode.copy(liked = isLiked))
            },
        )
    }

    fun toggleEpisodeWatched(episode: AfinityEpisode) {
        viewModelScope.launch {
            try {
                val isNowPlayed = !episode.played
                val updatedEpisode = episode.copy(played = isNowPlayed, playbackPositionTicks = 0)
                _selectedEpisode.value = updatedEpisode
                updateItemInSearchResults(updatedEpisode)

                val success =
                    if (episode.played) userDataRepository.markUnwatched(episode.id)
                    else userDataRepository.markWatched(episode.id)

                if (success) {
                    mediaRepository.refreshItemUserData(episode.id, FieldSets.REFRESH_USER_DATA)
                    playbackStateManager.notifyItemChanged(
                        episode.id,
                        episode.seriesId,
                        episode.seasonId,
                    )
                    mediaRepository.invalidateNextUpCache()
                } else {
                    _selectedEpisode.value = episode
                    updateItemInSearchResults(episode)
                }
            } catch (e: Exception) {
                _selectedEpisode.value = episode
            }
        }
    }

    private fun cancelAllSearchJobs() {
        searchJob?.cancel()
        jellyseerrSearchJob?.cancel()
        audiobookshelfSearchJob?.cancel()
    }

    fun loadLibraries() {
        viewModelScope.launch {
            try {
                val libraries = mediaRepository.getLibraries()
                _uiState.value = _uiState.value.copy(libraries = libraries)
                Timber.d("Loaded ${libraries.size} libraries")
            } catch (e: Exception) {
                Timber.e(e, "Failed to load libraries")
            }
        }
    }

    fun loadGenres() {
        viewModelScope.launch {
            try {
                val selectedLibraryId = _uiState.value.selectedLibrary?.id
                val genres =
                    mediaRepository.getGenres(
                        parentId = selectedLibraryId,
                        limit = 100,
                        includeItemTypes = listOf("MOVIE", "SERIES", "BOX_SET"),
                    )
                _uiState.value = _uiState.value.copy(genres = genres)
                Timber.d("Loaded ${genres.size} genres from API")
            } catch (e: Exception) {
                Timber.e(e, "Failed to load genres")
                _uiState.value = _uiState.value.copy(genres = emptyList())
            }
        }
    }

    fun loadAudiobookshelfGenres() {
        viewModelScope.launch {
            try {
                var libraries = audiobookshelfRepository.getLibrariesFlow().first()
                if (libraries.isEmpty()) {
                    libraries =
                        audiobookshelfRepository.refreshLibraries().getOrDefault(emptyList())
                }
                val libraryIds = libraries.map { it.id }
                audiobookshelfRepository
                    .getGenres(libraryIds)
                    .fold(
                        onSuccess = { genres ->
                            _uiState.update { it.copy(audiobookshelfGenres = genres) }
                            Timber.d(
                                "Loaded ${genres.size} Audiobookshelf genres across ${libraryIds.size} libraries"
                            )
                        },
                        onFailure = { error ->
                            Timber.e(error, "Failed to load Audiobookshelf genres")
                            _uiState.update { it.copy(audiobookshelfGenres = emptyList()) }
                        },
                    )
            } catch (e: Exception) {
                Timber.e(e, "Failed to load Audiobookshelf genres")
                _uiState.update { it.copy(audiobookshelfGenres = emptyList()) }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)

        if (query.length >= 2) {
            searchQueryFlow.tryEmit(query)
        } else if (query.isEmpty()) {
            cancelAllSearchJobs()
            _uiState.value =
                _uiState.value.copy(
                    searchResults = emptyList(),
                    jellyseerrSearchResults = emptyList(),
                    audiobookshelfSearchResults = emptyList(),
                    isSearching = false,
                    isAudiobookshelfSearching = false,
                    isJellyseerrSearching = false,
                )
        }
    }

    fun selectLibrary(library: AfinityCollection?) {
        searchJob?.cancel()

        _uiState.value =
            _uiState.value.copy(
                selectedLibrary = library,
                isAudiobookshelfSearchMode = false,
                audiobookshelfSearchResults = emptyList(),
                searchResults = emptyList(),
            )

        loadGenres()

        if (_uiState.value.searchQuery.isNotEmpty()) {
            performSearch()
        }
    }

    fun performSearch() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isEmpty()) return

        searchJob?.cancel()

        val selectedLibrary = _uiState.value.selectedLibrary
        val itemTypes =
            when (selectedLibrary?.type) {
                CollectionType.Movies -> listOf("MOVIE", "BOX_SET")
                CollectionType.TvShows -> listOf("SERIES", "EPISODE")
                CollectionType.BoxSets -> listOf("BOX_SET")
                else -> listOf("MOVIE", "SERIES", "EPISODE", "BOX_SET")
            }

        searchJob =
            viewModelScope.launch {
                try {
                    _uiState.value = _uiState.value.copy(isSearching = true)
                    val results =
                        mediaRepository.getItems(
                            parentId = selectedLibrary?.id,
                            searchTerm = query,
                            includeItemTypes = itemTypes,
                            limit = 50,
                            sortBy = SortBy.NAME,
                            sortDescending = false,
                            fields = FieldSets.SEARCH_RESULTS,
                        )

                    val afinityItems =
                        withContext(Dispatchers.Default) {
                            yield()

                            val mappedItems =
                                results.items
                                    ?.filter {
                                        it.locationType !=
                                            org.jellyfin.sdk.model.api.LocationType.VIRTUAL
                                    }
                                    ?.mapNotNull { baseItemDto ->
                                        try {
                                            val item =
                                                baseItemDto.toAfinityItem(
                                                    jellyfinRepository.getBaseUrl()
                                                )
                                            when (item) {
                                                is AfinityMovie,
                                                is AfinityShow,
                                                is AfinityEpisode,
                                                is AfinityBoxSet -> item
                                                else -> null
                                            }
                                        } catch (e: Exception) {
                                            Timber.w(
                                                e,
                                                "Failed to convert item: ${baseItemDto.name}",
                                            )
                                            null
                                        }
                                    } ?: emptyList()

                            yield()

                            mappedItems.sortedBy { item ->
                                val name = item.name.lowercase()
                                val queryLower = query.lowercase()
                                when {
                                    name == queryLower -> 0
                                    name.startsWith(queryLower) -> 1
                                    name.contains(queryLower) -> 2
                                    else -> 3
                                }
                            }
                        }

                    _uiState.value =
                        _uiState.value.copy(searchResults = afinityItems, isSearching = false)

                    Timber.d("Search completed: ${afinityItems.size} results for '$query'")
                } catch (e: Exception) {
                    if (e is CancellationException) throw e

                    Timber.e(e, "Failed to perform search")
                    _uiState.value =
                        _uiState.value.copy(searchResults = emptyList(), isSearching = false)
                }
            }
    }

    fun clearSearch() {
        cancelAllSearchJobs()
        _uiState.value =
            _uiState.value.copy(
                searchQuery = "",
                searchResults = emptyList(),
                isSearching = false,
                jellyseerrSearchResults = emptyList(),
                isJellyseerrSearching = false,
                audiobookshelfSearchResults = emptyList(),
                isAudiobookshelfSearching = false,
            )
    }

    fun selectJellyseerrSearchMode() {
        cancelAllSearchJobs()
        _uiState.update {
            it.copy(
                isJellyseerrSearchMode = true,
                isAudiobookshelfSearchMode = false,
                selectedLibrary = null,
                audiobookshelfSearchResults = emptyList(),
                searchResults = emptyList(),
                jellyseerrSearchResults = emptyList(),
            )
        }

        loadCurrentUser()

        if (_uiState.value.searchQuery.isNotEmpty()) {
            performJellyseerrSearch()
        }
    }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            jellyseerrRepository
                .getCurrentUser()
                .fold(
                    onSuccess = { user -> _currentUser.value = user },
                    onFailure = { error -> Timber.e(error, "Failed to load current user") },
                )
        }
    }

    fun selectJellyfinSearchMode() {
        cancelAllSearchJobs()
        _uiState.update {
            it.copy(isJellyseerrSearchMode = false, isAudiobookshelfSearchMode = false)
        }

        if (_uiState.value.searchQuery.isNotEmpty()) {
            performSearch()
            performAudiobookshelfSearch()
            performJellyseerrSearch()
        }
    }

    fun selectAudiobookshelfSearchMode() {
        cancelAllSearchJobs()
        _uiState.update {
            it.copy(
                isAudiobookshelfSearchMode = true,
                isJellyseerrSearchMode = false,
                selectedLibrary = null,
                jellyseerrSearchResults = emptyList(),
                searchResults = emptyList(),
                audiobookshelfSearchResults = emptyList(),
            )
        }

        loadAudiobookshelfGenres()

        if (_uiState.value.searchQuery.isNotEmpty()) {
            performAudiobookshelfSearch()
        }
    }

    fun performAudiobookshelfSearch() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isEmpty()) return

        audiobookshelfSearchJob?.cancel()
        audiobookshelfSearchJob =
            viewModelScope.launch {
                try {
                    _uiState.update { it.copy(isAudiobookshelfSearching = true) }

                    var libraries = audiobookshelfRepository.getLibrariesFlow().first()
                    if (libraries.isEmpty()) {
                        Timber.d("No cached libraries, refreshing from server")
                        libraries =
                            audiobookshelfRepository.refreshLibraries().getOrDefault(emptyList())
                    }

                    Timber.d(
                        "Searching ${libraries.size} libraries: ${libraries.map { "${it.name}(${it.id})" }}"
                    )

                    val results =
                        libraries
                            .map { library ->
                                async {
                                    audiobookshelfRepository
                                        .searchLibrary(library.id, query)
                                        .onSuccess { response ->
                                            Timber.d(
                                                "Library '${library.name}': ${response.book?.size ?: 0} books, ${response.podcast?.size ?: 0} podcasts"
                                            )
                                        }
                                        .onFailure {
                                            Timber.w(
                                                it,
                                                "Search failed for library ${library.name}",
                                            )
                                        }
                                        .getOrNull()
                                }
                            }
                            .awaitAll()
                            .filterNotNull()

                    val items =
                        withContext(Dispatchers.Default) {
                            results
                                .flatMap { response ->
                                    val books = response.book?.map { it.libraryItem } ?: emptyList()
                                    val podcasts =
                                        response.podcast?.map { it.libraryItem } ?: emptyList()
                                    books + podcasts
                                }
                                .distinctBy { it.id }
                        }

                    _uiState.update {
                        it.copy(
                            audiobookshelfSearchResults = items,
                            isAudiobookshelfSearching = false,
                        )
                    }
                    Timber.d("Audiobookshelf search completed: ${items.size} results for '$query'")
                } catch (e: Exception) {
                    if (e is CancellationException) throw e

                    _uiState.update {
                        it.copy(
                            audiobookshelfSearchResults = emptyList(),
                            isAudiobookshelfSearching = false,
                        )
                    }
                    Timber.e(e, "Error during Audiobookshelf search")
                }
            }
    }

    fun performJellyseerrSearch() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isEmpty()) return

        jellyseerrSearchJob?.cancel()
        jellyseerrSearchJob =
            viewModelScope.launch {
                try {
                    _uiState.update { it.copy(isJellyseerrSearching = true) }
                    jellyseerrRepository
                        .findMediaByName(query)
                        .fold(
                            onSuccess = { results ->
                                val filteredResults =
                                    withContext(Dispatchers.Default) {
                                        results.filter { it.getMediaType() != null }
                                    }

                                _uiState.update {
                                    it.copy(
                                        jellyseerrSearchResults = filteredResults,
                                        isJellyseerrSearching = false,
                                    )
                                }
                                Timber.d(
                                    "Jellyseerr search completed: ${filteredResults.size} results"
                                )
                            },
                            onFailure = { error ->
                                _uiState.update {
                                    it.copy(
                                        jellyseerrSearchResults = emptyList(),
                                        isJellyseerrSearching = false,
                                    )
                                }
                                Timber.e(error, "Jellyseerr search failed")
                            },
                        )
                } catch (e: Exception) {
                    if (e is CancellationException) throw e

                    _uiState.update {
                        it.copy(
                            jellyseerrSearchResults = emptyList(),
                            isJellyseerrSearching = false,
                        )
                    }
                    Timber.e(e, "Error during Jellyseerr search")
                }
            }
    }

    fun showRequestDialog(
        tmdbId: Int,
        mediaType: MediaType,
        title: String,
        posterUrl: String?,
        availableSeasons: Int = 0,
        existingStatus: MediaStatus? = null,
    ) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isFetchingTvDetails = true) }

                val detailsResult =
                    if (mediaType == MediaType.TV) {
                        jellyseerrRepository.getTvDetails(tmdbId)
                    } else {
                        jellyseerrRepository.getMovieDetails(tmdbId)
                    }

                detailsResult.fold(
                    onSuccess = { details ->
                        _uiState.update { it.copy(isFetchingTvDetails = false) }

                        val seasonCount =
                            if (mediaType == MediaType.TV) details.getSeasonCount() else 0
                        val alreadyAvailableSeasons =
                            details.mediaInfo?.getAvailableSeasons() ?: emptyList()
                        val selectableSeasons =
                            if (mediaType == MediaType.TV) {
                                (1..seasonCount).filter { it !in alreadyAvailableSeasons }
                            } else emptyList()

                        _uiState.update {
                            it.copy(
                                showRequestDialog = true,
                                pendingRequest =
                                    PendingRequestSearch(
                                        tmdbId = tmdbId,
                                        mediaType = mediaType,
                                        title = details.title ?: details.name ?: title,
                                        posterUrl = details.getPosterUrl(),
                                        availableSeasons = seasonCount,
                                        existingStatus = existingStatus,
                                        backdropUrl = details.getBackdropUrl(),
                                        tagline = details.tagline,
                                        overview = details.overview,
                                        releaseDate = details.releaseDate ?: details.firstAirDate,
                                        runtime = details.runtime,
                                        voteAverage = details.voteAverage,
                                        certification = details.getCertification(),
                                        originalLanguage = details.originalLanguage,
                                        director = details.getDirector(),
                                        genres = details.getGenreNames(),
                                        ratingsCombined = details.ratingsCombined,
                                    ),
                                selectedSeasons = selectableSeasons,
                                disabledSeasons = alreadyAvailableSeasons,
                            )
                        }
                        loadServiceSettings(mediaType)
                    },
                    onFailure = { error ->
                        _uiState.update { it.copy(isFetchingTvDetails = false) }
                        Timber.w(error, "Failed to fetch details, using fallback")

                        _uiState.update {
                            it.copy(
                                showRequestDialog = true,
                                pendingRequest =
                                    PendingRequestSearch(
                                        tmdbId = tmdbId,
                                        mediaType = mediaType,
                                        title = title,
                                        posterUrl = posterUrl,
                                        availableSeasons = availableSeasons,
                                        existingStatus = existingStatus,
                                    ),
                                selectedSeasons =
                                    if (mediaType == MediaType.TV && availableSeasons > 0) {
                                        (1..availableSeasons).toList()
                                    } else emptyList(),
                                disabledSeasons = emptyList(),
                            )
                        }
                        loadServiceSettings(mediaType)
                    },
                )
            } catch (e: Exception) {
                Timber.e(e, "Error showing request dialog")
                _uiState.update {
                    it.copy(
                        showRequestDialog = true,
                        pendingRequest =
                            PendingRequestSearch(
                                tmdbId = tmdbId,
                                mediaType = mediaType,
                                title = title,
                                posterUrl = posterUrl,
                                availableSeasons = availableSeasons,
                                existingStatus = existingStatus,
                            ),
                        selectedSeasons =
                            if (mediaType == MediaType.TV && availableSeasons > 0) {
                                (1..availableSeasons).toList()
                            } else emptyList(),
                        disabledSeasons = emptyList(),
                        isFetchingTvDetails = false,
                    )
                }
                loadServiceSettings(mediaType)
            }
        }
    }

    fun confirmRequest() {
        val pending = _uiState.value.pendingRequest ?: return
        val state = _uiState.value
        val seasons =
            if (pending.mediaType == MediaType.TV) {
                state.selectedSeasons.takeIf { it.isNotEmpty() }
            } else null

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isCreatingRequest = true) }

                jellyseerrRepository
                    .createRequest(
                        mediaId = pending.tmdbId,
                        mediaType = pending.mediaType,
                        seasons = seasons,
                        is4k = state.is4kRequested,
                        serverId = state.selectedServer?.id,
                        profileId = state.selectedProfile?.id,
                        rootFolder = state.selectedRootFolder,
                    )
                    .fold(
                        onSuccess = { newRequest ->
                            val updatedResults =
                                _uiState.value.jellyseerrSearchResults.map { item ->
                                    if (item.id == pending.tmdbId) {
                                        val updatedMediaInfo =
                                            newRequest.media.copy(
                                                requests = listOfNotNull(newRequest)
                                            )
                                        item.copy(mediaInfo = updatedMediaInfo)
                                    } else {
                                        item
                                    }
                                }

                            _uiState.update {
                                it.copy(
                                    isCreatingRequest = false,
                                    showRequestDialog = false,
                                    pendingRequest = null,
                                    selectedSeasons = emptyList(),
                                    is4kRequested = false,
                                    availableServers = emptyList(),
                                    selectedServer = null,
                                    availableProfiles = emptyList(),
                                    selectedProfile = null,
                                    selectedRootFolder = null,
                                    jellyseerrSearchResults = updatedResults,
                                )
                            }
                            Timber.d("Request created successfully: ${newRequest.id}")
                        },
                        onFailure = { error ->
                            _uiState.update { it.copy(isCreatingRequest = false) }
                            Timber.e(error, "Failed to create request")
                        },
                    )
            } catch (e: Exception) {
                _uiState.update { it.copy(isCreatingRequest = false) }
                Timber.e(e, "Error creating request")
            }
        }
    }

    fun dismissRequestDialog() {
        _uiState.update {
            it.copy(
                showRequestDialog = false,
                pendingRequest = null,
                selectedSeasons = emptyList(),
                is4kRequested = false,
                availableServers = emptyList(),
                selectedServer = null,
                availableProfiles = emptyList(),
                selectedProfile = null,
                selectedRootFolder = null,
                isLoadingServers = false,
                isLoadingProfiles = false,
            )
        }
    }

    fun setSelectedSeasons(seasons: List<Int>) {
        _uiState.update { it.copy(selectedSeasons = seasons) }
    }

    fun setIs4kRequested(is4k: Boolean) {
        _uiState.update {
            it.copy(
                is4kRequested = is4k,
                selectedServer = null,
                availableProfiles = emptyList(),
                selectedProfile = null,
                selectedRootFolder = null,
            )
        }
        val mediaType = _uiState.value.pendingRequest?.mediaType ?: return
        loadServiceSettings(mediaType)
    }

    fun selectServer(server: ServiceSettings) {
        _uiState.update {
            it.copy(
                selectedServer = server,
                selectedRootFolder = null,
                selectedProfile = null,
                availableProfiles = emptyList(),
            )
        }
        val mediaType = _uiState.value.pendingRequest?.mediaType ?: return
        loadQualityProfiles(mediaType, server.id)
    }

    fun selectProfile(profile: QualityProfile) {
        _uiState.update { it.copy(selectedProfile = profile) }
    }

    private fun loadServiceSettings(mediaType: MediaType) {
        val user = _currentUser.value ?: return
        if (
            !user.hasPermission(Permissions.REQUEST_ADVANCED) &&
                !user.hasPermission(Permissions.REQUEST_4K) &&
                !user.hasPermission(Permissions.MANAGE_REQUESTS)
        )
            return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingServers = true) }
            jellyseerrRepository
                .getServiceSettings(mediaType)
                .fold(
                    onSuccess = { servers ->
                        val is4k = _uiState.value.is4kRequested
                        val filtered = servers.filter { it.is4k == is4k }
                        _uiState.update {
                            it.copy(availableServers = filtered, isLoadingServers = false)
                        }
                    },
                    onFailure = { error ->
                        Timber.e(error, "Failed to load service settings")
                        _uiState.update { it.copy(isLoadingServers = false) }
                    },
                )
        }
    }

    private fun loadQualityProfiles(mediaType: MediaType, serviceId: Int) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingProfiles = true) }
            jellyseerrRepository
                .getServiceDetails(mediaType, serviceId)
                .fold(
                    onSuccess = { details ->
                        val activeProfileId = details.server?.activeProfileId
                        val preselected = details.profiles.find { it.id == activeProfileId }
                        val rootFolder =
                            details.server?.activeDirectory
                                ?: details.rootFolders.firstOrNull()?.path
                        _uiState.update {
                            it.copy(
                                availableProfiles = details.profiles,
                                selectedProfile = preselected,
                                selectedRootFolder = rootFolder,
                                isLoadingProfiles = false,
                            )
                        }
                    },
                    onFailure = { error ->
                        Timber.e(error, "Failed to load quality profiles")
                        _uiState.update { it.copy(isLoadingProfiles = false) }
                    },
                )
        }
    }

    private fun updateItemInSearchResults(updatedItem: AfinityItem) {
        val currentResults = _uiState.value.searchResults
        val index = currentResults.indexOfFirst { it.id == updatedItem.id }

        if (index != -1) {
            val mutableResults = currentResults.toMutableList()
            mutableResults[index] = updatedItem
            _uiState.update { it.copy(searchResults = mutableResults) }
            Timber.d("Updated search result: ${updatedItem.name}")
        }
    }
}

data class SearchUiState(
    val searchQuery: String = "",
    val searchResults: List<AfinityItem> = emptyList(),
    val isSearching: Boolean = false,
    val libraries: List<AfinityCollection> = emptyList(),
    val selectedLibrary: AfinityCollection? = null,
    val genres: List<String> = emptyList(),
    val audiobookshelfGenres: List<String> = emptyList(),
    val isJellyseerrSearchMode: Boolean = false,
    val jellyseerrSearchResults: List<SearchResultItem> = emptyList(),
    val isJellyseerrSearching: Boolean = false,
    val isAudiobookshelfSearchMode: Boolean = false,
    val audiobookshelfSearchResults: List<LibraryItem> = emptyList(),
    val isAudiobookshelfSearching: Boolean = false,
    val audiobookshelfServerUrl: String? = null,
    val showRequestDialog: Boolean = false,
    val pendingRequest: PendingRequestSearch? = null,
    val selectedSeasons: List<Int> = emptyList(),
    val disabledSeasons: List<Int> = emptyList(),
    val isCreatingRequest: Boolean = false,
    val isFetchingTvDetails: Boolean = false,
    val is4kRequested: Boolean = false,
    val availableServers: List<ServiceSettings> = emptyList(),
    val selectedServer: ServiceSettings? = null,
    val availableProfiles: List<QualityProfile> = emptyList(),
    val selectedProfile: QualityProfile? = null,
    val selectedRootFolder: String? = null,
    val isLoadingServers: Boolean = false,
    val isLoadingProfiles: Boolean = false,
)

data class PendingRequestSearch(
    val tmdbId: Int,
    val mediaType: MediaType,
    val title: String,
    val posterUrl: String?,
    val availableSeasons: Int = 0,
    val existingStatus: MediaStatus? = null,
    val backdropUrl: String? = null,
    val tagline: String? = null,
    val overview: String? = null,
    val releaseDate: String? = null,
    val runtime: Int? = null,
    val voteAverage: Double? = null,
    val certification: String? = null,
    val originalLanguage: String? = null,
    val director: String? = null,
    val genres: List<String> = emptyList(),
    val ratingsCombined: RatingsCombined? = null,
)
