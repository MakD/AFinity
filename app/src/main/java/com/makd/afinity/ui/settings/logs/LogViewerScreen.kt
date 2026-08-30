package com.makd.afinity.ui.settings.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.navigation.LocalPlayerOffset
import com.makd.afinity.util.logging.LogLevel

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
    var showExport by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.revision, uiState.following) {
        if (uiState.following && uiState.rows.isNotEmpty()) {
            listState.scrollToItem(uiState.rows.lastIndex)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_chevron_left),
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showExport = true }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_download),
                            contentDescription = stringResource(R.string.logs_save_title),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
            )
        },
        bottomBar = {
            BottomBar(
                following = uiState.following,
                totalCount = uiState.totalCount,
                bufferCapacity = uiState.bufferCapacity,
                onToggleFollow = { viewModel.setFollowing(!uiState.following) },
                modifier = Modifier.padding(bottom = playerOffset),
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            Text(
                text = stringResource(R.string.logs_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CountChip(
                    label = stringResource(R.string.logs_chip_all),
                    count = uiState.totalCount,
                    tint = MaterialTheme.colorScheme.onSurface,
                    selected = uiState.scope.levels == null,
                    onClick = { viewModel.selectLevel(null) },
                )
                CountChip(
                    label = stringResource(R.string.logs_chip_warnings),
                    count = uiState.warningCount,
                    tint = LogLevelColors.content(LogLevel.WARN),
                    selected = uiState.scope.levels == setOf(LogLevel.WARN),
                    onClick = { viewModel.selectLevel(LogLevel.WARN) },
                )
                CountChip(
                    label = stringResource(R.string.logs_chip_errors),
                    count = uiState.errorCount,
                    tint = LogLevelColors.content(LogLevel.ERROR),
                    selected = uiState.scope.levels == setOf(LogLevel.ERROR),
                    onClick = { viewModel.selectLevel(LogLevel.ERROR) },
                )
            }

            if (uiState.density == LogDensity.COMFORTABLE) {
                GroupingBar(
                    matchCount = uiState.matchCount,
                    groupCount = uiState.groupCount,
                    grouped = uiState.groupRepeats,
                    onToggle = viewModel::toggleGrouping,
                )
            } else {
                Spacer(modifier = Modifier.height(14.dp))
            }

            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .weight(1f)
                        .then(
                            if (uiState.density == LogDensity.COMPACT) {
                                Modifier.padding(horizontal = 16.dp)
                                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                                    .background(LogLevelColors.consoleSurface)
                            } else {
                                Modifier
                            }
                        )
            ) {
                LogListContent(
                    rows = uiState.rows,
                    density = uiState.density,
                    listState = listState,
                    contentPadding =
                        PaddingValues(
                            top = if (uiState.density == LogDensity.COMPACT) 10.dp else 4.dp,
                            bottom = padding.calculateBottomPadding() + 16.dp,
                        ),
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
}

@Composable
private fun CountChip(
    label: String,
    count: Int,
    tint: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        shape = CircleShape,
        border = null,
        colors =
            FilterChipDefaults.filterChipColors(
                selectedContainerColor = tint,
                selectedLabelColor = MaterialTheme.colorScheme.surface,
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                labelColor = tint,
            ),
        label = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(text = label, fontWeight = FontWeight.Medium)
                Text(
                    text = compact(count),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                )
            }
        },
    )
}

@Composable
private fun GroupingBar(
    matchCount: Int,
    groupCount: Int,
    grouped: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text =
                if (grouped) {
                    stringResource(R.string.logs_groups_fmt, matchCount, groupCount)
                } else {
                    stringResource(R.string.logs_events_fmt, matchCount)
                },
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text =
                stringResource(
                    if (grouped) R.string.logs_expand else R.string.logs_collapse
                ),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable(onClick = onToggle).padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun BottomBar(
    following: Boolean,
    totalCount: Int,
    bufferCapacity: Int,
    onToggleFollow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier.size(7.dp)
                    .clip(CircleShape)
                    .background(
                        if (following) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline
                    )
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text =
                if (following) {
                    stringResource(R.string.logs_buffered_fmt, totalCount, bufferCapacity)
                } else {
                    stringResource(R.string.logs_paused)
                },
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (following) {
            OutlinedButton(onClick = onToggleFollow, shape = RoundedCornerShape(20.dp)) {
                Text(text = stringResource(R.string.logs_pause))
            }
        } else {
            Button(onClick = onToggleFollow, shape = RoundedCornerShape(20.dp)) {
                Text(text = stringResource(R.string.logs_back_to_live))
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
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

private fun compact(value: Int): String =
    if (value >= 1000) "%.1fk".format(value / 1000f) else value.toString()
