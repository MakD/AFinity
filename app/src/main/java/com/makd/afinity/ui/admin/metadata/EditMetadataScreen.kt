package com.makd.afinity.ui.admin.metadata

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.data.models.admin.EditablePerson
import com.makd.afinity.ui.components.AFinitySnackbar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditMetadataScreen(
    onNavigateUp: () -> Unit,
    onSaveSuccess: () -> Unit = onNavigateUp,
    viewModel: EditMetadataViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(uiState.saveSuccess) {
        when (uiState.saveSuccess) {
            true -> {
                snackbarHostState.showSnackbar("Saved successfully")
                onSaveSuccess()
            }
            false -> snackbarHostState.showSnackbar(uiState.error ?: "Save failed")
            null -> Unit
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.admin_edit_metadata_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.action_close))
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
            )
        },
        floatingActionButton = {
            if (uiState.edited != null) {
                ExtendedFloatingActionButton(
                    onClick = { if (!uiState.saving) viewModel.save() },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (uiState.saving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Text(if (uiState.saving) stringResource(R.string.admin_saving) else stringResource(R.string.admin_save_changes))
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState, snackbar = { AFinitySnackbar(it) }) },
    ) { padding ->
        when {
            uiState.loading ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

            uiState.error != null && uiState.edited == null ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
                }

            uiState.edited != null -> {
                val item = uiState.edited!!
                Column(modifier = Modifier.padding(padding)) {
                    SecondaryTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        divider = {},
                        indicator = {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(selectedTab),
                                color = MaterialTheme.colorScheme.primary,
                                height = 3.dp,
                            )
                        },
                    ) {
                        listOf(
                            stringResource(R.string.admin_tab_general),
                            stringResource(R.string.admin_tab_people),
                            stringResource(R.string.admin_tab_advanced),
                        ).forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = {
                                    Text(
                                        title,
                                        style =
                                            if (selectedTab == index)
                                                MaterialTheme.typography.titleSmall
                                            else MaterialTheme.typography.bodyMedium,
                                    )
                                },
                                selectedContentColor = MaterialTheme.colorScheme.primary,
                                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    when (selectedTab) {
                        0 -> GeneralTab(item = item, viewModel = viewModel)
                        1 -> PeopleTab(item = item, viewModel = viewModel)
                        2 -> AdvancedTab(item = item, viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun SleekTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = Int.MAX_VALUE,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        keyboardOptions = keyboardOptions,
        shape = RoundedCornerShape(12.dp),
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GeneralTab(
    item: com.makd.afinity.data.models.admin.EditableItem,
    viewModel: EditMetadataViewModel,
) {
    Column(
        modifier =
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .imePadding()
                .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SleekTextField(
            value = item.name,
            onValueChange = { viewModel.updateName(it) },
            label = stringResource(R.string.admin_field_title),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        SleekTextField(
            value = item.originalTitle ?: "",
            onValueChange = { viewModel.updateOriginalTitle(it) },
            label = stringResource(R.string.admin_field_original_title),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        SleekTextField(
            value = item.overview ?: "",
            onValueChange = { viewModel.updateOverview(it) },
            label = stringResource(R.string.admin_field_overview),
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            maxLines = 8,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SleekTextField(
                value = item.productionYear?.toString() ?: "",
                onValueChange = { viewModel.updateYear(it) },
                label = stringResource(R.string.admin_field_year),
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
            )
            SleekTextField(
                value = item.officialRating ?: "",
                onValueChange = { viewModel.updateOfficialRating(it) },
                label = stringResource(R.string.admin_field_rating),
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
        }

        SectionHeader(stringResource(R.string.admin_section_genres))
        ChipInput(
            chips = item.genres,
            onAdd = { viewModel.addGenre(it) },
            onRemove = { viewModel.removeGenre(it) },
        )

        SectionHeader(stringResource(R.string.admin_section_tags))
        ChipInput(
            chips = item.tags,
            onAdd = { viewModel.addTag(it) },
            onRemove = { viewModel.removeTag(it) },
        )

        SectionHeader(stringResource(R.string.admin_section_studios))
        ChipInput(
            chips = item.studios,
            onAdd = { viewModel.addStudio(it) },
            onRemove = { viewModel.removeStudio(it) },
        )

        Spacer(modifier = Modifier.height(88.dp))
    }
}

@Composable
private fun PeopleTab(
    item: com.makd.afinity.data.models.admin.EditableItem,
    viewModel: EditMetadataViewModel,
) {
    var showAddPerson by remember { mutableStateOf(false) }
    var newPersonName by remember { mutableStateOf("") }
    var newPersonType by remember { mutableStateOf("Actor") }
    var newPersonRole by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.admin_section_cast),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            TextButton(
                onClick = { showAddPerson = !showAddPerson },
                colors =
                    ButtonDefaults.textButtonColors(
                        containerColor =
                            if (showAddPerson) MaterialTheme.colorScheme.secondaryContainer
                            else Color.Transparent
                    ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(
                    painterResource(if (showAddPerson) R.drawable.ic_close else R.drawable.ic_add),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (showAddPerson) stringResource(R.string.admin_close_form) else stringResource(R.string.admin_add_person))
            }
        }

        if (showAddPerson) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SleekTextField(
                        value = newPersonName,
                        onValueChange = { newPersonName = it },
                        label = stringResource(R.string.admin_field_person_name),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    SleekTextField(
                        value = newPersonType,
                        onValueChange = { newPersonType = it },
                        label = stringResource(R.string.admin_field_person_type),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    SleekTextField(
                        value = newPersonRole,
                        onValueChange = { newPersonRole = it },
                        label = stringResource(R.string.admin_field_person_role),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Button(
                            onClick = {
                                if (newPersonName.isNotBlank()) {
                                    viewModel.addPerson(
                                        EditablePerson(
                                            id = null,
                                            name = newPersonName.trim(),
                                            type = newPersonType.trim().ifBlank { "Actor" },
                                            role = newPersonRole.trim().ifBlank { null },
                                        )
                                    )
                                    newPersonName = ""
                                    newPersonRole = ""
                                    showAddPerson = false
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(stringResource(R.string.admin_add_to_list))
                        }
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 88.dp),
        ) {
            items(item.people.indices.toList()) { index ->
                val person = item.people[index]
                ListItem(
                    headlineContent = {
                        Text(person.name, style = MaterialTheme.typography.bodyLarge)
                    },
                    supportingContent = {
                        Text(
                            buildString {
                                append(person.type)
                                if (!person.role.isNullOrBlank()) append(" · ${person.role}")
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = {
                        IconButton(
                            onClick = { viewModel.removePerson(index) },
                            colors =
                                IconButtonDefaults.iconButtonColors(
                                    containerColor =
                                        MaterialTheme.colorScheme.errorContainer.copy(
                                            alpha = 0.15f
                                        ),
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_delete),
                                contentDescription = stringResource(R.string.cd_admin_remove),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvancedTab(
    item: com.makd.afinity.data.models.admin.EditableItem,
    viewModel: EditMetadataViewModel,
) {
    val allFields =
        listOf(
            "Cast",
            "Genres",
            "ProductionLocations",
            "Studios",
            "Tags",
            "Name",
            "Overview",
            "Runtime",
            "OfficialRating",
        )

    Column(
        modifier =
            Modifier.fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .imePadding()
                .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SleekTextField(
            value = item.communityRating?.toString() ?: "",
            onValueChange = { viewModel.updateCommunityRating(it) },
            label = stringResource(R.string.admin_field_community_rating),
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
        )
        SleekTextField(
            value = item.customRating ?: "",
            onValueChange = { viewModel.updateCustomRating(it) },
            label = stringResource(R.string.admin_field_custom_rating),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        if (item.type == "Series") {
            SleekTextField(
                value = item.status ?: "",
                onValueChange = { viewModel.updateStatus(it) },
                label = stringResource(R.string.admin_field_series_status),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            SleekTextField(
                value = item.displayOrder ?: "",
                onValueChange = { viewModel.updateDisplayOrder(it) },
                label = stringResource(R.string.admin_field_display_order),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

        SwitchRowSimple(
            label = stringResource(R.string.admin_lock_data),
            checked = item.lockData,
            onToggle = { viewModel.toggleLockData() },
        )

        if (item.lockData) {
            SectionHeader(stringResource(R.string.admin_section_locked_fields))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                allFields.forEach { field ->
                    FilterChip(
                        selected = field in item.lockedFields,
                        onClick = { viewModel.toggleLockedField(field) },
                        label = { Text(field) },
                        shape = CircleShape,
                        border = null,
                        colors =
                            FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                disabledContainerColor =
                                    MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(88.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipInput(
    chips: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.admin_add_item_placeholder)) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors =
                TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                ),
        )
        IconButton(
            onClick = {
                if (text.isNotBlank()) {
                    onAdd(text.trim())
                    text = ""
                }
            },
            modifier =
                Modifier.background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    .size(48.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_add),
                contentDescription = stringResource(R.string.cd_admin_add),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { chip ->
            InputChip(
                selected = false,
                onClick = {},
                label = { Text(chip, style = MaterialTheme.typography.bodyMedium) },
                shape = CircleShape,
                colors =
                    InputChipDefaults.inputChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                border = null,
                trailingIcon = {
                    IconButton(
                        onClick = { onRemove(chip) },
                        modifier = Modifier.size(16.dp),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.cd_admin_remove),
                            modifier = Modifier.size(12.dp),
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRowSimple(label: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable { onToggle() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
        )
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
        )
    }
}
