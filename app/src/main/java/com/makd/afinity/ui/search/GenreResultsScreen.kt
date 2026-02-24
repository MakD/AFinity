package com.makd.afinity.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.makd.afinity.R
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.ui.components.MediaItemCard
import com.makd.afinity.ui.theme.CardDimensions.gridMinSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenreResultsScreen(
    genre: String,
    onBackClick: () -> Unit,
    onItemClick: (AfinityItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GenreResultsViewModel = hiltViewModel(),
    widthSizeClass: WindowWidthSizeClass,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val movies =
        viewModel.moviesPagingData.collectAsStateWithLifecycle().value.collectAsLazyPagingItems()
    val shows =
        viewModel.showsPagingData.collectAsStateWithLifecycle().value.collectAsLazyPagingItems()

    LaunchedEffect(genre) { viewModel.loadGenreResults(genre) }

    Column(modifier = modifier.fillMaxSize().safeDrawingPadding()) {
        TopAppBar(
            title = {
                Text(
                    text = genre,
                    style =
                        MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                )
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_chevron_left),
                        contentDescription = stringResource(R.string.cd_back),
                    )
                }
            },
        )

        when {
            uiState.isLoading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            uiState.error != null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.error_something_went_wrong),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            text = uiState.error ?: stringResource(R.string.error_unknown),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            else -> {
                GenreResultsContent(
                    movies = movies,
                    shows = shows,
                    onItemClick = onItemClick,
                    widthSizeClass = widthSizeClass,
                )
            }
        }
    }
}

@Composable
private fun GenreResultsContent(
    movies: LazyPagingItems<AfinityItem>,
    shows: LazyPagingItems<AfinityItem>,
    onItemClick: (AfinityItem) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(stringResource(R.string.tab_movies), stringResource(R.string.tab_tv_shows))

    Column {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                tonalElevation = 2.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    tabs.forEachIndexed { index, title ->
                        val isSelected = selectedTab == index

                        Surface(
                            onClick = { selectedTab = index },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            color =
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.Transparent
                                },
                            tonalElevation = if (isSelected) 4.dp else 0.dp,
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = title,
                                    style =
                                        MaterialTheme.typography.labelLarge.copy(
                                            fontWeight =
                                                if (isSelected) FontWeight.SemiBold
                                                else FontWeight.Medium
                                        ),
                                    color =
                                        if (isSelected) {
                                            MaterialTheme.colorScheme.onPrimary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                )
                            }
                        }
                    }
                }
            }
        }

        when (selectedTab) {
            0 -> {
                val isMoviesEmpty =
                    movies.loadState.refresh is LoadState.NotLoading && movies.itemCount == 0

                if (isMoviesEmpty) {
                    EmptyStateMessage(stringResource(R.string.empty_genre_movies))
                } else {
                    ItemGrid(
                        items = movies,
                        onItemClick = onItemClick,
                        widthSizeClass = widthSizeClass,
                    )
                }
            }

            1 -> {
                val isShowsEmpty =
                    shows.loadState.refresh is LoadState.NotLoading && shows.itemCount == 0

                if (isShowsEmpty) {
                    EmptyStateMessage(stringResource(R.string.empty_genre_tv))
                } else {
                    ItemGrid(
                        items = shows,
                        onItemClick = onItemClick,
                        widthSizeClass = widthSizeClass,
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemGrid(
    items: LazyPagingItems<AfinityItem>,
    onItemClick: (AfinityItem) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(widthSizeClass.gridMinSize),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(count = items.itemCount) { index ->
            val item = items[index]
            if (item != null) {
                MediaItemCard(
                    item = item,
                    onClick = { onItemClick(item) },
                    cardWidth = widthSizeClass.gridMinSize,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun EmptyStateMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
