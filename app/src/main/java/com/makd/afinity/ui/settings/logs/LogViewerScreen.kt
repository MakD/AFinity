package com.makd.afinity.ui.settings.logs

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.navigation.LocalPlayerOffset
import com.makd.afinity.util.logging.LogLevel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LogViewerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerOffset = LocalPlayerOffset.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val screenInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)

    var showExport by remember { mutableStateOf(false) }
    var showTags by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showWindow by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var errorCursor by remember { mutableIntStateOf(-1) }

    val dragged by listState.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(uiState.revision, uiState.following, dragged) {
        if (uiState.following && !dragged && uiState.rows.isNotEmpty()) {
            listState.scrollToItem(uiState.rows.lastIndex)
        }
    }

    LaunchedEffect(dragged) {
        if (dragged && uiState.following) {
            snapshotFlow { listState.canScrollForward }.first { it }
            viewModel.setFollowing(false)
        }
    }

    val jumpToLatest: () -> Unit = {
        scope.launch {
            viewModel.setFollowing(true)
            if (uiState.rows.isNotEmpty()) listState.animateScrollToItem(uiState.rows.lastIndex)
        }
    }

    val closeOverlay: (() -> Unit)? =
        when {
            uiState.openCrash != null -> {
                { viewModel.openCrash(null) }
            }
            uiState.searchActive -> {
                { viewModel.setSearchActive(false) }
            }
            uiState.tab == LogTab.CRASHES -> {
                { viewModel.openTab(LogTab.LOGS) }
            }
            else -> null
        }

    BackHandler(enabled = closeOverlay != null) { closeOverlay?.invoke() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.searchActive) {
                        SearchField(
                            query = uiState.scope.query,
                            onQueryChange = viewModel::setQuery,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.logs_title),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { closeOverlay?.invoke() ?: onBackClick() }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_chevron_left),
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                actions = {
                    when {
                        uiState.openCrash != null -> {
                            IconButton(
                                onClick = { uiState.openCrash?.let(viewModel::deleteCrash) }
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_delete),
                                    contentDescription = stringResource(R.string.logs_crash_delete),
                                    tint = LogLevelColors.content(LogLevel.ERROR),
                                )
                            }
                        }
                        uiState.searchActive -> {
                            IconButton(onClick = { viewModel.setSearchActive(false) }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_close),
                                    contentDescription = stringResource(R.string.action_cancel),
                                )
                            }
                        }
                        else -> {
                            IconButton(onClick = { viewModel.setSearchActive(true) }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_search),
                                    contentDescription = stringResource(R.string.logs_search),
                                )
                            }
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_dots_vertical),
                                        contentDescription = stringResource(R.string.logs_more),
                                    )
                                }
                                OverflowMenu(
                                    expanded = showMenu,
                                    tab = uiState.tab,
                                    matchCount = uiState.matchCount,
                                    selectedTagCount = uiState.scope.tags.size,
                                    groupRepeats = uiState.groupRepeats,
                                    paused = uiState.paused,
                                    onDismiss = { showMenu = false },
                                    onCopyVisible = {
                                        showMenu = false
                                        viewModel.copyVisible()
                                    },
                                    onSave = {
                                        showMenu = false
                                        showExport = true
                                    },
                                    onTags = {
                                        showMenu = false
                                        showTags = true
                                    },
                                    onToggleGrouping = {
                                        showMenu = false
                                        viewModel.toggleGrouping()
                                    },
                                    onTogglePause = {
                                        showMenu = false
                                        viewModel.setPaused(!uiState.paused)
                                    },
                                    onClear = {
                                        showMenu = false
                                        showClearConfirm = true
                                    },
                                    onDeleteCrashes = {
                                        showMenu = false
                                        viewModel.deleteAllCrashes()
                                    },
                                )
                            }
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                windowInsets =
                    screenInsets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
            )
        },
        bottomBar = {
            if (uiState.openCrash == null) {
                BottomBar(
                    tab = uiState.tab,
                    paused = uiState.paused,
                    totalCount = uiState.totalCount,
                    bufferCapacity = uiState.bufferCapacity,
                    crashCount = uiState.crashes.size,
                    window = uiState.scope.window,
                    windowExpanded = showWindow,
                    onWindowClick = { showWindow = true },
                    onWindowDismiss = { showWindow = false },
                    onWindowSelected = {
                        showWindow = false
                        viewModel.setWindow(it)
                    },
                    modifier = Modifier.padding(bottom = playerOffset),
                )
            }
        },
    ) { padding ->
        val openCrash = uiState.openCrash
        if (openCrash != null) {
            CrashDetailContent(
                report = openCrash,
                isExporting = uiState.isExporting,
                onCopyForIssue = { viewModel.copyCrashForIssue(openCrash) },
                onShare = { viewModel.shareCrash(openCrash) },
                contentPadding =
                    PaddingValues(
                        top = padding.calculateTopPadding(),
                        bottom = max(padding.calculateBottomPadding(), playerOffset),
                    ),
                modifier =
                    Modifier.fillMaxSize()
                        .windowInsetsPadding(screenInsets.only(WindowInsetsSides.Horizontal)),
            )
            return@Scaffold
        }

        Column(
            modifier =
                Modifier.fillMaxSize()
                    .windowInsetsPadding(screenInsets.only(WindowInsetsSides.Horizontal))
                    .padding(top = padding.calculateTopPadding())
        ) {
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LogCountChip(
                    label = stringResource(R.string.logs_chip_all),
                    count = uiState.totalCount,
                    tint = MaterialTheme.colorScheme.onSurface,
                    selected = uiState.tab == LogTab.LOGS && uiState.scope.level == null,
                    onClick = {
                        viewModel.openTab(LogTab.LOGS)
                        viewModel.selectLevel(null)
                    },
                )
                LogCountChip(
                    label = stringResource(R.string.logs_chip_warnings),
                    count = uiState.warningCount,
                    tint = LogLevelColors.content(LogLevel.WARN),
                    selected = uiState.tab == LogTab.LOGS && uiState.scope.level == LogLevel.WARN,
                    onClick = {
                        viewModel.openTab(LogTab.LOGS)
                        viewModel.selectLevel(LogLevel.WARN)
                    },
                )
                LogCountChip(
                    label = stringResource(R.string.logs_chip_errors),
                    count = uiState.errorCount,
                    tint = LogLevelColors.content(LogLevel.ERROR),
                    selected = uiState.tab == LogTab.LOGS && uiState.scope.level == LogLevel.ERROR,
                    onClick = {
                        viewModel.openTab(LogTab.LOGS)
                        viewModel.selectLevel(LogLevel.ERROR)
                    },
                )
                LogCountChip(
                    label = stringResource(R.string.logs_chip_crashes),
                    count = uiState.crashes.size,
                    tint = LogLevelColors.content(LogLevel.ERROR),
                    selected = uiState.tab == LogTab.CRASHES,
                    showDot = uiState.unseenCrashes > 0,
                    onClick = { viewModel.openTab(LogTab.CRASHES) },
                )
                uiState.scope.tags.forEach { tag ->
                    TagChip(tag = tag, onRemove = { viewModel.toggleTag(tag) })
                }
            }

            when {
                uiState.tab == LogTab.CRASHES ->
                    Text(
                        text = stringResource(R.string.logs_crash_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp),
                    )
                uiState.scope.query.isNotBlank() || uiState.scope.tags.isNotEmpty() ->
                    MetaBar(
                        text = stringResource(R.string.logs_matches_fmt, uiState.matchCount),
                        action = stringResource(R.string.logs_clear_filters),
                        onAction = viewModel::clearFilters,
                    )
                uiState.density == LogDensity.COMFORTABLE ->
                    MetaBar(
                        text =
                            if (uiState.groupRepeats) {
                                stringResource(
                                    R.string.logs_groups_fmt,
                                    uiState.matchCount,
                                    uiState.groupCount,
                                )
                            } else {
                                stringResource(R.string.logs_events_fmt, uiState.matchCount)
                            },
                        action =
                            stringResource(
                                if (uiState.groupRepeats) R.string.logs_expand
                                else R.string.logs_collapse
                            ),
                        onAction = viewModel::toggleGrouping,
                    )
                else -> Spacer(modifier = Modifier.height(14.dp))
            }

            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .weight(1f)
                        .then(
                            if (
                                uiState.tab == LogTab.LOGS && uiState.density == LogDensity.COMPACT
                            ) {
                                Modifier.padding(horizontal = 16.dp)
                                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                                    .background(LogLevelColors.consoleSurface)
                            } else {
                                Modifier
                            }
                        )
            ) {
                val bottomPadding = padding.calculateBottomPadding() + 16.dp

                when {
                    uiState.emptyReason != LogEmptyReason.NONE ->
                        LogEmptyState(
                            reason = uiState.emptyReason,
                            query = uiState.scope.query,
                            canWiden = uiState.scope.isFiltered,
                            onWiden = viewModel::clearFilters,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    uiState.tab == LogTab.CRASHES ->
                        CrashListContent(
                            crashes = uiState.crashes,
                            newCount = uiState.newCrashCount,
                            contentPadding = PaddingValues(top = 16.dp, bottom = bottomPadding),
                            onOpen = { viewModel.openCrash(it) },
                        )
                    else ->
                        LogListContent(
                            rows = uiState.rows,
                            density = uiState.density,
                            expandedKey = uiState.expandedKey,
                            listState = listState,
                            contentPadding =
                                PaddingValues(
                                    top =
                                        if (uiState.density == LogDensity.COMPACT) 10.dp else 4.dp,
                                    bottom = bottomPadding,
                                ),
                            onRowClick = { viewModel.toggleExpanded(it.key) },
                            onCopy = viewModel::copyRow,
                            onCopyWithContext = viewModel::copyRowWithContext,
                            onFilterTag = { tag ->
                                viewModel.collapse()
                                viewModel.toggleTag(tag)
                            },
                        )
                }

                val errorIndices = uiState.errorRowIndices
                if (
                    uiState.tab == LogTab.LOGS &&
                        errorIndices.isNotEmpty() &&
                        uiState.emptyReason == LogEmptyReason.NONE
                ) {
                    val jump: (Boolean) -> Unit = { forward ->
                        errorCursor = stepCursor(errorCursor, errorIndices.size, forward)
                        val target = errorIndices[errorCursor]
                        viewModel.setFollowing(false)
                        scope.launch { listState.animateScrollToItem(target) }
                    }
                    ErrorJumpPill(
                        count = errorIndices.size,
                        onPrevious = { jump(false) },
                        onNext = { jump(true) },
                        modifier =
                            Modifier.align(Alignment.BottomEnd)
                                .padding(end = 10.dp, bottom = bottomPadding),
                    )
                }

                JumpToLatestPill(
                    visible =
                        uiState.tab == LogTab.LOGS &&
                            !uiState.following &&
                            !uiState.paused &&
                            uiState.emptyReason == LogEmptyReason.NONE,
                    onClick = jumpToLatest,
                    modifier =
                        Modifier.align(Alignment.BottomCenter).padding(bottom = bottomPadding),
                )
            }
        }
    }

    if (showExport) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showExport = false }, sheetState = sheetState) {
            ExportSheetContent(
                bufferedCount = uiState.totalCount,
                visibleCount = uiState.matchCount.takeIf { uiState.scope.isFiltered },
                isExporting = uiState.isExporting,
                onExport = { visibleOnly ->
                    viewModel.export(visibleOnly)
                    showExport = false
                },
                onCancel = { showExport = false },
                modifier = Modifier.padding(bottom = playerOffset),
            )
        }
    }

    if (showTags) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showTags = false }, sheetState = sheetState) {
            TagFilterSheetContent(
                tagCounts = uiState.tagCounts,
                selected = uiState.scope.tags,
                matchCount = uiState.matchCount,
                onToggle = viewModel::toggleTag,
                onClear = viewModel::clearTags,
                onDone = { showTags = false },
                modifier = Modifier.padding(bottom = playerOffset),
            )
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_delete),
                    contentDescription = null,
                    tint = LogLevelColors.content(LogLevel.ERROR),
                )
            },
            title = { Text(text = stringResource(R.string.logs_clear_title)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.logs_clear_body_fmt, uiState.totalCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (uiState.crashes.isNotEmpty()) {
                        Text(
                            text =
                                stringResource(
                                    R.string.logs_clear_crashes_kept_fmt,
                                    uiState.crashes.size,
                                ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = LogLevelColors.content(LogLevel.ERROR),
                            modifier =
                                Modifier.padding(top = 16.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        LogLevelColors.content(LogLevel.ERROR).copy(alpha = 0.09f)
                                    )
                                    .padding(horizontal = 14.dp, vertical = 11.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearConfirm = false
                        viewModel.clearBuffer()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.logs_clear_action),
                        color = LogLevelColors.content(LogLevel.ERROR),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_search),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(19.dp),
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle =
                MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.weight(1f).focusRequester(focusRequester),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.logs_search_placeholder),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

@Composable
private fun TagChip(tag: String, onRemove: () -> Unit) {
    Row(
        modifier =
            Modifier.height(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f))
                .clickable(onClick = onRemove)
                .padding(start = 14.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = tag.uppercase(),
            style = LogTextStyles.meta,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            painter = painterResource(id = R.drawable.ic_close),
            contentDescription = stringResource(R.string.logs_tags_clear),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(13.dp),
        )
    }
}

@Composable
private fun MetaBar(text: String, action: String, onAction: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = LogTextStyles.metaSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = action,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onAction).padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun OverflowMenu(
    expanded: Boolean,
    tab: LogTab,
    matchCount: Int,
    selectedTagCount: Int,
    groupRepeats: Boolean,
    paused: Boolean,
    onDismiss: () -> Unit,
    onCopyVisible: () -> Unit,
    onSave: () -> Unit,
    onTags: () -> Unit,
    onToggleGrouping: () -> Unit,
    onTogglePause: () -> Unit,
    onClear: () -> Unit,
    onDeleteCrashes: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (tab == LogTab.LOGS) {
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.logs_copy_visible)) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_copy),
                        contentDescription = null,
                    )
                },
                trailingIcon = {
                    Text(
                        text = matchCount.toString(),
                        style = LogTextStyles.meta,
                        color = MaterialTheme.colorScheme.outline,
                    )
                },
                onClick = onCopyVisible,
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.logs_save_title)) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_download),
                        contentDescription = null,
                    )
                },
                onClick = onSave,
            )
            DropdownMenuItem(
                text = { Text(text = stringResource(R.string.logs_tags_action)) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_filter),
                        contentDescription = null,
                    )
                },
                trailingIcon = {
                    if (selectedTagCount > 0) {
                        Text(
                            text = selectedTagCount.toString(),
                            style = LogTextStyles.meta,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                onClick = onTags,
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text =
                            stringResource(
                                if (groupRepeats) R.string.logs_expand_repeats
                                else R.string.logs_collapse_repeats
                            )
                    )
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_arrows_output),
                        contentDescription = null,
                    )
                },
                onClick = onToggleGrouping,
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text =
                            stringResource(
                                if (paused) R.string.logs_resume else R.string.logs_pause
                            )
                    )
                },
                leadingIcon = {
                    Icon(
                        painter =
                            painterResource(
                                id =
                                    if (paused) R.drawable.ic_player_play_filled
                                    else R.drawable.ic_player_pause_filled
                            ),
                        contentDescription = null,
                    )
                },
                onClick = onTogglePause,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.logs_clear_action_menu),
                        color = LogLevelColors.content(LogLevel.ERROR),
                    )
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_delete),
                        contentDescription = null,
                        tint = LogLevelColors.content(LogLevel.ERROR),
                    )
                },
                onClick = onClear,
            )
        } else {
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.logs_crash_delete_all),
                        color = LogLevelColors.content(LogLevel.ERROR),
                    )
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_delete),
                        contentDescription = null,
                        tint = LogLevelColors.content(LogLevel.ERROR),
                    )
                },
                onClick = onDeleteCrashes,
            )
        }
    }
}

