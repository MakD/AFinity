package com.makd.afinity.ui.admin.users

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.makd.afinity.ui.admin.components.AdminDiscardDialog
import com.makd.afinity.ui.admin.components.AdminLoadContent
import com.makd.afinity.ui.admin.components.AdminRemoveButton
import com.makd.afinity.ui.admin.components.AdminScaffold
import com.makd.afinity.ui.admin.components.ReportUnsavedChanges
import com.makd.afinity.ui.components.AfinityTextField
import com.makd.afinity.ui.components.SettingsDivider
import com.makd.afinity.ui.components.SettingsGroup
import com.makd.afinity.ui.components.SettingsItem
import com.makd.afinity.ui.components.SettingsSwitchItem
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

private const val HALF_HOUR = 0.5
private const val HOURS_PER_DAY = 24
private const val MINUTES_PER_HALF_HOUR = 30
private const val DEFAULT_START_HOUR = 8.0
private const val DEFAULT_END_HOUR = 20.0

private data class UnratedType(val value: String, @param:StringRes val label: Int)

private val UNRATED_TYPES =
    listOf(
        UnratedType("Book", R.string.parental_unrated_books),
        UnratedType("ChannelContent", R.string.parental_unrated_channels),
        UnratedType("LiveTvChannel", R.string.parental_unrated_live_tv),
        UnratedType("Movie", R.string.parental_unrated_movies),
        UnratedType("Music", R.string.parental_unrated_music),
        UnratedType("Trailer", R.string.parental_unrated_trailers),
        UnratedType("Series", R.string.parental_unrated_shows),
    )

private val SCHEDULE_DAYS =
    listOf(
        "Everyday",
        "Weekday",
        "Weekend",
        "Monday",
        "Tuesday",
        "Wednesday",
        "Thursday",
        "Friday",
        "Saturday",
        "Sunday",
    )

private val START_HOURS = List(HOURS_PER_DAY * 2) { it * HALF_HOUR }
private val END_HOURS = List(HOURS_PER_DAY * 2) { (it + 1) * HALF_HOUR }

private enum class TagList {
    ALLOWED,
    BLOCKED,
}

@Composable
fun UserParentalScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: UserParentalViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val commandFailedMessage = stringResource(R.string.session_command_failed)

    LaunchedEffect(Unit) {
        viewModel.commandError.collect { snackbarHostState.showSnackbar(commandFailedMessage) }
    }

    UserParentalContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBackClick = onBackClick,
        onRetry = viewModel::load,
        onSetMaxRating = viewModel::setMaxRating,
        onSetUnratedBlocked = viewModel::setUnratedBlocked,
        onAddAllowedTag = viewModel::addAllowedTag,
        onRemoveAllowedTag = viewModel::removeAllowedTag,
        onAddBlockedTag = viewModel::addBlockedTag,
        onRemoveBlockedTag = viewModel::removeBlockedTag,
        onAddSchedule = viewModel::addSchedule,
        onRemoveSchedule = viewModel::removeSchedule,
        onSave = viewModel::save,
        modifier = modifier,
    )
}

