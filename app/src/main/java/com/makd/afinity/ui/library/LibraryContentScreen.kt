@file:OptIn(ExperimentalMaterial3Api::class)

package com.makd.afinity.ui.library

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.makd.afinity.R
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.LibraryFilters
import com.makd.afinity.navigation.Destination
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.AlphabetScroller
import com.makd.afinity.ui.components.FullScreenEmpty
import com.makd.afinity.ui.components.FullScreenError
import com.makd.afinity.ui.components.FullScreenLoading
import com.makd.afinity.ui.components.MediaItemGridCard
import com.makd.afinity.ui.components.PaginatedMediaGrid

@Composable
fun LibraryContentScreen(
    onItemClick: (AfinityItem) -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier,
    navController: NavController,
    viewModel: LibraryContentViewModel = hiltViewModel(),
    widthSizeClass: WindowWidthSizeClass,
    isMiniPlayerVisible: Boolean = false,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagingDataFlow by viewModel.pagingData.collectAsStateWithLifecycle()
    val lazyPagingItems = pagingDataFlow.collectAsLazyPagingItems()
    val gridState = rememberLazyGridState()
    val scrollToIndex by viewModel.scrollToIndex.collectAsStateWithLifecycle()
    var showSortDialog by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
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
    val playerOffset by
        animateDpAsState(
            targetValue = if (isMiniPlayerVisible) 112.dp else 0.dp,
            label = "playerOffset",
        )

    LaunchedEffect(scrollToIndex) {
        if (scrollToIndex >= 0) {
            gridState.animateScrollToItem(scrollToIndex)
            viewModel.resetScrollIndex()
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AfinityTopAppBar(
                title = {
                    Text(
                        text = uiState.libraryName,
                        style =
                            MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold
                            ),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                },
                backgroundOpacity = { 1f },
                userProfileImageUrl = uiState.userProfileImageUrl,
                onProfileClick = onProfileClick,
                onSearchClick = {
                    val route = Destination.createSearchRoute()
                    navController.navigate(route)
                },
            )
            Box(
                modifier =
                    Modifier.weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    when {
                        uiState.isLoading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                FullScreenLoading()
                            }
                        }

                        uiState.error != null -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                FullScreenError(message = uiState.error)
                            }
                        }

                        lazyPagingItems.itemCount == 0 &&
                            lazyPagingItems.loadState.refresh !is LoadState.Loading -> {
                            val selectedLetter = uiState.selectedLetter
                            if (selectedLetter != null) {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    Box(
                                        modifier =
                                            Modifier.weight(1f).fillMaxSize().padding(top = 16.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        FullScreenEmpty(
                                            title = stringResource(R.string.library_empty_title),
                                            message =
                                                stringResource(
                                                    R.string.library_empty_letter_fmt,
                                                    selectedLetter,
                                                ),
                                            actionText = stringResource(R.string.action_show_all),
                                            onActionClick = { viewModel.clearLetterFilter() },
                                        )
                                    }
                                    Box(
                                        modifier = Modifier.fillMaxHeight(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        AlphabetScroller(
                                            onLetterSelected = { viewModel.scrollToLetter(it) },
                                            selectedLetter = uiState.selectedLetter,
                                            modifier =
                                                Modifier.background(
                                                    MaterialTheme.colorScheme.surface.copy(
                                                        alpha = 0.8f
                                                    )
                                                ),
                                        )
                                    }
                                }
                            } else if (!uiState.currentFilters.isEmpty) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    FullScreenEmpty(
                                        title = stringResource(R.string.library_empty_title),
                                        message = stringResource(R.string.filter_empty_filtered),
                                        actionText = stringResource(R.string.action_clear_filter),
                                        onActionClick = {
                                            viewModel.updateFilters(LibraryFilters())
                                        },
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    FullScreenEmpty(
                                        title = stringResource(R.string.library_empty_title),
                                        message = stringResource(R.string.library_empty_message),
                                    )
                                }
                            }
                        }

                        else -> {
                            Row(modifier = Modifier.fillMaxSize()) {
                                PaginatedMediaGrid(
                                    items = lazyPagingItems,
                                    widthSizeClass = widthSizeClass,
                                    state = gridState,
                                    modifier = Modifier.weight(1f),
                                    contentPadding =
                                        PaddingValues(
                                            start = 16.dp,
                                            end = 16.dp,
                                            top = 16.dp,
                                            bottom = 80.dp + playerOffset,
                                        ),
                                ) { item ->
                                    MediaItemGridCard(
                                        item = item,
                                        onClick = {
                                            viewModel.onItemClick(item)
                                            onItemClick(item)
                                        },
                                    )
                                }
                                Box(
                                    modifier = Modifier.fillMaxHeight(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    AlphabetScroller(
                                        onLetterSelected = { viewModel.scrollToLetter(it) },
                                        selectedLetter = uiState.selectedLetter,
                                        modifier =
                                            Modifier.background(
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                                            ),
                                    )
                                }
                            }
                        }
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.End,
                    modifier =
                        Modifier.align(Alignment.BottomEnd)
                            .padding(end = 24.dp)
                            .padding(bottom = 16.dp + playerOffset),
                ) {
                    FloatingActionButton(onClick = { showFilterSheet = true }) {
                        val activeCount = uiState.currentFilters.activeCount
                        if (activeCount > 0) {
                            BadgedBox(badge = { Badge { Text(activeCount.toString()) } }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_filter_active),
                                    contentDescription = stringResource(R.string.cd_filter_fab),
                                )
                            }
                        } else {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_filter),
                                contentDescription = stringResource(R.string.cd_filter_fab),
                            )
                        }
                    }

                    FloatingActionButton(onClick = { showSortDialog = true }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_arrows_sort),
                            contentDescription = stringResource(R.string.cd_sort_fab),
                        )
                    }
                }
            }
        }
    }

    if (showSortDialog) {
        SortDialog(
            currentSortBy = uiState.currentSortBy,
            currentSortDescending = uiState.currentSortDescending,
            onDismiss = { showSortDialog = false },
            onSortSelected = { sortBy, descending ->
                viewModel.updateSort(sortBy, descending)
                showSortDialog = false
            },
        )
    }

    if (showFilterSheet) {
        LibraryFilterBottomSheet(
            filters = uiState.currentFilters,
            options = uiState.filterOptions,
            capabilities = libraryFilterCapabilities(uiState.libraryType),
            onApply = { viewModel.updateFilters(it) },
            onDismiss = { showFilterSheet = false },
        )
    }
}

