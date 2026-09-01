package com.makd.afinity.ui.settings.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
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
import com.makd.afinity.data.models.CustomSectionItemType
import com.makd.afinity.data.models.CustomSectionSourceType
import com.makd.afinity.data.models.CustomSectionTypeGroup
import com.makd.afinity.data.models.DiscoveryConfig
import com.makd.afinity.data.models.DiscoveryDensity
import com.makd.afinity.data.models.DiscoverySection
import com.makd.afinity.data.models.HomeRow
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.media.LibraryFilterOptions
import com.makd.afinity.data.models.media.LibraryFilters
import com.makd.afinity.navigation.LocalPlayerOffset
import com.makd.afinity.ui.components.AfinitySwitch
import com.makd.afinity.ui.components.AfinityTextField
import com.makd.afinity.ui.components.ListPickerDialog
import com.makd.afinity.ui.components.SettingsDivider
import com.makd.afinity.ui.components.SettingsGroup
import com.makd.afinity.ui.components.SettingsItem
import com.makd.afinity.ui.components.SettingsSwitchItem
import com.makd.afinity.ui.components.filter.SearchableChipMultiSelect
import com.makd.afinity.ui.library.LibraryFilterBottomSheet
import com.makd.afinity.ui.library.LibraryFilterCapabilities
import com.makd.afinity.util.DateSkeleton
import com.makd.afinity.util.localizedDateFormatter
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.LocalDate
import java.time.Month
import java.time.MonthDay
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomSectionsScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CustomSectionsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerOffset = LocalPlayerOffset.current
    val locale = LocalConfiguration.current.locales[0]
    val context = LocalContext.current

    var editing by remember { mutableStateOf<CustomHomeSection?>(null) }
    var editingIsNew by remember { mutableStateOf(false) }
    var choosingTemplate by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()
    val reorderState =
        rememberReorderableLazyListState(lazyListState) { from, to ->
            viewModel.move(from.key, to.key)
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
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
            )
        },
        floatingActionButton = {
            if (uiState.canAddMore) {
                FloatingActionButton(
                    onClick = { choosingTemplate = true },
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
                    GroupHeader(
                        title = stringResource(R.string.custom_sections_group_always),
                        caption = stringResource(R.string.custom_sections_always_caption),
                    )
                    SettingsGroup {
                        HomeRow.alwaysOn.forEachIndexed { index, row ->
                            if (index > 0) SettingsDivider()
                            SettingsItem(
                                title = stringResource(row.labelRes),
                                trailing = { AlwaysOnBadge() },
                            )
                        }
                    }
                }
            }

            item {
                Column {
                    GroupHeader(
                        title = stringResource(R.string.custom_sections_group_rows),
                        caption = stringResource(R.string.custom_sections_rows_caption),
                    )
                    SettingsGroup {
                        HomeRow.configurable.forEachIndexed { index, row ->
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

            if (uiState.sections.isNotEmpty()) {
                item {
                    GroupHeader(
                        title = stringResource(R.string.custom_sections_group_yours),
                        caption = stringResource(R.string.custom_sections_yours_caption),
                    )
                }
            }

            itemsIndexed(uiState.sections, key = { _, section -> section.id }) { _, section ->
                ReorderableItem(reorderState, key = section.id) { isDragging ->
                    val elevation by
                        animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "drag_elev")
                    CustomSectionRow(
                        section = section,
                        sourceLabels = uiState.sourceLabelsFor(section),
                        elevation = elevation,
                        onClick = {
                            editing = section
                            editingIsNew = false
                        },
                        onToggle = { viewModel.setEnabled(section.id, it) },
                        dragHandleModifier =
                            Modifier.draggableHandle(onDragStopped = { viewModel.commitOrder() }),
                    )
                }
            }

            item {
                Column {
                    GroupHeader(
                        title = stringResource(R.string.custom_sections_group_discovery),
                        caption = stringResource(R.string.custom_sections_discovery_caption),
                    )
                    SettingsGroup {
                        DropdownSelectorItem(
                            title = stringResource(R.string.custom_sections_field_density),
                            selectedLabel = stringResource(uiState.discovery.density.labelRes),
                            entries =
                                DiscoveryDensity.displayOrder.map {
                                    stringResource(it.labelRes) to it
                                },
                            isSelected = { it == uiState.discovery.density },
                            onSelect = { viewModel.setDensity(it) },
                        )
                        if (uiState.discovery.density == DiscoveryDensity.CUSTOM) {
                            SettingsDivider()
                            AdvancedHeader()
                            DiscoverySection.entries.forEach { section ->
                                SettingsDivider()
                                DiscoveryCountItem(
                                    section = section,
                                    config = uiState.discovery,
                                    onCount = { viewModel.setDiscoveryCount(section, it) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val pendingTemplate by viewModel.pendingTemplate.collectAsStateWithLifecycle()
    LaunchedEffect(pendingTemplate) {
        pendingTemplate?.let { template ->
            editing = template
            editingIsNew = true
            choosingTemplate = false
            viewModel.consumePendingTemplate()
        }
    }

    val presetTitles = SeasonalPreset.entries.associateWith { stringResource(it.titleRes) }

    if (choosingTemplate) {
        AddSectionDialog(
            locale = locale,
            onBlank = {
                editing = viewModel.newSectionTemplate()
                editingIsNew = true
                choosingTemplate = false
            },
            onPreset = { preset ->
                val resolvedTitle = presetTitles[preset] ?: ""
                viewModel.requestPreset(preset, resolvedTitle)
                choosingTemplate = false
            },
            onDismiss = { choosingTemplate = false },
        )
    }

    val current = editing
    if (current != null) {
        val containerHeight =
            with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }
        val dialogHeight = min(720.dp, containerHeight * 0.9f)
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
                optionsFor = uiState::optionsFor,
                sourceStateFor = uiState::stateFor,
                onLoadSources = viewModel::ensureSourcesLoaded,
                filterOptions = uiState.filterOptions,
                onLoadFilterOptions = viewModel::ensureFilterOptionsLoaded,
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
    sourceLabels: List<String>?,
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
                        section.title.ifBlank { stringResource(R.string.custom_sections_untitled) },
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        listOfNotNull(
                                stringResource(section.sourceType.labelRes),
                                sourceLabels?.takeIf { it.isNotEmpty() }?.joinToString(", "),
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
private fun ColumnScope.CustomSectionEditor(
    section: CustomHomeSection,
    optionsFor: (CustomSectionSourceType) -> List<SourceOption>,
    sourceStateFor: (CustomSectionSourceType) -> SourceLoadState?,
    onLoadSources: (CustomSectionSourceType, Boolean) -> Unit,
    filterOptions: LibraryFilterOptions,
    onLoadFilterOptions: () -> Unit,
    onSave: (CustomHomeSection) -> Unit,
    onDelete: (() -> Unit)?,
) {
    var draft by remember(section.id) { mutableStateOf(section.withSanitizedItemTypes()) }
    var sourceQuery by remember(section.id) { mutableStateOf("") }
    val options = optionsFor(draft.sourceType)
    val allowsMultiple = draft.sourceType.supportsMultipleSources
    val unresolvedLabel = stringResource(R.string.custom_sections_source_resolving)
    val selectedOptions =
        draft.sourceValues.map { value ->
            options.firstOrNull { it.value == value }
                ?: SourceOption(
                    value = value,
                    label = if (draft.sourceType.usesItemIds) unresolvedLabel else value,
                )
        }
    val libraryType =
        if (draft.sourceType == CustomSectionSourceType.LIBRARY) {
            selectedOptions.firstOrNull()?.libraryType
        } else null
    val allowedItemTypes =
        remember(draft.sourceType, libraryType) {
            CustomSectionItemType.availableFor(draft.sourceType, libraryType)
        }

    val supportsRefinement = draft.sourceType.supportsRefinement
    var showRefineSheet by remember { mutableStateOf(false) }

    LaunchedEffect(allowedItemTypes) { draft = draft.withItemTypesLimitedTo(allowedItemTypes) }

    LaunchedEffect(draft.sourceType) { onLoadSources(draft.sourceType, false) }

    LaunchedEffect(supportsRefinement) { if (supportsRefinement) onLoadFilterOptions() }

    if (showRefineSheet) {
        LibraryFilterBottomSheet(
            filters = draft.filters,
            options = filterOptions,
            capabilities =
                LibraryFilterCapabilities(
                    genres = draft.sourceType.refinesGenres,
                    ratings = true,
                    tags = draft.sourceType.refinesTags,
                    years = true,
                    videoType = CustomSectionItemType.MOVIE in draft.itemTypes,
                    seriesStatus = CustomSectionItemType.SERIES in draft.itemTypes,
                ),
            isLoadingOptions = filterOptions == LibraryFilterOptions(),
            onApply = { draft = draft.copy(filters = it) },
            onDismiss = { showRefineSheet = false },
        )
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
                selectedLabel = stringResource(draft.sourceType.labelRes),
                entries = CustomSectionSourceType.entries.map { stringResource(it.labelRes) to it },
                isSelected = { it == draft.sourceType },
                onSelect = {
                    draft = draft.withSourceType(it)
                    sourceQuery = ""
                },
            )
            SettingsDivider()
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
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
                    placeholder = stringResource(sourcePlaceholderRes(draft.sourceType)),
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
                if (options.isEmpty()) {
                    SourceStatusRow(
                        state = sourceStateFor(draft.sourceType),
                        onRetry = { onLoadSources(draft.sourceType, true) },
                    )
                }
            }
            if (supportsRefinement) {
                SettingsDivider()
                SettingsItem(
                    title = stringResource(R.string.custom_sections_field_refine),
                    subtitle = refineSummary(draft.filters),
                    onClick = { showRefineSheet = true },
                    trailing = {
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron_right),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                )
            }
            SettingsDivider()
            ItemTypeSelector(
                selected = draft.itemTypes,
                available = allowedItemTypes,
                sourceType = draft.sourceType,
                onToggle = { draft = draft.withItemTypeToggled(it) },
            )
        }

        SettingsGroup(title = stringResource(R.string.custom_sections_group_display)) {
            val lockedStyle = draft.lockedCardStyle
            if (lockedStyle == null) {
                DropdownSelectorItem(
                    title = stringResource(R.string.custom_sections_field_card_style),
                    selectedLabel = stringResource(draft.cardStyle.labelRes),
                    entries =
                        CustomSectionCardStyle.entries
                            .filterNot { it == CustomSectionCardStyle.SQUARE }
                            .map { stringResource(it.labelRes) to it },
                    isSelected = { it == draft.cardStyle },
                    onSelect = { draft = draft.copy(cardStyle = it) },
                )
            } else {
                SettingsItem(
                    title = stringResource(R.string.custom_sections_field_card_style),
                    subtitle =
                        stringResource(
                            R.string.custom_sections_card_style_locked_fmt,
                            stringResource(lockedStyle.labelRes),
                        ),
                )
            }
            SettingsDivider()
            DropdownSelectorItem(
                title = stringResource(R.string.custom_sections_field_sort),
                selectedLabel =
                    if (draft.randomOrder) stringResource(R.string.custom_sections_sort_random)
                    else stringResource(draft.sortBy.labelRes),
                entries =
                    listOf(stringResource(R.string.custom_sections_sort_random) to null) +
                        SortBy.entries
                            .filterNot { it == SortBy.RANDOM }
                            .map { stringResource(it.labelRes) to it },
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
                        if (checked) {
                            val start = LocalDate.now()
                            val end = start.plusMonths(1)
                            draft.copy(
                                seasonStart = formatMonthDay(start.monthValue, start.dayOfMonth),
                                seasonEnd = formatMonthDay(end.monthValue, end.dayOfMonth),
                            )
                        } else draft.copy(seasonStart = null, seasonEnd = null)
                },
            )
            if (draft.isSeasonal) {
                SettingsDivider()
                MonthDayPickerItem(
                    label = stringResource(R.string.custom_sections_field_season_start),
                    value = draft.seasonStart.orEmpty(),
                    onValueChange = { draft = draft.copy(seasonStart = it) },
                )
                SettingsDivider()
                MonthDayPickerItem(
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
            enabled =
                draft.title.isNotBlank() &&
                    draft.sourceValues.isNotEmpty() &&
                    draft.itemTypes.isNotEmpty(),
        ) {
            Text(text = stringResource(R.string.custom_sections_save))
        }
    }
}

@Composable
private fun refineSummary(filters: LibraryFilters): String {
    if (filters.isEmpty) return stringResource(R.string.custom_sections_refine_none)
    val parts = buildList {
        if (filters.played) add(stringResource(R.string.filter_played))
        if (filters.unplayed) add(stringResource(R.string.filter_unplayed))
        if (filters.resumable) add(stringResource(R.string.filter_resumable))
        if (filters.favorites) add(stringResource(R.string.filter_favorites))
        if (filters.watchlist) add(stringResource(R.string.filter_watchlist))
        addAll(filters.genres)
        addAll(filters.tags)
        addAll(filters.officialRatings)
        addAll(filters.years.map { it.toString() })
        if (filters.features.isNotEmpty()) add(stringResource(R.string.library_filter_features))
        if (filters.videoTypes.isNotEmpty()) {
            add(stringResource(R.string.library_filter_video_type))
        }
        if (filters.seriesStatuses.isNotEmpty()) {
            add(stringResource(R.string.library_filter_series_status))
        }
    }
    val lead = parts.take(2).joinToString(", ")
    return if (filters.activeCount <= 2) {
        lead
    } else {
        pluralStringResource(
            R.plurals.custom_sections_refine_summary_fmt,
            filters.activeCount,
            lead,
            filters.activeCount,
        )
    }
}

@Composable
private fun SourceStatusRow(state: SourceLoadState?, onRetry: () -> Unit) {
    val failed = state == SourceLoadState.FAILED
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text =
                stringResource(
                    when (state) {
                        SourceLoadState.LOADED -> R.string.custom_sections_sources_empty
                        SourceLoadState.FAILED -> R.string.custom_sections_sources_failed
                        else -> R.string.custom_sections_sources_loading
                    }
                ),
            style = MaterialTheme.typography.bodySmall,
            color =
                if (failed) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (failed) {
            TextButton(onClick = onRetry) {
                Text(text = stringResource(R.string.custom_sections_sources_retry))
            }
        }
    }
}

@Composable
private fun ItemTypeSelector(
    selected: List<CustomSectionItemType>,
    available: List<CustomSectionItemType>,
    sourceType: CustomSectionSourceType,
    onToggle: (CustomSectionItemType) -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = stringResource(R.string.custom_sections_field_item_types),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text =
                when {
                    selected.firstOrNull()?.group == CustomSectionTypeGroup.EPISODE ->
                        stringResource(R.string.custom_sections_item_types_episode_hint)
                    selected.firstOrNull()?.group == CustomSectionTypeGroup.MUSIC ->
                        stringResource(R.string.custom_sections_item_types_music_hint)
                    sourceType == CustomSectionSourceType.PLAYLIST ->
                        stringResource(R.string.custom_sections_item_types_playlist_hint)
                    sourceType == CustomSectionSourceType.COLLECTION ->
                        stringResource(R.string.custom_sections_item_types_collection_hint)
                    sourceType == CustomSectionSourceType.LIBRARY ->
                        stringResource(R.string.custom_sections_item_types_library_hint)
                    else -> stringResource(R.string.custom_sections_item_types_hint)
                },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            available.forEach { type ->
                FilterChip(
                    selected = type in selected,
                    onClick = { onToggle(type) },
                    label = { Text(text = stringResource(type.labelRes)) },
                )
            }
        }
    }
}

@Composable
private fun AddSectionDialog(
    locale: Locale,
    onBlank: () -> Unit,
    onPreset: (SeasonalPreset) -> Unit,
    onDismiss: () -> Unit,
) {
    ListPickerDialog(
        title = stringResource(R.string.custom_sections_add),
        onDismiss = onDismiss,
        height = 400.dp,
    ) {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            SettingsItem(
                title = stringResource(R.string.custom_sections_add_blank),
                subtitle = stringResource(R.string.custom_sections_add_blank_summary),
                onClick = onBlank,
            )
            SettingsDivider()
            SeasonalPreset.entries.forEachIndexed { index, preset ->
                if (index > 0) SettingsDivider()
                SettingsItem(
                    title = stringResource(preset.titleRes),
                    subtitle =
                        stringResource(
                            R.string.custom_sections_preset_range_fmt,
                            monthDayLabel(preset.start, locale),
                            monthDayLabel(preset.end, locale),
                        ),
                    onClick = { onPreset(preset) },
                )
            }
        }
    }
}

private fun monthDayLabel(
    month: Int,
    day: Int,
    locale: Locale,
    skeleton: String = DateSkeleton.MONTH_DAY,
): String =
    MonthDay.of(month, day.coerceIn(1, Month.of(month).maxLength()))
        .format(localizedDateFormatter(locale, skeleton))

private fun monthDayLabel(value: String, locale: Locale): String {
    val month = value.substringBefore('-').toIntOrNull()?.coerceIn(1, 12) ?: 1
    val day = value.substringAfter('-', "").toIntOrNull() ?: 1
    return monthDayLabel(month, day, locale)
}

@Composable
private fun GroupHeader(title: String, caption: String? = null) {
    Column(modifier = Modifier.padding(start = 32.dp, end = 32.dp, bottom = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun AlwaysOnBadge() {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text = stringResource(R.string.custom_sections_always_on),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun AdvancedHeader() {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
        Text(
            text = stringResource(R.string.custom_sections_advanced),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.custom_sections_advanced_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun DiscoveryCountItem(
    section: DiscoverySection,
    config: DiscoveryConfig,
    onCount: (Int) -> Unit,
) {
    var showInput by remember { mutableStateOf(false) }
    val value = config.countFor(section)

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(section.labelRes),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
        )
        CountStepper(
            value = value,
            max = section.ceiling,
            onValueChange = onCount,
            onValueClick = { showInput = true },
        )
    }

    if (showInput) {
        CountInputDialog(
            title = stringResource(section.labelRes),
            value = value,
            max = section.ceiling,
            onDismiss = { showInput = false },
            onConfirm = {
                onCount(it)
                showInput = false
            },
        )
    }
}

@Composable
private fun CountStepper(
    value: Int,
    max: Int,
    onValueChange: (Int) -> Unit,
    onValueClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StepperButton(
            iconRes = R.drawable.ic_remove,
            contentDescription = stringResource(R.string.cd_decrease_limit),
            enabled = value > 0,
            onClick = { onValueChange(value - 1) },
        )

        Surface(
            onClick = onValueClick,
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
        ) {
            Text(
                text = if (value <= 0) stringResource(R.string.discovery_off) else value.toString(),
                style =
                    MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    ),
                textAlign = TextAlign.Center,
                modifier =
                    Modifier.widthIn(min = 48.dp).padding(horizontal = 12.dp, vertical = 6.dp),
                color =
                    if (value <= 0) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary,
            )
        }

        StepperButton(
            iconRes = R.drawable.ic_add,
            contentDescription = stringResource(R.string.cd_increase_limit),
            enabled = value < max,
            onClick = { onValueChange(value + 1) },
        )
    }
}

@Composable
private fun StepperButton(
    iconRes: Int,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.size(36.dp),
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = contentDescription,
                tint =
                    if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun CountInputDialog(
    title: String,
    value: Int,
    max: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var text by remember { mutableStateOf(value.toString()) }
    val parsed = text.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AfinityTextField(
                    value = text,
                    onValueChange = { input -> text = input.filter { it.isDigit() }.take(2) },
                    label = stringResource(R.string.discovery_count_label),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.discovery_count_range_fmt, max),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let { onConfirm(it.coerceIn(0, max)) } },
                enabled = parsed != null,
            ) {
                Text(text = stringResource(R.string.custom_sections_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.action_cancel)) }
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
private fun MonthDayPickerItem(label: String, value: String, onValueChange: (String) -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val today = remember { MonthDay.now() }
    val month = value.substringBefore('-').toIntOrNull()?.coerceIn(1, 12) ?: today.monthValue
    val day = value.substringAfter('-', "").toIntOrNull()?.coerceIn(1, 31) ?: today.dayOfMonth
    var picking by remember { mutableStateOf(false) }

    SettingsItem(
        title = label,
        subtitle = monthDayLabel(month, day, locale, DateSkeleton.MONTH_DAY_LONG),
        onClick = { picking = true },
    )

    if (picking) {
        MonthDayPickerDialog(
            title = label,
            initialMonth = month,
            initialDay = day,
            locale = locale,
            onDismiss = { picking = false },
            onConfirm = { newMonth, newDay ->
                onValueChange(formatMonthDay(newMonth, newDay))
                picking = false
            },
        )
    }
}

@Composable
private fun MonthDayPickerDialog(
    title: String,
    initialMonth: Int,
    initialDay: Int,
    locale: Locale,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    var month by remember { mutableStateOf(initialMonth) }
    var day by remember { mutableStateOf(initialDay) }
    var monthGridOpen by remember { mutableStateOf(false) }
    val today = remember { MonthDay.now() }
    val daysInMonth = Month.of(month).maxLength()
    val clampedDay = day.coerceAtMost(daysInMonth)
    val monthName = Month.of(month).getDisplayName(TextStyle.FULL, locale)
    val chevronRotation by
        animateFloatAsState(if (monthGridOpen) 180f else 0f, label = "month_chevron")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "$monthName $clampedDay",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier =
                            Modifier.clip(RoundedCornerShape(50))
                                .clickable { monthGridOpen = !monthGridOpen }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = monthName,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Icon(
                            painter = painterResource(id = R.drawable.ic_keyboard_arrow_down),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp).rotate(chevronRotation),
                        )
                    }

                    if (!monthGridOpen) {
                        Row {
                            IconButton(onClick = { month = if (month == 1) 12 else month - 1 }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_chevron_left),
                                    contentDescription = stringResource(R.string.cd_previous_month),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { month = if (month == 12) 1 else month + 1 }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_chevron_right),
                                    contentDescription = stringResource(R.string.cd_next_month),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                if (monthGridOpen) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        (1..12).chunked(3).forEach { quarter ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                quarter.forEach { candidate ->
                                    MonthCell(
                                        label =
                                            Month.of(candidate)
                                                .getDisplayName(TextStyle.SHORT, locale),
                                        selected = candidate == month,
                                        isToday = candidate == today.monthValue,
                                        onClick = {
                                            month = candidate
                                            monthGridOpen = false
                                        },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        (1..daysInMonth).chunked(7).forEach { week ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                week.forEach { candidate ->
                                    DayCell(
                                        day = candidate,
                                        selected = candidate == clampedDay,
                                        isToday =
                                            month == today.monthValue &&
                                                candidate == today.dayOfMonth,
                                        onClick = { day = candidate },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                repeat(7 - week.size) { Spacer(modifier = Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(month, clampedDay) }) {
                Text(text = stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun MonthCell(
    label: String,
    selected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier =
            modifier
                .heightIn(min = 44.dp)
                .clip(shape)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                .then(
                    if (isToday && !selected) {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.primary, shape)
                    } else Modifier
                )
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                ),
            color =
                if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun DayCell(
    day: Int,
    selected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .clip(CircleShape)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                .then(
                    if (isToday && !selected) {
                        Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    } else Modifier
                )
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.toString(),
            style =
                MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                ),
            color =
                if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun sourcePlaceholderRes(type: CustomSectionSourceType): Int =
    when (type) {
        CustomSectionSourceType.GENRE -> R.string.custom_sections_pick_genre
        CustomSectionSourceType.STUDIO -> R.string.custom_sections_pick_studio
        CustomSectionSourceType.TAG -> R.string.custom_sections_pick_tag
        CustomSectionSourceType.COLLECTION -> R.string.custom_sections_pick_collection
        CustomSectionSourceType.PLAYLIST -> R.string.custom_sections_pick_playlist
        CustomSectionSourceType.LIBRARY -> R.string.custom_sections_pick_library
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
        return monthDayLabel(month, day, locale)
    }
    val from = label(start)
    val to = label(end)
    return if (from != null && to != null) "$from – $to" else ""
}

private fun formatMonthDay(month: Int, day: Int): String = "%02d-%02d".format(Locale.US, month, day)
