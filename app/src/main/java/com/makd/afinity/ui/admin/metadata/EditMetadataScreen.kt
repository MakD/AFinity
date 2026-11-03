package com.makd.afinity.ui.admin.metadata

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
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
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import com.makd.afinity.data.models.admin.ExternalIdProvider
import com.makd.afinity.ui.admin.components.AdminCardShape
import com.makd.afinity.ui.admin.components.ProviderIdFieldGrid
import com.makd.afinity.ui.admin.components.adminContentBottomPadding
import com.makd.afinity.ui.components.AFinitySnackbar
import com.makd.afinity.ui.components.AfinityModalBottomSheet
import com.makd.afinity.ui.components.AfinityScreen
import com.makd.afinity.ui.components.AfinitySwitch
import com.makd.afinity.ui.components.AfinityTextField
import com.makd.afinity.ui.components.AfinityTopAppBarTitle
import com.makd.afinity.ui.components.AfinityTopBar
import com.makd.afinity.ui.components.DateFieldPickerDialog
import com.makd.afinity.ui.components.FullScreenError
import com.makd.afinity.ui.components.playerClearance
import com.makd.afinity.ui.components.rememberAfinitySheetState
import com.makd.afinity.util.DateSkeleton
import com.makd.afinity.util.localizedDateFormatter
import java.time.LocalDate

private const val DEFAULT_PERSON_TYPE = "Actor"
private const val NEW_PERSON = -1
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
private val MUSIC_TYPES = setOf("MusicAlbum", "Audio", "MusicArtist", "MusicGenre", "MusicVideo")
private val ARTIST_TYPES = setOf("Audio", "MusicVideo")
private val ALBUM_ARTIST_TYPES = setOf("Audio", "MusicAlbum", "MusicVideo")
private val ALBUM_TYPES = setOf("Audio", "MusicVideo")
private val TRACK_NUMBER_TYPES = setOf("Audio")
private const val PERSON_TYPE = "Person"
private const val SERIES_TYPE = "Series"
private val REDUCED_TYPES = setOf("MusicGenre", "Genre", "Studio", PERSON_TYPE)
private val NO_DATE_TYPES = setOf("TvChannel")
private val PERSON_LOCKABLE_FIELDS = setOf("Name", "Overview", "Tags", "ProductionLocations")

