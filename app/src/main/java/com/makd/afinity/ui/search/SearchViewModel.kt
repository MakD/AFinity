package com.makd.afinity.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.extensions.toAfinityItem
import com.makd.afinity.data.models.jellyseerr.MediaStatus
import com.makd.afinity.data.models.jellyseerr.MediaType
import com.makd.afinity.data.models.jellyseerr.RatingsCombined
import com.makd.afinity.data.models.jellyseerr.SearchResultItem
import com.makd.afinity.data.models.media.AfinityCollection
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.JellyfinRepository
import com.makd.afinity.data.repository.JellyseerrRepository
import com.makd.afinity.data.repository.media.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val jellyfinRepository: JellyfinRepository,
    private val jellyseerrRepository: JellyseerrRepository,
    private val appDataRepository: AppDataRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    val isJellyseerrAuthenticated = jellyseerrRepository.isAuthenticated

    init {
        viewModelScope.launch {
            appDataRepository.isInitialDataLoaded.collect { isLoaded ->
                if (isLoaded) {
                    loadLibraries()
                    loadGenres()

                    if (_uiState.value.searchQuery.isNotEmpty()) {
                        if (_uiState.value.isJellyseerrSearchMode) {
                            performJellyseerrSearch()
                        } else {
                            performSearch()
                        }
                    }
                } else {
                    _uiState.value = SearchUiState()
                }
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
                val genres = mediaRepository.getGenres(
                    parentId = selectedLibraryId,
                    limit = 100,
                    includeItemTypes = listOf("MOVIE", "SERIES", "BOXSET")
                )
                _uiState.value = _uiState.value.copy(genres = genres)
                Timber.d("Loaded ${genres.size} genres from API")
            } catch (e: Exception) {
                Timber.e(e, "Failed to load genres")
                _uiState.value = _uiState.value.copy(genres = emptyList())
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)

        if (query.length >= 2) {
            viewModelScope.launch {
                kotlinx.coroutines.delay(500)
                if (_uiState.value.searchQuery == query) {
                    if (_uiState.value.isJellyseerrSearchMode) {
                        performJellyseerrSearch()
                    } else {
                        performSearch()
                    }
                }
            }
        } else if (query.isEmpty()) {
            _uiState.value = _uiState.value.copy(
                searchResults = emptyList(),
                jellyseerrSearchResults = emptyList()
            )
        }
    }

    fun selectLibrary(library: AfinityCollection?) {
        _uiState.value = _uiState.value.copy(selectedLibrary = library)

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

                val results = mediaRepository.getItems(
                    parentId = _uiState.value.selectedLibrary?.id,
                    searchTerm = query,
                    includeItemTypes = listOf("MOVIE", "SERIES"),
                    limit = 50,
                    sortBy = SortBy.NAME,
                    sortDescending = false,
                    fields = FieldSets.SEARCH_RESULTS
                )

                val afinityItems = results.items?.mapNotNull { baseItemDto ->
                    try {
                        val item = baseItemDto.toAfinityItem(jellyfinRepository.getBaseUrl())
                        when (item) {
                            is AfinityMovie -> item
                            is AfinityShow -> item
                            else -> null
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to convert item: ${baseItemDto.name}")
                        null
                    }
                }?.let { items ->
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

                _uiState.value = _uiState.value.copy(
                    searchResults = afinityItems,
                    isSearching = false
                )

                Timber.d("Search completed: ${afinityItems.size} results for '$query'")

            } catch (e: Exception) {
                Timber.e(e, "Failed to perform search")
                _uiState.value = _uiState.value.copy(
                    searchResults = emptyList(),
                    isSearching = false
                )
            }
        }
    }

    fun clearSearch() {
        _uiState.value = _uiState.value.copy(
            searchQuery = "",
            searchResults = emptyList(),
            isSearching = false,
            jellyseerrSearchResults = emptyList(),
            isJellyseerrSearching = false
        )
    }

    fun selectJellyseerrSearchMode() {
        _uiState.update {
            it.copy(
                isJellyseerrSearchMode = true,
                selectedLibrary = null
            )
        }

        if (_uiState.value.searchQuery.isNotEmpty()) {
            performJellyseerrSearch()
        }
    }

    fun selectJellyfinSearchMode() {
        _uiState.update {
            it.copy(
                isJellyseerrSearchMode = false,
                jellyseerrSearchResults = emptyList()
            )
        }

        if (_uiState.value.searchQuery.isNotEmpty()) {
            performSearch()
        }
    }

    fun performJellyseerrSearch() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isEmpty()) return

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isJellyseerrSearching = true) }
                jellyseerrRepository.findMediaByName(query).fold(
                    onSuccess = { results ->
                        _uiState.update {
                            it.copy(
                                jellyseerrSearchResults = results,
                                isJellyseerrSearching = false
                            )
                        }
                        Timber.d("Jellyseerr search completed: ${results.size} results")
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                jellyseerrSearchResults = emptyList(),
                                isJellyseerrSearching = false
                            )
                        }
                        Timber.e(error, "Jellyseerr search failed")
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        jellyseerrSearchResults = emptyList(),
                        isJellyseerrSearching = false
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
        existingStatus: MediaStatus? = null
    ) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isFetchingTvDetails = true) }

                val detailsResult = if (mediaType == MediaType.TV) {
                    jellyseerrRepository.getTvDetails(tmdbId)
                } else {
                    jellyseerrRepository.getMovieDetails(tmdbId)
                }

                detailsResult.fold(
                    onSuccess = { details ->
                        _uiState.update { it.copy(isFetchingTvDetails = false) }

                        val seasonCount = if (mediaType == MediaType.TV) details.getSeasonCount() else 0
                        val alreadyAvailableSeasons = details.mediaInfo?.getAvailableSeasons() ?: emptyList()
                        val selectableSeasons = if (mediaType == MediaType.TV) {
                            (1..seasonCount).filter { it !in alreadyAvailableSeasons }
                        } else emptyList()

                        _uiState.update {
                            it.copy(
                                showRequestDialog = true,
                                pendingRequest = PendingRequestSearch(
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
                                    ratingsCombined = details.ratingsCombined
                                ),
                                selectedSeasons = selectableSeasons,
                                disabledSeasons = alreadyAvailableSeasons
                            )
                        }
                    },
                    onFailure = { error ->
                        _uiState.update { it.copy(isFetchingTvDetails = false) }
                        Timber.w(error, "Failed to fetch details, using fallback")

                        _uiState.update {
                            it.copy(
                                showRequestDialog = true,
                                pendingRequest = PendingRequestSearch(
                                    tmdbId = tmdbId,
                                    mediaType = mediaType,
                                    title = title,
                                    posterUrl = posterUrl,
                                    availableSeasons = availableSeasons,
                                    existingStatus = existingStatus
                                ),
                                selectedSeasons = if (mediaType == MediaType.TV && availableSeasons > 0) {
                                    (1..availableSeasons).toList()
                                } else emptyList(),
                                disabledSeasons = emptyList()
                            )
                        }
                    }
                )
            } catch (e: Exception) {
                Timber.e(e, "Error showing request dialog")
                _uiState.update {
                    it.copy(
                        showRequestDialog = true,
                        pendingRequest = PendingRequestSearch(
                            tmdbId = tmdbId,
                            mediaType = mediaType,
                            title = title,
                            posterUrl = posterUrl,
                            availableSeasons = availableSeasons,
                            existingStatus = existingStatus
                        ),
                        selectedSeasons = if (mediaType == MediaType.TV && availableSeasons > 0) {
                            (1..availableSeasons).toList()
                        } else emptyList(),
                        disabledSeasons = emptyList(),
                        isFetchingTvDetails = false
                    )
                }
            }
        }
    }

    fun confirmRequest() {
        val pending = _uiState.value.pendingRequest ?: return
        val seasons = if (pending.mediaType == MediaType.TV) {
            _uiState.value.selectedSeasons.takeIf { it.isNotEmpty() }
        } else null

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isCreatingRequest = true) }

                jellyseerrRepository.createRequest(pending.tmdbId, pending.mediaType, seasons).fold(
                    onSuccess = { newRequest ->
                        val updatedResults = _uiState.value.jellyseerrSearchResults.map { item ->
                            if (item.id == pending.tmdbId) {
                                val updatedMediaInfo = newRequest.media.copy(
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
                                jellyseerrSearchResults = updatedResults
                            )
                        }
                        Timber.d("Request created successfully: ${newRequest.id}")
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(isCreatingRequest = false)
                        }
                        Timber.e(error, "Failed to create request")
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isCreatingRequest = false)
                }
                Timber.e(e, "Error creating request")
            }
        }
    }

    fun dismissRequestDialog() {
        _uiState.update {
            it.copy(
                showRequestDialog = false,
                pendingRequest = null,
                selectedSeasons = emptyList()
            )
        }
    }

    fun setSelectedSeasons(seasons: List<Int>) {
        _uiState.update { it.copy(selectedSeasons = seasons) }
    }
}

data class SearchUiState(
    val searchQuery: String = "",
    val searchResults: List<AfinityItem> = emptyList(),
    val isSearching: Boolean = false,
    val libraries: List<AfinityCollection> = emptyList(),
    val selectedLibrary: AfinityCollection? = null,
    val genres: List<String> = emptyList(),

    val isJellyseerrSearchMode: Boolean = false,
    val jellyseerrSearchResults: List<SearchResultItem> = emptyList(),
    val isJellyseerrSearching: Boolean = false,

    val showRequestDialog: Boolean = false,
    val pendingRequest: PendingRequestSearch? = null,
    val selectedSeasons: List<Int> = emptyList(),
    val disabledSeasons: List<Int> = emptyList(),
    val isCreatingRequest: Boolean = false,
    val isFetchingTvDetails: Boolean = false
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
    val ratingsCombined: RatingsCombined? = null
)