package com.makd.afinity.ui.admin.logs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.ui.admin.components.AdminLoadContent
import com.makd.afinity.ui.admin.components.AdminScaffold
import com.makd.afinity.ui.components.AfinityActionMenu
import com.makd.afinity.ui.components.AfinityModalBottomSheet
import com.makd.afinity.ui.components.DetailMenuAction
import com.makd.afinity.ui.components.rememberAfinitySheetState
import com.makd.afinity.ui.settings.logs.ErrorJumpPill
import com.makd.afinity.ui.settings.logs.JumpToLatestPill
import com.makd.afinity.ui.settings.logs.LogCountChip
import com.makd.afinity.ui.settings.logs.LogEmptyReason
import com.makd.afinity.ui.settings.logs.LogEmptyState
import com.makd.afinity.ui.settings.logs.LogLevelColors
import com.makd.afinity.ui.settings.logs.MetaBar
import com.makd.afinity.ui.settings.logs.SearchField
import com.makd.afinity.ui.settings.logs.TagChip
import com.makd.afinity.ui.settings.logs.TagFilterSheetContent
import com.makd.afinity.util.logging.LogLevel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerLogViewerScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ServerLogViewerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val view = state.view
    var showTags by rememberSaveable { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    BackHandler(enabled = state.searchActive) { viewModel.setSearchActive(false) }

    AdminScaffold(
        title = viewModel.fileName,
        onBackClick = {
            if (state.searchActive) viewModel.setSearchActive(false) else onBackClick()
        },
        modifier = modifier,
        titleContent =
            if (state.searchActive) {
                { SearchField(query = state.query, onQueryChange = viewModel::setQuery) }
            } else {
                null
            },
        actions = {
            if (view != null) {
                IconButton(onClick = { viewModel.setSearchActive(!state.searchActive) }) {
                    Icon(
                        painter =
                            painterResource(
                                id =
                                    if (state.searchActive) R.drawable.ic_close
                                    else R.drawable.ic_search
                            ),
                        contentDescription =
                            stringResource(
                                if (state.searchActive) R.string.action_cancel
                                else R.string.logs_search
                            ),
                    )
                }
                if (!state.searchActive) {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_dots_vertical),
                                contentDescription = stringResource(R.string.logs_more),
                            )
                        }
                        ServerLogMenu(
                            expanded = showMenu,
                            matchCount = view.matchCount,
                            selectedTagCount = state.tags.size,
                            showGrouping = state.level != null,
                            groupRepeats = state.groupRepeats,
                            onDismiss = { showMenu = false },
                            onCopyVisible = {
                                showMenu = false
                                viewModel.copyVisible()
                            },
                            onTags = {
                                showMenu = false
                                showTags = true
                            },
                            onToggleGrouping = {
                                showMenu = false
                                viewModel.toggleGrouping()
                            },
                        )
                    }
                }
            }
        },
    ) { contentPadding ->
        AdminLoadContent(
            data = state.takeIf { it.isLoaded },
            failed = state.failed,
            onRetry = viewModel::load,
            contentPadding = contentPadding,
        ) { loaded ->
            val parsed = loaded.view
            val plain = loaded.plain
            if (parsed != null) {
                ServerLogContent(
                    state = loaded,
                    view = parsed,
                    contentPadding = contentPadding,
                    onSelectLevel = viewModel::selectLevel,
                    onToggleTag = viewModel::toggleTag,
                    onToggleGrouping = viewModel::toggleGrouping,
                    onRowClick = viewModel::toggleExpanded,
                    onCopy = viewModel::copyRow,
                    onCopyWithContext = viewModel::copyRowWithContext,
                    onShowAll = viewModel::showGroup,
                    onClearGroupFilter = viewModel::clearGroupFilter,
                    onClearFilters = viewModel::clearFilters,
                )
            } else if (plain != null) {
                ServerLogPlainContent(
                    lines = plain.lines,
                    totalLines = plain.totalLines,
                    contentPadding = contentPadding,
                )
            }
        }
    }

    if (showTags && view != null) {
        val sheetState = rememberAfinitySheetState()
        AfinityModalBottomSheet(onDismissRequest = { showTags = false }, sheetState = sheetState) {
            TagFilterSheetContent(
                tagCounts = view.document.tagCounts,
                selected = state.tags,
                matchCount = view.matchCount,
                onToggle = viewModel::toggleTag,
                onClear = viewModel::clearTags,
                onDone = { showTags = false },
                subtitle = stringResource(R.string.server_log_tags_subtitle),
                warningTint = LogLevelColors.content(LogLevel.WARN),
                errorTint = LogLevelColors.content(LogLevel.ERROR),
            )
        }
    }
}

@Composable
private fun ServerLogMenu(
    expanded: Boolean,
    matchCount: Int,
    selectedTagCount: Int,
    showGrouping: Boolean,
    groupRepeats: Boolean,
    onDismiss: () -> Unit,
    onCopyVisible: () -> Unit,
    onTags: () -> Unit,
    onToggleGrouping: () -> Unit,
) {
    val copyLabel = stringResource(R.string.logs_copy_visible)
    val tagsLabel = stringResource(R.string.logs_tags_action)
    val groupingLabel =
        stringResource(
            if (groupRepeats) R.string.logs_expand_repeats else R.string.logs_collapse_repeats
        )
    val actions = buildList {
        add(
            DetailMenuAction(
                label = copyLabel,
                iconRes = R.drawable.ic_clipboard,
                onClick = onCopyVisible,
                trailingText = matchCount.toString(),
            )
        )
        add(
            DetailMenuAction(
                label = tagsLabel,
                iconRes = R.drawable.ic_filter,
                onClick = onTags,
                trailingText = selectedTagCount.takeIf { it > 0 }?.toString(),
            )
        )
        if (showGrouping) {
            add(
                DetailMenuAction(
                    label = groupingLabel,
                    iconRes = R.drawable.ic_arrows_output,
                    onClick = onToggleGrouping,
                )
            )
        }
    }
    AfinityActionMenu(expanded = expanded, onDismissRequest = onDismiss, actions = actions)
}

