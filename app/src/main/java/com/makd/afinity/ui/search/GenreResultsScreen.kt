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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.makd.afinity.R
import com.makd.afinity.data.models.media.AfinityAudiobook
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMusicVideo
import com.makd.afinity.ui.components.AfinityBackButton
import com.makd.afinity.ui.components.AfinityScreen
import com.makd.afinity.ui.components.AfinityTopAppBarTitle
import com.makd.afinity.ui.components.AfinityTopBar
import com.makd.afinity.ui.components.AudiobookCardFrame
import com.makd.afinity.ui.components.FullScreenEmpty
import com.makd.afinity.ui.components.FullScreenError
import com.makd.afinity.ui.components.FullScreenLoading
import com.makd.afinity.ui.components.MediaItemCard
import com.makd.afinity.ui.components.MediaItemGridCard
import com.makd.afinity.ui.components.PaginatedMediaGrid
import com.makd.afinity.ui.components.playerClearance
import com.makd.afinity.ui.theme.CardDimensions
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
    val books =
        viewModel.booksPagingData.collectAsStateWithLifecycle().value.collectAsLazyPagingItems()
    val hasBooksLibrary by viewModel.hasBooksLibrary.collectAsStateWithLifecycle()
    val musicVideos =
        viewModel.musicVideosPagingData
            .collectAsStateWithLifecycle()
            .value
            .collectAsLazyPagingItems()
    val hasMusicVideosLibrary by viewModel.hasMusicVideosLibrary.collectAsStateWithLifecycle()
    val playerClearance = playerClearance()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onScreenResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(genre) { viewModel.loadGenreResults(genre) }

    val customPadding =
        PaddingValues(top = 0.dp, start = 0.dp, end = 0.dp, bottom = playerClearance)

    AfinityScreen(
        topBar = { backgroundOpacity ->
            AfinityTopBar(
                title = { AfinityTopAppBarTitle(genre) },
                navigationIcon = { AfinityBackButton(onClick = onBackClick) },
                backgroundOpacity = backgroundOpacity,
            )
        },
        modifier = modifier,
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
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
                        books = if (hasBooksLibrary) books else null,
                        musicVideos = if (hasMusicVideosLibrary) musicVideos else null,
                        onItemClick = onItemClick,
                        widthSizeClass = widthSizeClass,
                        customPadding = customPadding,
                    )
                }
            }
        }
    }
}

private class GenreTab(
    val title: String,
    val items: LazyPagingItems<AfinityItem>,
    val emptyMessage: String,
    val landscape: Boolean = false,
)

@Composable
private fun GenreResultsContent(
    movies: LazyPagingItems<AfinityItem>,
    shows: LazyPagingItems<AfinityItem>,
    books: LazyPagingItems<AfinityItem>?,
    musicVideos: LazyPagingItems<AfinityItem>?,
    onItemClick: (AfinityItem) -> Unit,
    widthSizeClass: WindowWidthSizeClass,
    customPadding: PaddingValues,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var tabChosen by rememberSaveable { mutableStateOf(false) }
    val genreTabs =
        listOfNotNull(
            GenreTab(
                stringResource(R.string.tab_movies),
                movies,
                stringResource(R.string.empty_genre_movies),
            ),
            GenreTab(
                stringResource(R.string.tab_tv_shows),
                shows,
                stringResource(R.string.empty_genre_tv),
            ),
            books?.let {
                GenreTab(
                    stringResource(R.string.section_books),
                    it,
                    stringResource(R.string.empty_genre_books),
                )
            },
            musicVideos?.let {
                GenreTab(
                    stringResource(R.string.music_section_music_videos),
                    it,
                    stringResource(R.string.empty_genre_music_videos),
                    landscape = true,
                )
            },
        )
    val tabs = genreTabs.map { it.title }
    val settled = genreTabs.map { it.items.loadState.refresh is LoadState.NotLoading }
    val counts = genreTabs.map { it.items.itemCount }
    LaunchedEffect(settled, counts) {
        val current = genreTabs.getOrNull(selectedTab)
        val currentEmpty = current == null || (settled[selectedTab] && counts[selectedTab] == 0)
        if (!tabChosen && currentEmpty) {
            val firstFilled = counts.indexOfFirst { it > 0 }
            if (firstFilled >= 0) selectedTab = firstFilled
        }
    }

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
                            onClick = {
                                selectedTab = index
                                tabChosen = true
                            },
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

        val activeTab = genreTabs.getOrElse(selectedTab) { genreTabs.first() }
        val activeList = activeTab.items
        val isEmpty =
            activeList.loadState.refresh is LoadState.NotLoading && activeList.itemCount == 0

        if (isEmpty) {
            FullScreenEmpty(message = activeTab.emptyMessage)
        } else {
            PaginatedMediaGrid(
                items = activeList,
                modifier = Modifier.fillMaxSize(),
                columns =
                    if (activeTab.landscape) CardDimensions.landscapeGridCells()
                    else CardDimensions.gridCells(),
                contentPadding =
                    PaddingValues(
                        top = 16.dp,
                        start = 16.dp,
                        end = 16.dp,
                        bottom = customPadding.calculateBottomPadding() + 16.dp,
                    ),
            ) { item ->
                if (item is AfinityAudiobook || item is AfinityMusicVideo) {
                    MediaItemGridCard(
                        item = item,
                        onClick = { onItemClick(item) },
                        audiobookFrame = AudiobookCardFrame.LETTERBOX,
                    )
                } else {
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
}
