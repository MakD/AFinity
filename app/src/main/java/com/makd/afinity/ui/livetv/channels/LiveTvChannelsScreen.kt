@file:UnstableApi

package com.makd.afinity.ui.livetv.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.makd.afinity.R
import com.makd.afinity.data.models.livetv.AfinityChannel
import com.makd.afinity.navigation.Destination
import com.makd.afinity.navigation.LocalPlayerOffset
import com.makd.afinity.ui.components.AlphabetScroller
import com.makd.afinity.ui.components.FilterFab
import com.makd.afinity.ui.components.FullScreenError
import com.makd.afinity.ui.components.FullScreenLoading
import com.makd.afinity.ui.livetv.components.ChannelCard
import com.makd.afinity.ui.livetv.components.LiveTvFilterDialog
import com.makd.afinity.ui.livetv.components.LiveTvTopAppBar
import com.makd.afinity.ui.main.MainUiState
import com.makd.afinity.ui.player.PlayerLauncher
import com.makd.afinity.ui.theme.CardDimensions
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun LiveTvChannelsScreen(
    navController: NavController,
    mainUiState: MainUiState,
    modifier: Modifier = Modifier,
    viewModel: LiveTvChannelsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val viewOptions by viewModel.viewOptions.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showFilters by rememberSaveable { mutableStateOf(false) }

    LifecycleResumeEffect(viewModel) {
        viewModel.setScreenVisible(true)
        onPauseOrDispose { viewModel.setScreenVisible(false) }
    }

    Scaffold(
        topBar = {
            LiveTvTopAppBar(
                title = stringResource(R.string.livetv_tab_channels),
                navController = navController,
                mainUiState = mainUiState,
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        LiveTvChannelsContent(
            uiState = uiState,
            onChannelClick = { channel ->
                navController.navigate(Destination.createLiveTvChannelRoute(channel.id.toString()))
            },
            onChannelLongClick = { channel ->
                PlayerLauncher.launchLiveChannel(
                    context = context,
                    channelId = channel.id,
                    channelName = channel.name,
                )
            },
            onFavoriteClick = { channel -> viewModel.toggleFavorite(channel.id) },
            onLetterSelected = viewModel::onLetterSelected,
            onClearFilter = viewModel::clearLetterFilter,
            onVisibleChannelsChanged = viewModel::onVisibleChannelsChanged,
            onRetry = viewModel::retry,
            filterCount = viewOptions.activeCount(includeGuideOptions = false),
            onFilterClick = { showFilters = true },
            modifier = Modifier.padding(paddingValues),
        )
    }

    if (showFilters) {
        LiveTvFilterDialog(
            options = viewOptions,
            showGuideOptions = false,
            onApply = viewModel::applyViewOptions,
            onDismiss = { showFilters = false },
        )
    }
}

@Composable
private fun LiveTvChannelsContent(
    uiState: LiveTvChannelsUiState,
    onChannelClick: (AfinityChannel) -> Unit,
    onChannelLongClick: (AfinityChannel) -> Unit,
    onFavoriteClick: (AfinityChannel) -> Unit,
    onLetterSelected: (String) -> Unit,
    onClearFilter: () -> Unit,
    onVisibleChannelsChanged: (Int, Int) -> Unit,
    onRetry: () -> Unit,
    filterCount: Int,
    onFilterClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (uiState.isLoading) {
        FullScreenLoading(modifier = modifier)
        return
    }

    if (uiState.loadFailed && uiState.allChannels.isEmpty()) {
        FullScreenError(
            message = stringResource(R.string.livetv_channels_load_failed),
            modifier = modifier,
            actionText = stringResource(R.string.action_retry),
            onActionClick = onRetry,
        )
        return
    }

    val selectedLetter = uiState.selectedLetter
    val playerOffset = LocalPlayerOffset.current
    val safeBottom = WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding()

    val gridState = rememberLazyGridState()
    val currentOnVisibleChannelsChanged by rememberUpdatedState(onVisibleChannelsChanged)
    LaunchedEffect(gridState) {
        snapshotFlow {
            val lastItem = gridState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: 0
            (gridState.firstVisibleItemIndex - 1).coerceAtLeast(0) to
                (lastItem - 1).coerceAtLeast(0)
        }
            .distinctUntilChanged()
            .collect { (first, last) -> currentOnVisibleChannelsChanged(first, last) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                if (uiState.channels.isEmpty() && selectedLetter != null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text =
                                    stringResource(
                                        R.string.livetv_empty_letter_fmt,
                                        selectedLetter,
                                    ),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(
                                onClick = onClearFilter,
                                modifier = Modifier.padding(top = 16.dp),
                            ) {
                                Text(stringResource(R.string.action_show_all_channels))
                            }
                        }
                    }
                } else if (uiState.channels.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.livetv_empty_generic),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = CardDimensions.channelGridCells,
                        modifier = Modifier.fillMaxSize(),
                        state = gridState,
                        contentPadding =
                            PaddingValues(
                                start = 14.dp,
                                end = 16.dp,
                                top = 16.dp,
                                bottom = max(safeBottom, playerOffset) + 16.dp,
                            ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                                Text(
                                    text =
                                        if (selectedLetter != null)
                                            stringResource(
                                                R.string.livetv_header_letter_fmt,
                                                selectedLetter,
                                            )
                                        else stringResource(R.string.livetv_header_all),
                                    style =
                                        MaterialTheme.typography.headlineSmall.copy(
                                            fontWeight = FontWeight.Bold
                                        ),
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                Text(
                                    text =
                                        pluralStringResource(
                                            R.plurals.livetv_count_fmt,
                                            uiState.channels.size,
                                            uiState.channels.size,
                                        ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        items(items = uiState.channels, key = { it.id }) { channel ->
                            ChannelCard(
                                channel = channel,
                                onClick = { onChannelClick(channel) },
                                onFavoriteClick = { onFavoriteClick(channel) },
                                showProgramOverlays = false,
                                programPending = channel.id !in uiState.programsLoaded,
                                onLongClick = { onChannelLongClick(channel) },
                            )
                        }
                    }
                }

                FilterFab(
                    active = filterCount > 0,
                    onClick = onFilterClick,
                    badgeCount = filterCount,
                    modifier =
                        Modifier.align(Alignment.BottomEnd)
                            .padding(end = 8.dp, bottom = max(safeBottom, playerOffset) + 16.dp),
                )
            }

            Box(
                modifier =
                    Modifier.fillMaxHeight()
                        .padding(top = 16.dp, bottom = safeBottom)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End)),
                contentAlignment = Alignment.Center,
            ) {
                AlphabetScroller(
                    selectedLetter = selectedLetter,
                    onLetterSelected = onLetterSelected,
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
