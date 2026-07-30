package com.makd.afinity.ui.settings.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.min
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.data.models.CustomHomeSection
import com.makd.afinity.data.models.CustomSectionCardStyle
import com.makd.afinity.data.models.CustomSectionSourceType
import com.makd.afinity.data.models.DiscoveryConfig
import com.makd.afinity.data.models.DiscoveryDensity
import com.makd.afinity.data.models.DiscoverySection
import com.makd.afinity.data.models.HomeRow
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.navigation.LocalPlayerOffset
import com.makd.afinity.ui.components.AfinitySwitch
import com.makd.afinity.ui.components.AfinityTextField
import com.makd.afinity.ui.components.ListPickerDialog
import com.makd.afinity.ui.components.SettingsDivider
import com.makd.afinity.ui.components.SettingsGroup
import com.makd.afinity.ui.components.SettingsItem
import com.makd.afinity.ui.components.SettingsSwitchItem
import com.makd.afinity.ui.components.filter.SearchableChipMultiSelect
import java.time.Month
import java.time.MonthDay
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomSectionsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CustomSectionsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerOffset = LocalPlayerOffset.current

    var editing by remember { mutableStateOf<CustomHomeSection?>(null) }
    var editingIsNew by remember { mutableStateOf(false) }
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()
    val reorderState =
        rememberReorderableLazyListState(lazyListState) { from, to ->
            viewModel.move(from.index, to.index)
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.custom_sections_title),
                        style =
                            MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_chevron_left),
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                actions = {
                    if (uiState.canAddMore) {
                        var presetsExpanded by remember { mutableStateOf(false) }
                        IconButton(onClick = { presetsExpanded = true }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_calendar),
                                contentDescription =
                                    stringResource(R.string.custom_sections_add_preset),
                            )
                        }
                        DropdownMenu(
                            expanded = presetsExpanded,
                            onDismissRequest = { presetsExpanded = false },
                        ) {
                            SeasonalPreset.entries.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(text = preset.defaultTitle) },
                                    onClick = {
                                        editing = viewModel.presetTemplate(preset)
                                        editingIsNew = true
                                        presetsExpanded = false
                                    },
                                )
                            }
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
            )
        },
        floatingActionButton = {
            if (uiState.canAddMore) {
                FloatingActionButton(
                    onClick = {
                        editing = viewModel.newSectionTemplate()
                        editingIsNew = true
                    },
                    modifier = Modifier.padding(bottom = playerOffset),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_add),
                        contentDescription = stringResource(R.string.custom_sections_add),
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    top = innerPadding.calculateTopPadding() + 16.dp,
                    start = innerPadding.calculateStartPadding(layoutDirection),
                    end = innerPadding.calculateEndPadding(layoutDirection),
                    bottom = max(innerPadding.calculateBottomPadding(), playerOffset) + 96.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column {
                    GroupCaption(stringResource(R.string.custom_sections_rows_caption))
                    SettingsGroup(title = stringResource(R.string.custom_sections_group_rows)) {
                        HomeRow.entries.forEachIndexed { index, row ->
                            if (index > 0) SettingsDivider()
                            SettingsSwitchItem(
                                title = stringResource(row.labelRes),
                                checked = row !in uiState.hiddenRows,
                                onCheckedChange = { viewModel.setRowVisible(row, it) },
                            )
                        }
                    }
                }
            }

            item {
                GroupCaption(stringResource(R.string.custom_sections_discovery_caption))
            }

            item {
                SettingsGroup(title = stringResource(R.string.custom_sections_group_discovery)) {
                    DropdownSelectorItem(
                        title = stringResource(R.string.custom_sections_field_density),
                        selectedLabel = stringResource(uiState.discovery.density.labelRes),
                        entries =
                            DiscoveryDensity.entries.map { stringResource(it.labelRes) to it },
                        isSelected = { it == uiState.discovery.density },
                        onSelect = { viewModel.setDensity(it) },
                    )
                    SettingsDivider()
                    SettingsItem(
                        title = stringResource(R.string.custom_sections_advanced),
                        subtitle = stringResource(R.string.custom_sections_advanced_summary),
                        onClick = { advancedExpanded = !advancedExpanded },
                        trailing = {
                            Icon(
                                painter =
                                    painterResource(
                                        id =
                                            if (advancedExpanded) {
                                                R.drawable.ic_keyboard_arrow_up
                                            } else R.drawable.ic_keyboard_arrow_down
                                    ),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                    )
                    if (advancedExpanded) {
                        DiscoverySection.entries.forEach { section ->
                            SettingsDivider()
                            DiscoverySectionItem(
                                section = section,
                                config = uiState.discovery,
                                onAuto = { viewModel.setDiscoveryAuto(section) },
                                onOff = { viewModel.setDiscoveryOff(section) },
                                onCount = { viewModel.setDiscoveryCount(section, it) },
                            )
                        }
                    }
                }
            }

            item {
                Column {
                    Text(
                        text = stringResource(R.string.custom_sections_group_yours),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 32.dp, bottom = 8.dp),
                    )
                    GroupCaption(stringResource(R.string.custom_sections_yours_caption))
                }
            }

            if (uiState.sections.isEmpty()) {
                item {
                    CustomSectionsEmptyState(
                        onAdd = {
                            editing = viewModel.newSectionTemplate()
                            editingIsNew = true
                        }
                    )
                }
            } else {
                itemsIndexed(uiState.sections, key = { _, section -> section.id }) { _, section ->
                    ReorderableItem(reorderState, key = section.id) { isDragging ->
                        val elevation by
                            animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "drag_elev")
                        CustomSectionRow(
                            section = section,
                            elevation = elevation,
                            onClick = {
                                editing = section
                                editingIsNew = false
                            },
                            onToggle = { viewModel.setEnabled(section.id, it) },
                            dragHandleModifier =
                                Modifier.draggableHandle(
                                    onDragStopped = { viewModel.commitOrder() }
                                ),
                        )
                    }
                }
            }
        }
    }

    val current = editing
    if (current != null) {
        val containerHeight =
            with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
        val dialogHeight = min(560.dp, containerHeight * 0.9f)
        ListPickerDialog(
            title =
                stringResource(
                    if (editingIsNew) R.string.custom_sections_new
                    else R.string.custom_sections_edit
                ),
            onDismiss = { editing = null },
            height = dialogHeight,
        ) {
            CustomSectionEditor(
                section = current,
                optionsFor = viewModel::optionsFor,
                onSave = {
                    viewModel.save(it, editingIsNew)
                    editing = null
                },
                onDelete =
                    if (editingIsNew) null
                    else {
                        {
                            viewModel.delete(current.id)
                            editing = null
                        }
                    },
            )
        }
    }
}

