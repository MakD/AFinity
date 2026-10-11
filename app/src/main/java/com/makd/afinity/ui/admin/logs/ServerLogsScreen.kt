package com.makd.afinity.ui.admin.logs

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.ui.admin.components.AdminLoadContent
import com.makd.afinity.ui.admin.components.AdminScaffold
import com.makd.afinity.ui.components.FullScreenEmpty
import com.makd.afinity.ui.components.SettingsDivider
import com.makd.afinity.ui.components.SettingsGroup
import com.makd.afinity.ui.components.SettingsItem
import com.makd.afinity.ui.settings.servers.utils.formatRelativeTime
import com.makd.afinity.util.formatFileSize

@Composable
fun ServerLogsScreen(
    onBackClick: () -> Unit,
    onOpenLog: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ServerLogsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ServerLogsContent(
        state = state,
        onBackClick = onBackClick,
        onOpenLog = onOpenLog,
        onRetry = viewModel::load,
        modifier = modifier,
    )
}

@Composable
private fun ServerLogsContent(
    state: ServerLogsUiState,
    onBackClick: () -> Unit,
    onOpenLog: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AdminScaffold(
        title = stringResource(R.string.title_server_logs),
        onBackClick = onBackClick,
        modifier = modifier,
    ) { contentPadding ->
        AdminLoadContent(
            data = state.files,
            failed = state.failed,
            onRetry = onRetry,
            contentPadding = contentPadding,
        ) { files ->
            if (files.isEmpty()) {
                FullScreenEmpty(
                    message = stringResource(R.string.server_logs_empty),
                    modifier = Modifier.padding(contentPadding),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(contentPadding),
                    contentPadding = PaddingValues(vertical = 16.dp),
                ) {
                    item(key = "files") {
                        SettingsGroup {
                            files.forEachIndexed { index, file ->
                                if (index > 0) SettingsDivider()
                                LogFileRow(file = file, onClick = { onOpenLog(file.name) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogFileRow(file: ServerLogFileRow, onClick: () -> Unit) {
    val context = LocalContext.current
    val summary =
        listOf(formatFileSize(context, file.size), formatRelativeTime(context, file.modified))
            .joinToString(" · ")
    SettingsItem(
        icon = painterResource(id = R.drawable.ic_logs),
        title = file.name,
        subtitle = summary,
        onClick = onClick,
    )
}
