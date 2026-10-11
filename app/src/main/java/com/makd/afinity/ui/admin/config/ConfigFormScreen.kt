package com.makd.afinity.ui.admin.config

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.ui.admin.components.AdminDiscardDialog
import com.makd.afinity.ui.admin.components.AdminLoadContent
import com.makd.afinity.ui.admin.components.AdminScaffold
import com.makd.afinity.ui.admin.components.ReportUnsavedChanges
import com.makd.afinity.ui.components.AfinityTextField
import com.makd.afinity.ui.components.SettingsDivider
import com.makd.afinity.ui.components.SettingsGroup
import com.makd.afinity.ui.components.SettingsItem
import com.makd.afinity.ui.components.SettingsSwitchItem

private const val SEARCHABLE_CHOICE_MIN_OPTIONS = 12

@Composable
fun ConfigFormScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConfigFormViewModel = hiltViewModel(),
) {
    ConfigFormEditorScreen(
        editor = viewModel.editor,
        onBackClick = onBackClick,
        modifier = modifier,
    )
}

@Composable
fun ConfigFormEditorScreen(
    editor: ConfigFormEditor,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by editor.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val commandFailedMessage = stringResource(R.string.session_command_failed)

    LaunchedEffect(editor) {
        editor.commandError.collect { snackbarHostState.showSnackbar(commandFailedMessage) }
    }

    ConfigFormContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBackClick = onBackClick,
        onRetry = editor::load,
        onSetFlag = editor::setFlag,
        onSetChoice = editor::setChoice,
        onSetSelected = editor::setSelected,
        onSetText = editor::setText,
        onSave = editor::save,
        modifier = modifier,
    )
}