@Composable
private fun ServerLogContent(
    state: ServerLogViewerUiState,
    view: ServerLogView,
    contentPadding: PaddingValues,
    onSelectLevel: (LogLevel?) -> Unit,
    onToggleTag: (String) -> Unit,
    onToggleGrouping: () -> Unit,
    onRowClick: (String) -> Unit,
    onCopy: (Int) -> Unit,
    onCopyWithContext: (Int) -> Unit,
    onShowAll: (Int) -> Unit,
    onClearGroupFilter: () -> Unit,
    onClearFilters: () -> Unit,
) {
    val listState =
        rememberLazyListState(initialFirstVisibleItemIndex = (view.size - 1).coerceAtLeast(0))
    val scope = rememberCoroutineScope()
    var shownView by remember { mutableStateOf(view) }
    val atEnd by remember { derivedStateOf { !listState.canScrollForward } }
    val console = state.level == null
    val narrowed = state.query.isNotBlank() || state.tags.isNotEmpty() || state.groupFilter != null

    LaunchedEffect(view) {
        if (shownView !== view) {
            shownView = view
            if (view.size > 0) listState.scrollToItem(view.size - 1)
        }
    }

    val jumpToError: (Boolean) -> Unit = { forward ->
        val errors = view.errorRowIndices
        val from = listState.firstVisibleItemIndex
        val target =
            if (forward) errors.firstOrNull { it > from } ?: errors.first()
            else errors.lastOrNull { it < from } ?: errors.last()
        scope.launch { listState.animateScrollToItem(target) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LogCountChip(
                label = stringResource(R.string.logs_chip_all),
                count = view.document.entryCount,
                tint = MaterialTheme.colorScheme.onSurface,
                selected = state.level == null,
                onClick = { onSelectLevel(null) },
            )
            LogCountChip(
                label = stringResource(R.string.logs_chip_warnings),
                count = view.document.warningCount,
                tint = LogLevelColors.content(LogLevel.WARN),
                selected = state.level == LogLevel.WARN,
                onClick = { onSelectLevel(LogLevel.WARN) },
            )
            LogCountChip(
                label = stringResource(R.string.logs_chip_errors),
                count = view.document.errorCount,
                tint = LogLevelColors.content(LogLevel.ERROR),
                selected = state.level == LogLevel.ERROR,
                onClick = { onSelectLevel(LogLevel.ERROR) },
            )
            if (state.groupFilter != null) {
                TagChip(
                    tag = stringResource(R.string.server_log_same_message),
                    onRemove = onClearGroupFilter,
                )
            }
            state.tags.forEach { tag -> TagChip(tag = tag, onRemove = { onToggleTag(tag) }) }
        }

        when {
            console && narrowed ->
                MetaBar(
                    text = stringResource(R.string.logs_matches_fmt, view.matchCount),
                    action = stringResource(R.string.logs_clear_filters),
                    onAction = onClearFilters,
                )
            console -> Spacer(modifier = Modifier.height(14.dp))
            else ->
                MetaBar(
                    text =
                        if (view.grouped) {
                            stringResource(
                                R.string.logs_groups_fmt,
                                view.matchCount,
                                view.groupCount,
                            )
                        } else {
                            stringResource(R.string.logs_events_fmt, view.matchCount)
                        },
                    action =
                        stringResource(
                            if (view.grouped) R.string.logs_expand else R.string.logs_collapse
                        ),
                    onAction = onToggleGrouping,
                )
        }

        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .weight(1f)
                    .then(
                        if (console) {
                            Modifier.padding(horizontal = 16.dp)
                                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                                .background(LogLevelColors.consoleSurface)
                        } else {
                            Modifier
                        }
                    )
        ) {
            if (view.size == 0) {
                LogEmptyState(
                    reason = LogEmptyReason.NO_MATCHES,
                    query = state.query,
                    canWiden = state.isFiltered,
                    onWiden = onClearFilters,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                if (console) {
                    ServerLogConsoleList(
                        view = view,
                        query = state.query,
                        expandedKey = state.expandedKey,
                        listState = listState,
                        contentPadding = PaddingValues(top = 10.dp, bottom = 16.dp),
                        onRowClick = onRowClick,
                        onCopy = onCopy,
                        onCopyWithContext = onCopyWithContext,
                        onFilterTag = onToggleTag,
                    )
                } else {
                    ServerLogList(
                        view = view,
                        query = state.query,
                        expandedKey = state.expandedKey,
                        listState = listState,
                        contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp),
                        onRowClick = onRowClick,
                        onCopy = onCopy,
                        onCopyWithContext = onCopyWithContext,
                        onFilterTag = onToggleTag,
                        onShowAll = onShowAll,
                    )
                }
                if (view.errorRowIndices.isNotEmpty()) {
                    ErrorJumpPill(
                        count = view.errorRowIndices.size,
                        onPrevious = { jumpToError(false) },
                        onNext = { jumpToError(true) },
                        modifier =
                            Modifier.align(Alignment.BottomEnd)
                                .padding(end = 10.dp, bottom = 16.dp),
                    )
                }
                JumpToLatestPill(
                    visible = !atEnd,
                    onClick = { scope.launch { listState.animateScrollToItem(view.size - 1) } },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                )
            }
        }
    }
}