@Composable
private fun CustomSectionRow(
    section: CustomHomeSection,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    dragHandleModifier: Modifier = Modifier,
    elevation: Dp = 0.dp,
) {
    val locale = LocalConfiguration.current.locales[0]
    val contentAlpha = if (section.enabled) 1f else 0.45f

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = elevation,
        shadowElevation = elevation,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier.size(40.dp)
                        .clip(CircleShape)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = contentAlpha)
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = sourceTypeIcon(section.sourceType)),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = contentAlpha),
                    modifier = Modifier.size(20.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f).padding(start = 16.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text =
                        section.title.ifBlank {
                            stringResource(R.string.custom_sections_untitled)
                        },
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        listOfNotNull(
                                sourceTypeLabel(section.sourceType, locale),
                                section.sourceValues.takeIf { it.isNotEmpty() }?.joinToString(", "),
                            )
                            .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = contentAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (section.isSeasonal) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_calendar),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha),
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text =
                                formatSeasonRange(
                                    section.seasonStart.orEmpty(),
                                    section.seasonEnd.orEmpty(),
                                    locale,
                                ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha),
                        )
                    }
                }
            }

            AfinitySwitch(checked = section.enabled, onCheckedChange = onToggle)

            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 4.dp).size(20.dp),
            )

            Icon(
                painter = painterResource(id = R.drawable.ic_arrows_sort),
                contentDescription = stringResource(R.string.custom_sections_reorder),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = dragHandleModifier.padding(start = 4.dp).size(24.dp),
            )
        }
    }
}

