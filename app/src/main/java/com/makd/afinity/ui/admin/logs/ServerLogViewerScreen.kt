package com.makd.afinity.ui.admin.logs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.navigation.LocalPlayerOffset
import com.makd.afinity.ui.admin.components.AdminLoadContent
import com.makd.afinity.ui.admin.components.AdminScaffold
import com.makd.afinity.ui.components.AfinityModalBottomSheet
import com.makd.afinity.ui.components.rememberAfinitySheetState
import com.makd.afinity.ui.settings.logs.LogCountChip
import com.makd.afinity.ui.settings.logs.LogEmptyReason
import com.makd.afinity.ui.settings.logs.LogEmptyState
import com.makd.afinity.ui.settings.logs.MetaBar
import com.makd.afinity.ui.settings.logs.SearchField
import com.makd.afinity.ui.settings.logs.TagChip
import com.makd.afinity.ui.settings.logs.TagFilterSheetContent
import com.makd.afinity.util.logging.LogLevel

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
                    onOpenTags = { showTags = true },
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
                modifier = Modifier.padding(bottom = LocalPlayerOffset.current),
                subtitle = stringResource(R.string.server_log_tags_subtitle),
                warningTint = ServerLogColors.content(LogLevel.WARN),
                errorTint = ServerLogColors.content(LogLevel.ERROR),
            )
        }
    }
}

@Composable
private fun ServerLogContent(
    state: ServerLogViewerUiState,
    view: ServerLogView,
    contentPadding: PaddingValues,
    onSelectLevel: (LogLevel?) -> Unit,
    onOpenTags: () -> Unit,
    onToggleTag: (String) -> Unit,
    onToggleGrouping: () -> Unit,
    onRowClick: (String) -> Unit,
    onCopy: (Int) -> Unit,
    onCopyWithContext: (Int) -> Unit,
    onShowAll: (Int) -> Unit,
    onClearGroupFilter: () -> Unit,
    onClearFilters: () -> Unit,
) {
    val listState = rememberLazyListState()
    var shownView by remember { mutableStateOf(view) }

    LaunchedEffect(view) {
        if (shownView !== view) {
            shownView = view
            listState.scrollToItem(0)
        }
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
                tint = ServerLogColors.content(LogLevel.WARN),
                selected = state.level == LogLevel.WARN,
                onClick = { onSelectLevel(LogLevel.WARN) },
            )
            LogCountChip(
                label = stringResource(R.string.logs_chip_errors),
                count = view.document.errorCount,
                tint = ServerLogColors.content(LogLevel.ERROR),
                selected = state.level == LogLevel.ERROR,
                onClick = { onSelectLevel(LogLevel.ERROR) },
            )
            TagFilterChip(onClick = onOpenTags)
            if (state.groupFilter != null) {
                TagChip(
                    tag = stringResource(R.string.server_log_same_message),
                    onRemove = onClearGroupFilter,
                )
            }
            state.tags.forEach { tag -> TagChip(tag = tag, onRemove = { onToggleTag(tag) }) }
        }

        MetaBar(
            text =
                if (view.grouped) {
                    stringResource(R.string.logs_groups_fmt, view.matchCount, view.groupCount)
                } else {
                    stringResource(R.string.logs_events_fmt, view.matchCount)
                },
            action =
                stringResource(if (view.grouped) R.string.logs_expand else R.string.logs_collapse),
            onAction = onToggleGrouping,
        )

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (view.size == 0) {
                LogEmptyState(
                    reason = LogEmptyReason.NO_MATCHES,
                    query = state.query,
                    canWiden = state.isFiltered,
                    onWiden = onClearFilters,
                    modifier = Modifier.align(Alignment.Center),
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
        }
    }
}

@Composable
private fun TagFilterChip(onClick: () -> Unit) {
    Box(
        modifier =
            Modifier.height(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_filter),
            contentDescription = stringResource(R.string.logs_tags_title),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
    }
}
