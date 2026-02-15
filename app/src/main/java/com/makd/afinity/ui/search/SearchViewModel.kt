package com.makd.afinity.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.audiobookshelf.LibraryItem
import com.makd.afinity.data.models.common.SortBy
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
import com.makd.afinity.data.models.media.AfinityCollection
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.AudiobookshelfRepository
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.JellyfinRepository
import com.makd.afinity.data.repository.JellyseerrRepository
import com.makd.afinity.data.repository.media.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class SearchViewModel
@Inject
constructor(
    private val mediaRepository: MediaRepository,
    private val jellyfinRepository: JellyfinRepository,
    private val jellyseerrRepository: JellyseerrRepository,
    private val appDataRepository: AppDataRepository,
    private val audiobookshelfRepository: AudiobookshelfRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    val isJellyseerrAuthenticated = jellyseerrRepository.isAuthenticated
    val isAudiobookshelfAuthenticated = audiobookshelfRepository.isAuthenticated

    private val _currentUser = MutableStateFlow<JellyseerrUser?>(null)
    val currentUser: StateFlow<JellyseerrUser?> = _currentUser.asStateFlow()

    init {
        viewModelScope.launch {
            appDataRepository.isInitialDataLoaded.collect { isLoaded ->
                if (isLoaded) {
                    loadLibraries()
                    loadGenres()

                    if (_uiState.value.searchQuery.isNotEmpty()) {
                        when {
                            _uiState.value.isJellyseerrSearchMode -> performJellyseerrSearch()
                            _uiState.value.isAudiobookshelfSearchMode ->
                                performAudiobookshelfSearch()
                            else -> {
                                performSearch()
                                performAudiobookshelfSearch()
                            }
                        }
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
                        includeItemTypes = listOf("MOVIE", "SERIES", "BOXSET"),
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
                audiobookshelfRepository
                    .getGenres()
                    .fold(
                        onSuccess = { genres ->
                            _uiState.update { it.copy(audiobookshelfGenres = genres) }
                            Timber.d("Loaded ${genres.size} Audiobookshelf genres")
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
            viewModelScope.launch {
                kotlinx.coroutines.delay(500)
                if (_uiState.value.searchQuery == query) {
                    when {
                        _uiState.value.isJellyseerrSearchMode -> performJellyseerrSearch()
                        _uiState.value.isAudiobookshelfSearchMode -> performAudiobookshelfSearch()
                        else -> {
                            performSearch()
                            performAudiobookshelfSearch()
                        }
                    }
                }
            }
        } else if (query.isEmpty()) {
            _uiState.value =
                _uiState.value.copy(
                    searchResults = emptyList(),
                    jellyseerrSearchResults = emptyList(),
                    audiobookshelfSearchResults = emptyList(),
                )
        }
    }

    fun selectLibrary(library: AfinityCollection?) {
        _uiState.value =
            _uiState.value.copy(
                selectedLibrary = library,
                isAudiobookshelfSearchMode = false,
                audiobookshelfSearchResults = emptyList(),
            )

        loadGenres()

        if (_uiState.value.searchQuery.isNotEmpty()) {
            performSearch()
        }
    }

    fun performSearch() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isEmpty()) return

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isSearching = true)

                Timber.d("Performing search:")
                Timber.d("- Query: '$query'")
                Timber.d("- Selected library: ${_uiState.value.selectedLibrary?.name}")

                val results =
                    mediaRepository.getItems(
                        parentId = _uiState.value.selectedLibrary?.id,
                        searchTerm = query,
                        includeItemTypes = listOf("MOVIE", "SERIES"),
                        limit = 50,
                        sortBy = SortBy.NAME,
                        sortDescending = false,
                        fields = FieldSets.SEARCH_RESULTS,
                    )

                val afinityItems =
                    results.items
                        ?.mapNotNull { baseItemDto ->
                            try {
                                val item =
                                    baseItemDto.toAfinityItem(jellyfinRepository.getBaseUrl())
                                when (item) {
                                    is AfinityMovie -> item
                                    is AfinityShow -> item
                                    else -> null
                                }
                            } catch (e: Exception) {
                                Timber.w(e, "Failed to convert item: ${baseItemDto.name}")
                                null
                            }
                        }
                        ?.let { items ->
                            items.sortedBy { item ->
                                val name = item.name.lowercase()
                                val queryLower = query.lowercase()
                                when {
                                    name == queryLower -> 0
                                    name.startsWith(queryLower) -> 1
                                    name.contains(queryLower) -> 2
                                    else -> 3
                                }
                            }
                        } ?: emptyList()

                _uiState.value =
                    _uiState.value.copy(searchResults = afinityItems, isSearching = false)

                Timber.d("Search completed: ${afinityItems.size} results for '$query'")
            } catch (e: Exception) {
                Timber.e(e, "Failed to perform search")
                _uiState.value =
                    _uiState.value.copy(searchResults = emptyList(), isSearching = false)
            }
        }
    }

    fun clearSearch() {
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
        _uiState.update {
            it.copy(
                isJellyseerrSearchMode = true,
                isAudiobookshelfSearchMode = false,
                selectedLibrary = null,
                audiobookshelfSearchResults = emptyList(),
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
                    onFailure = { error ->
                        Timber.e(error, "Failed to load current user")
                    },
                )
        }
    }

    fun selectJellyfinSearchMode() {
        _uiState.update {
            it.copy(
                isJellyseerrSearchMode = false,
                isAudiobookshelfSearchMode = false,
                jellyseerrSearchResults = emptyList(),
                audiobookshelfSearchResults = emptyList(),
            )
        }

        if (_uiState.value.searchQuery.isNotEmpty()) {
            performSearch()
            performAudiobookshelfSearch()
        }
    }

    fun selectAudiobookshelfSearchMode() {
        _uiState.update {
            it.copy(
                isAudiobookshelfSearchMode = true,
                isJellyseerrSearchMode = false,
                selectedLibrary = null,
                jellyseerrSearchResults = emptyList(),
                searchResults = emptyList(),
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
                                        Timber.w(it, "Search failed for library ${library.name}")
                                    }
                                    .getOrNull()
                            }
                        }
                        .awaitAll()
                        .filterNotNull()

                val items =
                    results
                        .flatMap { response ->
                            val books = response.book?.map { it.libraryItem } ?: emptyList()
                            val podcasts = response.podcast?.map { it.libraryItem } ?: emptyList()
                            books + podcasts
                        }
                        .distinctBy { it.id }

                _uiState.update {
                    it.copy(audiobookshelfSearchResults = items, isAudiobookshelfSearching = false)
                }
                Timber.d("Audiobookshelf search completed: ${items.size} results for '$query'")
            } catch (e: Exception) {
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

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isJellyseerrSearching = true) }
                jellyseerrRepository
                    .findMediaByName(query)
                    .fold(
                        onSuccess = { results ->
                            _uiState.update {
                                it.copy(
                                    jellyseerrSearchResults = results,
                                    isJellyseerrSearching = false,
                                )
                            }
                            Timber.d("Jellyseerr search completed: ${results.size} results")
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
                _uiState.update {
                    it.copy(jellyseerrSearchResults = emptyList(), isJellyseerrSearching = false)
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
        if (!user.hasPermission(Permissions.REQUEST_ADVANCED) &&
            !user.hasPermission(Permissions.REQUEST_4K) &&
            !user.hasPermission(Permissions.MANAGE_REQUESTS)
        ) return
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
                        val preselected =
                            details.profiles.find { it.id == activeProfileId }
                        val rootFolder = details.server?.activeDirectory
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