@Composable
private fun JumpToLatestPill(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn() + scaleIn(initialScale = 0.85f),
        exit = fadeOut() + scaleOut(targetScale = 0.85f),
    ) {
        Row(
            modifier =
                Modifier.clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onClick)
                    .padding(start = 12.dp, end = 14.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_keyboard_arrow_down),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(R.string.logs_jump_to_latest),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun ErrorJumpPill(
    count: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = LogLevelColors.content(LogLevel.ERROR)
    Column(
        modifier =
            modifier
                .width(34.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(tint.copy(alpha = 0.12f))
                .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_keyboard_arrow_up),
            contentDescription = stringResource(R.string.logs_previous_error),
            tint = tint,
            modifier =
                Modifier.size(26.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onPrevious)
                    .padding(5.dp),
        )
        Text(
            text = count.toString(),
            style = LogTextStyles.metaSmall,
            fontWeight = FontWeight.SemiBold,
            color = tint,
        )
        Icon(
            painter = painterResource(id = R.drawable.ic_keyboard_arrow_down),
            contentDescription = stringResource(R.string.logs_next_error),
            tint = tint,
            modifier =
                Modifier.size(26.dp).clip(CircleShape).clickable(onClick = onNext).padding(5.dp),
        )
    }
}

@Composable
private fun BottomBar(
    tab: LogTab,
    paused: Boolean,
    totalCount: Int,
    bufferCapacity: Int,
    crashCount: Int,
    window: LogWindow,
    windowExpanded: Boolean,
    onWindowClick: () -> Unit,
    onWindowDismiss: () -> Unit,
    onWindowSelected: (LogWindow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val barInsets =
        WindowInsets.systemBars
            .union(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)

    if (tab == LogTab.CRASHES) {
        Text(
            text = stringResource(R.string.logs_crash_reports_kept_fmt, crashCount),
            style = LogTextStyles.meta,
            color = MaterialTheme.colorScheme.outline,
            modifier =
                modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .windowInsetsPadding(barInsets)
                    .padding(horizontal = 20.dp, vertical = 18.dp),
        )
        return
    }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .windowInsetsPadding(barInsets)
                .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier =
                Modifier.size(7.dp)
                    .clip(CircleShape)
                    .background(
                        if (paused) MaterialTheme.colorScheme.outline
                        else MaterialTheme.colorScheme.primary
                    )
        )
        Text(
            text =
                if (paused) {
                    stringResource(R.string.logs_paused)
                } else {
                    stringResource(R.string.logs_buffered_fmt, totalCount, bufferCapacity)
                },
            style = LogTextStyles.meta,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )

        Box {
            Row(
                modifier =
                    Modifier.height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(onClick = onWindowClick)
                        .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = stringResource(windowLabel(window)),
                    style = LogTextStyles.meta,
                    color =
                        if (window == LogWindow.ALL) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                )
                Icon(
                    painter = painterResource(id = R.drawable.ic_keyboard_arrow_down),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
            DropdownMenu(expanded = windowExpanded, onDismissRequest = onWindowDismiss) {
                LogWindow.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(text = stringResource(windowMenuLabel(option))) },
                        leadingIcon = {
                            if (option == window) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_check),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                        onClick = { onWindowSelected(option) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportSheetContent(
    bufferedCount: Int,
    visibleCount: Int?,
    isExporting: Boolean,
    onExport: (Boolean) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visibleOnly by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 28.dp)) {
        Text(
            text = stringResource(R.string.logs_save_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(20.dp))

        ScopeOption(
            title = stringResource(R.string.logs_save_all),
            subtitle = stringResource(R.string.logs_save_lines_fmt, bufferedCount),
            selected = !visibleOnly,
            onClick = { visibleOnly = false },
        )

        if (visibleCount != null) {
            Spacer(modifier = Modifier.height(10.dp))
            ScopeOption(
                title = stringResource(R.string.logs_save_visible),
                subtitle = stringResource(R.string.logs_save_lines_fmt, visibleCount),
                selected = visibleOnly,
                onClick = { visibleOnly = true },
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.logs_save_redaction_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(26.dp),
            ) {
                Text(text = stringResource(R.string.action_cancel))
            }
            Button(
                onClick = { onExport(visibleOnly) },
                enabled = !isExporting,
                modifier = Modifier.weight(1.6f).height(52.dp),
                shape = RoundedCornerShape(26.dp),
            ) {
                Text(text = stringResource(R.string.logs_save_action))
            }
        }
    }
}

@Composable
private fun ScopeOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    else MaterialTheme.colorScheme.surfaceContainerLow
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = subtitle,
            style = LogTextStyles.metaSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

private fun stepCursor(cursor: Int, size: Int, forward: Boolean): Int {
    if (size == 0) return 0
    val next = if (forward) cursor + 1 else cursor - 1
    return ((next % size) + size) % size
}

private fun windowLabel(window: LogWindow): Int =
    when (window) {
        LogWindow.ALL -> R.string.logs_window_all_short
        LogWindow.ONE_MINUTE -> R.string.logs_window_1m_short
        LogWindow.FIVE_MINUTES -> R.string.logs_window_5m_short
        LogWindow.FIFTEEN_MINUTES -> R.string.logs_window_15m_short
    }

private fun windowMenuLabel(window: LogWindow): Int =
    when (window) {
        LogWindow.ALL -> R.string.logs_window_all
        LogWindow.ONE_MINUTE -> R.string.logs_window_1m
        LogWindow.FIVE_MINUTES -> R.string.logs_window_5m
        LogWindow.FIFTEEN_MINUTES -> R.string.logs_window_15m
    }
