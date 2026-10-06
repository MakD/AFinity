package com.makd.afinity.ui.admin.metadata

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.data.models.admin.EditableItem
import com.makd.afinity.data.models.admin.EditablePerson
import com.makd.afinity.navigation.LocalPlayerOffset
import com.makd.afinity.ui.components.AFinitySnackbar
import com.makd.afinity.ui.components.AfinitySwitch
import com.makd.afinity.ui.components.AfinityTextField
import com.makd.afinity.ui.components.FullScreenError

private const val DEFAULT_PERSON_TYPE = "Actor"
private const val NEW_PERSON = -1
private val CardShape = RoundedCornerShape(16.dp)
private val PERSON_TYPES =
    listOf(
        DEFAULT_PERSON_TYPE,
        "GuestStar",
        "Director",
        "Writer",
        "Producer",
        "Composer",
        "Creator",
    )
private val SERIES_STATUSES = listOf("Continuing", "Ended", "Unreleased")
private val DISPLAY_ORDERS =
    listOf(
        "",
        "originalAirDate",
        "absolute",
        "dvd",
        "digital",
        "storyArc",
        "production",
        "tv",
        "alternate",
        "regional",
        "altdvd",
    )
private val LOCKABLE_FIELDS =
    listOf(
        "Name",
        "Overview",
        "Genres",
        "Tags",
        "Studios",
        "Cast",
        "OfficialRating",
        "Runtime",
        "ProductionLocations",
    )
private val FOLDER_TYPES = setOf("Series", "Season", "BoxSet")

@Composable
private fun personTypeLabel(type: String): String =
    when (type) {
        "Actor" -> stringResource(R.string.admin_person_type_actor)
        "GuestStar" -> stringResource(R.string.admin_person_type_guest_star)
        "Director" -> stringResource(R.string.admin_person_type_director)
        "Writer" -> stringResource(R.string.admin_person_type_writer)
        "Producer" -> stringResource(R.string.admin_person_type_producer)
        "Composer" -> stringResource(R.string.admin_person_type_composer)
        "Creator" -> stringResource(R.string.admin_person_type_creator)
        else -> type
    }

@Composable
private fun seriesStatusLabel(status: String?): String =
    when (status) {
        null,
        "" -> stringResource(R.string.admin_option_not_set)

        "Continuing" -> stringResource(R.string.series_status_continuing)
        "Ended" -> stringResource(R.string.series_status_ended)
        "Unreleased" -> stringResource(R.string.series_status_unreleased)
        else -> status
    }

@Composable
private fun displayOrderLabel(order: String?): String =
    when (order?.lowercase()) {
        null,
        "" -> stringResource(R.string.admin_display_order_aired)

        "originalairdate" -> stringResource(R.string.admin_display_order_original_air_date)
        "absolute" -> stringResource(R.string.admin_display_order_absolute)
        "dvd" -> stringResource(R.string.admin_display_order_dvd)
        "digital" -> stringResource(R.string.admin_display_order_digital)
        "storyarc" -> stringResource(R.string.admin_display_order_story_arc)
        "production" -> stringResource(R.string.admin_display_order_production)
        "tv" -> stringResource(R.string.admin_display_order_tv)
        "alternate" -> stringResource(R.string.admin_display_order_alternate)
        "regional" -> stringResource(R.string.admin_display_order_regional)
        "altdvd" -> stringResource(R.string.admin_display_order_alt_dvd)
        else -> order.orEmpty()
    }

@Composable
private fun lockedFieldLabel(field: String): String =
    when (field) {
        "Name" -> stringResource(R.string.admin_field_title)
        "Overview" -> stringResource(R.string.admin_field_overview)
        "Genres" -> stringResource(R.string.admin_section_genres)
        "Tags" -> stringResource(R.string.admin_section_tags)
        "Studios" -> stringResource(R.string.admin_section_studios)
        "Cast" -> stringResource(R.string.admin_section_cast)
        "OfficialRating" -> stringResource(R.string.admin_field_rating)
        "Runtime" -> stringResource(R.string.discover_filter_runtime)
        "ProductionLocations" -> stringResource(R.string.admin_locked_field_production_locations)
        else -> field
    }

