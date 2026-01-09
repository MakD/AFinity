package com.makd.afinity.ui.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.RequestConfirmationDialog
import com.makd.afinity.ui.main.MainUiState

enum class FilterType {
    GENRE_MOVIE,
    GENRE_TV,
    STUDIO,
    NETWORK,
    TRENDING,
    POPULAR_MOVIES,
    UPCOMING_MOVIES,
    POPULAR_TV,
    UPCOMING_TV
}

data class FilterParams(
    val type: FilterType,
    val id: Int,
    val name: String
)

@Composable
fun FilteredMediaScreen(
    filterParams: FilterParams,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    mainUiState: MainUiState,
    onItemClick: (jellyfinItemId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FilteredMediaViewModel = hiltViewModel(),
    requestsViewModel: RequestsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val requestsUiState by requestsViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(filterParams) {
        viewModel.loadContent(filterParams)
    }

    Scaffold(
        topBar = {
            AfinityTopAppBar(
                title = {
                    Text(
                        text = filterParams.name,
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
        when {
            uiState.isLoading && uiState.items.isEmpty() -> {
                LoadingView(modifier = Modifier.fillMaxSize().padding(innerPadding))
            }
            uiState.error != null && uiState.items.isEmpty() -> {
                ErrorView(
                    message = uiState.error!!,
                    onRetry = { viewModel.loadContent(filterParams) },
                    modifier = Modifier.fillMaxSize().padding(innerPadding)
                )
            }
            uiState.items.isEmpty() -> {
                EmptyView(modifier = Modifier.fillMaxSize().padding(innerPadding))
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(
                        count = uiState.items.size,
                        key = { index -> uiState.items[index].id }
                    ) { index ->
                        val item = uiState.items[index]

                        if (index >= uiState.items.size - 5 && !uiState.isLoading && !uiState.hasReachedEnd) {
                            LaunchedEffect(Unit) {
                                viewModel.loadNextPage()
                            }
                        }

                        DiscoverMediaCard(
                            item = item,
                            onClick = {
                                if (item.mediaInfo?.isFullyAvailable() == true) {
                                    item.mediaInfo?.getJellyfinItemId()?.let { jellyfinId ->
                                        onItemClick(jellyfinId)
                                    }
                                } else {
                                    item.getMediaType()?.let { mediaType ->
                                        requestsViewModel.showRequestDialog(
                                            tmdbId = item.id,
                                            mediaType = mediaType,
                                            title = item.getDisplayTitle(),
                                            posterUrl = item.getPosterUrl(),
                                            availableSeasons = 0,
                                            existingStatus = item.getDisplayStatus()
                                        )
                                    }
                                }
                            }
                        )
                    }

                    if (uiState.isLoading && uiState.items.isNotEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
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
            existingStatus = requestsUiState.pendingRequest!!.existingStatus,
            isLoading = requestsUiState.isCreatingRequest,
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
            ratingsCombined = requestsUiState.pendingRequest!!.ratingsCombined
        )
    }
}

@Composable
private fun LoadingView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
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
private fun EmptyView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "No content available",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}