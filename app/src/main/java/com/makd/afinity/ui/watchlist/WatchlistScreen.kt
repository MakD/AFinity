package com.makd.afinity.ui.watchlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.navigation.Destination
import com.makd.afinity.ui.components.ContinueWatchingCard
import com.makd.afinity.ui.components.MediaItemCard
import com.makd.afinity.ui.components.AfinityTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    onItemClick: (AfinityItem) -> Unit = {},
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: WatchlistViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadWatchlist()
    }

    Scaffold(
        topBar = {
            AfinityTopAppBar(
                title = "Watchlist",
                onSearchClick = {
                    val route = Destination.createSearchRoute()
                    navController.navigate(route)
                },
                onProfileClick = {
                    val route = Destination.createSettingsRoute()
                    navController.navigate(route)
                },
                userProfileImageUrl = uiState.userProfileImageUrl
            )
        },
        modifier = modifier
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.error ?: "Unknown error",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            uiState.movies.isEmpty() && uiState.shows.isEmpty() && uiState.episodes.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Your watchlist is empty",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    if (uiState.movies.isNotEmpty()) {
                        item {
                            WatchlistSection(
                                title = "Movies",
                                items = uiState.movies,
                                onItemClick = onItemClick
                            )
                        }
                    }

                    if (uiState.shows.isNotEmpty()) {
                        item {
                            WatchlistSection(
                                title = "TV Shows",
                                items = uiState.shows,
                                onItemClick = onItemClick
                            )
                        }
                    }

                    if (uiState.episodes.isNotEmpty()) {
                        item {
                            WatchlistSection(
                                title = "Episodes",
                                items = uiState.episodes,
                                onItemClick = onItemClick
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchlistSection(
    title: String,
    items: List<AfinityItem>,
    onItemClick: (AfinityItem) -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "$title (${items.size})",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            items(items) { item ->
                when (item) {
                    is AfinityEpisode -> {
                        ContinueWatchingCard(
                            item = item,
                            onClick = { onItemClick(item) }
                        )
                    }
                    else -> {
                        MediaItemCard(
                            item = item,
                            onClick = { onItemClick(item) }
                        )
                    }
                }
            }
        }
    }
}