@Composable
private fun metadataChipColors() =
    FilterChipDefaults.filterChipColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditMetadataScreen(
    onNavigateUp: () -> Unit,
    onSaveSuccess: () -> Unit = onNavigateUp,
    viewModel: EditMetadataViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val currentOnSaveSuccess by rememberUpdatedState(onSaveSuccess)
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val canLeave = !uiState.isDirty || uiState.saved
    val requestClose: () -> Unit = { if (canLeave) onNavigateUp() else showDiscardDialog = true }

    BackHandler(enabled = !canLeave) { showDiscardDialog = true }

    LaunchedEffect(uiState.saved) { if (uiState.saved) currentOnSaveSuccess() }

    val message = uiState.message
    val messageText = message?.let { stringResource(it.textRes) }
    LaunchedEffect(message) {
        if (message != null && messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.consumeMessage()
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = {
                Text(
                    stringResource(R.string.admin_metadata_discard_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            },
            text = {
                Text(
                    stringResource(R.string.admin_metadata_discard_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onNavigateUp()
                    },
                    colors =
                        ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                ) {
                    Text(stringResource(R.string.admin_metadata_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.admin_metadata_keep_editing))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.admin_edit_metadata_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = requestClose) {
                        Icon(
                            painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.action_close),
                        )
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
            if (uiState.isDirty && !uiState.saved) {
                ExtendedFloatingActionButton(
                    onClick = { if (!uiState.saving) viewModel.save() },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = CardShape,
                    modifier = Modifier.padding(bottom = LocalPlayerOffset.current),
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
                        } else {
                            Icon(
                                painterResource(R.drawable.ic_check),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Text(
                            if (uiState.saving) stringResource(R.string.admin_saving)
                            else stringResource(R.string.admin_save_changes)
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState, snackbar = { AFinitySnackbar(it) }) },
    ) { padding ->
        val item = uiState.edited
        when {
            uiState.loading ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }

            uiState.loadFailed || item == null ->
                FullScreenError(
                    message = stringResource(R.string.admin_metadata_load_failed),
                    modifier = Modifier.padding(padding),
                    actionText = stringResource(R.string.action_retry),
                    onActionClick = viewModel::loadItem,
                )

            else ->
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
                            )
                            .forEachIndexed { index, title ->
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
                                    unselectedContentColor =
                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                    }
                    when (selectedTab) {
                        0 -> GeneralTab(item = item, uiState = uiState, viewModel = viewModel)
                        1 -> PeopleTab(item = item, viewModel = viewModel)
                        2 -> AdvancedTab(item = item, uiState = uiState, viewModel = viewModel)
                    }
                }
        }
    }
}

@Composable
private fun GeneralTab(
    item: EditableItem,
    uiState: EditMetadataUiState,
    viewModel: EditMetadataViewModel,
) {
    Column(
        modifier =
            Modifier.fillMaxSize()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AfinityTextField(
            value = item.name,
            onValueChange = viewModel::updateName,
            label = stringResource(R.string.admin_field_title),
            modifier = Modifier.fillMaxWidth(),
            isError = uiState.titleMissing,
            supportingText =
                if (uiState.titleMissing) {
                    stringResource(R.string.admin_metadata_title_required)
                } else null,
        )
        AfinityTextField(
            value = item.originalTitle ?: "",
            onValueChange = viewModel::updateOriginalTitle,
            label = stringResource(R.string.admin_field_original_title),
            modifier = Modifier.fillMaxWidth(),
        )
        AfinityTextField(
            value = item.overview ?: "",
            onValueChange = viewModel::updateOverview,
            label = stringResource(R.string.admin_field_overview),
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            minLines = 4,
            maxLines = 8,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AfinityTextField(
                value = uiState.yearText,
                onValueChange = viewModel::updateYear,
                label = stringResource(R.string.admin_field_year),
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            SuggestionField(
                value = item.officialRating ?: "",
                onValueChange = viewModel::updateOfficialRating,
                label = stringResource(R.string.admin_field_rating),
                suggestions = item.availableParentalRatings,
                modifier = Modifier.weight(1f),
            )
        }

        ChipSection(
            title = stringResource(R.string.admin_section_genres),
            field = ChipField.GENRES,
            chips = item.genres,
            uiState = uiState,
            viewModel = viewModel,
        )
        ChipSection(
            title = stringResource(R.string.admin_section_tags),
            field = ChipField.TAGS,
            chips = item.tags,
            uiState = uiState,
            viewModel = viewModel,
        )
        ChipSection(
            title = stringResource(R.string.admin_section_studios),
            field = ChipField.STUDIOS,
            chips = item.studios,
            uiState = uiState,
            viewModel = viewModel,
        )

        Spacer(modifier = Modifier.height(88.dp + LocalPlayerOffset.current))
    }
}

@Composable
private fun ChipSection(
    title: String,
    field: ChipField,
    chips: List<String>,
    uiState: EditMetadataUiState,
    viewModel: EditMetadataViewModel,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SectionHeader(title)
        ChipGroup(
            chips = chips,
            pendingText = uiState.pendingChips[field].orEmpty(),
            onPendingChange = { viewModel.updatePendingChip(field, it) },
            onAdd = { viewModel.commitChip(field) },
            onRemove = { viewModel.removeChip(field, it) },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipGroup(
    chips: List<String>,
    pendingText: String,
    onPendingChange: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    val showField = adding || pendingText.isNotEmpty()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(adding) { if (adding) focusRequester.requestFocus() }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_close),
                                contentDescription = stringResource(R.string.cd_admin_remove),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    },
                )
            }
            if (!showField) {
                FilterChip(
                    selected = false,
                    onClick = { adding = true },
                    label = { Text(stringResource(R.string.cd_admin_add)) },
                    leadingIcon = {
                        Icon(
                            painterResource(R.drawable.ic_add),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    shape = CircleShape,
                    border = null,
                    colors =
                        FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            labelColor = MaterialTheme.colorScheme.primary,
                            iconColor = MaterialTheme.colorScheme.primary,
                        ),
                )
            }
        }

        if (showField) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                AfinityTextField(
                    value = pendingText,
                    onValueChange = onPendingChange,
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    placeholder = stringResource(R.string.admin_add_item_placeholder),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onAdd() }),
                )
                IconButton(onClick = onAdd, enabled = pendingText.isNotBlank()) {
                    Icon(
                        painterResource(R.drawable.ic_check),
                        contentDescription = stringResource(R.string.cd_admin_add),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(
                    onClick = {
                        onPendingChange("")
                        adding = false
                    }
                ) {
                    Icon(
                        painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.action_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PeopleTab(item: EditableItem, viewModel: EditMetadataViewModel) {
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    editingIndex?.let { index ->
        val person = item.people.getOrNull(index)
        PersonSheet(
            person = person,
            onDone = { updated ->
                if (person == null) viewModel.addPerson(updated)
                else viewModel.updatePerson(index, updated)
                editingIndex = null
            },
            onRemove =
                if (person != null) {
                    {
                        viewModel.removePerson(index)
                        editingIndex = null
                    }
                } else null,
            onDismiss = { editingIndex = null },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 88.dp + LocalPlayerOffset.current,
            ),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.admin_section_cast),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = item.people.size.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { editingIndex = NEW_PERSON }) {
                    Icon(
                        painterResource(R.drawable.ic_add),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.admin_add_person))
                }
            }
        }

        itemsIndexed(item.people) { index, person ->
            val last = item.people.lastIndex
            val shape: Shape =
                when {
                    last == 0 -> CardShape
                    index == 0 -> RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    index == last -> RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                    else -> RectangleShape
                }
            PersonRow(
                person = person,
                shape = shape,
                showDivider = index > 0,
                onClick = { editingIndex = index },
            )
        }
    }
}

