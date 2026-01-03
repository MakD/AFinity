package com.makd.afinity.ui.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.makd.afinity.data.models.jellyseerr.JellyseerrRequest
import com.makd.afinity.data.models.jellyseerr.RequestStatus
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.RequestConfirmationDialog
import com.makd.afinity.ui.main.MainUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestsScreen(
    mainUiState: MainUiState,
    onJellyseerrLoginClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RequestsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isAuthenticated by viewModel.isAuthenticated.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AfinityTopAppBar(
                title = {
                    Text(
                        text = "Requests",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                onSearchClick = null,
                onProfileClick = null,
                userProfileImageUrl = mainUiState.userProfileImageUrl
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        if (!isAuthenticated) {
            NotLoggedInView(
                onLoginClick = onJellyseerrLoginClick,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            var selectedTabIndex by remember { mutableIntStateOf(0) }
            val tabs = listOf("My Requests", "Discover")

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }

                when (selectedTabIndex) {
                    0 -> {
                        PullToRefreshBox(
                            isRefreshing = uiState.isLoading,
                            onRefresh = { viewModel.loadRequests() },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            when {
                                uiState.error != null && uiState.requests.isEmpty() -> {
                                    ErrorView(
                                        message = uiState.error!!,
                                        onRetry = { viewModel.loadRequests() },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                uiState.requests.isEmpty() && !uiState.isLoading -> {
                                    EmptyRequestsView(
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                else -> {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(
                                            items = uiState.requests,
                                            key = { it.id }
                                        ) { request ->
                                            RequestListItemBasic(
                                                request = request,
                                                onDeleteClick = { viewModel.deleteRequest(request.id) },
                                                onApproveClick = { viewModel.approveRequest(request.id) },
                                                onDeclineClick = { viewModel.declineRequest(request.id) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        DiscoverContent(
                            trendingItems = uiState.trendingItems,
                            popularMovies = uiState.popularMovies,
                            popularTv = uiState.popularTv,
                            upcomingMovies = uiState.upcomingMovies,
                            upcomingTv = uiState.upcomingTv,
                            isLoading = uiState.isLoadingDiscover,
                            onItemClick = { item ->
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
                            },
                            modifier = Modifier.fillMaxSize()
                        )
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
                    onDismiss = { viewModel.dismissRequestDialog() }
                )
            }
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
                painter = painterResource(id = R.drawable.ic_request),
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Jellyseerr Not Connected",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Connect to Jellyseerr to request movies and TV shows",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onLoginClick,
                modifier = Modifier.fillMaxWidth(0.6f)
            ) {
                Text("Connect to Jellyseerr")
            }
        }
    }
}

@Composable
private fun EmptyRequestsView(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_request),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = "No Requests Yet",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Your content requests will appear here",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_error),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )

            Text(
                text = "Error",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
private fun RequestListItemBasic(
    request: JellyseerrRequest,
    onDeleteClick: () -> Unit,
    onApproveClick: () -> Unit,
    onDeclineClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Request #${request.id}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "Type: ${request.media.mediaType}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                RequestStatusBadgeBasic(status = request.getRequestStatus())
            }

            Text(
                text = "Requested by: ${request.requestedBy.displayName ?: "Unknown"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onDeleteClick) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_delete),
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }

                if (request.getRequestStatus() == RequestStatus.PENDING) {
                    Button(
                        onClick = onApproveClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Approve")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = onDeclineClick,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Decline")
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestStatusBadgeBasic(
    status: RequestStatus,
    modifier: Modifier = Modifier
) {
    val (text, color) = when (status) {
        RequestStatus.PENDING -> "Pending" to MaterialTheme.colorScheme.tertiary
        RequestStatus.APPROVED -> "Approved" to MaterialTheme.colorScheme.primary
        RequestStatus.AVAILABLE -> "Available" to MaterialTheme.colorScheme.secondary
        RequestStatus.PARTIALLY_AVAILABLE -> "Partial" to MaterialTheme.colorScheme.secondary
        RequestStatus.DECLINED -> "Declined" to MaterialTheme.colorScheme.error
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}
