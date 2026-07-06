package com.makd.afinity.ui.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.data.models.jellyseerr.DiscoverSlider
import com.makd.afinity.data.models.jellyseerr.MediaStatus
import com.makd.afinity.data.models.jellyseerr.MediaType
import com.makd.afinity.data.models.jellyseerr.Permissions
import com.makd.afinity.data.models.jellyseerr.RequestStatus
import com.makd.afinity.data.models.jellyseerr.hasPermission
import com.makd.afinity.data.models.jellyseerr.isAdmin
import com.makd.afinity.navigation.LocalPlayerOffset
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.EmptyState
import com.makd.afinity.ui.components.FullScreenLoading
import com.makd.afinity.ui.components.RequestConfirmationDialog
import com.makd.afinity.ui.main.MainUiState
import com.makd.afinity.ui.settings.JellyseerrBottomSheet

@Composable
fun RequestsScreen(
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    mainUiState: MainUiState,
    modifier: Modifier = Modifier,
    viewModel: RequestsViewModel = hiltViewModel(),
    onNavigateToFilteredMedia: (FilterParams) -> Unit = {},
    onItemClick: (jellyfinItemId: String, itemType: String?) -> Unit = { _, _ -> },
    onNavigateToSeerrMedia: (item: com.makd.afinity.data.models.jellyseerr.SearchResultItem) -> Unit =
        {},
    widthSizeClass: WindowWidthSizeClass,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isAuthenticated by
        viewModel.isAuthenticated.collectAsStateWithLifecycle(initialValue = false)
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    var showJellyseerrBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val playerOffset = LocalPlayerOffset.current

    LaunchedEffect(Unit) {
        viewModel.navigateToItem.collect { (jellyfinId, mediaType) ->
            onItemClick(jellyfinId, mediaType)
        }
    }

    Scaffold(
        topBar = {
            AfinityTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.requests_title),
                        style =
                            MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
                onSearchClick = onSearchClick,
                onProfileClick = onProfileClick,
                userProfileImageUrl = mainUiState.userProfileImageUrl,
                userName = mainUiState.userName,
            )
        },
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        if (!isAuthenticated) {
            NotLoggedInView(
                onLoginClick = { showJellyseerrBottomSheet = true },
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        } else {
            when {
                uiState.isLoadingDiscover && uiState.discoverSections.isEmpty() -> {
                    FullScreenLoading(modifier = Modifier.padding(innerPadding))
                }

                uiState.error != null &&
                    uiState.requests.isEmpty() &&
                    uiState.discoverSections.isEmpty() -> {
                    ErrorView(
                        message = uiState.error ?: stringResource(R.string.error_unknown),
                        onRetry = {
                            viewModel.loadRequests()
                            viewModel.loadDiscoverContent()
                        },
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                    )
                }

                else -> {
                    val activeRequests =
                        uiState.requests.filter { req ->
                            val statusValue =
                                if (req.is4k) req.media.status4k ?: 1 else req.media.status ?: 1
                            val mediaStatus = MediaStatus.fromValue(statusValue)
                            mediaStatus != MediaStatus.AVAILABLE &&
                                mediaStatus != MediaStatus.PARTIALLY_AVAILABLE
                        }
                    val availableRequests =
                        uiState.requests.filter { req ->
                            val statusValue =
                                if (req.is4k) req.media.status4k ?: 1 else req.media.status ?: 1
                            val mediaStatus = MediaStatus.fromValue(statusValue)
                            mediaStatus == MediaStatus.AVAILABLE ||
                                mediaStatus == MediaStatus.PARTIALLY_AVAILABLE
                        }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp + playerOffset),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        if (activeRequests.isNotEmpty()) {
                            item {
                                MyRequestsSection(
                                    requests = activeRequests,
                                    baseUrl = uiState.jellyseerrUrl,
                                    isAdmin = currentUser?.isAdmin() == true,
                                    onRequestClick = { request ->
                                        if (currentUser?.isAdmin() == true) {
                                            viewModel.selectRequest(request)
                                        }
                                    },
                                    onApprove = { viewModel.approveRequest(it) },
                                    onDecline = { viewModel.declineRequest(it) },
                                    widthSizeClass = widthSizeClass,
                                )
                            }
                        }

                        if (availableRequests.isNotEmpty()) {
                            item {
                                AvailableRequestsSection(
                                    requests = availableRequests,
                                    onRequestClick = { viewModel.resolveAndNavigate(it) },
                                    widthSizeClass = widthSizeClass,
                                )
                            }
                        }

                        items(
                            items = uiState.discoverSections,
                            key = { section -> section.key },
                        ) { section ->
                            when (section) {
                                is DiscoverSectionContent.MediaRow -> {
                                    val title =
                                        section.customTitle
                                            ?: builtInSectionTitleRes(section.sliderType)?.let {
                                                stringResource(it)
                                            }
                                            ?: ""
                                    DiscoverSection(
                                        title = title,
                                        items = section.items,
                                        onItemClick = { item ->
                                            if (item.mediaInfo?.isFullyAvailable() == true) {
                                                item.mediaInfo?.getJellyfinItemId()?.let {
                                                    jellyfinId ->
                                                    val mappedType =
                                                        when (item.mediaType.lowercase()) {
                                                            "tv" -> "Series"
                                                            "movie" -> "Movie"
                                                            else -> null
                                                        }
                                                    onItemClick(jellyfinId, mappedType)
                                                } ?: onNavigateToSeerrMedia(item)
                                            } else {
                                                onNavigateToSeerrMedia(item)
                                            }
                                        },
                                        onViewAllClick =
                                            section.viewAllType?.let { viewAllType ->
                                                {
                                                    onNavigateToFilteredMedia(
                                                        FilterParams(
                                                            type = viewAllType,
                                                            id = section.viewAllId,
                                                            name = title,
                                                        )
                                                    )
                                                }
                                            },
                                        widthSizeClass = widthSizeClass,
                                    )
                                }
                                is DiscoverSectionContent.MovieGenres ->
                                    MovieGenresSection(
                                        genres = section.genres,
                                        onGenreClick = { genre ->
                                            onNavigateToFilteredMedia(
                                                FilterParams(
                                                    type = FilterType.GENRE_MOVIE,
                                                    id = genre.id,
                                                    name = genre.name,
                                                )
                                            )
                                        },
                                        genreBackdrops = section.backdrops,
                                        widthSizeClass = widthSizeClass,
                                    )
                                is DiscoverSectionContent.TvGenres ->
                                    TvGenresSection(
                                        genres = section.genres,
                                        onGenreClick = { genre ->
                                            onNavigateToFilteredMedia(
                                                FilterParams(
                                                    type = FilterType.GENRE_TV,
                                                    id = genre.id,
                                                    name = genre.name,
                                                )
                                            )
                                        },
                                        genreBackdrops = section.backdrops,
                                        widthSizeClass = widthSizeClass,
                                    )
                                is DiscoverSectionContent.Studios ->
                                    StudiosSection(
                                        studios = section.studios,
                                        onStudioClick = { studio ->
                                            onNavigateToFilteredMedia(
                                                FilterParams(
                                                    type = FilterType.STUDIO,
                                                    id = studio.id,
                                                    name = studio.name,
                                                )
                                            )
                                        },
                                        widthSizeClass = widthSizeClass,
                                    )
                                is DiscoverSectionContent.Networks ->
                                    NetworksSection(
                                        networks = section.networks,
                                        onNetworkClick = { network ->
                                            onNavigateToFilteredMedia(
                                                FilterParams(
                                                    type = FilterType.NETWORK,
                                                    id = network.id,
                                                    name = network.name,
                                                )
                                            )
                                        },
                                        widthSizeClass = widthSizeClass,
                                    )
                            }
                        }

                        if (
                            uiState.requests.isEmpty() &&
                                uiState.discoverSections.isEmpty() &&
                                !uiState.isLoading &&
                                !uiState.isLoadingDiscover
                        ) {
                            item {
                                EmptyStateView(modifier = Modifier.fillMaxWidth().padding(32.dp))
                            }
                        }
                    }
                }
            }
        }

        if (uiState.selectedRequest != null) {
            val req = uiState.selectedRequest!!
            val details = uiState.selectedRequestDetails

            val type = req.getMediaType()

            val fourKEnabledOnServer =
                uiState.publicSettings?.let {
                    if (type == MediaType.MOVIE) it.movie4kEnabled else it.series4kEnabled
                } ?: true
            val canToggle4k =
                fourKEnabledOnServer &&
                    (currentUser?.let { user ->
                        user.hasPermission(Permissions.REQUEST_4K) ||
                            (type == MediaType.MOVIE &&
                                user.hasPermission(Permissions.REQUEST_4K_MOVIE)) ||
                            (type == MediaType.TV && user.hasPermission(Permissions.REQUEST_4K_TV))
                    } ?: false)

            RequestConfirmationDialog(
                isManagementMode = true,
                requestStatus = RequestStatus.fromValue(req.status),
                mediaTitle = details?.title ?: details?.name ?: req.media.getDisplayTitle(),
                mediaPosterUrl = details?.getPosterUrl() ?: req.media.getPosterUrl(),
                mediaBackdropUrl = details?.getBackdropUrl() ?: req.media.getBackdropUrl(),
                mediaOverview = details?.overview,
                mediaTagline = details?.tagline,
                mediaType = req.getMediaType() ?: MediaType.MOVIE,
                releaseDate =
                    details?.releaseDate ?: details?.firstAirDate ?: req.media.releaseDate,
                runtime = details?.runtime,
                voteAverage = details?.voteAverage,
                certification = details?.getCertification(),
                originalLanguage = details?.originalLanguage,
                director = details?.getDirector(),
                genres = details?.getGenreNames() ?: emptyList(),
                ratingsCombined = details?.ratingsCombined,
                selectedSeasons = req.seasons?.map { it.seasonNumber } ?: emptyList(),
                onSeasonsChange = {},
                is4k = uiState.is4kRequested,
                onIs4kChange = { viewModel.setIs4kRequested(it) },
                can4k = canToggle4k,
                manageRootFolder = req.rootFolder,
                manageServerName = uiState.selectedRequestServerName,
                manageProfileName = uiState.selectedRequestProfileName,
                isLoading =
                    uiState.isLoadingDetails ||
                        uiState.isProcessingRequest ||
                        uiState.isDeletingRequest,
                onUpdate = { viewModel.updateRequest(req.id) },
                onApprove = { viewModel.approveRequest(req.id) },
                onDecline = { viewModel.declineRequest(req.id) },
                onDelete = { viewModel.deleteRequest(req.id) },
                onDismiss = { viewModel.dismissManagementDialog() },
                onConfirm = {},
                availableServers = uiState.availableServers,
                selectedServer = uiState.selectedServer,
                onServerSelected = { viewModel.selectServer(it) },
                availableProfiles = uiState.availableProfiles,
                selectedProfile = uiState.selectedProfile,
                onProfileSelected = { viewModel.selectProfile(it) },
                selectedRootFolder = uiState.selectedRootFolder,
                isLoadingServers = uiState.isLoadingServers,
                isLoadingProfiles = uiState.isLoadingProfiles,
            )
        }

        if (uiState.showRequestDialog && uiState.pendingRequest != null) {
            val pending = uiState.pendingRequest!!

            val fourKEnabledOnServer =
                uiState.publicSettings?.let {
                    if (pending.mediaType == MediaType.MOVIE) it.movie4kEnabled
                    else it.series4kEnabled
                } ?: true
            val canRequest4k =
                fourKEnabledOnServer &&
                    (currentUser?.let { user ->
                        user.hasPermission(Permissions.REQUEST_4K) ||
                            (pending.mediaType == MediaType.MOVIE &&
                                user.hasPermission(Permissions.REQUEST_4K_MOVIE)) ||
                            (pending.mediaType == MediaType.TV &&
                                user.hasPermission(Permissions.REQUEST_4K_TV))
                    } ?: false)

            RequestConfirmationDialog(
                isManagementMode = false,
                mediaTitle = pending.title,
                mediaPosterUrl = pending.posterUrl,
                mediaType = pending.mediaType,
                availableSeasons = pending.availableSeasons,
                selectedSeasons = uiState.selectedSeasons,
                onSeasonsChange = { viewModel.setSelectedSeasons(it) },
                disabledSeasons = uiState.disabledSeasons,
                canSelectSeasons = uiState.publicSettings?.partialRequestsEnabled ?: true,
                quota =
                    uiState.userQuota?.let {
                        if (pending.mediaType == MediaType.TV) it.tv else it.movie
                    },
                existingStatus = pending.existingStatus,
                isLoading = uiState.isCreatingRequest,
                detailsLoading = uiState.isFetchingTvDetails,
                onConfirm = { viewModel.confirmRequest() },
                onDismiss = { viewModel.dismissRequestDialog() },
                mediaBackdropUrl = pending.backdropUrl,
                mediaTagline = pending.tagline,
                mediaOverview = pending.overview,
                releaseDate = pending.releaseDate,
                runtime = pending.runtime,
                voteAverage = pending.voteAverage,
                certification = pending.certification,
                originalLanguage = pending.originalLanguage,
                director = pending.director,
                genres = pending.genres,
                ratingsCombined = pending.ratingsCombined,
                can4k = canRequest4k,
                is4k = uiState.is4kRequested,
                onIs4kChange = { viewModel.setIs4kRequested(it) },
                canAdvanced =
                    currentUser?.hasPermission(Permissions.REQUEST_ADVANCED) == true ||
                        currentUser?.hasPermission(Permissions.MANAGE_REQUESTS) == true,
                availableServers = uiState.availableServers,
                selectedServer = uiState.selectedServer,
                onServerSelected = { viewModel.selectServer(it) },
                availableProfiles = uiState.availableProfiles,
                selectedProfile = uiState.selectedProfile,
                onProfileSelected = { viewModel.selectProfile(it) },
                selectedRootFolder = uiState.selectedRootFolder,
                isLoadingServers = uiState.isLoadingServers,
                isLoadingProfiles = uiState.isLoadingProfiles,
                tvdbCandidates = uiState.tvdbCandidates,
                selectedTvdbId = uiState.selectedTvdbId,
                onTvdbSelected = { viewModel.selectTvdbCandidate(it) },
                availableLanguageProfiles = uiState.availableLanguageProfiles,
                selectedLanguageProfile = uiState.selectedLanguageProfile,
                onLanguageProfileSelected = { viewModel.selectLanguageProfile(it) },
                availableTags = uiState.availableTags,
                selectedTagIds = uiState.selectedTagIds,
                onTagToggle = { viewModel.toggleTag(it) },
                availableUsers = uiState.availableUsers,
                selectedRequestUser = uiState.selectedRequestUser,
                onRequestUserSelected = { viewModel.selectRequestUser(it) },
            )
        }

        if (showJellyseerrBottomSheet) {
            JellyseerrBottomSheet(
                onDismiss = { showJellyseerrBottomSheet = false },
                sheetState = sheetState,
            )
        }
    }
}

private fun builtInSectionTitleRes(sliderType: Int): Int? =
    when (sliderType) {
        DiscoverSlider.Type.TRENDING -> R.string.section_trending
        DiscoverSlider.Type.POPULAR_MOVIES -> R.string.section_popular_movies
        DiscoverSlider.Type.UPCOMING_MOVIES -> R.string.section_upcoming_movies
        DiscoverSlider.Type.POPULAR_TV -> R.string.section_popular_tv
        DiscoverSlider.Type.UPCOMING_TV -> R.string.section_upcoming_tv
        else -> null
    }

@Composable
private fun NotLoggedInView(onLoginClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_seerr_logo),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.height(64.dp),
            )
            Text(
                text = stringResource(R.string.jellyseerr_connect_title),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.jellyseerr_connect_message),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_exclamation_circle),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.height(48.dp),
            )
            Text(
                text = stringResource(R.string.error_title),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun EmptyStateView(modifier: Modifier = Modifier) {
    EmptyState(
        icon = painterResource(id = R.drawable.ic_inbox),
        title = stringResource(R.string.error_no_content),
        message = stringResource(R.string.empty_content_message),
        modifier = modifier,
        iconSize = 48.dp,
    )
}