@Composable
private fun CustomSectionsEmptyState(onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier =
                Modifier.size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_view_module),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
        }
        Text(
            text = stringResource(R.string.custom_sections_empty_title),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.custom_sections_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onAdd, modifier = Modifier.padding(top = 8.dp)) {
            Text(text = stringResource(R.string.custom_sections_add))
        }
    }
}

@Composable
private fun ColumnScope.CustomSectionEditor(
    section: CustomHomeSection,
    optionsFor: (CustomSectionSourceType) -> List<SourceOption>,
    onSave: (CustomHomeSection) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val locale = LocalConfiguration.current.locales[0]
    var draft by remember(section.id) { mutableStateOf(section) }
    var sourceQuery by remember(section.id) { mutableStateOf("") }
    val options = optionsFor(draft.sourceType)
    val allowsMultiple = draft.sourceType.supportsMultipleSources
    val selectedOptions =
        draft.sourceValues.map { value ->
            options.firstOrNull { it.value == value } ?: SourceOption(value, value)
        }

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        SettingsGroup(title = stringResource(R.string.custom_sections_group_content)) {
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                AfinityTextField(
                    value = draft.title,
                    onValueChange = { draft = draft.copy(title = it) },
                    label = stringResource(R.string.custom_sections_field_title),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            SettingsDivider()
            DropdownSelectorItem(
                title = stringResource(R.string.custom_sections_field_source_type),
                selectedLabel = sourceTypeLabel(draft.sourceType, locale),
                entries = CustomSectionSourceType.entries.map { sourceTypeLabel(it, locale) to it },
                isSelected = { it == draft.sourceType },
                onSelect = {
                    draft = draft.copy(sourceType = it, sourceValues = emptyList())
                    sourceQuery = ""
                },
            )
            SettingsDivider()
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                val suggestions =
                    remember(options, draft.sourceValues, sourceQuery) {
                        options.filter {
                            it.value !in draft.sourceValues &&
                                (sourceQuery.isBlank() ||
                                    it.label.contains(sourceQuery, ignoreCase = true))
                        }
                    }
                SearchableChipMultiSelect(
                    label = stringResource(R.string.custom_sections_field_source),
                    placeholder = stringResource(R.string.custom_sections_pick_source),
                    query = sourceQuery,
                    onQueryChange = { sourceQuery = it },
                    suggestions = suggestions,
                    suggestionLabel = { it.label },
                    onSuggestionSelected = { option ->
                        draft =
                            if (allowsMultiple) {
                                draft.copy(sourceValues = draft.sourceValues + option.value)
                            } else {
                                draft.copy(sourceValues = listOf(option.value))
                            }
                        sourceQuery = ""
                    },
                    selected = selectedOptions,
                    selectedLabel = { it.label },
                    onRemoveSelected = { option ->
                        draft = draft.copy(sourceValues = draft.sourceValues - option.value)
                    },
                    onClearAll = { draft = draft.copy(sourceValues = emptyList()) },
                    collapseOnSelect = !allowsMultiple,
                )
            }
        }

        SettingsGroup(title = stringResource(R.string.custom_sections_group_display)) {
            DropdownSelectorItem(
                title = stringResource(R.string.custom_sections_field_card_style),
                selectedLabel = cardStyleLabel(draft.cardStyle, locale),
                entries = CustomSectionCardStyle.entries.map { cardStyleLabel(it, locale) to it },
                isSelected = { it == draft.cardStyle },
                onSelect = { draft = draft.copy(cardStyle = it) },
            )
            SettingsDivider()
            DropdownSelectorItem(
                title = stringResource(R.string.custom_sections_field_sort),
                selectedLabel =
                    if (draft.randomOrder) stringResource(R.string.custom_sections_sort_random)
                    else sortLabel(draft.sortBy, locale),
                entries =
                    listOf(stringResource(R.string.custom_sections_sort_random) to null) +
                        SortBy.entries.map { sortLabel(it, locale) to it },
                isSelected = {
                    if (it == null) draft.randomOrder else !draft.randomOrder && it == draft.sortBy
                },
                onSelect = { value ->
                    draft =
                        if (value == null) draft.copy(randomOrder = true)
                        else draft.copy(randomOrder = false, sortBy = value)
                },
            )
            SettingsDivider()
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                AfinityTextField(
                    value = draft.itemLimit.toString(),
                    onValueChange = { text ->
                        val parsed = text.filter { it.isDigit() }.toIntOrNull()
                        draft = draft.copy(itemLimit = (parsed ?: 0).coerceIn(1, 50))
                    },
                    label = stringResource(R.string.custom_sections_field_limit),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        SettingsGroup(title = stringResource(R.string.custom_sections_group_schedule)) {
            SettingsSwitchItem(
                title = stringResource(R.string.custom_sections_field_seasonal),
                subtitle = stringResource(R.string.custom_sections_seasonal_hint),
                checked = draft.isSeasonal,
                onCheckedChange = { checked ->
                    draft =
                        if (checked) draft.copy(seasonStart = "10-01", seasonEnd = "10-31")
                        else draft.copy(seasonStart = null, seasonEnd = null)
                },
            )
            if (draft.isSeasonal) {
                SettingsDivider()
                MonthDayPickerItems(
                    label = stringResource(R.string.custom_sections_field_season_start),
                    value = draft.seasonStart.orEmpty(),
                    onValueChange = { draft = draft.copy(seasonStart = it) },
                )
                SettingsDivider()
                MonthDayPickerItems(
                    label = stringResource(R.string.custom_sections_field_season_end),
                    value = draft.seasonEnd.orEmpty(),
                    onValueChange = { draft = draft.copy(seasonEnd = it) },
                )
            }
        }
    }

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onDelete != null) {
            TextButton(onClick = onDelete) {
                Text(
                    text = stringResource(R.string.custom_sections_delete),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Box(modifier = Modifier.weight(1f))
        Button(
            onClick = { onSave(draft) },
            enabled = draft.title.isNotBlank() && draft.sourceValues.isNotEmpty(),
        ) {
            Text(text = stringResource(R.string.custom_sections_save))
        }
    }
}

@Composable
private fun GroupCaption(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 32.dp, end = 32.dp, bottom = 8.dp),
    )
}

