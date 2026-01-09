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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.data.models.jellyseerr.isAdmin
import com.makd.afinity.ui.components.AfinityTopAppBar
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
    onItemClick: (jellyfinItemId: String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isAuthenticated by viewModel.isAuthenticated.collectAsStateWithLifecycle(initialValue = false)
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    var showJellyseerrBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    Scaffold(
        topBar = {
            AfinityTopAppBar(
                title = {
                    Text(
                        text = "Requests",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                onSearchClick = onSearchClick,
                onProfileClick = onProfileClick,
                userProfileImageUrl = mainUiState.userProfileImageUrl
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        if (!isAuthenticated) {
            NotLoggedInView(
                onLoginClick = { showJellyseerrBottomSheet = true },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            when {
                uiState.error != null && uiState.requests.isEmpty() && uiState.trendingItems.isEmpty() -> {
                    ErrorView(
                        message = uiState.error ?: "Unknown error",
                        onRetry = {
                            viewModel.loadRequests()
                            viewModel.loadDiscoverContent()
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        if (uiState.requests.isNotEmpty()) {
                            item {
                                MyRequestsSection(
                                    requests = uiState.requests,
                                    isAdmin = currentUser?.isAdmin() == true,
                                    onRequestClick = { /* TODO: Navigate to request details */ },
                                    onApprove = { requestId -> viewModel.approveRequest(requestId) },
                                    onDecline = { requestId -> viewModel.declineRequest(requestId) }
                                )
                            }
                        }

                        if (uiState.trendingItems.isNotEmpty()) {
                            item {
                                DiscoverSection(
                                    title = "Trending Now",
                                    items = uiState.trendingItems.take(15),
                                    onItemClick = { item ->
                                        if (item.mediaInfo?.isFullyAvailable() == true) {
                                            item.mediaInfo?.getJellyfinItemId()?.let { jellyfinId ->
                                                onItemClick(jellyfinId)
                                            }
                                        } else {
                                            item.getMediaType()?.let { mediaType ->
                                                viewModel.showRequestDialog(
                                                    tmdbId = item.id,
                                                    mediaType = mediaType,
                                                    title = item.getDisplayTitle(),
                                                    posterUrl = item.getPosterUrl(),
                                                    availableSeasons = 0,
                                                    existingStatus = item.getDisplayStatus()
                                                )
                                            }
                                        }
                                    },
                                    onViewAllClick = {
                                        onNavigateToFilteredMedia(
                                            FilterParams(
                                                type = FilterType.TRENDING,
                                                id = 0,
                                                name = "Trending Now"
                                            )
                                        )
                                    }
                                )
                            }
                        }

                        if (uiState.popularMovies.isNotEmpty()) {
                            item {
                                DiscoverSection(
                                    title = "Popular Movies",
                                    items = uiState.popularMovies.take(15),
                                    onItemClick = { item ->
                                        if (item.mediaInfo?.isFullyAvailable() == true) {
                                            item.mediaInfo?.getJellyfinItemId()?.let { jellyfinId ->
                                                onItemClick(jellyfinId)
                                            }
                                        } else {
                                            item.getMediaType()?.let { mediaType ->
                                                viewModel.showRequestDialog(
                                                    tmdbId = item.id,
                                                    mediaType = mediaType,
                                                    title = item.getDisplayTitle(),
                                                    posterUrl = item.getPosterUrl(),
                                                    availableSeasons = 0,
                                                    existingStatus = item.getDisplayStatus()
                                                )
                                            }
                                        }
                                    },
                                    onViewAllClick = {
                                        onNavigateToFilteredMedia(
                                            FilterParams(
                                                type = FilterType.POPULAR_MOVIES,
                                                id = 0,
                                                name = "Popular Movies"
                                            )
                                        )
                                    }
                                )
                            }
                        }

                        if (uiState.movieGenres.isNotEmpty()) {
                            item {
                                MovieGenresSection(
                                    genres = uiState.movieGenres,
                                    onGenreClick = { genre ->
                                        onNavigateToFilteredMedia(
                                            FilterParams(
                                                type = FilterType.GENRE_MOVIE,
                                                id = genre.id,
                                                name = genre.name
                                            )
                                        )
                                    },
                                    backdropTracker = viewModel.backdropTracker
                                )
                            }
                        }

                        if (uiState.upcomingMovies.isNotEmpty()) {
                            item {
                                DiscoverSection(
                                    title = "Upcoming Movies",
                                    items = uiState.upcomingMovies.take(15),
                                    onItemClick = { item ->
                                        if (item.mediaInfo?.isFullyAvailable() == true) {
                                            item.mediaInfo?.getJellyfinItemId()?.let { jellyfinId ->
                                                onItemClick(jellyfinId)
                                            }
                                        } else {
                                            item.getMediaType()?.let { mediaType ->
                                                viewModel.showRequestDialog(
                                                    tmdbId = item.id,
                                                    mediaType = mediaType,
                                                    title = item.getDisplayTitle(),
                                                    posterUrl = item.getPosterUrl(),
                                                    availableSeasons = 0,
                                                    existingStatus = item.getDisplayStatus()
                                                )
                                            }
                                        }
                                    },
                                    onViewAllClick = {
                                        onNavigateToFilteredMedia(
                                            FilterParams(
                                                type = FilterType.UPCOMING_MOVIES,
                                                id = 0,
                                                name = "Upcoming Movies"
                                            )
                                        )
                                    }
                                )
                            }
                        }

                        if (uiState.studios.isNotEmpty()) {
                            item {
                                StudiosSection(
                                    studios = uiState.studios,
                                    onStudioClick = { studio ->
                                        onNavigateToFilteredMedia(
                                            FilterParams(
                                                type = FilterType.STUDIO,
                                                id = studio.id,
                                                name = studio.name
                                            )
                                        )
                                    }
                                )
                            }
                        }

                        if (uiState.popularTv.isNotEmpty()) {
                            item {
                                DiscoverSection(
                                    title = "Popular TV Shows",
                                    items = uiState.popularTv.take(15),
                                    onItemClick = { item ->
                                        if (item.mediaInfo?.isFullyAvailable() == true) {
                                            item.mediaInfo?.getJellyfinItemId()?.let { jellyfinId ->
                                                onItemClick(jellyfinId)
                                            }
                                        } else {
                                            item.getMediaType()?.let { mediaType ->
                                                viewModel.showRequestDialog(
                                                    tmdbId = item.id,
                                                    mediaType = mediaType,
                                                    title = item.getDisplayTitle(),
                                                    posterUrl = item.getPosterUrl(),
                                                    availableSeasons = 0,
                                                    existingStatus = item.getDisplayStatus()
                                                )
                                            }
                                        }
                                    },
                                    onViewAllClick = {
                                        onNavigateToFilteredMedia(
                                            FilterParams(
                                                type = FilterType.POPULAR_TV,
                                                id = 0,
                                                name = "Popular TV Shows"
                                            )
                                        )
                                    }
                                )
                            }
                        }

                        if (uiState.tvGenres.isNotEmpty()) {
                            item {
                                TvGenresSection(
                                    genres = uiState.tvGenres,
                                    onGenreClick = { genre ->
                                        onNavigateToFilteredMedia(
                                            FilterParams(
                                                type = FilterType.GENRE_TV,
                                                id = genre.id,
                                                name = genre.name
                                            )
                                        )
                                    },
                                    backdropTracker = viewModel.backdropTracker
                                )
                            }
                        }

                        if (uiState.upcomingTv.isNotEmpty()) {
                            item {
                                DiscoverSection(
                                    title = "Upcoming TV Shows",
                                    items = uiState.upcomingTv.take(15),
                                    onItemClick = { item ->
                                        if (item.mediaInfo?.isFullyAvailable() == true) {
                                            item.mediaInfo?.getJellyfinItemId()?.let { jellyfinId ->
                                                onItemClick(jellyfinId)
                                            }
                                        } else {
                                            item.getMediaType()?.let { mediaType ->
                                                viewModel.showRequestDialog(
                                                    tmdbId = item.id,
                                                    mediaType = mediaType,
                                                    title = item.getDisplayTitle(),
                                                    posterUrl = item.getPosterUrl(),
                                                    availableSeasons = 0,
                                                    existingStatus = item.getDisplayStatus()
                                                )
                                            }
                                        }
                                    },
                                    onViewAllClick = {
                                        onNavigateToFilteredMedia(
                                            FilterParams(
                                                type = FilterType.UPCOMING_TV,
                                                id = 0,
                                                name = "Upcoming TV Shows"
                                            )
                                        )
                                    }
                                )
                            }
                        }

                        if (uiState.networks.isNotEmpty()) {
                            item {
                                NetworksSection(
                                    networks = uiState.networks,
                                    onNetworkClick = { network ->
                                        onNavigateToFilteredMedia(
                                            FilterParams(
                                                type = FilterType.NETWORK,
                                                id = network.id,
                                                name = network.name
                                            )
                                        )
                                    }
                                )
                            }
                        }

                        if (uiState.requests.isEmpty() &&
                            uiState.trendingItems.isEmpty() &&
                            uiState.popularMovies.isEmpty() &&
                            uiState.popularTv.isEmpty() &&
                            uiState.upcomingMovies.isEmpty() &&
                            uiState.upcomingTv.isEmpty() &&
                            !uiState.isLoading
                        ) {
                            item {
                                EmptyStateView(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (uiState.showRequestDialog && uiState.pendingRequest != null) {
            RequestConfirmationDialog(
                mediaTitle = uiState.pendingRequest!!.title,
                mediaPosterUrl = uiState.pendingRequest!!.posterUrl,
                mediaType = uiState.pendingRequest!!.mediaType,
                availableSeasons = uiState.pendingRequest!!.availableSeasons,
                selectedSeasons = uiState.selectedSeasons,
                onSeasonsChange = { viewModel.setSelectedSeasons(it) },
                disabledSeasons = uiState.disabledSeasons,
                existingStatus = uiState.pendingRequest!!.existingStatus,
                isLoading = uiState.isCreatingRequest,
                onConfirm = { viewModel.confirmRequest() },
                onDismiss = { viewModel.dismissRequestDialog() },
                mediaBackdropUrl = uiState.pendingRequest!!.backdropUrl,
                mediaTagline = uiState.pendingRequest!!.tagline,
                mediaOverview = uiState.pendingRequest!!.overview,
                releaseDate = uiState.pendingRequest!!.releaseDate,
                runtime = uiState.pendingRequest!!.runtime,
                voteAverage = uiState.pendingRequest!!.voteAverage,
                certification = uiState.pendingRequest!!.certification,
                originalLanguage = uiState.pendingRequest!!.originalLanguage,
                director = uiState.pendingRequest!!.director,
                genres = uiState.pendingRequest!!.genres,
                ratingsCombined = uiState.pendingRequest!!.ratingsCombined
            )
        }

        if (showJellyseerrBottomSheet) {
            JellyseerrBottomSheet(
                onDismiss = { showJellyseerrBottomSheet = false },
                sheetState = sheetState
            )
        }
    }
}

@Composable
private fun NotLoggedInView(
    onLoginClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_request_seerr_dark),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.height(64.dp)
            )
            Text(
                text = "Connect to Jellyseerr",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Login to Jellyseerr to request movies and TV shows",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ErrorView(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_exclamation_circle),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.height(48.dp)
            )
            Text(
                text = "Error",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun EmptyStateView(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_inbox),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.height(48.dp)
            )
            Text(
                text = "No content available",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Check back later for new content",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}