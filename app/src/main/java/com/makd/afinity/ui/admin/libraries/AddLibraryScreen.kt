package com.makd.afinity.ui.admin.libraries

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.data.models.admin.ServerFolder
import com.makd.afinity.ui.admin.components.AdminDiscardDialog
import com.makd.afinity.ui.admin.components.AdminRemoveButton
import com.makd.afinity.ui.admin.components.AdminScaffold
import com.makd.afinity.ui.admin.components.ReportUnsavedChanges
import com.makd.afinity.ui.admin.config.ChoiceField
import com.makd.afinity.ui.admin.config.ConfigField
import com.makd.afinity.ui.admin.config.ConfigFormSections
import com.makd.afinity.ui.admin.config.ConfigFormUiState
import com.makd.afinity.ui.components.AfinityTextField
import com.makd.afinity.ui.components.SettingsDivider
import com.makd.afinity.ui.components.SettingsGroup
import com.makd.afinity.ui.components.SettingsItem

@Composable
fun AddLibraryScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddLibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val editor = viewModel.editor.collectAsStateWithLifecycle().value
    val formState by editor.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val commandFailedMessage = stringResource(R.string.session_command_failed)
    val currentOnBackClick by rememberUpdatedState(onBackClick)

    LaunchedEffect(Unit) {
        viewModel.commandError.collect { snackbarHostState.showSnackbar(commandFailedMessage) }
    }

    LaunchedEffect(Unit) { viewModel.created.collect { currentOnBackClick() } }

    AddLibraryContent(
        state = state,
        formState = formState,
        snackbarHostState = snackbarHostState,
        onBackClick = onBackClick,
        loadFolders = viewModel::browseFolders,
        onNameChange = viewModel::setName,
        onTypeChange = viewModel::setType,
        onAddPath = viewModel::addPath,
        onRemovePath = viewModel::removePath,
        onSetFlag = editor::setFlag,
        onSetChoice = editor::setChoice,
        onSetSelected = editor::setSelected,
        onSetText = editor::setText,
        onCreate = viewModel::create,
        modifier = modifier,
    )
}

@Composable
private fun AddLibraryContent(
    state: AddLibraryUiState,
    formState: ConfigFormUiState,
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit,
    loadFolders: suspend (path: String?) -> Result<List<ServerFolder>>,
    onNameChange: (String) -> Unit,
    onTypeChange: (NewLibraryType) -> Unit,
    onAddPath: (String) -> Unit,
    onRemovePath: (String) -> Unit,
    onSetFlag: (key: String, enabled: Boolean) -> Unit,
    onSetChoice: (field: ChoiceField, value: String) -> Unit,
    onSetSelected: (key: String, value: String, selected: Boolean) -> Unit,
    onSetText: (field: ConfigField, text: String) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    var showAddFolder by rememberSaveable { mutableStateOf(false) }
    val hasInput = state.hasInput && !state.isCreating
    val requestClose: () -> Unit = { if (hasInput) showDiscard = true else onBackClick() }
    val canCreate =
        state.isComplete &&
            formState.document != null &&
            formState.invalidKeys.isEmpty() &&
            formState.conflictKeys.isEmpty()
    val editable = !state.isCreating

    BackHandler(enabled = hasInput) { showDiscard = true }
    ReportUnsavedChanges(hasInput)

    if (showDiscard) {
        AdminDiscardDialog(
            message = stringResource(R.string.library_add_discard_message),
            onDiscard = {
                showDiscard = false
                onBackClick()
            },
            onDismiss = { showDiscard = false },
        )
    }

    if (showAddFolder) {
        ServerFolderPickerDialog(
            loadFolders = loadFolders,
            isDuplicate = { path -> path.trim() in state.paths },
            onConfirm = { path ->
                onAddPath(path)
                showAddFolder = false
            },
            onDismiss = { showAddFolder = false },
        )
    }

    AdminScaffold(
        title = stringResource(R.string.library_add),
        onBackClick = requestClose,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        floatingActionButton = {
            CreateButton(creating = state.isCreating, enabled = canCreate, onCreate = onCreate)
        },
    ) { contentPadding ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .padding(contentPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            SettingsGroup {
                TypeRow(selected = state.type, enabled = editable, onSelect = onTypeChange)
                AfinityTextField(
                    value = state.name,
                    onValueChange = onNameChange,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    label = stringResource(R.string.library_name_label),
                    enabled = editable,
                    isError = state.isDuplicateName,
                    supportingText =
                        if (state.isDuplicateName) stringResource(R.string.library_add_exists)
                        else null,
                )
            }

            SettingsGroup(title = stringResource(R.string.library_folders)) {
                SettingsItem(
                    title = stringResource(R.string.library_folder_add),
                    subtitle = stringResource(R.string.library_add_folders_help),
                    icon = painterResource(id = R.drawable.ic_add),
                    onClick = { showAddFolder = true },
                    enabled = editable,
                )
                state.paths.forEach { path ->
                    SettingsDivider()
                    SettingsItem(
                        title = path,
                        enabled = editable,
                        trailing = {
                            AdminRemoveButton(
                                contentDescription = stringResource(R.string.action_remove),
                                onClick = { onRemovePath(path) },
                                enabled = editable,
                            )
                        },
                    )
                }
            }

            ConfigFormSections(
                state = formState,
                enabled = editable,
                showAdvanced = true,
                onSetFlag = onSetFlag,
                onSetChoice = onSetChoice,
                onSetSelected = onSetSelected,
                onSetText = onSetText,
            )
        }
    }
}

@Composable
private fun TypeRow(
    selected: NewLibraryType,
    enabled: Boolean,
    onSelect: (NewLibraryType) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        SettingsItem(
            title = stringResource(R.string.library_add_type),
            subtitle = stringResource(selected.label),
            onClick = { expanded = true },
            enabled = enabled,
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            NewLibraryType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(stringResource(type.label)) },
                    onClick = {
                        expanded = false
                        onSelect(type)
                    },
                )
            }
        }
    }
}

@Composable
private fun CreateButton(creating: Boolean, enabled: Boolean, onCreate: () -> Unit) {
    val isActive = enabled || creating
    val contentColor =
        if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    ExtendedFloatingActionButton(
        onClick = { if (enabled && !creating) onCreate() },
        containerColor =
            if (isActive) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = contentColor,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (creating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = contentColor,
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(
                if (creating) stringResource(R.string.library_add_creating)
                else stringResource(R.string.library_add_create)
            )
        }
    }
}