@Composable
private fun DiscoverySectionItem(
    section: DiscoverySection,
    config: DiscoveryConfig,
    onAuto: () -> Unit,
    onOff: () -> Unit,
    onCount: (Int) -> Unit,
) {
    val enabled = config.isEnabled(section)
    val override = config.overrides[section]
    val resolved = config.countFor(section)

    val autoLabel =
        if (section.ceiling > 1) {
            stringResource(
                R.string.discovery_auto_count_fmt,
                (section.defaultCount * config.density.factor)
                    .roundToInt()
                    .coerceIn(1, section.ceiling),
            )
        } else stringResource(R.string.discovery_auto)

    val selectedLabel =
        when {
            !enabled -> stringResource(R.string.discovery_off)
            override == null -> autoLabel
            section.ceiling > 1 -> stringResource(R.string.discovery_count_fmt, resolved)
            else -> stringResource(R.string.discovery_on)
        }

    val entries =
        buildList<Pair<String, Int?>> {
            add(autoLabel to AUTO_CHOICE)
            add(stringResource(R.string.discovery_off) to OFF_CHOICE)
            if (section.ceiling > 1) {
                (1..section.ceiling).forEach { n ->
                    add(stringResource(R.string.discovery_count_fmt, n) to n)
                }
            }
        }

    DropdownSelectorItem(
        title = stringResource(section.labelRes),
        selectedLabel = selectedLabel,
        entries = entries,
        isSelected = { choice ->
            when (choice) {
                AUTO_CHOICE -> enabled && override == null
                OFF_CHOICE -> !enabled
                else -> enabled && override == choice
            }
        },
        onSelect = { choice ->
            when (choice) {
                AUTO_CHOICE -> onAuto()
                OFF_CHOICE -> onOff()
                else -> choice?.let(onCount)
            }
        },
    )
}

