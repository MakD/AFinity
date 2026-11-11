package com.makd.afinity.ui.admin.config

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.ui.admin.components.AdminDiscardDialog
import com.makd.afinity.ui.admin.components.AdminLoadContent
import com.makd.afinity.ui.admin.components.AdminScaffold
import com.makd.afinity.ui.admin.components.ReportUnsavedChanges
import com.makd.afinity.ui.admin.components.adminContentBottomPadding
import com.makd.afinity.ui.components.AfinityTextField
import com.makd.afinity.ui.components.AfinityToggleChip
import com.makd.afinity.ui.components.AfinityToggleChipFlow
import com.makd.afinity.ui.components.SettingsDivider
import com.makd.afinity.ui.components.SettingsGroup
import com.makd.afinity.ui.components.SettingsItem
import com.makd.afinity.ui.components.SettingsSwitchItem
import sh.calvin.reorderable.ReorderableColumn

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
        onEditProvider = editor::editProvider,
        onSetImageOptions = editor::setImageOptions,
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
    onEditProvider: (field: ProviderListField, edit: ProviderEdit) -> Unit,
    onSetImageOptions: (field: ImageOptionsField, values: List<ImageOptionValue>) -> Unit,
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
                ConfigSaveButton(saving = state.isSaving, enabled = state.canSave, onSave = onSave)
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
                        .padding(top = 16.dp, bottom = adminContentBottomPadding(state.isDirty)),
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
                    onEditProvider = onEditProvider,
                    onSetImageOptions = onSetImageOptions,
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
    onEditProvider: (field: ProviderListField, edit: ProviderEdit) -> Unit = { _, _ -> },
    onSetImageOptions: (field: ImageOptionsField, values: List<ImageOptionValue>) -> Unit =
        { _, _ ->
        },
    sections: List<ConfigSection> = state.spec?.sections.orEmpty(),
) {
    sections.forEach { section ->
        val fields = section.fields.filter { state.isVisible(it.visibleWhen) }
        if (fields.isNotEmpty() && (showAdvanced || !section.advanced)) {
            SettingsGroup(title = stringResource(section.title)) {
                section.note?.let { note ->
                    FieldHelp(
                        text = stringResource(note),
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                    )
                }
                fields.forEachIndexed { index, field ->
                    if (index > 0) SettingsDivider(startPadding = 16.dp)
                    ConfigFieldRow(
                        field = field,
                        state = state,
                        enabled = enabled,
                        onSetFlag = onSetFlag,
                        onSetChoice = onSetChoice,
                        onSetSelected = onSetSelected,
                        onSetText = onSetText,
                        onEditProvider = onEditProvider,
                        onSetImageOptions = onSetImageOptions,
                    )
                }
            }
        }
    }
}

