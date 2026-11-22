package com.makd.afinity.ui.audiobookshelf.libraries

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.ui.audiobookshelf.libraries.components.AudiobookCard
import com.makd.afinity.ui.components.AfinityScreen
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.AfinityTopAppBarTitle
import com.makd.afinity.ui.components.AlphabetScroller
import com.makd.afinity.ui.components.AppBarProfile
import com.makd.afinity.ui.components.FAB_CONTENT_BOTTOM_PADDING
import com.makd.afinity.ui.components.FilterSortToolbar
import com.makd.afinity.ui.components.FullScreenEmpty
import com.makd.afinity.ui.components.FullScreenLoading
import com.makd.afinity.ui.components.SortOptionsDialog
import com.makd.afinity.ui.components.playerClearance
import com.makd.afinity.ui.components.rememberFilterSortToolbarScrollBehavior
import com.makd.afinity.ui.main.MainUiState
import com.makd.afinity.ui.theme.CardDimensions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiobookshelfLibraryScreen(
    onNavigateToItem: (String) -> Unit,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    mainUiState: MainUiState,
    viewModel: AudiobookshelfLibraryViewModel = hiltViewModel(),
) {
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val display by viewModel.display.collectAsStateWithLifecycle()
    val displayItems = display.items
    val selectedLetter by viewModel.selectedLetter.collectAsStateWithLifecycle()
    val config by viewModel.currentConfig.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val filterOptions by viewModel.filterOptions.collectAsStateWithLifecycle()
    val isPodcastLibrary by viewModel.isPodcastLibrary.collectAsStateWithLifecycle()
    val playerClearance = playerClearance()
    val toolbarScroll = rememberFilterSortToolbarScrollBehavior()
    var showSortDialog by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    ScrollToTopOnOrderChange(display.orderVersion) { gridState.requestScrollToItem(0) }

    if (showSortDialog) {
        SortOptionsDialog(
            options = AbsItemSort.optionsFor(isPodcastLibrary),
            current = sort.sort.takeIf { it.availableFor(isPodcastLibrary) } ?: AbsItemSort.TITLE,
            currentDescending = sort.descending,
            optionLabel = { stringResource(it.labelRes) },
            onDismiss = { showSortDialog = false },
            onSortSelected = viewModel::setSort,
        )
    }

    if (showFilterSheet) {
        AbsFilterSheet(
            filters = filters,
            options = filterOptions,
            onApply = viewModel::setFilters,
            onDismiss = { showFilterSheet = false },
            progressOptions =
                if (isPodcastLibrary) AbsProgressFilter.withoutNotFinished
                else AbsProgressFilter.entries,
        )
    }

    AfinityScreen(
        topBar = { backgroundOpacity ->
            AfinityTopAppBar(
                title = { AfinityTopAppBarTitle(viewModel.libraryName) },
                backgroundOpacity = backgroundOpacity,
                onSearchClick = onSearchClick,
                profile =
                    AppBarProfile(
                        onClick = onProfileClick,
                        name = mainUiState.userName,
                        imageUrl = mainUiState.userProfileImageUrl,
                    ),
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding).nestedScroll(toolbarScroll)) {
            when {
                isLoading -> FullScreenLoading()

                displayItems.isEmpty() && selectedLetter != null -> {
                    Row(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier.weight(1f).fillMaxSize().padding(top = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            FullScreenEmpty(
                                title = stringResource(R.string.library_empty_title),
                                message =
                                    stringResource(
                                        R.string.library_empty_letter_fmt,
                                        selectedLetter.orEmpty(),
                                    ),
                                actionText = stringResource(R.string.action_show_all),
                                onActionClick = viewModel::clearLetterFilter,
                            )
                        }
                        Box(
                            modifier = Modifier.fillMaxHeight(),
                            contentAlignment = Alignment.Center,
                        ) {
                            AlphabetScroller(
                                selectedLetter = selectedLetter,
                                onLetterSelected = viewModel::onLetterSelected,
                                modifier =
                                    Modifier.background(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                        shape = MaterialTheme.shapes.small,
                                    ),
                            )
                        }
                    }
                }

                displayItems.isEmpty() && filters.isActive -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        FullScreenEmpty(
                            title = stringResource(R.string.library_empty_title),
                            message = stringResource(R.string.filter_empty_filtered),
                            actionText = stringResource(R.string.action_clear_filter),
                            onActionClick = viewModel::clearFilters,
                        )
                    }
                }

                displayItems.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        FullScreenEmpty(
                            title = stringResource(R.string.library_empty_title),
                            message = stringResource(R.string.abs_no_items_in_library),
                        )
                    }
                }

                else -> {
                    Row(modifier = Modifier.fillMaxSize()) {
                        LazyVerticalGrid(
                            columns = CardDimensions.gridCells(),
                            state = gridState,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            contentPadding =
                                PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 16.dp,
                                    bottom = FAB_CONTENT_BOTTOM_PADDING + playerClearance,
                                ),
                            horizontalArrangement = Arrangement.spacedBy(CardDimensions.RowGap),
                            verticalArrangement = Arrangement.spacedBy(CardDimensions.GridRowGap),
                        ) {
                            items(displayItems, key = { it.id }) { item ->
                                AudiobookCard(
                                    item = item,
                                    serverUrl = config?.serverUrl,
                                    onClick = { onNavigateToItem(item.id) },
                                )
                            }
                        }

                        Box(
                            modifier = Modifier.fillMaxHeight(),
                            contentAlignment = Alignment.Center,
                        ) {
                            AlphabetScroller(
                                selectedLetter = selectedLetter,
                                onLetterSelected = viewModel::onLetterSelected,
                                modifier =
                                    Modifier.background(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                        shape = MaterialTheme.shapes.small,
                                    ),
                            )
                        }
                    }
                }
            }

            if (!isLoading && (displayItems.isNotEmpty() || filters.isActive)) {
                FilterSortToolbar(
                    modifier =
                        Modifier.align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp + playerClearance),
                    onFilterClick = { showFilterSheet = true },
                    filterActive = filters.isActive,
                    filterCount = filters.activeCount,
                    onSortClick = { showSortDialog = true },
                    scrollBehavior = toolbarScroll,
                )
            }
        }
    }
}

@Composable
internal fun ScrollToTopOnOrderChange(orderVersion: Int, scrollToTop: () -> Unit) {
    var seenVersion by rememberSaveable { mutableIntStateOf(orderVersion) }
    SideEffect {
        if (orderVersion != seenVersion) {
            seenVersion = orderVersion
            scrollToTop()
        }
    }
}
