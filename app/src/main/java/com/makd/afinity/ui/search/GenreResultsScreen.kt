@file:OptIn(ExperimentalMaterial3Api::class)

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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.makd.afinity.ui.components.FullScreenEmpty
import com.makd.afinity.ui.components.FullScreenError
import com.makd.afinity.ui.components.FullScreenLoading
import com.makd.afinity.ui.components.MediaItemCard
import com.makd.afinity.ui.components.PaginatedMediaGrid
import com.makd.afinity.ui.theme.CardDimensions.gridMinSize

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
            uiState.isLoading -> FullScreenLoading()
            uiState.error != null ->
                FullScreenError(
                    title = stringResource(R.string.error_something_went_wrong),
                    message = uiState.error,
                )
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
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else Color.Transparent,
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
                                        if (isSelected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        val activeList = if (selectedTab == 0) movies else shows
        val emptyMessage =
            if (selectedTab == 0) stringResource(R.string.empty_genre_movies)
            else stringResource(R.string.empty_genre_tv)
        val isEmpty =
            activeList.loadState.refresh is LoadState.NotLoading && activeList.itemCount == 0

        if (isEmpty) {
            FullScreenEmpty(message = emptyMessage)
        } else {
            PaginatedMediaGrid(
                items = activeList,
                widthSizeClass = widthSizeClass,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
            ) { item ->
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
