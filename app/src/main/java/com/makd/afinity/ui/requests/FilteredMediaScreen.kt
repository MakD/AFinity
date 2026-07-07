package com.makd.afinity.ui.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.data.models.jellyseerr.MediaType
import com.makd.afinity.data.models.jellyseerr.Permissions
import com.makd.afinity.data.models.jellyseerr.hasPermission
import com.makd.afinity.navigation.LocalPlayerOffset
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.FullScreenEmpty
import com.makd.afinity.ui.components.FullScreenLoading
import com.makd.afinity.ui.components.RequestConfirmationDialog
import com.makd.afinity.ui.main.MainUiState
import com.makd.afinity.ui.theme.CardDimensions.gridMinSize
import com.makd.afinity.ui.theme.CardDimensions.portraitWidth

enum class FilterType {
    GENRE_MOVIE,
    GENRE_TV,
    STUDIO,
    NETWORK,
    TRENDING,
    POPULAR_MOVIES,
    UPCOMING_MOVIES,
    POPULAR_TV,
    UPCOMING_TV,
    PERSON,
}

data class FilterParams(val type: FilterType, val id: Int, val name: String)

@Composable
fun FilteredMediaScreen(
    filterParams: FilterParams,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    mainUiState: MainUiState,
    onItemClick: (jellyfinItemId: String, itemType: String?) -> Unit,
    onNavigateToSeerrMedia: (item: com.makd.afinity.data.models.jellyseerr.SearchResultItem) -> Unit =
        {},
    modifier: Modifier = Modifier,
    viewModel: FilteredMediaViewModel = hiltViewModel(),
    requestsViewModel: RequestsViewModel = hiltViewModel(),
    widthSizeClass: WindowWidthSizeClass,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val requestsUiState by requestsViewModel.uiState.collectAsStateWithLifecycle()
    val currentUser by requestsViewModel.currentUser.collectAsStateWithLifecycle()
    val playerOffset = LocalPlayerOffset.current

    LaunchedEffect(filterParams) { viewModel.loadContent(filterParams) }

    val cardWidth = widthSizeClass.portraitWidth

    Scaffold(
        topBar = {
            AfinityTopAppBar(
                title = {
                    Text(
                        text = filterParams.name,
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
        when {
            uiState.isLoading && uiState.items.isEmpty() -> {
                FullScreenLoading(modifier = Modifier.padding(innerPadding))
            }

            uiState.error != null && uiState.items.isEmpty() -> {
                ErrorView(
                    message = uiState.error!!,
                    onRetry = { viewModel.loadContent(filterParams) },
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                )
            }

            uiState.items.isEmpty() -> {
                FullScreenEmpty(
                    message = stringResource(R.string.error_no_content),
                    modifier = Modifier.padding(innerPadding),
                )
            }

            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(widthSizeClass.gridMinSize),
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentPadding =
                        PaddingValues(
                            start = 14.dp,
                            top = 16.dp,
                            end = 14.dp,
                            bottom = 16.dp + playerOffset,
                        ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(count = uiState.items.size, key = { index -> uiState.items[index].id }) {
                        index ->
                        val item = uiState.items[index]

                        if (
                            index >= uiState.items.size - 5 &&
                                !uiState.isLoading &&
                                !uiState.hasReachedEnd
                        ) {
                            LaunchedEffect(Unit) { viewModel.loadNextPage() }
                        }

                        DiscoverMediaCard(
                            item = item,
                            onClick = {
                                if (item.mediaInfo?.isFullyAvailable() == true) {
                                    item.mediaInfo?.getJellyfinItemId()?.let { jellyfinId ->
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
                            cardWidth = cardWidth,
                        )
                    }

                    if (uiState.isLoading && uiState.items.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(80.dp).padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }

    if (requestsUiState.showRequestDialog && requestsUiState.pendingRequest != null) {
        RequestConfirmationDialog(
            mediaTitle = requestsUiState.pendingRequest!!.title,
            mediaPosterUrl = requestsUiState.pendingRequest!!.posterUrl,
            mediaType = requestsUiState.pendingRequest!!.mediaType,
            availableSeasons = requestsUiState.pendingRequest!!.availableSeasons,
            selectedSeasons = requestsUiState.selectedSeasons,
            onSeasonsChange = { requestsViewModel.setSelectedSeasons(it) },
            disabledSeasons = requestsUiState.disabledSeasons,
            canSelectSeasons = requestsUiState.publicSettings?.partialRequestsEnabled ?: true,
            quota =
                requestsUiState.userQuota?.let {
                    if (requestsUiState.pendingRequest!!.mediaType == MediaType.TV) it.tv
                    else it.movie
                },
            existingStatus = requestsUiState.pendingRequest!!.existingStatus,
            isLoading = requestsUiState.isCreatingRequest,
            detailsLoading = requestsUiState.isFetchingTvDetails,
            onConfirm = { requestsViewModel.confirmRequest() },
            onDismiss = { requestsViewModel.dismissRequestDialog() },
            mediaBackdropUrl = requestsUiState.pendingRequest!!.backdropUrl,
            mediaTagline = requestsUiState.pendingRequest!!.tagline,
            mediaOverview = requestsUiState.pendingRequest!!.overview,
            releaseDate = requestsUiState.pendingRequest!!.releaseDate,
            runtime = requestsUiState.pendingRequest!!.runtime,
            voteAverage = requestsUiState.pendingRequest!!.voteAverage,
            certification = requestsUiState.pendingRequest!!.certification,
            originalLanguage = requestsUiState.pendingRequest!!.originalLanguage,
            director = requestsUiState.pendingRequest!!.director,
            genres = requestsUiState.pendingRequest!!.genres,
            ratingsCombined = requestsUiState.pendingRequest!!.ratingsCombined,
            can4k =
                (requestsUiState.publicSettings?.let {
                    if (requestsUiState.pendingRequest!!.mediaType == MediaType.MOVIE)
                        it.movie4kEnabled
                    else it.series4kEnabled
                } ?: true) && currentUser?.hasPermission(Permissions.REQUEST_4K) == true,
            is4k = requestsUiState.is4kRequested,
            onIs4kChange = { requestsViewModel.setIs4kRequested(it) },
            canAdvanced =
                currentUser?.hasPermission(Permissions.REQUEST_ADVANCED) == true ||
                    currentUser?.hasPermission(Permissions.MANAGE_REQUESTS) == true,
            availableServers = requestsUiState.availableServers,
            selectedServer = requestsUiState.selectedServer,
            onServerSelected = { requestsViewModel.selectServer(it) },
            availableProfiles = requestsUiState.availableProfiles,
            selectedProfile = requestsUiState.selectedProfile,
            onProfileSelected = { requestsViewModel.selectProfile(it) },
            selectedRootFolder = requestsUiState.selectedRootFolder,
            isLoadingServers = requestsUiState.isLoadingServers,
            isLoadingProfiles = requestsUiState.isLoadingProfiles,
            tvdbCandidates = requestsUiState.tvdbCandidates,
            selectedTvdbId = requestsUiState.selectedTvdbId,
            onTvdbSelected = { requestsViewModel.selectTvdbCandidate(it) },
            availableLanguageProfiles = requestsUiState.availableLanguageProfiles,
            selectedLanguageProfile = requestsUiState.selectedLanguageProfile,
            onLanguageProfileSelected = { requestsViewModel.selectLanguageProfile(it) },
            availableTags = requestsUiState.availableTags,
            selectedTagIds = requestsUiState.selectedTagIds,
            onTagToggle = { requestsViewModel.toggleTag(it) },
            availableUsers = requestsUiState.availableUsers,
            selectedRequestUser = requestsUiState.selectedRequestUser,
            onRequestUserSelected = { requestsViewModel.selectRequestUser(it) },
        )
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
            Button(onClick = onRetry) { Text(text = stringResource(R.string.action_retry)) }
        }
    }
}