@Composable
internal fun FormNote(text: String) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
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
    onEditProvider: (field: ProviderListField, edit: ProviderEdit) -> Unit,
    onSetImageOptions: (field: ImageOptionsField, values: List<ImageOptionValue>) -> Unit,
) {
    val summary = field.summary?.let { stringResource(it) }
    when (field) {
        is ProviderListField ->
            ProviderList(field = field, state = state, enabled = enabled, onEdit = onEditProvider)
        is ImageOptionsField ->
            ImageOptionsRow(
                field = field,
                state = state,
                enabled = enabled,
                onSet = onSetImageOptions,
            )
        is ReadOnlyField ->
            Column {
                SettingsItem(
                    title = stringResource(field.label),
                    subtitle =
                        when (state.flagOrNull(field.key)) {
                            true -> stringResource(R.string.state_toggle_on)
                            false -> stringResource(R.string.state_toggle_off)
                            null ->
                                state.display(field.key).ifEmpty {
                                    stringResource(R.string.config_not_set)
                                }
                        },
                    subtitleMaxLines = Int.MAX_VALUE,
                )
                summary?.let { FieldHelp(text = it, modifier = Modifier.padding(bottom = 16.dp)) }
            }
        is MultiChoiceField ->
            MultiChoiceChips(
                field = field,
                state = state,
                enabled = enabled,
                onSetSelected = onSetSelected,
                onSetFlag = onSetFlag,
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
                subtitleMaxLines = Int.MAX_VALUE,
            )
        is ChoiceField ->
            Column {
                ChoiceRow(
                    field = field,
                    selected = state.choice(field.key),
                    enabled = enabled,
                    onSelect = { onSetChoice(field, it) },
                )
                summary?.let { FieldHelp(text = it, modifier = Modifier.padding(bottom = 16.dp)) }
            }
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
private fun FieldHelp(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
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
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            )
        },
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
private fun MultiChoiceChips(
    field: MultiChoiceField,
    state: ConfigFormUiState,
    enabled: Boolean,
    onSetSelected: (key: String, value: String, selected: Boolean) -> Unit,
    onSetFlag: (key: String, enabled: Boolean) -> Unit,
) {
    val selected = state.selected(field.key)
    Column {
        Text(
            text = stringResource(field.label),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
        )
        AfinityToggleChipFlow {
            field.options
                .filter { state.isVisible(it.visibleWhen) }
                .forEach { option ->
                    AfinityToggleChip(
                        label = option.display(),
                        selected = option.value in selected,
                        onSelectedChange = { onSetSelected(field.key, option.value, it) },
                        enabled = enabled,
                    )
                }
            field.flags
                .filter { state.isVisible(it.visibleWhen) }
                .forEach { flag ->
                    AfinityToggleChip(
                        label = stringResource(flag.label),
                        selected = state.flag(flag.key),
                        onSelectedChange = { onSetFlag(flag.key, it) },
                        enabled = enabled,
                    )
                }
        }
        field.summary?.let { summary ->
            FieldHelp(text = stringResource(summary), modifier = Modifier.padding(bottom = 16.dp))
        }
    }
}

@Composable
private fun ProviderList(
    field: ProviderListField,
    state: ConfigFormUiState,
    enabled: Boolean,
    onEdit: (field: ProviderListField, edit: ProviderEdit) -> Unit,
) {
    val rows = state.providerRows(field)
    val canToggle = field.selection != ProviderSelection.NONE
    val inUse = rows.filter { it.enabled != false }
    val notUsed = rows.filter { it.enabled == false }
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(
            text = field.labelText ?: stringResource(field.label),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
        )
        if (canToggle) {
            ProviderSubheader(text = stringResource(R.string.library_provider_in_use))
        }
        when {
            inUse.isEmpty() ->
                Text(
                    text = stringResource(R.string.library_provider_none_in_use),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            field.orderKey != null ->
                RankedProviders(
                    field = field,
                    rows = inUse,
                    enabled = enabled,
                    canRemove = canToggle,
                    onEdit = onEdit,
                )
            else ->
                inUse.forEachIndexed { index, row ->
                    if (index > 0) SettingsDivider(startPadding = 16.dp)
                    InUseProviderRow(
                        name = row.name,
                        rank = null,
                        enabled = enabled,
                        elevation = 0.dp,
                        showHandle = false,
                        handleModifier = Modifier,
                        onRemove =
                            if (canToggle) {
                                { onEdit(field, ProviderEdit.Toggle(row.name, false)) }
                            } else null,
                        onMoveUp = null,
                        onMoveDown = null,
                    )
                }
        }
        if (canToggle && notUsed.isNotEmpty()) {
            ProviderSubheader(text = stringResource(R.string.library_provider_not_used))
            notUsed.forEachIndexed { index, row ->
                if (index > 0) SettingsDivider(startPadding = 16.dp)
                NotUsedProviderRow(
                    name = row.name,
                    enabled = enabled,
                    onAdd = { onEdit(field, ProviderEdit.Add(row.name)) },
                )
            }
        }
        field.summary?.let { summary ->
            FieldHelp(text = stringResource(summary), modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun RankedProviders(
    field: ProviderListField,
    rows: List<ProviderRow>,
    enabled: Boolean,
    canRemove: Boolean,
    onEdit: (field: ProviderListField, edit: ProviderEdit) -> Unit,
) {
    val canMove = enabled && rows.size > 1
    ReorderableColumn(
        list = rows,
        onSettle = { from, to ->
            val moved = rows.getOrNull(from)
            val target = rows.getOrNull(to)
            if (moved != null && target != null && from != to) {
                onEdit(field, ProviderEdit.MoveTo(moved.name, target.name))
            }
        },
    ) { index, row, isDragging ->
        ReorderableItem {
            val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "drag_elev")
            Column {
                if (index > 0) {
                    Box(modifier = Modifier.alpha(if (isDragging) 0f else 1f)) {
                        SettingsDivider(startPadding = 16.dp)
                    }
                }
                InUseProviderRow(
                    name = row.name,
                    rank = index + 1,
                    enabled = enabled,
                    elevation = elevation,
                    showHandle = rows.size > 1,
                    handleModifier = Modifier.draggableHandle(enabled = canMove),
                    onRemove =
                        if (canRemove) {
                            { onEdit(field, ProviderEdit.Toggle(row.name, false)) }
                        } else null,
                    onMoveUp =
                        if (canMove && index > 0) {
                            { onEdit(field, ProviderEdit.MoveTo(row.name, rows[index - 1].name)) }
                        } else null,
                    onMoveDown =
                        if (canMove && index < rows.lastIndex) {
                            { onEdit(field, ProviderEdit.MoveTo(row.name, rows[index + 1].name)) }
                        } else null,
                )
            }
        }
    }
}

@Composable
private fun ProviderSubheader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun RankPill(rank: Int) {
    Box(
        modifier =
            Modifier.size(24.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = rank.toString(),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun NotUsedProviderRow(name: String, enabled: Boolean, onAdd: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(
                    enabled = enabled,
                    onClickLabel = stringResource(R.string.cd_provider_add, name),
                    role = Role.Button,
                    onClick = onAdd,
                )
                .heightIn(min = 56.dp)
                .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.weight(1f).padding(vertical = 12.dp),
        )
        Icon(
            painter = painterResource(id = R.drawable.ic_circle_plus),
            contentDescription = null,
            tint =
                if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            modifier = Modifier.padding(12.dp).size(24.dp),
        )
    }
}

@Composable
private fun InUseProviderRow(
    name: String,
    rank: Int?,
    enabled: Boolean,
    elevation: Dp,
    showHandle: Boolean,
    handleModifier: Modifier,
    onRemove: (() -> Unit)?,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
) {
    val moveUpLabel = stringResource(R.string.cd_provider_move_up, name)
    val moveDownLabel = stringResource(R.string.cd_provider_move_down, name)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color =
            if (elevation > 0.dp) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = elevation,
        shadowElevation = elevation,
        modifier =
            Modifier.fillMaxWidth().semantics {
                customActions =
                    listOfNotNull(
                        onMoveUp?.let { move ->
                            CustomAccessibilityAction(moveUpLabel) {
                                move()
                                true
                            }
                        },
                        onMoveDown?.let { move ->
                            CustomAccessibilityAction(moveDownLabel) {
                                move()
                                true
                            }
                        },
                    )
            },
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .padding(
                        start = 16.dp,
                        end = if (showHandle || onRemove != null) 4.dp else 16.dp,
                    ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (rank != null) {
                RankPill(rank = rank)
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.6f),
                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
            )
            if (onRemove != null) {
                IconButton(onClick = onRemove, enabled = enabled) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_circle_minus),
                        contentDescription = stringResource(R.string.cd_provider_remove, name),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (showHandle) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_swap_vert),
                    contentDescription = stringResource(R.string.cd_provider_drag_reorder),
                    tint =
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (enabled) 0.5f else 0.25f
                        ),
                    modifier = handleModifier.padding(12.dp).size(24.dp),
                )
            }
        }
    }
}

