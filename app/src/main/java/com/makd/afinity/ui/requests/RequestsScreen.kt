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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
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
    onItemClick: (jellyfinItemId: String) -> Unit = {},
    widthSizeClass: WindowWidthSizeClass,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isAuthenticated by
        viewModel.isAuthenticated.collectAsStateWithLifecycle(initialValue = false)
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    var showJellyseerrBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                uiState.isLoadingDiscover && uiState.trendingItems.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                uiState.error != null &&
                    uiState.requests.isEmpty() &&
                    uiState.trendingItems.isEmpty() -> {
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
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        contentPadding = PaddingValues(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        if (uiState.requests.isNotEmpty()) {
                            item {
                                MyRequestsSection(
                                    requests = uiState.requests,
                                    isAdmin = currentUser?.isAdmin() == true,
                                    onRequestClick = { /* TODO: Navigate to request details */ },
                                    onApprove = { requestId ->
                                        viewModel.approveRequest(requestId)
                                    },
                                    onDecline = { requestId ->
                                        viewModel.declineRequest(requestId)
                                    },
                                    widthSizeClass = widthSizeClass,
                                )
                            }
                        }

                        if (uiState.trendingItems.isNotEmpty()) {
                            item {
                                val trendingTitle = stringResource(R.string.section_trending)
                                DiscoverSection(
                                    title = trendingTitle,
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
                                                    existingStatus = item.getDisplayStatus(),
                                                )
                                            }
                                        }
                                    },
                                    onViewAllClick = {
                                        onNavigateToFilteredMedia(
                                            FilterParams(
                                                type = FilterType.TRENDING,
                                                id = 0,
                                                name = trendingTitle,
                                            )
                                        )
                                    },
                                    widthSizeClass = widthSizeClass,
                                )
                            }
                        }

                        if (uiState.popularMovies.isNotEmpty()) {
                            item {
                                val popMoviesTitle = stringResource(R.string.section_popular_movies)
                                DiscoverSection(
                                    title = popMoviesTitle,
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
                                                    existingStatus = item.getDisplayStatus(),
                                                )
                                            }
                                        }
                                    },
                                    onViewAllClick = {
                                        onNavigateToFilteredMedia(
                                            FilterParams(
                                                type = FilterType.POPULAR_MOVIES,
                                                id = 0,
                                                name = popMoviesTitle,
                                            )
                                        )
                                    },
                                    widthSizeClass = widthSizeClass,
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
                                                name = genre.name,
                                            )
                                        )
                                    },
                                    backdropTracker = viewModel.backdropTracker,
                                    widthSizeClass = widthSizeClass,
                                )
                            }
                        }

                        if (uiState.upcomingMovies.isNotEmpty()) {
                            item {
                                val upcomingMoviesTitle =
                                    stringResource(R.string.section_upcoming_movies)
                                DiscoverSection(
                                    title = upcomingMoviesTitle,
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
                                                    existingStatus = item.getDisplayStatus(),
                                                )
                                            }
                                        }
                                    },
                                    onViewAllClick = {
                                        onNavigateToFilteredMedia(
                                            FilterParams(
                                                type = FilterType.UPCOMING_MOVIES,
                                                id = 0,
                                                name = upcomingMoviesTitle,
                                            )
                                        )
                                    },
                                    widthSizeClass = widthSizeClass,
                                )
                            }
                        }
                        if (!uiState.isLoadingDiscover && uiState.studios.isNotEmpty()) {
                            item {
                                StudiosSection(
                                    studios = uiState.studios,
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
                            }
                        }

                        if (uiState.popularTv.isNotEmpty()) {
                            item {
                                val popTvTitle = stringResource(R.string.section_popular_tv)
                                DiscoverSection(
                                    title = popTvTitle,
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
                                                    existingStatus = item.getDisplayStatus(),
                                                )
                                            }
                                        }
                                    },
                                    onViewAllClick = {
                                        onNavigateToFilteredMedia(
                                            FilterParams(
                                                type = FilterType.POPULAR_TV,
                                                id = 0,
                                                name = popTvTitle,
                                            )
                                        )
                                    },
                                    widthSizeClass = widthSizeClass,
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
                                                name = genre.name,
                                            )
                                        )
                                    },
                                    backdropTracker = viewModel.backdropTracker,
                                    widthSizeClass = widthSizeClass,
                                )
                            }
                        }

                        if (uiState.upcomingTv.isNotEmpty()) {
                            item {
                                val upcomingTvTitle = stringResource(R.string.section_upcoming_tv)
                                DiscoverSection(
                                    title = upcomingTvTitle,
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
                                                    existingStatus = item.getDisplayStatus(),
                                                )
                                            }
                                        }
                                    },
                                    onViewAllClick = {
                                        onNavigateToFilteredMedia(
                                            FilterParams(
                                                type = FilterType.UPCOMING_TV,
                                                id = 0,
                                                name = upcomingTvTitle,
                                            )
                                        )
                                    },
                                    widthSizeClass = widthSizeClass,
                                )
                            }
                        }
                        if (!uiState.isLoadingDiscover && uiState.networks.isNotEmpty()) {
                            item {
                                NetworksSection(
                                    networks = uiState.networks,
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
                                uiState.trendingItems.isEmpty() &&
                                uiState.popularMovies.isEmpty() &&
                                uiState.popularTv.isEmpty() &&
                                uiState.upcomingMovies.isEmpty() &&
                                uiState.upcomingTv.isEmpty() &&
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
                ratingsCombined = uiState.pendingRequest!!.ratingsCombined,
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
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_inbox),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.height(48.dp),
            )
            Text(
                text = stringResource(R.string.error_no_content),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.empty_content_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
