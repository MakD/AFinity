package com.makd.afinity.ui.admin.libraries

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.data.models.admin.ServerFolder
import com.makd.afinity.ui.admin.components.AdminDiscardDialog
import com.makd.afinity.ui.admin.components.AdminLoadContent
import com.makd.afinity.ui.admin.components.AdminPill
import com.makd.afinity.ui.admin.components.AdminRemoveButton
import com.makd.afinity.ui.admin.components.AdminScaffold
import com.makd.afinity.ui.admin.components.ReportUnsavedChanges
import com.makd.afinity.ui.admin.components.adminContentBottomPadding
import com.makd.afinity.ui.admin.config.ChoiceField
import com.makd.afinity.ui.admin.config.ConfigField
import com.makd.afinity.ui.admin.config.ConfigFormSections
import com.makd.afinity.ui.admin.config.ConfigFormUiState
import com.makd.afinity.ui.admin.config.ConfigSaveButton
import com.makd.afinity.ui.components.FullScreenEmpty
import com.makd.afinity.ui.components.SettingsDivider
import com.makd.afinity.ui.components.SettingsGroup
import com.makd.afinity.ui.components.SettingsItem

private const val LEADING_OPTION_SECTIONS = 1

@Composable
fun LibraryDetailScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val editor = viewModel.optionsEditor
    val formState by editor.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val commandFailedMessage = stringResource(R.string.session_command_failed)

    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose {}
    }

    LaunchedEffect(Unit) {
        viewModel.commandError.collect { snackbarHostState.showSnackbar(commandFailedMessage) }
    }

    LaunchedEffect(editor) {
        editor.commandError.collect { snackbarHostState.showSnackbar(commandFailedMessage) }
    }

    LibraryDetailContent(
        state = state,
        formState = formState,
        snackbarHostState = snackbarHostState,
        onBackClick = onBackClick,
        onRetry = viewModel::load,
        onRetryOptions = editor::load,
        loadFolders = viewModel::browseFolders,
        isFolderInLibrary = viewModel::isFolderInLibrary,
        onAddFolder = viewModel::addFolder,
        onRemoveFolder = viewModel::removeFolder,
        onSetFlag = editor::setFlag,
        onSetChoice = editor::setChoice,
        onSetSelected = editor::setSelected,
        onSetText = editor::setText,
        onSave = editor::save,
        modifier = modifier,
    )
}