@Composable
private fun PersonRow(
    person: EditablePerson,
    shape: Shape,
    showDivider: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable(onClick = onClick)
    ) {
        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f),
            )
        }
        Row(
            modifier =
                Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier =
                    Modifier.size(40.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = person.name.trim().take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = person.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        listOfNotNull(
                                personTypeLabel(person.type),
                                person.role?.takeIf { it.isNotBlank() },
                            )
                            .joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                painterResource(R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PersonSheet(
    person: EditablePerson?,
    onDone: (EditablePerson) -> Unit,
    onRemove: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(person?.name.orEmpty()) }
    var type by remember { mutableStateOf(person?.type ?: DEFAULT_PERSON_TYPE) }
    var role by remember { mutableStateOf(person?.role.orEmpty()) }
    val types = remember(person) { (PERSON_TYPES + listOfNotNull(person?.type)).distinct() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AfinityTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.admin_field_person_name),
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = stringResource(R.string.admin_field_person_type),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                types.forEach { option ->
                    FilterChip(
                        selected = option == type,
                        onClick = { type = option },
                        label = { Text(personTypeLabel(option)) },
                        leadingIcon =
                            if (option == type) {
                                {
                                    Icon(
                                        painterResource(R.drawable.ic_check),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            } else null,
                        shape = CircleShape,
                        border = null,
                        colors =
                            FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedLeadingIconColor =
                                    MaterialTheme.colorScheme.onPrimaryContainer,
                            ),
                    )
                }
            }

            AfinityTextField(
                value = role,
                onValueChange = { role = it },
                label = stringResource(R.string.admin_field_person_role),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (onRemove != null) {
                    TextButton(
                        onClick = onRemove,
                        colors =
                            ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                        modifier = Modifier.height(56.dp),
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_delete),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.action_remove))
                    }
                }
                Button(
                    onClick = {
                        onDone(
                            EditablePerson(
                                id = person?.id,
                                name = name.trim(),
                                type = type,
                                role = role.trim().ifBlank { null },
                            )
                        )
                    },
                    enabled = name.isNotBlank(),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Text(stringResource(R.string.admin_person_done))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AdvancedTab(
    item: EditableItem,
    uiState: EditMetadataUiState,
    viewModel: EditMetadataViewModel,
) {
    Column(
        modifier =
            Modifier.fillMaxSize()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AfinityTextField(
                value = uiState.communityRatingText,
                onValueChange = viewModel::updateCommunityRating,
                label = stringResource(R.string.admin_field_community_rating),
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            AfinityTextField(
                value = item.customRating ?: "",
                onValueChange = viewModel::updateCustomRating,
                label = stringResource(R.string.admin_field_custom_rating),
                modifier = Modifier.weight(1f),
            )
        }

        if (item.type == "Series") {
            val statusEntries =
                listOf<Pair<String, String?>>(seriesStatusLabel(null) to null) +
                    SERIES_STATUSES.map { seriesStatusLabel(it) to it }
            DropdownField(
                label = stringResource(R.string.admin_field_series_status),
                valueLabel = seriesStatusLabel(item.status),
                entries = statusEntries,
                isSelected = { it.orEmpty() == item.status.orEmpty() },
                onSelect = viewModel::updateStatus,
                modifier = Modifier.fillMaxWidth(),
            )

            val currentOrder = item.displayOrder.orEmpty()
            val orders =
                if (DISPLAY_ORDERS.any { it.equals(currentOrder, ignoreCase = true) }) {
                    DISPLAY_ORDERS
                } else DISPLAY_ORDERS + currentOrder
            val orderEntries = orders.map { displayOrderLabel(it) to it }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DropdownField(
                    label = stringResource(R.string.admin_field_display_order),
                    valueLabel = displayOrderLabel(currentOrder),
                    entries = orderEntries,
                    isSelected = { it.equals(currentOrder, ignoreCase = true) },
                    onSelect = viewModel::updateDisplayOrder,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.admin_display_order_changed_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color =
                        if (uiState.displayOrderChanged) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }

        SwitchRowSimple(
            label = stringResource(R.string.admin_lock_data),
            hint =
                if (item.type in FOLDER_TYPES) {
                    stringResource(R.string.admin_lock_children_hint)
                } else null,
            checked = item.lockData,
            onToggle = { viewModel.toggleLockData() },
        )

        if (!item.lockData) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SectionHeader(stringResource(R.string.admin_section_locked_fields))
                Text(
                    text = stringResource(R.string.admin_locked_fields_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                LOCKABLE_FIELDS.forEach { field ->
                    val selected = field in item.lockedFields
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.toggleLockedField(field) },
                        label = { Text(lockedFieldLabel(field)) },
                        leadingIcon =
                            if (selected) {
                                {
                                    Icon(
                                        painterResource(R.drawable.ic_check),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            } else null,
                        shape = CircleShape,
                        border = null,
                        colors = metadataChipColors(),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(88.dp + LocalPlayerOffset.current))
    }
}

@Composable
private fun <T> DropdownField(
    label: String,
    valueLabel: String,
    entries: List<Pair<String, T>>,
    isSelected: (T) -> Boolean,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        AfinityTextField(
            value = valueLabel,
            onValueChange = {},
            label = label,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                Icon(
                    painterResource(R.drawable.ic_keyboard_arrow_down),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        Box(
            modifier =
                Modifier.matchParentSize().clip(RoundedCornerShape(14.dp)).clickable {
                    expanded = true
                }
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
                                    painterResource(R.drawable.ic_check),
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
private fun SuggestionField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suggestions: List<String>,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val options =
        remember(suggestions, value) {
            (listOf(value) + suggestions).filter { it.isNotBlank() }.distinct()
        }
    Box(modifier = modifier) {
        AfinityTextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon =
                if (suggestions.isNotEmpty()) {
                    {
                        IconButton(onClick = { expanded = true }) {
                            Icon(
                                painterResource(R.drawable.ic_keyboard_arrow_down),
                                contentDescription = label,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else null,
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = option) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    },
                    leadingIcon =
                        if (option == value) {
                            {
                                Icon(
                                    painterResource(R.drawable.ic_check),
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
private fun SwitchRowSimple(
    label: String,
    hint: String?,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clip(CardShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable { onToggle() }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (hint != null) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AfinitySwitch(checked = checked, onCheckedChange = { onToggle() })
    }
}
