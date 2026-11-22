package com.makd.afinity.ui.audiobookshelf.libraries

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.ui.components.AfinityScreen
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.AfinityTopAppBarTitle
import com.makd.afinity.ui.components.AppBarProfile
import com.makd.afinity.ui.components.FilterSortToolbar
import com.makd.afinity.ui.components.SortOptionsDialog
import com.makd.afinity.ui.components.bottomClearance
import com.makd.afinity.ui.components.rememberFilterSortToolbarScrollBehavior
import com.makd.afinity.ui.main.MainUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudiobookshelfSeriesListScreen(
    onNavigateToItem: (String) -> Unit,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    mainUiState: MainUiState,
    widthSizeClass: WindowWidthSizeClass,
    viewModel: AudiobookshelfSeriesListViewModel = hiltViewModel(),
) {
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val allSeries by viewModel.allSeries.collectAsStateWithLifecycle()
    val display by viewModel.displaySeries.collectAsStateWithLifecycle()
    val config by viewModel.currentConfig.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val filterOptions by viewModel.filterOptions.collectAsStateWithLifecycle()
    val bottomClearance = bottomClearance()
    val toolbarScroll = rememberFilterSortToolbarScrollBehavior()
    var showSortDialog by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    ScrollToTopOnOrderChange(display.orderVersion) { listState.requestScrollToItem(0) }

    if (showSortDialog) {
        SortOptionsDialog(
            options = AbsSeriesSort.entries,
            current = sort.sort,
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
            progressOptions = AbsProgressFilter.withoutNotFinished,
        )
    }

    AfinityScreen(
        topBar = { backgroundOpacity ->
            AfinityTopAppBar(
                title = { AfinityTopAppBarTitle(stringResource(R.string.abs_tab_series)) },
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
        modifier = Modifier.nestedScroll(toolbarScroll),
    ) { innerPadding ->
        AudiobookshelfSeriesTab(
            seriesList = display.items,
            serverUrl = config?.serverUrl,
            onItemClick = { item -> onNavigateToItem(item.id) },
            isLoading = isLoading,
            widthSizeClass = widthSizeClass,
            modifier = Modifier.padding(innerPadding),
            filtersActive = filters.isActive,
            onClearFilters = viewModel::clearFilters,
            listState = listState,
        )

        if (!isLoading && allSeries.isNotEmpty()) {
            FilterSortToolbar(
                modifier =
                    Modifier.align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp + bottomClearance),
                onFilterClick = { showFilterSheet = true },
                filterActive = filters.isActive,
                filterCount = filters.activeCount,
                onSortClick = { showSortDialog = true },
                scrollBehavior = toolbarScroll,
            )
        }
    }
}
