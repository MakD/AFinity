package com.makd.afinity.ui.requests

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.R
import com.makd.afinity.data.models.jellyseerr.GenreSliderItem
import com.makd.afinity.data.models.jellyseerr.JellyseerrRequest
import com.makd.afinity.data.models.jellyseerr.JellyseerrUser
import com.makd.afinity.data.models.jellyseerr.MediaDetails
import com.makd.afinity.data.models.jellyseerr.MediaStatus
import com.makd.afinity.data.models.jellyseerr.MediaType
import com.makd.afinity.data.models.jellyseerr.Network
import com.makd.afinity.data.models.jellyseerr.Permissions
import com.makd.afinity.data.models.jellyseerr.QualityProfile
import com.makd.afinity.data.models.jellyseerr.RatingsCombined
import com.makd.afinity.data.models.jellyseerr.SearchResultItem
import com.makd.afinity.data.models.jellyseerr.ServiceSettings
import com.makd.afinity.data.models.jellyseerr.Studio
import com.makd.afinity.data.models.jellyseerr.hasPermission
import com.makd.afinity.data.repository.JellyseerrRepository
import com.makd.afinity.util.BackdropTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class RequestsViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val jellyseerrRepository: JellyseerrRepository,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(RequestsUiState(isLoading = true, isLoadingDiscover = true))
    val uiState: StateFlow<RequestsUiState> = _uiState.asStateFlow()

    val isAuthenticated = jellyseerrRepository.isAuthenticated

    private val _currentUser = MutableStateFlow<JellyseerrUser?>(null)
    val currentUser: StateFlow<JellyseerrUser?> = _currentUser.asStateFlow()
    private val _backdropTracker = BackdropTracker()
    val backdropTracker: BackdropTracker
        get() = _backdropTracker

    private var requestsJob: Job? = null

    init {
        viewModelScope.launch {
            combine(jellyseerrRepository.currentSessionId, jellyseerrRepository.isAuthenticated) {
                    sessionId,
                    isAuth ->
                    sessionId to isAuth
                }
                .collect { (sessionId, isAuth) ->
                    if (sessionId != null && isAuth) {
                        Timber.d(
                            "Session Active & Authenticated ($sessionId). Reloading ALL content..."
                        )
                        val serverUrl = jellyseerrRepository.getServerUrl()
                        _uiState.update { it.copy(jellyseerrUrl = serverUrl) }
                        loadCurrentUser()
                        observeRequests()
                        loadRequests()
                        loadDiscoverContent()
                    } else {
                        Timber.d("Session Inactive or Logged Out. Clearing content...")
                        _currentUser.value = null
                        requestsJob?.cancel()
                        _uiState.update {
                            it.copy(
                                jellyseerrUrl = null,
                                requests = emptyList(),
                                trendingItems = emptyList(),
                                popularMovies = emptyList(),
                                popularTv = emptyList(),
                                upcomingMovies = emptyList(),
                                upcomingTv = emptyList(),
                                movieGenres = emptyList(),
                                tvGenres = emptyList(),
                                isLoadingDiscover = false,
                            )
                        }
                    }
                }
        }
        observeRequestEvents()
    }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            jellyseerrRepository
                .getCurrentUser()
                .fold(
                    onSuccess = { user ->
                        _currentUser.value = user
                        Timber.d("Loaded current user: ${user.displayName}")
                    },
                    onFailure = { error -> Timber.e(error, "Failed to load current user") },
                )
        }
    }

    private suspend fun refreshCurrentUser() {
        jellyseerrRepository.getCurrentUser().onSuccess { user -> _currentUser.value = user }
    }

    private fun observeRequests() {
        requestsJob?.cancel()
        requestsJob =
            viewModelScope.launch {
                try {
                    jellyseerrRepository.observeRequests().collect { requests ->
                        _uiState.update { it.copy(requests = requests, isLoading = false) }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error observing requests")
                }
            }
    }

    private fun observeRequestEvents() {
        viewModelScope.launch {
            jellyseerrRepository.requestEvents.collect { event ->
                val updatedRequest = event.request
                val updatedMediaInfo =
                    updatedRequest.media.copy(requests = listOfNotNull(updatedRequest))

                val updateItemStatus: (SearchResultItem) -> SearchResultItem = { item ->
                    if (item.id == updatedRequest.media.tmdbId) {
                        item.copy(mediaInfo = updatedMediaInfo)
                    } else {
                        item
                    }
                }

                _uiState.update {
                    it.copy(
                        trendingItems = it.trendingItems.map(updateItemStatus),
                        popularMovies = it.popularMovies.map(updateItemStatus),
                        popularTv = it.popularTv.map(updateItemStatus),
                        upcomingMovies = it.upcomingMovies.map(updateItemStatus),
                        upcomingTv = it.upcomingTv.map(updateItemStatus),
                    )
                }
            }
        }
    }

    fun loadRequests() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoading = true, error = null) }
                jellyseerrRepository.getRequests().onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error =
                                error.message
                                    ?: context.getString(R.string.error_requests_load_failed),
                        )
                    }
                    Timber.e(error, "Failed to load requests")
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: context.getString(R.string.error_unknown),
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
                jellyseerrRepository
                    .deleteRequest(requestId)
                    .fold(
                        onSuccess = {
                            _uiState.update {
                                it.copy(isDeletingRequest = false, selectedRequest = null)
                            }
                            Timber.d("Request $requestId deleted successfully")
                        },
                        onFailure = { error ->
                            _uiState.update {
                                it.copy(
                                    isDeletingRequest = false,
                                    error =
                                        context.getString(
                                            R.string.error_request_delete_failed_fmt,
                                            error.message,
                                        ),
                                )
                            }
                            Timber.e(error, "Failed to delete request")
                        },
                    )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isDeletingRequest = false,
                        error = e.message ?: context.getString(R.string.error_unknown),
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
                jellyseerrRepository
                    .approveRequest(requestId)
                    .fold(
                        onSuccess = { updatedRequest ->
                            _uiState.update {
                                it.copy(
                                    isProcessingRequest = false,
                                    requests =
                                        it.requests.map { req ->
                                            if (req.id == requestId) updatedRequest else req
                                        },
                                    selectedRequest =
                                        if (it.selectedRequest?.id == requestId) updatedRequest
                                        else it.selectedRequest,
                                )
                            }
                        },
                        onFailure = { error ->
                            _uiState.update {
                                it.copy(
                                    isProcessingRequest = false,
                                    error =
                                        context.getString(
                                            R.string.error_request_approve_failed_fmt,
                                            error.message,
                                        ),
                                )
                            }
                        },
                    )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessingRequest = false,
                        error = e.message ?: context.getString(R.string.error_unknown),
                    )
                }
            }
        }
    }

    fun declineRequest(requestId: Int) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isProcessingRequest = true) }
                jellyseerrRepository
                    .declineRequest(requestId)
                    .fold(
                        onSuccess = { updatedRequest ->
                            _uiState.update {
                                it.copy(
                                    isProcessingRequest = false,
                                    requests =
                                        it.requests.map { req ->
                                            if (req.id == requestId) updatedRequest else req
                                        },
                                    selectedRequest =
                                        if (it.selectedRequest?.id == requestId) updatedRequest
                                        else it.selectedRequest,
                                )
                            }
                        },
                        onFailure = { error ->
                            _uiState.update {
                                it.copy(
                                    isProcessingRequest = false,
                                    error =
                                        context.getString(
                                            R.string.error_request_decline_failed_fmt,
                                            error.message,
                                        ),
                                )
                            }
                        },
                    )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isProcessingRequest = false,
                        error = e.message ?: context.getString(R.string.error_unknown),
                    )
                }
            }
        }
    }

    fun selectRequest(request: JellyseerrRequest) {
        val initialServerName = request.serverId?.toString()?.let { "ID: $it" } ?: "Default"
        val initialProfileName = request.profileId?.toString()?.let { "ID: $it" } ?: "Default"

        _uiState.update {
            it.copy(
                selectedRequest = request,
                selectedRequestDetails = null,
                selectedRequestServerName = initialServerName,
                selectedRequestProfileName = initialProfileName,
                isLoadingDetails = true,
            )
        }

        val tmdbId = request.media.tmdbId ?: return
        val mediaType = request.getMediaType() ?: MediaType.MOVIE

        viewModelScope.launch {
            val result =
                if (mediaType == MediaType.TV) {
                    jellyseerrRepository.getTvDetails(tmdbId)
                } else {
                    jellyseerrRepository.getMovieDetails(tmdbId)
                }

            result.fold(
                onSuccess = { details ->
                    _uiState.update {
                        it.copy(selectedRequestDetails = details, isLoadingDetails = false)
                    }
                },
                onFailure = { error ->
                    Timber.e(error, "Failed to load details for request")
                    _uiState.update { it.copy(isLoadingDetails = false) }
                },
            )
        }

        val user = _currentUser.value
        if (
            user != null &&
                (user.hasPermission(Permissions.MANAGE_REQUESTS) ||
                    user.hasPermission(Permissions.REQUEST_ADVANCED))
        ) {
            viewModelScope.launch {
                jellyseerrRepository
                    .getServiceSettings(mediaType)
                    .onSuccess { servers ->
                        val server =
                            servers.find { it.id == request.serverId }
                                ?: servers.firstOrNull { it.isDefault }
                                ?: servers.firstOrNull()

                        if (server != null) {
                            _uiState.update { it.copy(selectedRequestServerName = server.name) }
                        }

                        val targetServiceId = request.serverId ?: server?.id

                        if (targetServiceId != null) {
                            jellyseerrRepository
                                .getServiceDetails(mediaType, targetServiceId)
                                .onSuccess { serviceDetails ->
                                    val profile =
                                        if (request.profileId != null) {
                                            serviceDetails.profiles.find {
                                                it.id == request.profileId
                                            }
                                        } else {
                                            serviceDetails.profiles.find {
                                                it.id == serviceDetails.server?.activeProfileId
                                            }
                                        }

                                    if (profile?.name != null) {
                                        _uiState.update {
                                            it.copy(selectedRequestProfileName = profile.name)
                                        }
                                    }
                                }
                        }
                    }
                    .onFailure {
                        Timber.w(it, "Failed to resolve server/profile names (Permission issue?)")
                    }
            }
        }
    }

    fun dismissManagementDialog() {
        _uiState.update {
            it.copy(
                selectedRequest = null,
                selectedRequestDetails = null,
                selectedRequestServerName = null,
                selectedRequestProfileName = null,
                isLoadingDetails = false,
            )
        }
    }

    fun searchForContent(query: String, mediaType: MediaType? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, searchQuery = query, searchError = null) }
            jellyseerrRepository
                .findMediaByName(query, mediaType)
                .fold(
                    onSuccess = { results ->
                        _uiState.update {
                            it.copy(
                                searchResults = results,
                                isSearching = false,
                                showSearchDialog = results.isNotEmpty(),
                            )
                        }
                        if (results.isEmpty()) {
                            _uiState.update {
                                it.copy(
                                    searchError =
                                        context.getString(
                                            R.string.error_search_no_results_fmt,
                                            query,
                                        )
                                )
                            }
                        }
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isSearching = false,
                                searchError =
                                    context.getString(
                                        R.string.error_search_failed_fmt,
                                        error.message,
                                    ),
                            )
                        }
                    },
                )
        }
    }

    fun createRequest(mediaId: Int, mediaType: MediaType, seasons: List<Int>? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingRequest = true) }
            jellyseerrRepository
                .createRequest(mediaId, mediaType, seasons)
                .fold(
                    onSuccess = { newRequest ->
                        _uiState.update {
                            it.copy(
                                isCreatingRequest = false,
                                showSearchDialog = false,
                                searchResults = emptyList(),
                                searchQuery = "",
                            )
                        }
                        loadRequests()
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isCreatingRequest = false,
                                error =
                                    context.getString(
                                        R.string.error_request_create_failed_fmt,
                                        error.message,
                                    ),
                            )
                        }
                    },
                )
        }
    }

    fun dismissSearchDialog() {
        _uiState.update {
            it.copy(
                showSearchDialog = false,
                searchResults = emptyList(),
                searchQuery = "",
                searchError = null,
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
        _backdropTracker.reset()
        _uiState.update { it.copy(isLoadingDiscover = true) }
        viewModelScope.launch {
            jellyseerrRepository
                .getTrending(10)
                .fold(
                    onSuccess = { res ->
                        _uiState.update {
                            it.copy(trendingItems = res.results, isLoadingDiscover = false)
                        }
                    },
                    onFailure = { _uiState.update { it.copy(isLoadingDiscover = false) } },
                )
        }
        viewModelScope.launch {
            jellyseerrRepository.getDiscoverMovies().onSuccess { res ->
                _uiState.update { it.copy(popularMovies = res.results) }
            }
        }
        viewModelScope.launch {
            jellyseerrRepository.getDiscoverTv().onSuccess { res ->
                _uiState.update { it.copy(popularTv = res.results) }
            }
        }
        viewModelScope.launch {
            jellyseerrRepository.getUpcomingMovies().onSuccess { res ->
                _uiState.update { it.copy(upcomingMovies = res.results) }
            }
        }
        viewModelScope.launch {
            jellyseerrRepository.getUpcomingTv().onSuccess { res ->
                _uiState.update { it.copy(upcomingTv = res.results) }
            }
        }
        viewModelScope.launch {
            jellyseerrRepository.getMovieGenreSlider().onSuccess { res ->
                _uiState.update { it.copy(movieGenres = res) }
            }
        }
        viewModelScope.launch {
            jellyseerrRepository.getTvGenreSlider().onSuccess { res ->
                _uiState.update { it.copy(tvGenres = res) }
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
            val authJob = async { refreshCurrentUser() }

            _uiState.update { it.copy(isFetchingTvDetails = true) }
            val detailsJob = async {
                if (mediaType == MediaType.TV) {
                    jellyseerrRepository.getTvDetails(tmdbId)
                } else {
                    jellyseerrRepository.getMovieDetails(tmdbId)
                }
            }

            authJob.await()

            val detailsResult = detailsJob.await()

            detailsResult.fold(
                onSuccess = { details ->
                    _uiState.update { it.copy(isFetchingTvDetails = false) }

                    val seasonCount = if (mediaType == MediaType.TV) details.getSeasonCount() else 0
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
                                PendingRequest(
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
                                PendingRequest(
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
        }
    }

    fun confirmRequest() {
        val pending = _uiState.value.pendingRequest ?: return
        val state = _uiState.value
        val seasons =
            if (pending.mediaType == MediaType.TV) state.selectedSeasons.takeIf { it.isNotEmpty() }
            else null
        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingRequest = true) }
            jellyseerrRepository
                .createRequest(
                    pending.tmdbId,
                    pending.mediaType,
                    seasons,
                    state.is4kRequested,
                    state.selectedServer?.id,
                    state.selectedProfile?.id,
                    state.selectedRootFolder,
                )
                .fold(
                    onSuccess = { newRequest ->
                        val updatedMediaInfo =
                            newRequest.media.copy(requests = listOfNotNull(newRequest))
                        val updateItemStatus: (SearchResultItem) -> SearchResultItem = {
                            if (it.id == pending.tmdbId) it.copy(mediaInfo = updatedMediaInfo)
                            else it
                        }
                        _uiState.update {
                            it.copy(
                                isCreatingRequest = false,
                                showRequestDialog = false,
                                pendingRequest = null,
                                selectedSeasons = emptyList(),
                                disabledSeasons = emptyList(),
                                is4kRequested = false,
                                availableServers = emptyList(),
                                selectedServer = null,
                                availableProfiles = emptyList(),
                                selectedProfile = null,
                                selectedRootFolder = null,
                                trendingItems = it.trendingItems.map(updateItemStatus),
                                popularMovies = it.popularMovies.map(updateItemStatus),
                                popularTv = it.popularTv.map(updateItemStatus),
                                upcomingMovies = it.upcomingMovies.map(updateItemStatus),
                                upcomingTv = it.upcomingTv.map(updateItemStatus),
                            )
                        }
                        loadRequests()
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isCreatingRequest = false,
                                error =
                                    context.getString(
                                        R.string.error_request_create_failed_fmt,
                                        error.message,
                                    ),
                            )
                        }
                    },
                )
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
                        val defaultServer =
                            filtered.firstOrNull { it.isDefault } ?: filtered.firstOrNull()
                        _uiState.update {
                            it.copy(
                                availableServers = filtered,
                                selectedServer = defaultServer,
                                isLoadingServers = false,
                            )
                        }
                        if (defaultServer != null) loadQualityProfiles(mediaType, defaultServer.id)
                    },
                    onFailure = { _uiState.update { it.copy(isLoadingServers = false) } },
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
                        val preselected =
                            details.profiles.find { it.id == details.server?.activeProfileId }
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
                    onFailure = { _uiState.update { it.copy(isLoadingProfiles = false) } },
                )
        }
    }
}

data class RequestsUiState(
    val jellyseerrUrl: String? = null,
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
    val isFetchingTvDetails: Boolean = false,
    val is4kRequested: Boolean = false,
    val availableServers: List<ServiceSettings> = emptyList(),
    val selectedServer: ServiceSettings? = null,
    val availableProfiles: List<QualityProfile> = emptyList(),
    val selectedProfile: QualityProfile? = null,
    val selectedRootFolder: String? = null,
    val isLoadingServers: Boolean = false,
    val isLoadingProfiles: Boolean = false,
    val selectedRequest: JellyseerrRequest? = null,
    val selectedRequestDetails: MediaDetails? = null,
    val isLoadingDetails: Boolean = false,
    val selectedRequestServerName: String? = null,
    val selectedRequestProfileName: String? = null,
)

data class PendingRequest(
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