@Composable
private fun <T> DropdownSelectorItem(
    title: String,
    selectedLabel: String,
    entries: List<Pair<String, T>>,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        SettingsItem(
            title = title,
            subtitle = selectedLabel,
            onClick = { expanded = true },
            trailing = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_keyboard_arrow_down),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            entries.forEach { (entryLabel, value) ->
                DropdownMenuItem(
                    text = { Text(text = entryLabel) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    },
                    leadingIcon =
                        if (isSelected(value)) {
                            {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_check),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        } else null,
                )
            }
        }
    }
}

@Composable
private fun MonthDayPickerItems(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    val month = value.substringBefore('-').toIntOrNull()?.coerceIn(1, 12) ?: 1
    val day = value.substringAfter('-', "").toIntOrNull()?.coerceIn(1, 31) ?: 1
    val locale = LocalConfiguration.current.locales[0]
    val daysInMonth = MonthDay.of(month, 1).month.maxLength()
    val monthName = Month.of(month).getDisplayName(TextStyle.FULL, locale)

    DropdownSelectorItem(
        title = label,
        selectedLabel = "$monthName $day",
        entries = (1..12).map { Month.of(it).getDisplayName(TextStyle.FULL, locale) to it },
        isSelected = { it == month },
        onSelect = { newMonth ->
            val maxDay = MonthDay.of(newMonth, 1).month.maxLength()
            onValueChange(formatMonthDay(newMonth, day.coerceAtMost(maxDay)))
        },
    )
    DropdownSelectorItem(
        title = stringResource(R.string.custom_sections_field_day),
        selectedLabel = day.toString(),
        entries = (1..daysInMonth).map { it.toString() to it },
        isSelected = { it == day },
        onSelect = { onValueChange(formatMonthDay(month, it)) },
    )
}

private fun sourceTypeIcon(type: CustomSectionSourceType): Int =
    when (type) {
        CustomSectionSourceType.GENRE -> R.drawable.ic_genre
        CustomSectionSourceType.STUDIO -> R.drawable.ic_movie
        CustomSectionSourceType.TAG -> R.drawable.ic_bookmark
        CustomSectionSourceType.COLLECTION -> R.drawable.ic_collection
        CustomSectionSourceType.PLAYLIST -> R.drawable.ic_playlist
        CustomSectionSourceType.LIBRARY -> R.drawable.ic_video_library
    }

private fun formatSeasonRange(start: String, end: String, locale: Locale): String {
    fun label(value: String): String? {
        val month = value.substringBefore('-').toIntOrNull() ?: return null
        val day = value.substringAfter('-', "").toIntOrNull() ?: return null
        if (month !in 1..12) return null
        val monthName = Month.of(month).getDisplayName(TextStyle.SHORT, locale)
        return "$monthName $day"
    }
    val from = label(start)
    val to = label(end)
    return if (from != null && to != null) "$from – $to" else ""
}

private val AUTO_CHOICE: Int? = null
private val OFF_CHOICE: Int? = -1

private fun formatMonthDay(month: Int, day: Int): String = "%02d-%02d".format(Locale.US, month, day)

private fun titleCaseWords(raw: String, locale: Locale): String =
    raw.split('_').joinToString(" ") { word ->
        word.lowercase(locale).replaceFirstChar { it.titlecase(locale) }
    }

private fun sourceTypeLabel(type: CustomSectionSourceType, locale: Locale): String =
    titleCaseWords(type.name, locale)

private fun cardStyleLabel(style: CustomSectionCardStyle, locale: Locale): String =
    titleCaseWords(style.name, locale)

private fun sortLabel(sortBy: SortBy, locale: Locale): String = titleCaseWords(sortBy.name, locale)
