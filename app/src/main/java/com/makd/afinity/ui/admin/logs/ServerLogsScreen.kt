package com.makd.afinity.ui.admin.logs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.ui.admin.components.AdminDiscardDialog
import com.makd.afinity.ui.admin.components.AdminLoadContent
import com.makd.afinity.ui.admin.components.AdminScaffold
import com.makd.afinity.ui.admin.components.ReportUnsavedChanges
import com.makd.afinity.ui.admin.components.adminContentBottomPadding
import com.makd.afinity.ui.admin.config.ChoiceField
import com.makd.afinity.ui.admin.config.ConfigField
import com.makd.afinity.ui.admin.config.ConfigFormSections
import com.makd.afinity.ui.admin.config.ConfigFormUiState
import com.makd.afinity.ui.admin.config.ConfigSaveButton
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
    val editor = viewModel.settingsEditor
    val formState by editor.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val commandFailedMessage = stringResource(R.string.session_command_failed)

    LaunchedEffect(editor) {
        editor.commandError.collect { snackbarHostState.showSnackbar(commandFailedMessage) }
    }

    ServerLogsContent(
        state = state,
        formState = formState,
        snackbarHostState = snackbarHostState,
        onBackClick = onBackClick,
        onOpenLog = onOpenLog,
        onRetry = viewModel::load,
        onRetrySettings = editor::load,
        onSetFlag = editor::setFlag,
        onSetChoice = editor::setChoice,
        onSetSelected = editor::setSelected,
        onSetText = editor::setText,
        onSave = editor::save,
        modifier = modifier,
    )
}

@Composable
private fun ServerLogsContent(
    state: ServerLogsUiState,
    formState: ConfigFormUiState,
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit,
    onOpenLog: (String) -> Unit,
    onRetry: () -> Unit,
    onRetrySettings: () -> Unit,
    onSetFlag: (key: String, enabled: Boolean) -> Unit,
    onSetChoice: (field: ChoiceField, value: String) -> Unit,
    onSetSelected: (key: String, value: String, selected: Boolean) -> Unit,
    onSetText: (field: ConfigField, text: String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    val requestClose: () -> Unit = { if (formState.isDirty) showDiscard = true else onBackClick() }

    BackHandler(enabled = formState.isDirty) { showDiscard = true }
    ReportUnsavedChanges(formState.isDirty)

    if (showDiscard) {
        AdminDiscardDialog(
            message = stringResource(R.string.config_discard_message),
            onDiscard = {
                showDiscard = false
                onBackClick()
            },
            onDismiss = { showDiscard = false },
        )
    }

    AdminScaffold(
        title = stringResource(R.string.title_server_logs),
        onBackClick = requestClose,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        floatingActionButton = {
            if (formState.isDirty) {
                ConfigSaveButton(
                    saving = formState.isSaving,
                    enabled = formState.canSave,
                    onSave = onSave,
                )
            }
        },
    ) { contentPadding ->
        AdminLoadContent(
            data = state.files,
            failed = state.failed,
            onRetry = onRetry,
            contentPadding = contentPadding,
        ) { files ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(contentPadding),
                contentPadding =
                    PaddingValues(
                        top = 16.dp,
                        bottom = adminContentBottomPadding(formState.isDirty),
                    ),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                item(key = "settings") {
                    LogSettings(
                        formState = formState,
                        onRetry = onRetrySettings,
                        onSetFlag = onSetFlag,
                        onSetChoice = onSetChoice,
                        onSetSelected = onSetSelected,
                        onSetText = onSetText,
                    )
                }
                item(key = "files") {
                    SettingsGroup(title = stringResource(R.string.config_section_logs)) {
                        if (files.isEmpty()) {
                            SettingsItem(
                                icon = painterResource(id = R.drawable.ic_logs),
                                title = stringResource(R.string.server_logs_empty),
                            )
                        }
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

@Composable
private fun LogSettings(
    formState: ConfigFormUiState,
    onRetry: () -> Unit,
    onSetFlag: (key: String, enabled: Boolean) -> Unit,
    onSetChoice: (field: ChoiceField, value: String) -> Unit,
    onSetSelected: (key: String, value: String, selected: Boolean) -> Unit,
    onSetText: (field: ConfigField, text: String) -> Unit,
) {
    when {
        formState.document != null ->
            ConfigFormSections(
                state = formState,
                enabled = !formState.isSaving,
                showAdvanced = true,
                onSetFlag = onSetFlag,
                onSetChoice = onSetChoice,
                onSetSelected = onSetSelected,
                onSetText = onSetText,
            )
        formState.failed ->
            SettingsGroup(title = stringResource(R.string.settings_title)) {
                SettingsItem(
                    icon = painterResource(id = R.drawable.ic_refresh),
                    title = stringResource(R.string.library_options_load_failed),
                    subtitle = stringResource(R.string.action_retry),
                    onClick = onRetry,
                )
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