@Composable
private fun ImageOptionsRow(
    field: ImageOptionsField,
    state: ConfigFormUiState,
    enabled: Boolean,
    onSet: (field: ImageOptionsField, values: List<ImageOptionValue>) -> Unit,
) {
    var editing by rememberSaveable(field.key) { mutableStateOf(false) }
    val options = state.imageOptions(field)
    val labels = field.types.associate { choice -> choice.type to choice.displayName() }
    val counted = options.firstOrNull { it.type == field.countedType }
    val downloaded =
        options
            .filter { it.type != field.countedType && it.limit > 0 }
            .map { labels[it.type] ?: it.type }
    val imagesText =
        if (downloaded.isEmpty()) stringResource(R.string.library_image_settings_none)
        else downloaded.joinToString(", ")
    val backdropText =
        when {
            counted == null -> null
            counted.limit == 0 -> stringResource(R.string.library_image_settings_no_backdrops)
            counted.minWidth > 0 ->
                stringResource(
                    R.string.library_image_settings_backdrops_width_fmt,
                    counted.limit,
                    counted.minWidth,
                )
            else -> stringResource(R.string.library_image_settings_backdrops_fmt, counted.limit)
        }
    val title = field.labelText ?: stringResource(field.label)

    SettingsItem(
        title = stringResource(R.string.library_image_settings),
        subtitle = listOfNotNull(imagesText, backdropText).joinToString(" · "),
        onClick = { editing = true },
        enabled = enabled,
        subtitleMaxLines = Int.MAX_VALUE,
    )

    if (editing) {
        ImageOptionsDialog(
            typeLabel = title,
            options = options,
            labels = labels,
            countedType = field.countedType,
            onConfirm = { values ->
                onSet(field, values)
                editing = false
            },
            onDismiss = { editing = false },
        )
    }
}