@Composable
private fun ConfigFormContent(
    state: ConfigFormUiState,
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit,
    onRetry: () -> Unit,
    onSetFlag: (key: String, enabled: Boolean) -> Unit,
    onSetChoice: (field: ChoiceField, value: String) -> Unit,
    onSetSelected: (key: String, value: String, selected: Boolean) -> Unit,
    onSetText: (field: ConfigField, text: String) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    val requestClose: () -> Unit = { if (state.isDirty) showDiscard = true else onBackClick() }
    val spec = state.spec

    BackHandler(enabled = state.isDirty) { showDiscard = true }
    ReportUnsavedChanges(state.isDirty)

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
        title = state.title ?: stringResource(spec?.title ?: R.string.settings_title),
        onBackClick = requestClose,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        floatingActionButton = {
            if (state.isDirty) {
                SaveButton(saving = state.isSaving, enabled = state.canSave, onSave = onSave)
            }
        },
    ) { contentPadding ->
        AdminLoadContent(
            data = state.document.takeIf { spec != null },
            failed = state.failed,
            onRetry = onRetry,
            contentPadding = contentPadding,
        ) {
            val editable = !state.isSaving
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .padding(contentPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                spec?.note?.let { note -> FormNote(text = stringResource(note)) }
                ConfigFormSections(
                    state = state,
                    enabled = editable,
                    showAdvanced = showAdvanced,
                    onSetFlag = onSetFlag,
                    onSetChoice = onSetChoice,
                    onSetSelected = onSetSelected,
                    onSetText = onSetText,
                )
                if (spec?.hasAdvancedSections == true) {
                    SettingsGroup {
                        SettingsSwitchItem(
                            title = stringResource(R.string.config_show_advanced),
                            checked = showAdvanced,
                            onCheckedChange = { showAdvanced = it },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConfigFormSections(
    state: ConfigFormUiState,
    enabled: Boolean,
    showAdvanced: Boolean,
    onSetFlag: (key: String, enabled: Boolean) -> Unit,
    onSetChoice: (field: ChoiceField, value: String) -> Unit,
    onSetSelected: (key: String, value: String, selected: Boolean) -> Unit,
    onSetText: (field: ConfigField, text: String) -> Unit,
) {
    state.spec?.sections?.forEach { section ->
        val fields = section.fields.filter { state.isVisible(it.visibleWhen) }
        if (fields.isNotEmpty() && (showAdvanced || !section.advanced)) {
            SettingsGroup(title = stringResource(section.title)) {
                fields.forEachIndexed { index, field ->
                    if (index > 0) SettingsDivider()
                    ConfigFieldRow(
                        field = field,
                        state = state,
                        enabled = enabled,
                        onSetFlag = onSetFlag,
                        onSetChoice = onSetChoice,
                        onSetSelected = onSetSelected,
                        onSetText = onSetText,
                    )
                }
            }
        }
    }
}

@Composable
private fun FormNote(text: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_info),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ConfigFieldRow(
    field: ConfigField,
    state: ConfigFormUiState,
    enabled: Boolean,
    onSetFlag: (key: String, enabled: Boolean) -> Unit,
    onSetChoice: (field: ChoiceField, value: String) -> Unit,
    onSetSelected: (key: String, value: String, selected: Boolean) -> Unit,
    onSetText: (field: ConfigField, text: String) -> Unit,
) {
    val summary = field.summary?.let { stringResource(it) }
    when (field) {
        is ReadOnlyField ->
            SettingsItem(
                title = stringResource(field.label),
                subtitle =
                    when (state.flagOrNull(field.key)) {
                        true -> stringResource(R.string.state_toggle_on)
                        false -> stringResource(R.string.state_toggle_off)
                        null ->
                            state.display(field.key).ifEmpty {
                                summary ?: stringResource(R.string.config_not_set)
                            }
                    },
            )
        is MultiChoiceField ->
            MultiChoiceRows(
                field = field,
                state = state,
                enabled = enabled,
                onSetSelected = onSetSelected,
            )
        is DecimalField ->
            TextRow(
                field = field,
                state = state,
                enabled = enabled,
                hint = summary,
                keyboardType = KeyboardType.Decimal,
                onSetText = onSetText,
            )
        is SwitchField ->
            SettingsSwitchItem(
                title = stringResource(field.label),
                subtitle = summary,
                checked = state.flag(field.key),
                onCheckedChange = { onSetFlag(field.key, it) },
                enabled = enabled,
            )
        is ChoiceField ->
            ChoiceRow(
                field = field,
                selected = state.choice(field.key),
                enabled = enabled,
                onSelect = { onSetChoice(field, it) },
            )
        is NumberField ->
            TextRow(
                field = field,
                state = state,
                enabled = enabled,
                hint =
                    summary
                        ?: stringResource(R.string.config_number_range_fmt, field.min, field.max),
                keyboardType = KeyboardType.Number,
                onSetText = onSetText,
            )
        is NumberListField ->
            TextRow(
                field = field,
                state = state,
                enabled = enabled,
                hint = summary,
                keyboardType = KeyboardType.Text,
                onSetText = onSetText,
            )
        is TextInputField ->
            TextRow(
                field = field,
                state = state,
                enabled = enabled,
                hint = summary,
                keyboardType = KeyboardType.Text,
                onSetText = onSetText,
            )
    }
}

@Composable
private fun TextRow(
    field: ConfigField,
    state: ConfigFormUiState,
    enabled: Boolean,
    hint: String?,
    keyboardType: KeyboardType,
    onSetText: (field: ConfigField, text: String) -> Unit,
) {
    val isConflicting = field.key in state.conflictKeys
    AfinityTextField(
        value = state.text(field.key),
        onValueChange = { onSetText(field, it) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        label = stringResource(field.label),
        enabled = enabled,
        isError = isConflicting || field.key in state.invalidKeys,
        supportingText = if (isConflicting) stringResource(R.string.config_min_above_max) else hint,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    )
}

@Composable
private fun ChoiceRow(
    field: ChoiceField,
    selected: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = field.options.firstOrNull { it.value == selected }?.let { it.display() }
    val isSearchable = field.options.size > SEARCHABLE_CHOICE_MIN_OPTIONS

    if (expanded && isSearchable) {
        ChoiceSearchDialog(
            title = stringResource(field.label),
            options = field.options,
            onSelect = { value ->
                expanded = false
                onSelect(value)
            },
            onDismiss = { expanded = false },
        )
    }

    Box {
        SettingsItem(
            title = stringResource(field.label),
            subtitle = selectedLabel ?: selected,
            onClick = { expanded = true },
            enabled = enabled,
        )
        DropdownMenu(
            expanded = expanded && !isSearchable,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            field.options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = option.display()) },
                    onClick = {
                        expanded = false
                        onSelect(option.value)
                    },
                )
            }
        }
    }
}

@Composable
private fun ChoiceSearchDialog(
    title: String,
    options: List<ChoiceOption>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val labelled = options.map { option -> option.value to option.display() }
    val needle = query.trim()
    val shown =
        if (needle.isEmpty()) labelled
        else labelled.filter { (_, label) -> label.contains(needle, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AfinityTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = stringResource(R.string.config_choice_search),
                    leadingIcon = painterResource(id = R.drawable.ic_search),
                )
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
                    items(count = shown.size) { index ->
                        val (value, label) = shown[index]
                        SettingsItem(title = label, onClick = { onSelect(value) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable private fun ChoiceOption.display(): String = label?.let { stringResource(it) } ?: text

@Composable
private fun MultiChoiceRows(
    field: MultiChoiceField,
    state: ConfigFormUiState,
    enabled: Boolean,
    onSetSelected: (key: String, value: String, selected: Boolean) -> Unit,
) {
    val selected = state.selected(field.key)
    Column {
        Text(
            text = stringResource(field.label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
        )
        field.options
            .filter { state.isVisible(it.visibleWhen) }
            .forEach { option ->
                SettingsSwitchItem(
                    title = option.display(),
                    checked = option.value in selected,
                    onCheckedChange = { onSetSelected(field.key, option.value, it) },
                    enabled = enabled,
                )
            }
    }
}

@Composable
private fun SaveButton(saving: Boolean, enabled: Boolean, onSave: () -> Unit) {
    val contentColor =
        if (enabled || saving) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    ExtendedFloatingActionButton(
        onClick = { if (enabled) onSave() },
        containerColor =
            if (enabled || saving) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = contentColor,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (saving) {
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
                if (saving) stringResource(R.string.admin_saving)
                else stringResource(R.string.admin_save_changes)
            )
        }
    }
}
