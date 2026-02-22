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
import com.makd.afinity.data.models.jellyseerr.QualityProfile
import com.makd.afinity.data.models.jellyseerr.RatingsCombined
import com.makd.afinity.data.models.jellyseerr.SearchResultItem
import com.makd.afinity.data.models.jellyseerr.ServiceSettings
import com.makd.afinity.data.models.jellyseerr.Studio
import com.makd.afinity.data.repository.JellyseerrRepository
import com.makd.afinity.util.GenreDuotoneColorGenerator
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
                        val serverUrl = jellyseerrRepository.getServerUrl()
                        _uiState.update { it.copy(jellyseerrUrl = serverUrl) }
                        loadCurrentUser()
                        observeRequests()
                        loadRequests()
                        loadDiscoverContent()
                    } else {
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
                    onSuccess = { user -> _currentUser.value = user },
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
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: context.getString(R.string.error_unknown),
                    )
                }
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
                        },
                        onFailure = { error ->
                            _uiState.update {
                                it.copy(
                                    isDeletingRequest = false,
                                    error = "Failed to delete: ${error.message}",
                                )
                            }
                        },
                    )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isDeletingRequest = false, error = e.message ?: "Unknown error")
                }
            }
        }
    }

    fun approveRequest(requestId: Int) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isProcessingRequest = true) }

                var serverId = _uiState.value.selectedServer?.id
                var profileId = _uiState.value.selectedProfile?.id
                var rootFolder = _uiState.value.selectedRootFolder
                val isFromDialog = _uiState.value.selectedRequest?.id == requestId
                if (!isFromDialog && serverId == null) {
                    val request = _uiState.value.requests.find { it.id == requestId }
                    val mediaType = request?.getMediaType() ?: MediaType.MOVIE
                    val is4k = request?.is4k ?: false
                    jellyseerrRepository.getServiceSettings(mediaType).onSuccess { servers ->
                        val defaultServer =
                            servers.firstOrNull { it.is4k == is4k }
                                ?: servers.firstOrNull { it.isDefault }
                                ?: servers.firstOrNull()
                        serverId = defaultServer?.id
                    }

                    serverId?.let { sid ->
                        jellyseerrRepository.getServiceDetails(mediaType, sid).onSuccess { details
                            ->
                            profileId =
                                details.server?.activeProfileId
                                    ?: details.profiles.firstOrNull()?.id
                            rootFolder =
                                details.server?.activeDirectory
                                    ?: details.rootFolders.firstOrNull()?.path
                        }
                    }
                }

                jellyseerrRepository
                    .approveRequest(requestId, serverId, profileId, rootFolder)
                    .fold(
                        onSuccess = { updated ->
                            _uiState.update {
                                it.copy(
                                    isProcessingRequest = false,
                                    requests =
                                        it.requests.map { req ->
                                            if (req.id == requestId) updated else req
                                        },
                                    selectedRequest =
                                        if (it.selectedRequest?.id == requestId) updated
                                        else it.selectedRequest,
                                )
                            }
                            dismissManagementDialog()
                            loadRequests()
                        },
                        onFailure = { error ->
                            _uiState.update {
                                it.copy(
                                    isProcessingRequest = false,
                                    error = "Approve failed: ${error.message}",
                                )
                            }
                        },
                    )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isProcessingRequest = false, error = e.message ?: "Unknown error")
                }
            }
        }
    }

    fun updateRequest(requestId: Int) {
        val request = _uiState.value.selectedRequest ?: return
        val tmdbId = request.media.tmdbId ?: return
        val mediaType = request.getMediaType() ?: MediaType.MOVIE
        val state = _uiState.value
        val seasons =
            if (mediaType == MediaType.TV)
                state.selectedSeasons.takeIf { it.isNotEmpty() }
                    ?: request.seasons?.map { it.seasonNumber }
            else null

        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isProcessingRequest = true) }
                jellyseerrRepository
                    .updateRequest(
                        requestId,
                        tmdbId,
                        mediaType,
                        seasons,
                        state.is4kRequested,
                        state.selectedServer?.id,
                        state.selectedProfile?.id,
                        state.selectedRootFolder,
                    )
                    .fold(
                        onSuccess = { updated ->
                            _uiState.update {
                                it.copy(
                                    isProcessingRequest = false,
                                    requests =
                                        it.requests.map { req ->
                                            if (req.id == requestId) updated else req
                                        },
                                    selectedRequest = updated,
                                )
                            }
                            dismissManagementDialog()
                            loadRequests()
                        },
                        onFailure = { error ->
                            _uiState.update {
                                it.copy(
                                    isProcessingRequest = false,
                                    error = "Update failed: ${error.message}",
                                )
                            }
                        },
                    )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isProcessingRequest = false, error = e.message ?: "Unknown error")
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
                        onSuccess = { updated ->
                            _uiState.update {
                                it.copy(
                                    isProcessingRequest = false,
                                    requests =
                                        it.requests.map { req ->
                                            if (req.id == requestId) updated else req
                                        },
                                    selectedRequest =
                                        if (it.selectedRequest?.id == requestId) updated
                                        else it.selectedRequest,
                                )
                            }
                        },
                        onFailure = { error ->
                            _uiState.update {
                                it.copy(
                                    isProcessingRequest = false,
                                    error = "Decline failed: ${error.message}",
                                )
                            }
                        },
                    )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isProcessingRequest = false, error = e.message ?: "Unknown error")
                }
            }
        }
    }

    fun selectRequest(request: JellyseerrRequest) {
        Timber.d(
            "JellyseerrDebug: selectRequest called for Request ID: ${request.id}, pre-loading data..."
        )

        _uiState.update { it.copy(isLoadingManagementData = true) }

        val tmdbId = request.media.tmdbId ?: return
        val mediaType = request.getMediaType() ?: MediaType.MOVIE

        viewModelScope.launch {
            val detailsJob = async {
                if (mediaType == MediaType.TV) jellyseerrRepository.getTvDetails(tmdbId)
                else jellyseerrRepository.getMovieDetails(tmdbId)
            }
            val settingsJob = async { jellyseerrRepository.getServiceSettings(mediaType) }

            val detailsResult = detailsJob.await()
            val settingsResult = settingsJob.await()

            var detailsData: MediaDetails? = null
            var finalServers: List<ServiceSettings> = emptyList()
            var matchedServer: ServiceSettings? = null
            var finalProfiles: List<QualityProfile> = emptyList()
            var selectedProfile: QualityProfile? = null
            var finalRootFolder: String? = request.rootFolder
            var finalIs4k = request.is4k

            detailsResult.onSuccess { detailsData = it }

            settingsResult.onSuccess { servers ->
                val actualServer = servers.find { it.id == request.serverId }

                if (actualServer != null && actualServer.is4k != finalIs4k) {
                    Timber.w(
                        "JellyseerrDebug: Correction! Request.is4k=$finalIs4k but Server is4k=${actualServer.is4k}. Switching toggle."
                    )
                    finalIs4k = actualServer.is4k
                }

                val filtered = servers.filter { it.is4k == finalIs4k }
                finalServers = filtered.ifEmpty { servers }

                matchedServer =
                    actualServer
                        ?: finalServers.firstOrNull { it.isDefault }
                        ?: finalServers.firstOrNull()
            }

            val targetServiceId = request.serverId ?: matchedServer?.id
            if (targetServiceId != null) {
                jellyseerrRepository.getServiceDetails(mediaType, targetServiceId).onSuccess {
                    serviceDetails ->
                    finalProfiles = serviceDetails.profiles

                    selectedProfile =
                        if (request.profileId != null) {
                            finalProfiles.find { it.id == request.profileId }
                        } else {
                            finalProfiles.find { it.id == serviceDetails.server?.activeProfileId }
                        }

                    if (finalRootFolder == null) {
                        finalRootFolder =
                            serviceDetails.server?.activeDirectory
                                ?: serviceDetails.rootFolders.firstOrNull()?.path
                    }
                }
            }

            val initialServerName = request.serverId?.toString()?.let { "ID: $it" } ?: "Default"
            val initialProfileName = request.profileId?.toString()?.let { "ID: $it" } ?: "Default"

            _uiState.update {
                it.copy(
                    isLoadingManagementData = false,
                    selectedRequest = request,
                    selectedRequestDetails = detailsData,
                    selectedRequestServerName = matchedServer?.name ?: initialServerName,
                    selectedRequestProfileName = selectedProfile?.name ?: initialProfileName,
                    is4kRequested = finalIs4k,
                    availableServers = finalServers,
                    selectedServer = matchedServer,
                    availableProfiles = finalProfiles,
                    selectedProfile = selectedProfile,
                    selectedRootFolder = finalRootFolder,
                    isLoadingDetails = false,
                    isLoadingServers = false,
                    isLoadingProfiles = false,
                )
            }
        }
    }

    private suspend fun loadProfilesForService(
        mediaType: MediaType,
        serviceId: Int,
        requestedProfileId: Int?,
    ) {
        jellyseerrRepository
            .getServiceDetails(mediaType, serviceId)
            .fold(
                onSuccess = { serviceDetails ->
                    val profile =
                        if (requestedProfileId != null) {
                            serviceDetails.profiles.find { it.id == requestedProfileId }
                        } else {
                            serviceDetails.profiles.find {
                                it.id == serviceDetails.server?.activeProfileId
                            }
                        }

                    val rootFolder =
                        serviceDetails.server?.activeDirectory
                            ?: serviceDetails.rootFolders.firstOrNull()?.path

                    _uiState.update {
                        it.copy(
                            availableProfiles = serviceDetails.profiles,
                            selectedProfile = profile,
                            selectedRequestProfileName =
                                profile?.name ?: it.selectedRequestProfileName,
                            selectedRootFolder = it.selectedRootFolder ?: rootFolder,
                            isLoadingProfiles = false,
                        )
                    }
                },
                onFailure = { _uiState.update { it.copy(isLoadingProfiles = false) } },
            )
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
        val mediaType =
            _uiState.value.pendingRequest?.mediaType
                ?: _uiState.value.selectedRequest?.getMediaType()
                ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingServers = true) }
            jellyseerrRepository
                .getServiceSettings(mediaType)
                .fold(
                    onSuccess = { servers ->
                        val filtered = servers.filter { it.is4k == is4k }
                        val finalServers = filtered.ifEmpty { servers }
                        val defaultServer =
                            finalServers.firstOrNull { it.isDefault } ?: finalServers.firstOrNull()

                        _uiState.update {
                            it.copy(
                                availableServers = finalServers,
                                selectedServer = defaultServer,
                                isLoadingServers = false,
                            )
                        }

                        if (defaultServer != null) {
                            loadProfilesForService(mediaType, defaultServer.id, null)
                        }
                    },
                    onFailure = { _uiState.update { it.copy(isLoadingServers = false) } },
                )
        }
    }

    fun selectServer(server: ServiceSettings) {
        _uiState.update {
            it.copy(
                selectedServer = server,
                selectedRootFolder = null,
                selectedProfile = null,
                availableProfiles = emptyList(),
                isLoadingProfiles = true,
            )
        }
        val mediaType =
            _uiState.value.pendingRequest?.mediaType
                ?: _uiState.value.selectedRequest?.getMediaType()
                ?: return
        viewModelScope.launch { loadProfilesForService(mediaType, server.id, null) }
    }

    fun selectProfile(profile: QualityProfile) {
        _uiState.update { it.copy(selectedProfile = profile) }
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
                        if (results.isEmpty())
                            _uiState.update {
                                it.copy(
                                    searchError =
                                        context.getString(
                                            R.string.error_search_no_results_fmt,
                                            query,
                                        )
                                )
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
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isCreatingRequest = true) }
            jellyseerrRepository
                .createRequest(
                    mediaId = mediaId,
                    mediaType = mediaType,
                    seasons = seasons,
                    is4k = state.is4kRequested,
                    serverId = state.selectedServer?.id,
                    profileId = state.selectedProfile?.id,
                    rootFolder = state.selectedRootFolder,
                )
                .fold(
                    onSuccess = {
                        _uiState.update {
                            it.copy(
                                isCreatingRequest = false,
                                showSearchDialog = false,
                                searchResults = emptyList(),
                                searchQuery = "",
                            )
                        }
                        dismissRequestDialog()
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

    fun dismissManagementDialog() {
        _uiState.update {
            it.copy(
                selectedRequest = null,
                selectedRequestDetails = null,
                selectedRequestServerName = null,
                selectedRequestProfileName = null,
                isLoadingDetails = false,
                isProcessingRequest = false,
                isDeletingRequest = false,
                selectedServer = null,
                selectedProfile = null,
                selectedRootFolder = null,
                availableServers = emptyList(),
                availableProfiles = emptyList(),
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
            jellyseerrRepository.getMovieGenreSlider().onSuccess { genres ->
                val backdrops = buildGenreBackdrops(genres)
                _uiState.update { it.copy(movieGenres = genres, movieGenreBackdrops = backdrops) }
            }
        }
        viewModelScope.launch {
            jellyseerrRepository.getTvGenreSlider().onSuccess { genres ->
                val backdrops = buildGenreBackdrops(genres)
                _uiState.update { it.copy(tvGenres = genres, tvGenreBackdrops = backdrops) }
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
                if (mediaType == MediaType.TV) jellyseerrRepository.getTvDetails(tmdbId)
                else jellyseerrRepository.getMovieDetails(tmdbId)
            }
            authJob.await()
            val detailsResult = detailsJob.await()

            detailsResult.fold(
                onSuccess = { details ->
                    _uiState.update { it.copy(isFetchingTvDetails = false) }
                    val seasonCount = if (mediaType == MediaType.TV) details.getSeasonCount() else 0
                    val physicallyAvailable =
                        details.mediaInfo?.getAvailableSeasons() ?: emptyList()
                    val alreadyRequestedByOthers =
                        details.mediaInfo?.requests?.flatMap { request ->
                            request.seasons?.map { it.seasonNumber } ?: emptyList()
                        } ?: emptyList()
                    val allDisabledSeasons =
                        (physicallyAvailable + alreadyRequestedByOthers).distinct()
                    val selectableSeasons =
                        if (mediaType == MediaType.TV) {
                            (1..seasonCount).filter { it !in allDisabledSeasons }
                        } else {
                            emptyList()
                        }

                    _uiState.update {
                        it.copy(
                            showRequestDialog = true,
                            pendingRequest =
                                PendingRequest(
                                    tmdbId,
                                    mediaType,
                                    details.title ?: details.name ?: title,
                                    details.getPosterUrl(),
                                    seasonCount,
                                    existingStatus,
                                    details.getBackdropUrl(),
                                    details.tagline,
                                    details.overview,
                                    details.releaseDate ?: details.firstAirDate,
                                    details.runtime,
                                    details.voteAverage,
                                    details.getCertification(),
                                    details.originalLanguage,
                                    details.getDirector(),
                                    details.getGenreNames(),
                                    details.ratingsCombined,
                                ),
                            selectedSeasons = selectableSeasons,
                            disabledSeasons = allDisabledSeasons,
                        )
                    }
                    setIs4kRequested(false)
                },
                onFailure = { error ->
                    _uiState.update { it.copy(isFetchingTvDetails = false) }
                    Timber.w(error, "Failed to fetch details")
                    _uiState.update {
                        it.copy(
                            showRequestDialog = true,
                            pendingRequest =
                                PendingRequest(
                                    tmdbId,
                                    mediaType,
                                    title,
                                    posterUrl,
                                    availableSeasons,
                                    existingStatus,
                                ),
                            selectedSeasons =
                                if (mediaType == MediaType.TV && availableSeasons > 0)
                                    (1..availableSeasons).toList()
                                else emptyList(),
                            disabledSeasons = emptyList(),
                        )
                    }
                    setIs4kRequested(false)
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
        createRequest(pending.tmdbId, pending.mediaType, seasons)
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

    private fun buildGenreBackdrops(genres: List<GenreSliderItem>): Map<Int, String> {
        val usedPaths = mutableSetOf<String>()
        return buildMap {
            genres.forEachIndexed { index, genre ->
                val path =
                    genre.backdrops?.firstOrNull { it !in usedPaths }
                        ?: genre.backdrops?.firstOrNull()
                if (path != null) {
                    usedPaths.add(path)
                    put(
                        genre.id,
                        "${GenreDuotoneColorGenerator.getDuotoneFilterUrlByIndex(index)}$path",
                    )
                }
            }
        }
    }
}

data class RequestsUiState(
    val jellyseerrUrl: String? = null,
    val requests: List<JellyseerrRequest> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingManagementData: Boolean = false,
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
    val movieGenreBackdrops: Map<Int, String> = emptyMap(),
    val tvGenres: List<GenreSliderItem> = emptyList(),
    val tvGenreBackdrops: Map<Int, String> = emptyMap(),
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
