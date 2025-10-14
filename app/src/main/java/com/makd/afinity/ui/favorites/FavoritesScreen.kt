package com.makd.afinity.ui.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.makd.afinity.data.models.extensions.primaryBlurHash
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityPersonDetail
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.navigation.Destination
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.ContinueWatchingCard
import com.makd.afinity.ui.components.MediaItemCard
import com.makd.afinity.ui.components.OptimizedAsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    onItemClick: (AfinityItem) -> Unit = {},
    onPersonClick: (String) -> Unit = {},
    navController: NavController,
    modifier: Modifier = Modifier,
    viewModel: FavoritesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadFavorites()
    }

    Scaffold(
        topBar = {
            AfinityTopAppBar(
                title = "Favorites",
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
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                uiState.error != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "Something went wrong",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = uiState.error ?: "Unknown error",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> {
                    val hasAnyFavorites = uiState.movies.isNotEmpty() ||
                            uiState.shows.isNotEmpty() ||
                            uiState.episodes.isNotEmpty() ||
                            uiState.people.isNotEmpty()

                    if (!hasAnyFavorites) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "No Favorites Yet",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Items you mark as favorites will appear here",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            if (uiState.movies.isNotEmpty()) {
                                item {
                                    FavoriteSection(
                                        title = "Movies (${uiState.movies.size})"
                                    ) {
                                        FavoriteMoviesRow(
                                            movies = uiState.movies,
                                            onItemClick = onItemClick
                                        )
                                    }
                                }
                            }

                            if (uiState.shows.isNotEmpty()) {
                                item {
                                    FavoriteSection(
                                        title = "TV Shows (${uiState.shows.size})"
                                    ) {
                                        FavoriteShowsRow(
                                            shows = uiState.shows,
                                            onItemClick = onItemClick
                                        )
                                    }
                                }
                            }

                            if (uiState.episodes.isNotEmpty()) {
                                item {
                                    FavoriteSection(
                                        title = "Episodes (${uiState.episodes.size})"
                                    ) {
                                        FavoriteEpisodesRow(
                                            episodes = uiState.episodes,
                                            onItemClick = onItemClick
                                        )
                                    }
                                }
                            }

                            if (uiState.people.isNotEmpty()) {
                                item {
                                    FavoriteSection(
                                        title = "People (${uiState.people.size})"
                                    ) {
                                        FavoritePeopleRow(
                                            people = uiState.people,
                                            onPersonClick = onPersonClick
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
        content()
    }
}

@Composable
private fun FavoriteMoviesRow(
    movies: List<AfinityMovie>,
    onItemClick: (AfinityItem) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 0.dp)
    ) {
        items(movies) { movie ->
            MediaItemCard(
                item = movie,
                onClick = { onItemClick(movie) }
            )
        }
    }
}

@Composable
private fun FavoriteShowsRow(
    shows: List<AfinityShow>,
    onItemClick: (AfinityItem) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 0.dp)
    ) {
        items(shows) { show ->
            MediaItemCard(
                item = show,
                onClick = { onItemClick(show) }
            )
        }
    }
}

@Composable
private fun FavoriteEpisodesRow(
    episodes: List<AfinityEpisode>,
    onItemClick: (AfinityItem) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 0.dp)
    ) {
        items(episodes) { episode ->
            ContinueWatchingCard(
                item = episode,
                onClick = { onItemClick(episode) }
            )
        }
    }
}

@Composable
private fun FavoritePeopleRow(
    people: List<AfinityPersonDetail>,
    onPersonClick: (String) -> Unit
) {
    val configuration = LocalConfiguration.current
    val itemWidth = (configuration.screenWidthDp.dp / 2) - (14.dp * 2)
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 0.dp)
    ) {
        items(people) { person ->
            FavoritePersonCard(
                person = person,
                onClick = { onPersonClick(person.id.toString()) }
            )
        }
    }
}

@Composable
private fun FavoritePersonCard(
    person: AfinityPersonDetail,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(80.dp)
            .clip(RoundedCornerShape(8.dp))
    ) {
        Card(
            onClick = onClick,
            shape = CircleShape,
            modifier = Modifier.size(64.dp)
        ) {
            OptimizedAsyncImage(
                imageUrl = person.images.primaryImageUrl,
                blurHash = person.images.primaryBlurHash,
                contentDescription = person.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = person.name,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}