private enum class EditorTab(@param:StringRes val titleRes: Int) {
    GENERAL(R.string.admin_tab_general),
    PEOPLE(R.string.admin_tab_people),
    ADVANCED(R.string.admin_tab_advanced),
}

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
private fun lockedFieldLabel(field: String, isPerson: Boolean): String =
    when (field) {
        "Name" ->
            stringResource(
                if (isPerson) R.string.admin_field_person_name else R.string.admin_field_title
            )
        "Overview" -> stringResource(R.string.admin_field_overview)
        "Genres" -> stringResource(R.string.admin_section_genres)
        "Tags" -> stringResource(R.string.admin_section_tags)
        "Studios" -> stringResource(R.string.admin_section_studios)
        "Cast" -> stringResource(R.string.admin_section_cast)
        "OfficialRating" -> stringResource(R.string.admin_field_rating)
        "Runtime" -> stringResource(R.string.discover_filter_runtime)
        "ProductionLocations" ->
            stringResource(
                if (isPerson) R.string.admin_locked_field_birth_location
                else R.string.admin_locked_field_production_locations
            )
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

    AfinityScreen(
        topBar = { backgroundOpacity ->
            AfinityTopBar(
                title = {
                    AfinityTopAppBarTitle(stringResource(R.string.admin_edit_metadata_title))
                },
                navigationIcon = {
                    IconButton(onClick = requestClose) {
                        Icon(
                            painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.action_close),
                        )
                    }
                },
                backgroundOpacity = backgroundOpacity,
            )
        },
        floatingActionButton = {
            if (uiState.isDirty && !uiState.saved) {
                ExtendedFloatingActionButton(
                    onClick = { if (!uiState.saving) viewModel.save() },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = AdminCardShape,
                    modifier = Modifier.padding(bottom = playerClearance()),
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

            else -> {
                val tabs =
                    if (item.type in REDUCED_TYPES) {
                        listOf(EditorTab.GENERAL, EditorTab.ADVANCED)
                    } else EditorTab.entries
                val currentTab = selectedTab.coerceIn(tabs.indices)
                Column(modifier = Modifier.padding(padding)) {
                    SecondaryTabRow(
                        selectedTabIndex = currentTab,
                        containerColor = Color.Transparent,
                        divider = {},
                        indicator = {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(currentTab),
                                color = MaterialTheme.colorScheme.primary,
                                height = 3.dp,
                            )
                        },
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            Tab(
                                selected = currentTab == index,
                                onClick = { selectedTab = index },
                                text = {
                                    Text(
                                        stringResource(tab.titleRes),
                                        style =
                                            if (currentTab == index)
                                                MaterialTheme.typography.titleSmall
                                            else MaterialTheme.typography.bodyMedium,
                                    )
                                },
                                selectedContentColor = MaterialTheme.colorScheme.primary,
                                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    when (tabs[currentTab]) {
                        EditorTab.GENERAL ->
                            GeneralTab(item = item, uiState = uiState, viewModel = viewModel)
                        EditorTab.PEOPLE -> PeopleTab(item = item, viewModel = viewModel)
                        EditorTab.ADVANCED ->
                            AdvancedTab(item = item, uiState = uiState, viewModel = viewModel)
                    }
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val isPerson = item.type == PERSON_TYPE
        if (isPerson) {
            AfinityTextField(
                value = item.name,
                onValueChange = {},
                label = stringResource(R.string.admin_field_person_name),
                modifier = Modifier.fillMaxWidth(),
                enabled = false,
                supportingText = stringResource(R.string.admin_person_name_locked_hint),
                trailingIcon = {
                    Icon(
                        painterResource(R.drawable.ic_lock_filled),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
            )
        } else {
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
        }
        if (item.type !in MUSIC_TYPES) {
            AfinityTextField(
                value = item.originalTitle ?: "",
                onValueChange = viewModel::updateOriginalTitle,
                label =
                    stringResource(
                        if (isPerson) R.string.admin_field_original_name
                        else R.string.admin_field_original_title
                    ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        AfinityTextField(
            value = item.forcedSortName ?: "",
            onValueChange = viewModel::updateSortTitle,
            label =
                stringResource(
                    if (isPerson) R.string.admin_field_sort_name
                    else R.string.admin_field_sort_title
                ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (item.type in ALBUM_TYPES) {
            AfinityTextField(
                value = item.album ?: "",
                onValueChange = viewModel::updateAlbum,
                label = stringResource(R.string.admin_field_album),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (item.type in TRACK_NUMBER_TYPES) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AfinityTextField(
                    value = item.indexNumber?.toString().orEmpty(),
                    onValueChange = viewModel::updateIndexNumber,
                    label = stringResource(R.string.admin_field_track_number),
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                AfinityTextField(
                    value = item.parentIndexNumber?.toString().orEmpty(),
                    onValueChange = viewModel::updateParentIndexNumber,
                    label = stringResource(R.string.admin_field_disc_number),
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        }
        if (item.type in ARTIST_TYPES) {
            ChipSection(
                title = stringResource(R.string.admin_section_artists),
                field = ChipField.ARTISTS,
                chips = item.artists,
                uiState = uiState,
                viewModel = viewModel,
            )
        }
        if (item.type in ALBUM_ARTIST_TYPES) {
            ChipSection(
                title = stringResource(R.string.admin_section_album_artists),
                field = ChipField.ALBUM_ARTISTS,
                chips = item.albumArtists,
                uiState = uiState,
                viewModel = viewModel,
            )
        }
        AfinityTextField(
            value = item.overview ?: "",
            onValueChange = viewModel::updateOverview,
            label = stringResource(R.string.admin_field_overview),
            modifier = Modifier.fillMaxWidth(),
            singleLine = false,
            minLines = 4,
            maxLines = 8,
        )
        if (isPerson) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DateField(
                    label = stringResource(R.string.admin_field_birth_date),
                    value = item.premiereDate,
                    onPick = viewModel::updatePremiereDate,
                    clearable = true,
                    modifier = Modifier.weight(1f),
                )
                DateField(
                    label = stringResource(R.string.admin_field_death_date),
                    value = item.endDate,
                    onPick = viewModel::updateEndDate,
                    clearable = true,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AfinityTextField(
                value = uiState.yearText,
                onValueChange = viewModel::updateYear,
                label =
                    stringResource(
                        if (isPerson) R.string.admin_field_birth_year else R.string.admin_field_year
                    ),
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            if (item.type !in REDUCED_TYPES) {
                SuggestionField(
                    value = item.officialRating ?: "",
                    onValueChange = viewModel::updateOfficialRating,
                    label = stringResource(R.string.admin_field_rating),
                    suggestions = item.availableParentalRatings,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
        if (isPerson) {
            AfinityTextField(
                value = item.productionLocations.firstOrNull().orEmpty(),
                onValueChange = viewModel::updatePlaceOfBirth,
                label = stringResource(R.string.admin_field_place_of_birth),
                modifier = Modifier.fillMaxWidth(),
            )
        } else if (item.type !in NO_DATE_TYPES) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DateField(
                    label = stringResource(R.string.admin_field_release_date),
                    value = item.premiereDate,
                    onPick = viewModel::updatePremiereDate,
                    clearable = true,
                    modifier = Modifier.weight(1f),
                )
                DateField(
                    label = stringResource(R.string.admin_field_date_added),
                    value = item.dateCreated,
                    onPick = viewModel::updateDateCreated,
                    clearable = false,
                    modifier = Modifier.weight(1f),
                )
            }
            if (item.type == SERIES_TYPE) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DateField(
                        label = stringResource(R.string.admin_field_end_date),
                        value = item.endDate,
                        onPick = viewModel::updateEndDate,
                        clearable = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        if (item.type !in REDUCED_TYPES) {
            ChipSection(
                title = stringResource(R.string.admin_section_genres),
                field = ChipField.GENRES,
                chips = item.genres,
                uiState = uiState,
                viewModel = viewModel,
            )
        }
        ChipSection(
            title = stringResource(R.string.admin_section_tags),
            field = ChipField.TAGS,
            chips = item.tags,
            uiState = uiState,
            viewModel = viewModel,
        )
        if (item.type !in REDUCED_TYPES) {
            ChipSection(
                title = stringResource(R.string.admin_section_studios),
                field = ChipField.STUDIOS,
                chips = item.studios,
                uiState = uiState,
                viewModel = viewModel,
            )
        }
        if (item.type == "MusicAlbum") {
            PropagationNote(stringResource(R.string.admin_album_propagation_hint))
        }

        Spacer(
            modifier =
                Modifier.height(
                    adminContentBottomPadding(hasFloatingButton = true) + playerClearance()
                )
        )
    }
}

@Composable
private fun PropagationNote(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_info),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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
                bottom = adminContentBottomPadding(hasFloatingButton = true) + playerClearance(),
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
                    last == 0 -> AdminCardShape
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
    val sheetState = rememberAfinitySheetState()
    var name by remember { mutableStateOf(person?.name.orEmpty()) }
    var type by remember { mutableStateOf(person?.type ?: DEFAULT_PERSON_TYPE) }
    var role by remember { mutableStateOf(person?.role.orEmpty()) }
    val types = remember(person) { (PERSON_TYPES + listOfNotNull(person?.type)).distinct() }

    AfinityModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
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
                        modifier = Modifier.heightIn(min = 56.dp),
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
                    modifier = Modifier.weight(1f).heightIn(min = 56.dp),
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (item.type !in REDUCED_TYPES) {
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
        }

        if (item.externalIdProviders.isNotEmpty()) {
            ExternalIdsCard(
                providers = item.externalIdProviders,
                providerIds = item.providerIds,
                onIdChange = viewModel::updateProviderId,
            )
        }

        if (item.type == SERIES_TYPE) {
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
                val isPerson = item.type == PERSON_TYPE
                val lockable =
                    if (isPerson) LOCKABLE_FIELDS.filter { it in PERSON_LOCKABLE_FIELDS }
                    else LOCKABLE_FIELDS
                lockable.forEach { field ->
                    val selected = field in item.lockedFields
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.toggleLockedField(field) },
                        label = { Text(lockedFieldLabel(field, isPerson)) },
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

        Spacer(
            modifier =
                Modifier.height(
                    adminContentBottomPadding(hasFloatingButton = true) + playerClearance()
                )
        )
    }
}

@Composable
private fun ExternalIdsCard(
    providers: List<ExternalIdProvider>,
    providerIds: Map<String, String>,
    onIdChange: (String, String) -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(AdminCardShape)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.admin_section_external_ids),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.heightIn(min = 48.dp).padding(start = 4.dp, top = 14.dp),
        )
        Text(
            text = stringResource(R.string.admin_external_ids_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        ProviderIdFieldGrid(
            providers = providers,
            providerIds = providerIds,
            onIdChange = onIdChange,
        )
    }
}

@Composable
private fun DateField(
    label: String,
    value: String?,
    onPick: (String?) -> Unit,
    clearable: Boolean,
    modifier: Modifier = Modifier,
) {
    var picking by remember { mutableStateOf(false) }
    val locale = LocalConfiguration.current.locales[0]
    val formatter = remember(locale) { localizedDateFormatter(locale, DateSkeleton.MONTH_DAY_YEAR) }
    val text =
        remember(value, formatter) {
            value?.let { runCatching { LocalDate.parse(it).format(formatter) }.getOrNull() }
        }
    Box(modifier = modifier) {
        AfinityTextField(
            value = text.orEmpty(),
            onValueChange = {},
            label = label,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                Icon(
                    painterResource(R.drawable.ic_calendar),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        Box(
            modifier =
                Modifier.matchParentSize().clip(RoundedCornerShape(14.dp)).clickable(
                    role = Role.Button,
                    onClickLabel = label,
                ) {
                    picking = true
                }
        )
    }
    if (picking) {
        DateFieldPickerDialog(
            initialDate = value,
            onConfirm = { picked ->
                if (picked != null || clearable) onPick(picked)
                picking = false
            },
            onDismiss = { picking = false },
            onClear =
                if (clearable && value != null) {
                    {
                        onPick(null)
                        picking = false
                    }
                } else null,
        )
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
                .clip(AdminCardShape)
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