@Composable
private fun UserParentalContent(
    state: UserParentalUiState,
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit,
    onRetry: () -> Unit,
    onSetMaxRating: (RatingOption?) -> Unit,
    onSetUnratedBlocked: (type: String, blocked: Boolean) -> Unit,
    onAddAllowedTag: (String) -> Unit,
    onRemoveAllowedTag: (String) -> Unit,
    onAddBlockedTag: (String) -> Unit,
    onRemoveBlockedTag: (String) -> Unit,
    onAddSchedule: (AccessWindow) -> Unit,
    onRemoveSchedule: (AccessWindow) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    var addTagTo by rememberSaveable { mutableStateOf<TagList?>(null) }
    var showAddSchedule by rememberSaveable { mutableStateOf(false) }
    val requestClose: () -> Unit = { if (state.isDirty) showDiscard = true else onBackClick() }

    BackHandler(enabled = state.isDirty) { showDiscard = true }
    ReportUnsavedChanges(state.isDirty)

    if (showDiscard) {
        AdminDiscardDialog(
            message = stringResource(R.string.user_permissions_discard_message),
            onDiscard = {
                showDiscard = false
                onBackClick()
            },
            onDismiss = { showDiscard = false },
        )
    }

    addTagTo?.let { target ->
        AddTagDialog(
            onConfirm = { tag ->
                if (target == TagList.ALLOWED) onAddAllowedTag(tag) else onAddBlockedTag(tag)
                addTagTo = null
            },
            onDismiss = { addTagTo = null },
        )
    }

    if (showAddSchedule) {
        AddScheduleDialog(
            onConfirm = { window ->
                onAddSchedule(window)
                showAddSchedule = false
            },
            onDismiss = { showAddSchedule = false },
        )
    }

    AdminScaffold(
        title = state.userName.ifEmpty { stringResource(R.string.title_user_parental) },
        onBackClick = requestClose,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        floatingActionButton = {
            if (state.isDirty) SaveButton(saving = state.isSaving, onSave = onSave)
        },
    ) { contentPadding ->
        AdminLoadContent(
            data = state.policy,
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
                SettingsGroup(title = stringResource(R.string.section_parental_rating)) {
                    MaxRatingRow(state = state, enabled = editable, onSelect = onSetMaxRating)
                }

                SettingsGroup(title = stringResource(R.string.section_parental_unrated)) {
                    val blocked = state.blockedUnrated
                    UNRATED_TYPES.forEachIndexed { index, type ->
                        if (index > 0) SettingsDivider()
                        SettingsSwitchItem(
                            title = stringResource(type.label),
                            checked = type.value in blocked,
                            onCheckedChange = { onSetUnratedBlocked(type.value, it) },
                            enabled = editable,
                        )
                    }
                }

                TagGroup(
                    title = stringResource(R.string.section_parental_allowed_tags),
                    help = stringResource(R.string.parental_allowed_tags_help),
                    tags = state.allowedTags,
                    enabled = editable,
                    onAdd = { addTagTo = TagList.ALLOWED },
                    onRemove = onRemoveAllowedTag,
                )

                TagGroup(
                    title = stringResource(R.string.section_parental_blocked_tags),
                    help = stringResource(R.string.parental_blocked_tags_help),
                    tags = state.blockedTags,
                    enabled = editable,
                    onAdd = { addTagTo = TagList.BLOCKED },
                    onRemove = onRemoveBlockedTag,
                )

                if (!state.isAdministrator) {
                    SettingsGroup(title = stringResource(R.string.section_parental_schedule)) {
                        SettingsItem(
                            title = stringResource(R.string.parental_add_schedule),
                            subtitle = stringResource(R.string.parental_schedule_help),
                            icon = painterResource(id = R.drawable.ic_add),
                            onClick = { showAddSchedule = true },
                            enabled = editable,
                        )
                        state.schedules.forEach { window ->
                            SettingsDivider()
                            RemovableRow(
                                title = dayLabel(window.day),
                                subtitle =
                                    stringResource(
                                        R.string.parental_schedule_fmt,
                                        formatHour(window.startHour),
                                        formatHour(window.endHour),
                                    ),
                                enabled = editable,
                                onRemove = { onRemoveSchedule(window) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MaxRatingRow(
    state: UserParentalUiState,
    enabled: Boolean,
    onSelect: (RatingOption?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected =
        state.maxRating?.name
            ?: state.maxRatingScore?.toString()
            ?: stringResource(R.string.parental_max_rating_none)

    Box {
        SettingsItem(
            title = stringResource(R.string.parental_max_rating),
            subtitle = selected,
            onClick = { expanded = true },
            enabled = enabled,
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.parental_max_rating_none)) },
                onClick = {
                    expanded = false
                    onSelect(null)
                },
            )
            state.ratings.forEach { rating ->
                DropdownMenuItem(
                    text = { Text(rating.name) },
                    onClick = {
                        expanded = false
                        onSelect(rating)
                    },
                )
            }
        }
    }
}

@Composable
private fun TagGroup(
    title: String,
    help: String,
    tags: List<String>,
    enabled: Boolean,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    SettingsGroup(title = title) {
        SettingsItem(
            title = stringResource(R.string.parental_add_tag),
            subtitle = help,
            icon = painterResource(id = R.drawable.ic_add),
            onClick = onAdd,
            enabled = enabled,
        )
        tags.forEach { tag ->
            SettingsDivider()
            RemovableRow(
                title = tag,
                subtitle = null,
                enabled = enabled,
                onRemove = { onRemove(tag) },
            )
        }
    }
}

@Composable
private fun RemovableRow(
    title: String,
    subtitle: String?,
    enabled: Boolean,
    onRemove: () -> Unit,
) {
    SettingsItem(
        title = title,
        subtitle = subtitle,
        enabled = enabled,
        trailing = {
            AdminRemoveButton(
                contentDescription = stringResource(R.string.action_remove),
                onClick = onRemove,
                enabled = enabled,
            )
        },
    )
}

@Composable
private fun AddTagDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var tag by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.parental_add_tag),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            AfinityTextField(
                value = tag,
                onValueChange = { tag = it },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.parental_tag_label),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(tag) }, enabled = tag.isNotBlank()) {
                Text(stringResource(R.string.action_add))
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
private fun AddScheduleDialog(onConfirm: (AccessWindow) -> Unit, onDismiss: () -> Unit) {
    var day by rememberSaveable { mutableStateOf(SCHEDULE_DAYS.first()) }
    var startHour by rememberSaveable { mutableDoubleStateOf(DEFAULT_START_HOUR) }
    var endHour by rememberSaveable { mutableDoubleStateOf(DEFAULT_END_HOUR) }
    val isValid = startHour < endHour

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.parental_add_schedule),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column {
                PickerRow(
                    title = stringResource(R.string.parental_schedule_day),
                    selected = dayLabel(day),
                    options = SCHEDULE_DAYS,
                    optionLabel = { dayLabel(it) },
                    onSelect = { day = it },
                )
                PickerRow(
                    title = stringResource(R.string.parental_schedule_start),
                    selected = formatHour(startHour),
                    options = START_HOURS,
                    optionLabel = { formatHour(it) },
                    onSelect = { startHour = it },
                )
                PickerRow(
                    title = stringResource(R.string.parental_schedule_end),
                    selected = formatHour(endHour),
                    options = END_HOURS,
                    optionLabel = { formatHour(it) },
                    onSelect = { endHour = it },
                )
                if (!isValid) {
                    Text(
                        text = stringResource(R.string.parental_schedule_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(AccessWindow(day, startHour, endHour)) },
                enabled = isValid,
            ) {
                Text(stringResource(R.string.action_add))
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
private fun <T> PickerRow(
    title: String,
    selected: String,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        SettingsItem(title = title, subtitle = selected, onClick = { expanded = true })
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun dayLabel(day: String): String =
    when (day) {
        "Everyday" -> stringResource(R.string.parental_day_everyday)
        "Weekday" -> stringResource(R.string.parental_day_weekday)
        "Weekend" -> stringResource(R.string.parental_day_weekend)
        else ->
            runCatching { DayOfWeek.valueOf(day.uppercase()) }
                .getOrNull()
                ?.getDisplayName(TextStyle.FULL, Locale.getDefault()) ?: day
    }

private fun formatHour(hour: Double): String {
    val wholeHours = hour.toInt()
    val minutes = if (hour - wholeHours >= HALF_HOUR) MINUTES_PER_HALF_HOUR else 0
    val time = LocalTime.of(wholeHours % HOURS_PER_DAY, minutes)
    return time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
}

@Composable
private fun SaveButton(saving: Boolean, onSave: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = { if (!saving) onSave() },
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
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
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
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
