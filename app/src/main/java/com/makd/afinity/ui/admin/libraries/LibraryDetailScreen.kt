package com.makd.afinity.ui.admin.libraries

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.makd.afinity.ui.admin.components.AdminLoadContent
import com.makd.afinity.ui.admin.components.AdminPill
import com.makd.afinity.ui.admin.components.AdminRemoveButton
import com.makd.afinity.ui.admin.components.AdminScaffold
import com.makd.afinity.ui.components.AfinityTextField
import com.makd.afinity.ui.components.FullScreenEmpty
import com.makd.afinity.ui.components.SettingsDivider
import com.makd.afinity.ui.components.SettingsGroup
import com.makd.afinity.ui.components.SettingsItem

private enum class LibraryDialog {
    ADD_FOLDER,
    RENAME,
    REMOVE,
}

@Composable
fun LibraryDetailScreen(
    onBackClick: () -> Unit,
    onOpenOptions: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val commandFailedMessage = stringResource(R.string.session_command_failed)

    LifecycleResumeEffect(Unit) {
        viewModel.load()
        onPauseOrDispose {}
    }

    LaunchedEffect(Unit) {
        viewModel.commandError.collect { snackbarHostState.showSnackbar(commandFailedMessage) }
    }

    LaunchedEffect(Unit) { viewModel.closed.collect { onBackClick() } }

    LibraryDetailContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBackClick = onBackClick,
        onRetry = viewModel::load,
        onOpenOptions = onOpenOptions,
        loadFolders = viewModel::browseFolders,
        isFolderInLibrary = viewModel::isFolderInLibrary,
        onAddFolder = viewModel::addFolder,
        onRemoveFolder = viewModel::removeFolder,
        onRename = viewModel::rename,
        onRemove = viewModel::remove,
        modifier = modifier,
    )
}

@Composable
private fun LibraryDetailContent(
    state: LibraryDetailUiState,
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit,
    onRetry: () -> Unit,
    onOpenOptions: () -> Unit,
    loadFolders: suspend (path: String?) -> Result<List<ServerFolder>>,
    isFolderInLibrary: (String) -> Boolean,
    onAddFolder: (String) -> Unit,
    onRemoveFolder: (String) -> Unit,
    onRename: (String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dialog by rememberSaveable { mutableStateOf<LibraryDialog?>(null) }
    var folderToRemove by rememberSaveable { mutableStateOf<String?>(null) }
    val library = state.library

    if (library != null) {
        when (dialog) {
            LibraryDialog.ADD_FOLDER ->
                ServerFolderPickerDialog(
                    loadFolders = loadFolders,
                    isDuplicate = isFolderInLibrary,
                    onConfirm = { path ->
                        onAddFolder(path)
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                )
            LibraryDialog.RENAME ->
                RenameLibraryDialog(
                    currentName = library.name,
                    onConfirm = { name ->
                        onRename(name)
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                )
            LibraryDialog.REMOVE ->
                RemoveLibraryDialog(
                    name = library.name,
                    onConfirm = {
                        onRemove()
                        dialog = null
                    },
                    onDismiss = { dialog = null },
                )
            null -> Unit
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
        onBackClick = onBackClick,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
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
                val enabled = !state.isBusy
                Column(
                    modifier =
                        Modifier.fillMaxSize()
                            .padding(contentPadding)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    val removeLabel = stringResource(R.string.cd_library_folder_remove)
                    SettingsGroup(title = stringResource(R.string.library_folders)) {
                        loaded.locations.forEach { path ->
                            SettingsItem(
                                icon = painterResource(id = R.drawable.ic_folder),
                                title = path,
                                trailing = {
                                    AdminRemoveButton(
                                        contentDescription = removeLabel,
                                        onClick = { folderToRemove = path },
                                        enabled = enabled && loaded.locations.size > 1,
                                    )
                                },
                            )
                            SettingsDivider()
                        }
                        SettingsItem(
                            icon = painterResource(id = R.drawable.ic_add),
                            title = stringResource(R.string.library_folder_add),
                            onClick = { dialog = LibraryDialog.ADD_FOLDER },
                            enabled = enabled,
                        )
                    }

                    SettingsGroup {
                        SettingsItem(
                            icon = painterResource(id = R.drawable.ic_settings),
                            title = stringResource(R.string.library_options_title),
                            subtitle = stringResource(R.string.library_options_summary),
                            onClick = onOpenOptions,
                            trailing = scanPill(scanState = loaded.scanState),
                        )
                    }

                    SettingsGroup(title = stringResource(R.string.library_section_manage)) {
                        SettingsItem(
                            icon = painterResource(id = R.drawable.ic_edit),
                            title = stringResource(R.string.action_rename),
                            subtitle = stringResource(R.string.library_rename_summary),
                            onClick = { dialog = LibraryDialog.RENAME },
                            enabled = enabled,
                        )
                        SettingsDivider()
                        SettingsItem(
                            icon = painterResource(id = R.drawable.ic_delete),
                            iconTint = MaterialTheme.colorScheme.error,
                            title = stringResource(R.string.library_remove),
                            subtitle = stringResource(R.string.library_remove_summary),
                            onClick = { dialog = LibraryDialog.REMOVE },
                            enabled = enabled,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun scanPill(scanState: LibraryScanState): (@Composable () -> Unit)? {
    val label =
        when (scanState) {
            LibraryScanState.ACTIVE -> stringResource(R.string.library_scanning)
            LibraryScanState.QUEUED -> stringResource(R.string.library_scan_queued)
            LibraryScanState.IDLE -> return null
        }
    return { AdminPill(text = label, emphasised = scanState == LibraryScanState.ACTIVE) }
}

@Composable
private fun RenameLibraryDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { DialogTitle(text = stringResource(R.string.library_rename_title)) },
        text = {
            AfinityTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.library_name_label),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank() && name.trim() != currentName,
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun RemoveLibraryDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    var typed by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { DialogTitle(text = stringResource(R.string.library_remove_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.library_remove_message, name),
                    style = MaterialTheme.typography.bodyLarge,
                )
                AfinityTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = stringResource(R.string.library_remove_type_name),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = typed.trim() == name,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
            ) {
                Text(stringResource(R.string.action_remove))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
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