@Composable
private fun LibraryDetailContent(
    state: LibraryDetailUiState,
    formState: ConfigFormUiState,
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit,
    onRetry: () -> Unit,
    onRetryOptions: () -> Unit,
    loadFolders: suspend (path: String?) -> Result<List<ServerFolder>>,
    isFolderInLibrary: (String) -> Boolean,
    onAddFolder: (String) -> Unit,
    onRemoveFolder: (String) -> Unit,
    onSetFlag: (key: String, enabled: Boolean) -> Unit,
    onSetChoice: (field: ChoiceField, value: String) -> Unit,
    onSetSelected: (key: String, value: String, selected: Boolean) -> Unit,
    onSetText: (field: ConfigField, text: String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddFolder by rememberSaveable { mutableStateOf(false) }
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    var folderToRemove by rememberSaveable { mutableStateOf<String?>(null) }
    val library = state.library
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

    if (library != null) {
        if (showAddFolder) {
            ServerFolderPickerDialog(
                loadFolders = loadFolders,
                isDuplicate = isFolderInLibrary,
                onConfirm = { path ->
                    onAddFolder(path)
                    showAddFolder = false
                },
                onDismiss = { showAddFolder = false },
            )
        }

        folderToRemove
            ?.takeIf { it in library.locations }
            ?.let { path ->
                ConfirmDialog(
                    title = stringResource(R.string.library_folder_remove_title),
                    message = stringResource(R.string.library_folder_remove_message, path),
                    confirmLabel = stringResource(R.string.action_remove),
                    onConfirm = {
                        onRemoveFolder(path)
                        folderToRemove = null
                    },
                    onDismiss = { folderToRemove = null },
                )
            }
    }

    AdminScaffold(
        title = library?.name ?: stringResource(R.string.libraries_title),
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
        if (state.missing) {
            FullScreenEmpty(
                message = stringResource(R.string.library_not_found),
                modifier = Modifier.padding(contentPadding),
            )
        } else {
            AdminLoadContent(
                data = library,
                failed = state.failed,
                onRetry = onRetry,
                contentPadding = contentPadding,
            ) { loaded ->
                val sections = formState.spec?.sections.orEmpty()
                val optionsReady = formState.document != null && sections.isNotEmpty()
                val optionsEditable = !formState.isSaving
                Column(
                    modifier =
                        Modifier.fillMaxSize()
                            .padding(contentPadding)
                            .verticalScroll(rememberScrollState())
                            .padding(
                                top = 16.dp,
                                bottom = adminContentBottomPadding(formState.isDirty),
                            ),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    ScanStatus(scanState = loaded.scanState)

                    if (optionsReady) {
                        ConfigFormSections(
                            state = formState,
                            enabled = optionsEditable,
                            showAdvanced = true,
                            onSetFlag = onSetFlag,
                            onSetChoice = onSetChoice,
                            onSetSelected = onSetSelected,
                            onSetText = onSetText,
                            sections = sections.take(LEADING_OPTION_SECTIONS),
                        )
                    }

                    LibraryFolders(
                        locations = loaded.locations,
                        enabled = !state.isBusy,
                        onAdd = { showAddFolder = true },
                        onRemove = { path -> folderToRemove = path },
                    )

                    if (optionsReady) {
                        ConfigFormSections(
                            state = formState,
                            enabled = optionsEditable,
                            showAdvanced = true,
                            onSetFlag = onSetFlag,
                            onSetChoice = onSetChoice,
                            onSetSelected = onSetSelected,
                            onSetText = onSetText,
                            sections = sections.drop(LEADING_OPTION_SECTIONS),
                        )
                    } else if (formState.failed) {
                        SettingsGroup(title = stringResource(R.string.library_options_title)) {
                            SettingsItem(
                                icon = painterResource(id = R.drawable.ic_refresh),
                                title = stringResource(R.string.library_options_load_failed),
                                subtitle = stringResource(R.string.action_retry),
                                onClick = onRetryOptions,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryFolders(
    locations: List<String>,
    enabled: Boolean,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    val removeLabel = stringResource(R.string.cd_library_folder_remove)
    SettingsGroup(title = stringResource(R.string.library_folders)) {
        locations.forEach { path ->
            SettingsItem(
                icon = painterResource(id = R.drawable.ic_folder),
                title = path,
                trailing = {
                    AdminRemoveButton(
                        contentDescription = removeLabel,
                        onClick = { onRemove(path) },
                        enabled = enabled && locations.size > 1,
                    )
                },
            )
            SettingsDivider()
        }
        SettingsItem(
            icon = painterResource(id = R.drawable.ic_add),
            title = stringResource(R.string.library_folder_add),
            onClick = onAdd,
            enabled = enabled,
        )
    }
}

@Composable
private fun ScanStatus(scanState: LibraryScanState) {
    val label =
        when (scanState) {
            LibraryScanState.ACTIVE -> stringResource(R.string.library_scanning)
            LibraryScanState.QUEUED -> stringResource(R.string.library_scan_queued)
            LibraryScanState.IDLE -> return
        }
    Row(modifier = Modifier.padding(horizontal = 16.dp)) {
        AdminPill(text = label, emphasised = scanState == LibraryScanState.ACTIVE)
    }
}

@Composable
private fun DialogTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { DialogTitle(text = title) },
        text = { Text(text = message, style = MaterialTheme.typography.bodyLarge) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
