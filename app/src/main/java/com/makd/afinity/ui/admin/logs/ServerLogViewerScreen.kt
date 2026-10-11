package com.makd.afinity.ui.admin.logs

import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.ui.admin.components.AdminLoadContent
import com.makd.afinity.ui.admin.components.AdminScaffold
import com.makd.afinity.ui.components.AfinityTextField

private enum class LogLevelFilter(@param:StringRes val label: Int) {
    ALL(R.string.server_log_level_all),
    WARNINGS(R.string.server_log_level_warnings),
    ERRORS(R.string.server_log_level_errors),
}

private fun String.isErrorLine(): Boolean = contains("[ERR]") || contains("[FTL]")

private fun String.isWarningLine(): Boolean = contains("[WRN]")

@Composable
fun ServerLogViewerScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ServerLogViewerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ServerLogViewerContent(
        title = viewModel.fileName,
        state = state,
        onBackClick = onBackClick,
        onRetry = viewModel::load,
        modifier = modifier,
    )
}

@Composable
private fun ServerLogViewerContent(
    title: String,
    state: ServerLogViewerUiState,
    onBackClick: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var level by rememberSaveable { mutableStateOf(LogLevelFilter.ALL) }

    AdminScaffold(title = title, onBackClick = onBackClick, modifier = modifier) { contentPadding ->
        AdminLoadContent(
            data = state.lines,
            failed = state.failed,
            onRetry = onRetry,
            contentPadding = contentPadding,
        ) { lines ->
            val listState = rememberLazyListState()
            val isFiltered = query.isNotBlank() || level != LogLevelFilter.ALL
            val shown =
                remember(lines, query, level) {
                    val needle = query.trim()
                    lines.filter { line ->
                        val isError = line.isErrorLine()
                        val levelMatches =
                            when (level) {
                                LogLevelFilter.ALL -> true
                                LogLevelFilter.WARNINGS -> isError || line.isWarningLine()
                                LogLevelFilter.ERRORS -> isError
                            }
                        val textMatches = needle.isEmpty() || line.contains(needle, true)
                        levelMatches && textMatches
                    }
                }

            LaunchedEffect(shown) {
                if (shown.isNotEmpty()) listState.scrollToItem(shown.lastIndex)
            }

            Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
                AfinityTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = stringResource(R.string.server_log_search),
                    leadingIcon = painterResource(id = R.drawable.ic_search),
                )
                LevelFilterRow(selected = level, onSelect = { level = it })
                Text(
                    text =
                        if (isFiltered) {
                            stringResource(R.string.server_log_matches_fmt, shown.size)
                        } else {
                            stringResource(
                                R.string.server_log_tail_fmt,
                                lines.size,
                                state.totalLines,
                            )
                        },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                SelectionContainer {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    ) {
                        items(count = shown.size) { index -> LogLine(line = shown[index]) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LevelFilterRow(selected: LogLevelFilter, onSelect: (LogLevelFilter) -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LogLevelFilter.entries.forEach { filter ->
            val isSelected = filter == selected
            val checkIcon: (@Composable () -> Unit)? =
                if (isSelected) {
                    {
                        Icon(
                            painter = painterResource(R.drawable.ic_check),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                } else {
                    null
                }
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(filter) },
                label = { Text(stringResource(filter.label)) },
                leadingIcon = checkIcon,
                shape = CircleShape,
                border = null,
                colors =
                    FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            )
        }
    }
}

@Composable
private fun LogLine(line: String) {
    val color =
        when {
            line.isErrorLine() -> MaterialTheme.colorScheme.error
            line.isWarningLine() -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    Text(
        text = line,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = color,
    )
}