@Composable
private fun SortDialog(
    currentSortBy: SortBy,
    currentSortDescending: Boolean,
    onDismiss: () -> Unit,
    onSortSelected: (SortBy, Boolean) -> Unit,
) {
    var isAscending by remember { mutableStateOf(!currentSortDescending) }
    var selectedSort by remember { mutableStateOf(currentSortBy) }
    val maxDialogHeight = (LocalConfiguration.current.screenHeightDp * 0.92f).dp

    Dialog(
        onDismissRequest = {
            onSortSelected(selectedSort, !isAscending)
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier =
                Modifier.fillMaxWidth()
                    .widthIn(max = 480.dp)
                    .heightIn(max = maxDialogHeight)
                    .padding(16.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier =
                        Modifier.weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.sort_title),
                        style =
                            MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )

                    Spacer(Modifier.height(16.dp))

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = isAscending,
                            onClick = { isAscending = true },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        ) {
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.sort_ascending))
                        }

                        SegmentedButton(
                            selected = !isAscending,
                            onClick = { isAscending = false },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        ) {
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.sort_descending))
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        SortOptionRow(
                            stringResource(R.string.sort_option_title),
                            SortBy.NAME,
                            selectedSort,
                        ) {
                            selectedSort = it
                        }
                        SortOptionRow(
                            stringResource(R.string.sort_option_imdb),
                            SortBy.IMDB_RATING,
                            selectedSort,
                        ) {
                            selectedSort = it
                        }
                        SortOptionRow(
                            stringResource(R.string.sort_option_parental),
                            SortBy.PARENTAL_RATING,
                            selectedSort,
                        ) {
                            selectedSort = it
                        }
                        SortOptionRow(
                            stringResource(R.string.sort_option_date_added),
                            SortBy.DATE_ADDED,
                            selectedSort,
                        ) {
                            selectedSort = it
                        }
                        SortOptionRow(
                            stringResource(R.string.sort_option_date_played),
                            SortBy.DATE_PLAYED,
                            selectedSort,
                        ) {
                            selectedSort = it
                        }
                        SortOptionRow(
                            stringResource(R.string.sort_option_release_date),
                            SortBy.RELEASE_DATE,
                            selectedSort,
                        ) {
                            selectedSort = it
                        }
                    }
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                TextButton(
                    onClick = {
                        onSortSelected(selectedSort, !isAscending)
                        onDismiss()
                    },
                    modifier =
                        Modifier.fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    Text(stringResource(R.string.action_apply))
                }
            }
        }
    }
}

@Composable
private fun SortOptionRow(
    label: String,
    sortBy: SortBy,
    selectedSort: SortBy,
    onSelect: (SortBy) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onSelect(sortBy) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selectedSort == sortBy, onClick = { onSelect(sortBy) })
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}
