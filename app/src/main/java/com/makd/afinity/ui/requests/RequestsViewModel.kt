package com.makd.afinity.ui.requests

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.R
import com.makd.afinity.data.models.jellyseerr.DiscoverSlider
import com.makd.afinity.data.models.jellyseerr.GenreSliderItem
import com.makd.afinity.data.models.jellyseerr.JellyseerrRequest
import com.makd.afinity.data.models.jellyseerr.JellyseerrSearchResult
import com.makd.afinity.data.models.jellyseerr.JellyseerrUser
import com.makd.afinity.data.models.jellyseerr.MediaDetails
import com.makd.afinity.data.models.jellyseerr.MediaStatus
import com.makd.afinity.data.models.jellyseerr.MediaType
import com.makd.afinity.data.models.jellyseerr.Network
import com.makd.afinity.data.models.jellyseerr.PublicSettings
import com.makd.afinity.data.models.jellyseerr.QualityProfile
import com.makd.afinity.data.models.jellyseerr.RatingsCombined
import com.makd.afinity.data.models.jellyseerr.SearchResultItem
import com.makd.afinity.data.models.jellyseerr.ServiceSettings
import com.makd.afinity.data.models.jellyseerr.Studio
import com.makd.afinity.data.models.jellyseerr.UserQuotaResponse
import com.makd.afinity.data.repository.JellyseerrRepository
import com.makd.afinity.util.GenreDuotoneColorGenerator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class RequestsViewModel
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val jellyseerrRepository: JellyseerrRepository,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(RequestsUiState(isLoading = true, isLoadingDiscover = true))
    val uiState: StateFlow<RequestsUiState> = _uiState.asStateFlow()

    val isAuthenticated = jellyseerrRepository.isAuthenticated

    private val _currentUser = MutableStateFlow<JellyseerrUser?>(null)
    val currentUser: StateFlow<JellyseerrUser?> = _currentUser.asStateFlow()
    private var requestsJob: Job? = null

    private val _navigateToItem = MutableSharedFlow<Pair<String, String?>>(extraBufferCapacity = 1)
    val navigateToItem: SharedFlow<Pair<String, String?>> = _navigateToItem.asSharedFlow()

    private val jellyfinIdCache = mutableMapOf<Int, String>()
    private val fetchingIds = mutableSetOf<Int>()
    private val prefetchSemaphore = kotlinx.coroutines.sync.Semaphore(4)

    private var discoverJob: Job? = null
    private val sectionResults = LinkedHashMap<String, DiscoverSectionContent>()
    private var sectionOrder: List<String> = emptyList()

    private companion object {
        const val SLIDER_ITEM_LIMIT = 15
    }

    fun resolveAndNavigate(request: JellyseerrRequest) {
        val tmdbId = request.media.tmdbId ?: return
        val mediaType = request.getMediaType() ?: MediaType.MOVIE
        val mappedType = if (mediaType == MediaType.TV) "Series" else "Movie"
        val cached = jellyfinIdCache[tmdbId]
        if (cached != null) {
            viewModelScope.launch { _navigateToItem.emit(cached to mappedType) }
            return
        }
        viewModelScope.launch {
            val result =
                if (mediaType == MediaType.TV) jellyseerrRepository.getTvDetails(tmdbId)
                else jellyseerrRepository.getMovieDetails(tmdbId)
            result.onSuccess { details ->
                val jellyfinId = details.mediaInfo?.getJellyfinItemId() ?: return@onSuccess
                jellyfinIdCache[tmdbId] = jellyfinId
                _navigateToItem.emit(jellyfinId to mappedType)
            }
        }
    }

    private fun prefetchAvailableJellyfinIds(requests: List<JellyseerrRequest>) {
        requests.forEach { req ->
            val statusValue = if (req.is4k) req.media.status4k ?: 1 else req.media.status ?: 1
            val s = MediaStatus.fromValue(statusValue)
            if (s != MediaStatus.AVAILABLE && s != MediaStatus.PARTIALLY_AVAILABLE) return@forEach
            val tmdbId = req.media.tmdbId ?: return@forEach
            if (!fetchingIds.add(tmdbId)) return@forEach
            val mediaType = req.getMediaType() ?: MediaType.MOVIE
            viewModelScope.launch {
                prefetchSemaphore.withPermit {
                    val result =
                        if (mediaType == MediaType.TV) jellyseerrRepository.getTvDetails(tmdbId)
                        else jellyseerrRepository.getMovieDetails(tmdbId)
                    result.onSuccess { details ->
                        details.mediaInfo?.getJellyfinItemId()?.let { jellyfinIdCache[tmdbId] = it }
                    }
                }
            }
        }
    }

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
                        loadPublicSettings()
                        observeRequests()
                        loadRequests()
                        loadDiscoverContent()
                    } else {
                        _currentUser.value = null
                        requestsJob?.cancel()
                        discoverJob?.cancel()
                        sectionResults.clear()
                        sectionOrder = emptyList()
                        _uiState.update {
                            it.copy(
                                jellyseerrUrl = null,
                                requests = emptyList(),
                                discoverSections = emptyList(),
                                isLoadingDiscover = false,
                                publicSettings = null,
                                userQuota = null,
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

    private fun loadPublicSettings() {
        viewModelScope.launch {
            jellyseerrRepository.getPublicSettings().onSuccess { settings ->
                _uiState.update { it.copy(publicSettings = settings) }
            }
        }
    }

    private fun refreshUserQuota() {
        val userId = _currentUser.value?.id ?: return
        viewModelScope.launch {
            jellyseerrRepository.getUserQuota(userId).onSuccess { quota ->
                _uiState.update { it.copy(userQuota = quota) }
            }
        }
    }

    private fun observeRequests() {
        requestsJob?.cancel()
        requestsJob = viewModelScope.launch {
            try {
                jellyseerrRepository.observeRequests().collect { requests ->
                    _uiState.update { it.copy(requests = requests, isLoading = false) }
                    prefetchAvailableJellyfinIds(requests)
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

                val updateSection: (DiscoverSectionContent) -> DiscoverSectionContent = { section ->
                    if (section is DiscoverSectionContent.MediaRow) {
                        section.copy(items = section.items.map(updateItemStatus))
                    } else {
                        section
                    }
                }

                sectionResults.keys.toList().forEach { key ->
                    sectionResults[key]?.let { sectionResults[key] = updateSection(it) }
                }
                _uiState.update {
                    it.copy(discoverSections = it.discoverSections.map(updateSection))
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
                                    error = context.getString(R.string.error_request_fail_delete_fmt, error.message ?: ""),
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
                                    error = context.getString(R.string.error_request_fail_approve_fmt, error.message ?: ""),
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
                                    error = context.getString(R.string.error_request_update_failed_fmt, error.message ?: ""),
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
                                    error = context.getString(R.string.error_request_fail_decline_fmt, error.message ?: ""),
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
        discoverJob?.cancel()
        sectionResults.clear()
        sectionOrder = emptyList()
        _uiState.update { it.copy(isLoadingDiscover = true, discoverSections = emptyList()) }
        discoverJob = viewModelScope.launch {
            val sliders =
                jellyseerrRepository
                    .getDiscoverSliders()
                    .getOrNull()
                    ?.filter { it.enabled && isSupportedSlider(it) }
                    ?.sortedBy { it.order }
                    ?.takeIf { it.isNotEmpty() } ?: defaultSliders
            sectionOrder = sliders.map { sliderKey(it) }
            val isStatic = { slider: DiscoverSlider ->
                slider.type == DiscoverSlider.Type.STUDIOS ||
                    slider.type == DiscoverSlider.Type.NETWORKS
            }
            sliders
                .filterNot(isStatic)
                .map { slider -> launch { loadSliderContent(slider) } }
                .joinAll()
            sliders.filter(isStatic).forEach { loadSliderContent(it) }
            _uiState.update { it.copy(isLoadingDiscover = false) }
        }
    }

    private fun sliderKey(slider: DiscoverSlider) = "slider_${slider.id}_${slider.type}"

    private val defaultSliders =
        listOf(
            DiscoverSlider(id = -1, type = DiscoverSlider.Type.TRENDING),
            DiscoverSlider(id = -2, type = DiscoverSlider.Type.POPULAR_MOVIES),
            DiscoverSlider(id = -3, type = DiscoverSlider.Type.MOVIE_GENRES),
            DiscoverSlider(id = -4, type = DiscoverSlider.Type.UPCOMING_MOVIES),
            DiscoverSlider(id = -5, type = DiscoverSlider.Type.STUDIOS),
            DiscoverSlider(id = -6, type = DiscoverSlider.Type.POPULAR_TV),
            DiscoverSlider(id = -7, type = DiscoverSlider.Type.TV_GENRES),
            DiscoverSlider(id = -8, type = DiscoverSlider.Type.UPCOMING_TV),
            DiscoverSlider(id = -9, type = DiscoverSlider.Type.NETWORKS),
        )

    private fun isSupportedSlider(slider: DiscoverSlider): Boolean =
        when (slider.type) {
            DiscoverSlider.Type.TRENDING,
            DiscoverSlider.Type.POPULAR_MOVIES,
            DiscoverSlider.Type.MOVIE_GENRES,
            DiscoverSlider.Type.UPCOMING_MOVIES,
            DiscoverSlider.Type.STUDIOS,
            DiscoverSlider.Type.POPULAR_TV,
            DiscoverSlider.Type.TV_GENRES,
            DiscoverSlider.Type.UPCOMING_TV,
            DiscoverSlider.Type.NETWORKS -> true
            DiscoverSlider.Type.TMDB_MOVIE_KEYWORD,
            DiscoverSlider.Type.TMDB_TV_KEYWORD,
            DiscoverSlider.Type.TMDB_SEARCH -> !slider.data.isNullOrBlank()
            DiscoverSlider.Type.TMDB_MOVIE_GENRE,
            DiscoverSlider.Type.TMDB_TV_GENRE,
            DiscoverSlider.Type.TMDB_STUDIO,
            DiscoverSlider.Type.TMDB_NETWORK -> slider.data?.toIntOrNull() != null
            DiscoverSlider.Type.TMDB_MOVIE_STREAMING_SERVICES,
            DiscoverSlider.Type.TMDB_TV_STREAMING_SERVICES ->
                parseStreamingData(slider.data) != null
            else -> false
        }

    private fun parseStreamingData(data: String?): Pair<String, String>? {
        val parts = data?.split(",", limit = 2) ?: return null
        if (parts.size < 2 || parts[0].isBlank() || parts[1].isBlank()) return null
        return parts[0] to parts[1]
    }

    private suspend fun loadSliderContent(slider: DiscoverSlider) {
        val key = sliderKey(slider)
        when (slider.type) {
            DiscoverSlider.Type.TRENDING ->
                publishMediaRow(key, slider, FilterType.TRENDING) {
                    jellyseerrRepository.getTrending(limit = SLIDER_ITEM_LIMIT)
                }
            DiscoverSlider.Type.POPULAR_MOVIES ->
                publishMediaRow(key, slider, FilterType.POPULAR_MOVIES) {
                    jellyseerrRepository.getDiscoverMovies(limit = SLIDER_ITEM_LIMIT)
                }
            DiscoverSlider.Type.UPCOMING_MOVIES ->
                publishMediaRow(key, slider, FilterType.UPCOMING_MOVIES) {
                    jellyseerrRepository.getUpcomingMovies(limit = SLIDER_ITEM_LIMIT)
                }
            DiscoverSlider.Type.POPULAR_TV ->
                publishMediaRow(key, slider, FilterType.POPULAR_TV) {
                    jellyseerrRepository.getDiscoverTv(limit = SLIDER_ITEM_LIMIT)
                }
            DiscoverSlider.Type.UPCOMING_TV ->
                publishMediaRow(key, slider, FilterType.UPCOMING_TV) {
                    jellyseerrRepository.getUpcomingTv(limit = SLIDER_ITEM_LIMIT)
                }
            DiscoverSlider.Type.MOVIE_GENRES ->
                jellyseerrRepository.getMovieGenreSlider().onSuccess { genres ->
                    if (genres.isNotEmpty()) {
                        publishSection(
                            DiscoverSectionContent.MovieGenres(
                                key,
                                genres,
                                buildGenreBackdrops(genres),
                            )
                        )
                    }
                }
            DiscoverSlider.Type.TV_GENRES ->
                jellyseerrRepository.getTvGenreSlider().onSuccess { genres ->
                    if (genres.isNotEmpty()) {
                        publishSection(
                            DiscoverSectionContent.TvGenres(
                                key,
                                genres,
                                buildGenreBackdrops(genres),
                            )
                        )
                    }
                }
            DiscoverSlider.Type.STUDIOS ->
                publishSection(DiscoverSectionContent.Studios(key, Studio.getPopularStudios()))
            DiscoverSlider.Type.NETWORKS ->
                publishSection(DiscoverSectionContent.Networks(key, Network.getPopularNetworks()))
            DiscoverSlider.Type.TMDB_MOVIE_KEYWORD ->
                publishMediaRow(key, slider, null) {
                    jellyseerrRepository.getDiscoverMovies(
                        limit = SLIDER_ITEM_LIMIT,
                        keywords = slider.data,
                    )
                }
            DiscoverSlider.Type.TMDB_TV_KEYWORD ->
                publishMediaRow(key, slider, null) {
                    jellyseerrRepository.getDiscoverTv(
                        limit = SLIDER_ITEM_LIMIT,
                        keywords = slider.data,
                    )
                }
            DiscoverSlider.Type.TMDB_MOVIE_GENRE -> {
                val genreId = slider.data?.toIntOrNull() ?: return
                publishMediaRow(key, slider, FilterType.GENRE_MOVIE, genreId) {
                    jellyseerrRepository.getMoviesByGenre(genreId)
                }
            }
            DiscoverSlider.Type.TMDB_TV_GENRE -> {
                val genreId = slider.data?.toIntOrNull() ?: return
                publishMediaRow(key, slider, FilterType.GENRE_TV, genreId) {
                    jellyseerrRepository.getTvByGenre(genreId)
                }
            }
            DiscoverSlider.Type.TMDB_STUDIO -> {
                val studioId = slider.data?.toIntOrNull() ?: return
                publishMediaRow(key, slider, FilterType.STUDIO, studioId) {
                    jellyseerrRepository.getMoviesByStudio(studioId)
                }
            }
            DiscoverSlider.Type.TMDB_NETWORK -> {
                val networkId = slider.data?.toIntOrNull() ?: return
                publishMediaRow(key, slider, FilterType.NETWORK, networkId) {
                    jellyseerrRepository.getTvByNetwork(networkId)
                }
            }
            DiscoverSlider.Type.TMDB_SEARCH ->
                publishMediaRow(key, slider, null) {
                    jellyseerrRepository.searchMedia(slider.data.orEmpty())
                }
            DiscoverSlider.Type.TMDB_MOVIE_STREAMING_SERVICES -> {
                val (region, providers) = parseStreamingData(slider.data) ?: return
                publishMediaRow(key, slider, null) {
                    jellyseerrRepository.getDiscoverMovies(
                        limit = SLIDER_ITEM_LIMIT,
                        watchRegion = region,
                        watchProviders = providers,
                    )
                }
            }
            DiscoverSlider.Type.TMDB_TV_STREAMING_SERVICES -> {
                val (region, providers) = parseStreamingData(slider.data) ?: return
                publishMediaRow(key, slider, null) {
                    jellyseerrRepository.getDiscoverTv(
                        limit = SLIDER_ITEM_LIMIT,
                        watchRegion = region,
                        watchProviders = providers,
                    )
                }
            }
        }
    }

    private suspend fun publishMediaRow(
        key: String,
        slider: DiscoverSlider,
        viewAllType: FilterType?,
        viewAllId: Int = 0,
        fetch: suspend () -> Result<JellyseerrSearchResult>,
    ) {
        fetch().onSuccess { result ->
            val items =
                result.results.filter { it.getMediaType() != null }.take(SLIDER_ITEM_LIMIT)
            if (items.isNotEmpty()) {
                publishSection(
                    DiscoverSectionContent.MediaRow(
                        key = key,
                        sliderType = slider.type,
                        customTitle = slider.title?.takeIf { it.isNotBlank() },
                        items = items,
                        viewAllType = viewAllType,
                        viewAllId = viewAllId,
                    )
                )
            }
        }
    }

    private fun publishSection(section: DiscoverSectionContent) {
        sectionResults[section.key] = section
        val ordered = sectionOrder.mapNotNull { sectionResults[it] }
        _uiState.update { it.copy(discoverSections = ordered) }
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
            refreshUserQuota()
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
        if (isOverQuota(pending.mediaType, seasons?.size ?: 0, state.userQuota)) return
        createRequest(pending.tmdbId, pending.mediaType, seasons)
    }

    private fun isOverQuota(
        mediaType: MediaType,
        seasonCount: Int,
        quota: UserQuotaResponse?,
    ): Boolean {
        quota ?: return false
        return when (mediaType) {
            MediaType.MOVIE -> quota.movie.hasLimit() && (quota.movie.remaining ?: 0) <= 0
            MediaType.TV -> quota.tv.hasLimit() && seasonCount > (quota.tv.remaining ?: 0)
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
                userQuota = null,
            )
        }
    }

    fun setSelectedSeasons(seasons: List<Int>) {
        _uiState.update { state ->
            val tvQuota = state.userQuota?.tv
            val expanding = seasons.size > state.selectedSeasons.size
            val blocked =
                tvQuota != null &&
                    tvQuota.hasLimit() &&
                    expanding &&
                    seasons.size > (tvQuota.remaining ?: 0)
            if (blocked) state else state.copy(selectedSeasons = seasons)
        }
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
    val discoverSections: List<DiscoverSectionContent> = emptyList(),
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
    val publicSettings: PublicSettings? = null,
    val userQuota: UserQuotaResponse? = null,
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

sealed class DiscoverSectionContent {
    abstract val key: String

    data class MediaRow(
        override val key: String,
        val sliderType: Int,
        val customTitle: String?,
        val items: List<SearchResultItem>,
        val viewAllType: FilterType?,
        val viewAllId: Int = 0,
    ) : DiscoverSectionContent()

    data class MovieGenres(
        override val key: String,
        val genres: List<GenreSliderItem>,
        val backdrops: Map<Int, String>,
    ) : DiscoverSectionContent()

    data class TvGenres(
        override val key: String,
        val genres: List<GenreSliderItem>,
        val backdrops: Map<Int, String>,
    ) : DiscoverSectionContent()

    data class Studios(override val key: String, val studios: List<Studio>) :
        DiscoverSectionContent()

    data class Networks(override val key: String, val networks: List<Network>) :
        DiscoverSectionContent()
}