@Composable
private fun ImageTypeChoice.displayName(): String = label?.let { stringResource(it) } ?: type

@Composable
private fun ImageOptionsDialog(
    typeLabel: String,
    options: List<ImageOptionValue>,
    labels: Map<String, String>,
    countedType: String?,
    onConfirm: (List<ImageOptionValue>) -> Unit,
    onDismiss: () -> Unit,
) {
    val counted = options.firstOrNull { it.type == countedType }
    val toggled = options.filter { it.type != countedType }
    var checked by rememberSaveable {
        mutableStateOf(toggled.filter { it.limit > 0 }.map { it.type })
    }
    var countText by rememberSaveable { mutableStateOf(counted?.limit?.toString().orEmpty()) }
    var widthText by rememberSaveable { mutableStateOf(counted?.minWidth?.toString().orEmpty()) }
    val count = countText.trim().toIntOrNull()?.takeIf { it >= 0 }
    val width = widthText.trim().toIntOrNull()?.takeIf { it >= 0 }
    val isValid = counted == null || (count != null && width != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.library_image_settings),
                    style =
                        MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (toggled.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.library_image_settings_download),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    toggled.forEach { option ->
                        val isChecked = option.type in checked
                        Row(
                            modifier =
                                Modifier.fillMaxWidth()
                                    .toggleable(
                                        value = isChecked,
                                        role = Role.Checkbox,
                                        onValueChange = { on ->
                                            checked =
                                                if (on) checked + option.type
                                                else checked - option.type
                                        },
                                    )
                                    .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = isChecked, onCheckedChange = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = labels[option.type] ?: option.type,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
                if (counted != null) {
                    AfinityTextField(
                        value = countText,
                        onValueChange = { countText = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        label = stringResource(R.string.library_image_settings_max_backdrops),
                        isError = count == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    AfinityTextField(
                        value = widthText,
                        onValueChange = { widthText = it },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        label = stringResource(R.string.library_image_settings_min_width),
                        isError = width == null,
                        supportingText =
                            stringResource(R.string.library_image_settings_min_width_help),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        options.map { option ->
                            when {
                                option.type == countedType && count != null && width != null ->
                                    option.copy(limit = count, minWidth = width)
                                option.type == countedType -> option
                                option.type in checked ->
                                    option.copy(limit = maxOf(option.limit, 1))
                                else -> option.copy(limit = 0)
                            }
                        }
                    )
                },
                enabled = isValid,
            ) {
                Text(stringResource(R.string.action_done))
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
internal fun ConfigSaveButton(saving: Boolean, enabled: Boolean, onSave: () -> Unit) {
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
