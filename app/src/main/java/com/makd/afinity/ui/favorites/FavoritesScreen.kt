package com.makd.afinity.ui.favorites

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityPersonDetail
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.extensions.primaryBlurHash
import com.makd.afinity.ui.components.MediaItemCard
import com.makd.afinity.ui.components.OptimizedAsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    onItemClick: (AfinityItem) -> Unit = {},
    onPersonClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: FavoritesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.loadFavorites()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Favorites",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
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
                            contentPadding = PaddingValues(vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            if (uiState.movies.isNotEmpty()) {
                                item {
                                    FavoriteSectionHeader(title = "Movies")
                                }
                                item {
                                    FavoriteMoviesRow(
                                        movies = uiState.movies,
                                        onItemClick = onItemClick
                                    )
                                }
                            }

                            if (uiState.shows.isNotEmpty()) {
                                item {
                                    FavoriteSectionHeader(title = "TV Shows")
                                }
                                item {
                                    FavoriteShowsRow(
                                        shows = uiState.shows,
                                        onItemClick = onItemClick
                                    )
                                }
                            }

                            if (uiState.episodes.isNotEmpty()) {
                                item {
                                    FavoriteSectionHeader(title = "Episodes")
                                }
                                item {
                                    FavoriteEpisodesRow(
                                        episodes = uiState.episodes,
                                        onItemClick = onItemClick
                                    )
                                }
                            }

                            if (uiState.people.isNotEmpty()) {
                                item {
                                    FavoriteSectionHeader(title = "People")
                                }
                                item {
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

@Composable
private fun FavoriteSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall.copy(
            fontWeight = FontWeight.Bold
        ),
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

@Composable
private fun FavoriteMoviesRow(
    movies: List<AfinityMovie>,
    onItemClick: (AfinityItem) -> Unit
) {
    val configuration = LocalConfiguration.current
    val cardWidth = (configuration.screenWidthDp.dp - 56.dp) / 3.5f

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(movies) { movie ->
            FavoriteItemCard(
                item = movie,
                cardWidth = cardWidth,
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
    val configuration = LocalConfiguration.current
    val cardWidth = (configuration.screenWidthDp.dp - 56.dp) / 3.5f

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(shows) { show ->
            FavoriteItemCard(
                item = show,
                cardWidth = cardWidth,
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
    val configuration = LocalConfiguration.current
    val cardWidth = (configuration.screenWidthDp.dp - 40.dp) / 2.2f

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(episodes) { episode ->
            FavoriteEpisodeCard(
                episode = episode,
                cardWidth = cardWidth,
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
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
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
private fun FavoriteItemCard(
    item: AfinityItem,
    cardWidth: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    MediaItemCard(
        item = item,
        onClick = onClick,
        modifier = Modifier.width(cardWidth),
    )
}

@Composable
private fun FavoriteEpisodeCard(
    episode: AfinityEpisode,
    cardWidth: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(cardWidth),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            OptimizedAsyncImage(
                imageUrl = episode.images.primaryImageUrl,
                blurHash = episode.images.primaryBlurHash,
                contentDescription = episode.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                contentScale = ContentScale.Crop
            )

            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = episode.seriesName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = episode.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
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