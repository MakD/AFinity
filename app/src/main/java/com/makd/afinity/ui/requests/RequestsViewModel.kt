package com.makd.afinity.ui.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.jellyseerr.GenreSliderItem
import com.makd.afinity.data.models.jellyseerr.JellyseerrRequest
import com.makd.afinity.data.models.jellyseerr.JellyseerrUser
import com.makd.afinity.data.models.jellyseerr.MediaStatus
import com.makd.afinity.data.models.jellyseerr.MediaType
import com.makd.afinity.data.models.jellyseerr.Network
import com.makd.afinity.data.models.jellyseerr.SearchResultItem
import com.makd.afinity.data.models.jellyseerr.Studio
import com.makd.afinity.data.repository.JellyseerrRepository
import com.makd.afinity.util.BackdropTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class RequestsViewModel @Inject constructor(
    private val jellyseerrRepository: JellyseerrRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RequestsUiState())
    val uiState: StateFlow<RequestsUiState> = _uiState.asStateFlow()

    val isAuthenticated = jellyseerrRepository.isAuthenticated

    private val _currentUser = MutableStateFlow<JellyseerrUser?>(null)
    val currentUser: StateFlow<JellyseerrUser?> = _currentUser.asStateFlow()
    private val _backdropTracker = BackdropTracker()
    val backdropTracker: BackdropTracker get() = _backdropTracker

    init {
        loadRequests()
        loadDiscoverContent()
        loadCurrentUser()
    }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            jellyseerrRepository.getCurrentUser().fold(
                onSuccess = { user ->
                    _currentUser.value = user
                    Timber.d("Loaded current user: ${user.displayName}, isAdmin: ${user.permissions}")
                },
                onFailure = { error ->
                    Timber.e(error, "Failed to load current user")
                }
            )
        }
    }

    fun loadRequests() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }

                jellyseerrRepository.getRequests().fold(
                    onSuccess = { requests ->
                        _uiState.update {
                            it.copy(
                                requests = requests,
                                isLoading = false
                            )
                        }
                        Timber.d("Loaded ${requests.size} requests")
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = error.message ?: "Failed to load requests"
                            )
                        }
                        Timber.e(error, "Failed to load requests")
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Unknown error"
                    )
                }
                Timber.e(e, "Error loading requests")
            }
        }
    }

    fun deleteRequest(requestId: Int) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isDeletingRequest = true) }

                jellyseerrRepository.deleteRequest(requestId).fold(
                    onSuccess = {
                        _uiState.update { it.copy(isDeletingRequest = false) }
                        loadRequests()
                        Timber.d("Request $requestId deleted successfully")
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isDeletingRequest = false,
                                error = "Failed to delete request: ${error.message}"
                            )
                        }
                        Timber.e(error, "Failed to delete request")
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isDeletingRequest = false,
                        error = e.message ?: "Unknown error"
                    )
                }
                Timber.e(e, "Error deleting request")
            }
        }
    }

    fun approveRequest(requestId: Int) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isProcessingRequest = true) }

                jellyseerrRepository.approveRequest(requestId).fold(
                    onSuccess = { updatedRequest ->
                        _uiState.update {
                            it.copy(
                                isProcessingRequest = false,
                                requests = it.requests.map { request ->
                                    if (request.id == requestId) {
                                        updatedRequest.copy(
                                            media = updatedRequest.media.copy(
                                                title = request.media.title,
                                                name = request.media.name,
                                                posterPath = request.media.posterPath,
                                                backdropPath = request.media.backdropPath,
                                                releaseDate = request.media.releaseDate,
                                                firstAirDate = request.media.firstAirDate
                                            )
                                        )
                                    } else request
                                }
                            )
                        }
                        Timber.d("Request $requestId approved successfully")
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isProcessingRequest = false,
                                error = "Failed to approve request: ${error.message}"
                            )
                        }
                        Timber.e(error, "Failed to approve request")
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessingRequest = false,
                        error = e.message ?: "Unknown error"
                    )
                }
                Timber.e(e, "Error approving request")
            }
        }
    }

    fun declineRequest(requestId: Int) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isProcessingRequest = true) }

                jellyseerrRepository.declineRequest(requestId).fold(
                    onSuccess = { updatedRequest ->
                        _uiState.update {
                            it.copy(
                                isProcessingRequest = false,
                                requests = it.requests.map { request ->
                                    if (request.id == requestId) {
                                        updatedRequest.copy(
                                            media = updatedRequest.media.copy(
                                                title = request.media.title,
                                                name = request.media.name,
                                                posterPath = request.media.posterPath,
                                                backdropPath = request.media.backdropPath,
                                                releaseDate = request.media.releaseDate,
                                                firstAirDate = request.media.firstAirDate
                                            )
                                        )
                                    } else request
                                }
                            )
                        }
                        Timber.d("Request $requestId declined successfully")
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isProcessingRequest = false,
                                error = "Failed to decline request: ${error.message}"
                            )
                        }
                        Timber.e(error, "Failed to decline request")
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessingRequest = false,
                        error = e.message ?: "Unknown error"
                    )
                }
                Timber.e(e, "Error declining request")
            }
        }
    }

    fun searchForContent(query: String, mediaType: MediaType? = null) {
        viewModelScope.launch {
            try {
                _uiState.update {
                    it.copy(
                        isSearching = true,
                        searchQuery = query,
                        searchError = null
                    )
                }

                jellyseerrRepository.findMediaByName(query, mediaType).fold(
                    onSuccess = { results ->
                        _uiState.update {
                            it.copy(
                                searchResults = results,
                                isSearching = false,
                                showSearchDialog = results.isNotEmpty()
                            )
                        }
                        if (results.isEmpty()) {
                            _uiState.update {
                                it.copy(searchError = "No results found for '$query'")
                            }
                        }
                        Timber.d("Found ${results.size} search results")
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isSearching = false,
                                searchError = "Search failed: ${error.message}"
                            )
                        }
                        Timber.e(error, "Search failed")
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        searchError = e.message ?: "Unknown error"
                    )
                }
                Timber.e(e, "Error searching")
            }
        }
    }

    fun createRequest(mediaId: Int, mediaType: MediaType, seasons: List<Int>? = null) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isCreatingRequest = true) }

                jellyseerrRepository.createRequest(mediaId, mediaType, seasons).fold(
                    onSuccess = { newRequest ->
                        _uiState.update {
                            it.copy(
                                isCreatingRequest = false,
                                showSearchDialog = false,
                                searchResults = emptyList(),
                                searchQuery = ""
                            )
                        }
                        loadRequests()
                        Timber.d("Request created successfully: ${newRequest.id}")
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isCreatingRequest = false,
                                error = "Failed to create request: ${error.message}"
                            )
                        }
                        Timber.e(error, "Failed to create request")
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isCreatingRequest = false,
                        error = e.message ?: "Unknown error"
                    )
                }
                Timber.e(e, "Error creating request")
            }
        }
    }

    fun dismissSearchDialog() {
        _uiState.update {
            it.copy(
                showSearchDialog = false,
                searchResults = emptyList(),
                searchQuery = "",
                searchError = null
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearSearchError() {
        _uiState.update { it.copy(searchError = null) }
    }

    fun showSearchDialog() {
        _uiState.update { it.copy(showSearchDialog = true) }
    }

    fun loadDiscoverContent() {
        viewModelScope.launch {
            try {
                _backdropTracker.reset()

                _uiState.update { it.copy(isLoadingDiscover = true) }

                val trendingDeferred = async { jellyseerrRepository.getTrending() }
                val popularMoviesDeferred = async { jellyseerrRepository.getDiscoverMovies() }
                val popularTvDeferred = async { jellyseerrRepository.getDiscoverTv() }
                val upcomingMoviesDeferred = async { jellyseerrRepository.getUpcomingMovies() }
                val upcomingTvDeferred = async { jellyseerrRepository.getUpcomingTv() }
                val movieGenresDeferred = async { jellyseerrRepository.getMovieGenreSlider() }
                val tvGenresDeferred = async { jellyseerrRepository.getTvGenreSlider() }

                val trendingResult = trendingDeferred.await()
                val popularMoviesResult = popularMoviesDeferred.await()
                val popularTvResult = popularTvDeferred.await()
                val upcomingMoviesResult = upcomingMoviesDeferred.await()
                val upcomingTvResult = upcomingTvDeferred.await()
                val movieGenresResult = movieGenresDeferred.await()
                val tvGenresResult = tvGenresDeferred.await()

                movieGenresResult.onFailure { error ->
                    Timber.e(error, "Failed to load movie genres")
                }

                tvGenresResult.onFailure { error ->
                    Timber.e(error, "Failed to load TV genres")
                }

                _uiState.update {
                    it.copy(
                        trendingItems = trendingResult.getOrNull()?.results ?: emptyList(),
                        popularMovies = popularMoviesResult.getOrNull()?.results ?: emptyList(),
                        popularTv = popularTvResult.getOrNull()?.results ?: emptyList(),
                        upcomingMovies = upcomingMoviesResult.getOrNull()?.results ?: emptyList(),
                        upcomingTv = upcomingTvResult.getOrNull()?.results ?: emptyList(),
                        movieGenres = movieGenresResult.getOrNull() ?: emptyList(),
                        tvGenres = tvGenresResult.getOrNull() ?: emptyList(),
                        isLoadingDiscover = false
                    )
                }

                Timber.d("Loaded discover content: ${trendingResult.getOrNull()?.results?.size ?: 0} trending items, ${movieGenresResult.getOrNull()?.size ?: 0} movie genres, ${tvGenresResult.getOrNull()?.size ?: 0} TV genres")
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingDiscover = false) }
                Timber.e(e, "Error loading discover content")
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
                if (mediaType == MediaType.TV) {
                    _uiState.update { it.copy(isFetchingTvDetails = true) }

                    jellyseerrRepository.getTvDetails(tmdbId).fold(
                        onSuccess = { tvDetails ->
                            _uiState.update { it.copy(isFetchingTvDetails = false) }

                            val seasonCount = tvDetails.getSeasonCount()
                            val alreadyAvailableSeasons = tvDetails.mediaInfo?.getAvailableSeasons() ?: emptyList()

                            val selectableSeasons = (1..seasonCount).filter { it !in alreadyAvailableSeasons }

                            _uiState.update {
                                it.copy(
                                    showRequestDialog = true,
                                    pendingRequest = PendingRequest(tmdbId, mediaType, title, posterUrl, seasonCount, existingStatus),
                                    selectedSeasons = selectableSeasons,
                                    disabledSeasons = alreadyAvailableSeasons
                                )
                            }
                        },
                        onFailure = { error ->
                            _uiState.update { it.copy(isFetchingTvDetails = false) }
                            Timber.w(error, "Failed to fetch TV details, using fallback season count")

                            _uiState.update {
                                it.copy(
                                    showRequestDialog = true,
                                    pendingRequest = PendingRequest(tmdbId, mediaType, title, posterUrl, availableSeasons, existingStatus),
                                    selectedSeasons = if (availableSeasons > 0) (1..availableSeasons).toList() else emptyList(),
                                    disabledSeasons = emptyList()
                                )
                            }
                        }
                    )
                } else {
                    _uiState.update {
                        it.copy(
                            showRequestDialog = true,
                            pendingRequest = PendingRequest(tmdbId, mediaType, title, posterUrl, 0, existingStatus),
                            selectedSeasons = emptyList(),
                            disabledSeasons = emptyList()
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error showing request dialog")
                _uiState.update {
                    it.copy(
                        showRequestDialog = true,
                        pendingRequest = PendingRequest(tmdbId, mediaType, title, posterUrl, availableSeasons, existingStatus),
                        selectedSeasons = if (mediaType == MediaType.TV && availableSeasons > 0) (1..availableSeasons).toList() else emptyList(),
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
                        val updatedMediaInfo = newRequest.media.copy(
                            requests = listOfNotNull(newRequest)
                        )

                        val updateItemStatus: (SearchResultItem) -> SearchResultItem = { item ->
                            if (item.id == pending.tmdbId) {
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
                                disabledSeasons = emptyList(),
                                trendingItems = it.trendingItems.map(updateItemStatus),
                                popularMovies = it.popularMovies.map(updateItemStatus),
                                popularTv = it.popularTv.map(updateItemStatus),
                                upcomingMovies = it.upcomingMovies.map(updateItemStatus),
                                upcomingTv = it.upcomingTv.map(updateItemStatus)
                            )
                        }
                        loadRequests()
                        Timber.d("Request created successfully: ${newRequest.id}")
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isCreatingRequest = false,
                                error = "Failed to create request: ${error.message}"
                            )
                        }
                        Timber.e(error, "Failed to create request")
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isCreatingRequest = false,
                        error = e.message ?: "Unknown error"
                    )
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

data class RequestsUiState(
    val requests: List<JellyseerrRequest> = emptyList(),
    val isLoading: Boolean = false,
    val isSearching: Boolean = false,
    val isCreatingRequest: Boolean = false,
    val isDeletingRequest: Boolean = false,
    val isProcessingRequest: Boolean = false,
    val error: String? = null,
    val searchResults: List<SearchResultItem> = emptyList(),
    val searchQuery: String = "",
    val searchError: String? = null,
    val showSearchDialog: Boolean = false,

    val trendingItems: List<SearchResultItem> = emptyList(),
    val popularMovies: List<SearchResultItem> = emptyList(),
    val popularTv: List<SearchResultItem> = emptyList(),
    val upcomingMovies: List<SearchResultItem> = emptyList(),
    val upcomingTv: List<SearchResultItem> = emptyList(),
    val studios: List<Studio> = Studio.getPopularStudios(),
    val networks: List<Network> = Network.getPopularNetworks(),
    val movieGenres: List<GenreSliderItem> = emptyList(),
    val tvGenres: List<GenreSliderItem> = emptyList(),
    val isLoadingDiscover: Boolean = false,

    val showRequestDialog: Boolean = false,
    val pendingRequest: PendingRequest? = null,
    val selectedSeasons: List<Int> = emptyList(),
    val disabledSeasons: List<Int> = emptyList(),
    val isFetchingTvDetails: Boolean = false
)

data class PendingRequest(
    val tmdbId: Int,
    val mediaType: MediaType,
    val title: String,
    val posterUrl: String?,
    val availableSeasons: Int = 0,
    val existingStatus: MediaStatus? = null
)
