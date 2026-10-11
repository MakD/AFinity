package com.makd.afinity.ui.admin.tasks

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.ui.admin.components.AdminLoadContent
import com.makd.afinity.ui.admin.components.AdminRemoveButton
import com.makd.afinity.ui.admin.components.AdminScaffold
import com.makd.afinity.ui.components.AfinityTextField
import com.makd.afinity.ui.components.FullScreenEmpty
import com.makd.afinity.ui.components.SettingsDivider
import com.makd.afinity.ui.components.SettingsGroup
import com.makd.afinity.ui.components.SettingsItem
import com.makd.afinity.ui.settings.servers.utils.formatLastRun
import org.jellyfin.sdk.model.api.DayOfWeek
import org.jellyfin.sdk.model.api.TaskCompletionStatus
import org.jellyfin.sdk.model.api.TaskInfo
import org.jellyfin.sdk.model.api.TaskResult
import org.jellyfin.sdk.model.api.TaskState
import org.jellyfin.sdk.model.api.TaskTriggerInfo
import org.jellyfin.sdk.model.api.TaskTriggerInfoType

private const val DEFAULT_TRIGGER_HOUR = 3
private const val DEFAULT_INTERVAL_MINUTES = 24 * 60
private const val MAX_TIME_LIMIT_HOURS = 24

private val INTERVAL_OPTIONS_MINUTES =
    listOf(15, 30, 45, 60, 2 * 60, 3 * 60, 4 * 60, 6 * 60, 8 * 60, 12 * 60, 24 * 60)

private val TRIGGER_TYPES =
    listOf(
        TaskTriggerInfoType.DAILY_TRIGGER,
        TaskTriggerInfoType.WEEKLY_TRIGGER,
        TaskTriggerInfoType.INTERVAL_TRIGGER,
        TaskTriggerInfoType.STARTUP_TRIGGER,
    )

private val WEEK_DAYS =
    listOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
        DayOfWeek.SATURDAY,
        DayOfWeek.SUNDAY,
    )

@Composable
fun TaskDetailScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: TaskDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val commandFailedMessage = stringResource(R.string.session_command_failed)

    LaunchedEffect(Unit) {
        viewModel.commandError.collect { snackbarHostState.showSnackbar(commandFailedMessage) }
    }

    TaskDetailContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBackClick = onBackClick,
        onRetry = viewModel::retry,
        onRun = viewModel::run,
        onStop = viewModel::stop,
        onAddTrigger = viewModel::addTrigger,
        onRemoveTrigger = viewModel::removeTrigger,
        modifier = modifier,
    )
}

@Composable
private fun TaskDetailContent(
    state: TaskDetailUiState,
    snackbarHostState: SnackbarHostState,
    onBackClick: () -> Unit,
    onRetry: () -> Unit,
    onRun: () -> Unit,
    onStop: () -> Unit,
    onAddTrigger: (TaskTriggerInfo) -> Unit,
    onRemoveTrigger: (TaskTriggerInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddTrigger by rememberSaveable { mutableStateOf(false) }

    if (showAddTrigger) {
        AddTriggerSheet(
            onAdd = { trigger ->
                onAddTrigger(trigger)
                showAddTrigger = false
            },
            onDismiss = { showAddTrigger = false },
        )
    }

    AdminScaffold(
        title = state.task?.name ?: stringResource(R.string.title_scheduled_tasks),
        onBackClick = onBackClick,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) { contentPadding ->
        if (state.missing) {
            FullScreenEmpty(
                message = stringResource(R.string.task_not_found),
                modifier = Modifier.padding(contentPadding),
            )
        } else {
            AdminLoadContent(
                data = state.task,
                failed = state.failed,
                onRetry = onRetry,
                contentPadding = contentPadding,
            ) { task ->
                Column(
                    modifier =
                        Modifier.fillMaxSize()
                            .padding(contentPadding)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    val description = task.description.orEmpty()
                    if (description.isNotBlank()) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                    TaskStatusGroup(task = task)
                    RunButton(
                        task = task,
                        isPending = state.isRunCommandPending,
                        onRun = onRun,
                        onStop = onStop,
                    )
                    TriggersGroup(
                        triggers = task.triggers.orEmpty(),
                        enabled = !state.isSavingTriggers,
                        onAdd = { showAddTrigger = true },
                        onRemove = onRemoveTrigger,
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskStatusGroup(task: TaskInfo) {
    val isRunning = task.state == TaskState.RUNNING
    val isCancelling = task.state == TaskState.CANCELLING
    val result = task.lastExecutionResult

    SettingsGroup(
        title = if (isRunning || isCancelling) null else stringResource(R.string.task_last_run)
    ) {
        when {
            isRunning || isCancelling -> {
                val progress = task.currentProgressPercentage
                SettingsItem(
                    icon = painterResource(id = R.drawable.ic_refresh),
                    title =
                        if (isCancelling) stringResource(R.string.task_stopping)
                        else stringResource(R.string.category_running),
                    subtitle = progress?.let { "${"%.1f".format(it)}%" },
                )
                val progressModifier =
                    Modifier.fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                        .height(4.dp)
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { (progress / 100.0).toFloat() },
                        modifier = progressModifier,
                    )
                } else {
                    LinearProgressIndicator(modifier = progressModifier)
                }
            }
            result == null ->
                SettingsItem(
                    icon = painterResource(id = R.drawable.ic_history),
                    title = stringResource(R.string.task_never_run),
                )
            else -> LastRunRows(result = result)
        }
    }
}

@Composable
private fun LastRunRows(result: TaskResult) {
    val completed = result.status == TaskCompletionStatus.COMPLETED
    val statusLabel =
        when (result.status) {
            TaskCompletionStatus.COMPLETED -> stringResource(R.string.task_status_completed)
            TaskCompletionStatus.FAILED -> stringResource(R.string.task_status_failed)
            TaskCompletionStatus.CANCELLED -> stringResource(R.string.task_status_cancelled)
            TaskCompletionStatus.ABORTED -> stringResource(R.string.task_status_aborted)
        }
    SettingsItem(
        icon =
            painterResource(
                id = if (completed) R.drawable.ic_circle_check_outline else R.drawable.ic_info
            ),
        title = statusLabel,
        subtitle = formatLastRun(LocalContext.current, result.startTimeUtc, result.endTimeUtc),
    )
    val errorMessage = result.errorMessage?.trim().orEmpty()
    if (result.status == TaskCompletionStatus.FAILED && errorMessage.isNotEmpty()) {
        SelectionContainer {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun RunButton(task: TaskInfo, isPending: Boolean, onRun: () -> Unit, onStop: () -> Unit) {
    val isRunning = task.state == TaskState.RUNNING
    val isCancelling = task.state == TaskState.CANCELLING

    Button(
        onClick = if (isRunning) onStop else onRun,
        enabled = !isCancelling && !isPending,
        colors =
            if (isRunning) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                )
            } else {
                ButtonDefaults.buttonColors()
            },
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(56.dp),
    ) {
        Icon(
            painter =
                painterResource(
                    id = if (isRunning) R.drawable.ic_close else R.drawable.ic_player_play_filled
                ),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text =
                if (isRunning || isCancelling) stringResource(R.string.task_stop_run)
                else stringResource(R.string.task_run_now)
        )
    }
}

@Composable
private fun TriggersGroup(
    triggers: List<TaskTriggerInfo>,
    enabled: Boolean,
    onAdd: () -> Unit,
    onRemove: (TaskTriggerInfo) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsGroup(title = stringResource(R.string.task_triggers)) {
            if (triggers.isEmpty()) {
                Text(
                    text = stringResource(R.string.task_triggers_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                )
                SettingsDivider()
            }
            triggers.forEach { trigger ->
                SettingsItem(
                    icon = painterResource(id = trigger.type.iconRes()),
                    title = trigger.label(),
                    subtitle =
                        trigger.maxRuntimeTicks?.let { ticks ->
                            stringResource(
                                R.string.task_trigger_time_limit_fmt,
                                formatDurationTicks(ticks),
                            )
                        },
                    trailing = {
                        AdminRemoveButton(
                            contentDescription = stringResource(R.string.cd_remove_trigger),
                            onClick = { onRemove(trigger) },
                            enabled = enabled,
                        )
                    },
                )
                SettingsDivider()
            }
            SettingsItem(
                icon = painterResource(id = R.drawable.ic_add),
                title = stringResource(R.string.task_trigger_add),
                onClick = onAdd,
                enabled = enabled,
            )
        }
        Text(
            text = stringResource(R.string.task_triggers_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddTriggerSheet(onAdd: (TaskTriggerInfo) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var type by rememberSaveable { mutableStateOf(TaskTriggerInfoType.DAILY_TRIGGER) }
    var hour by rememberSaveable { mutableStateOf(DEFAULT_TRIGGER_HOUR) }
    var minute by rememberSaveable { mutableStateOf(0) }
    var day by rememberSaveable { mutableStateOf(DayOfWeek.MONDAY) }
    var intervalMinutes by rememberSaveable { mutableStateOf(DEFAULT_INTERVAL_MINUTES) }
    var timeLimitText by rememberSaveable { mutableStateOf("") }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }

    val timeLimitHours = timeLimitText.trim().toIntOrNull()
    val timeLimitValid =
        timeLimitText.isBlank() ||
            (timeLimitHours != null && timeLimitHours in 1..MAX_TIME_LIMIT_HOURS)
    val usesTime =
        type == TaskTriggerInfoType.DAILY_TRIGGER || type == TaskTriggerInfoType.WEEKLY_TRIGGER

    if (showTimePicker) {
        TimePickerDialog(
            initialHour = hour,
            initialMinute = minute,
            onConfirm = { pickedHour, pickedMinute ->
                hour = pickedHour
                minute = pickedMinute
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }

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
            Text(
                text = stringResource(R.string.task_trigger_add),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                TRIGGER_TYPES.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = option == type,
                        onClick = { type = option },
                        shape =
                            SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = TRIGGER_TYPES.size,
                            ),
                        icon = {},
                    ) {
                        Text(text = stringResource(option.typeLabelRes()), maxLines = 1)
                    }
                }
            }

            if (usesTime) {
                Surface(
                    onClick = { showTimePicker = true },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.task_trigger_server_time),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = formatTimeOfDay(hour, minute),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        Icon(
                            painter = painterResource(id = R.drawable.ic_clock),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (type == TaskTriggerInfoType.WEEKLY_TRIGGER) {
                ChoiceLabel(text = stringResource(R.string.task_trigger_day))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    WEEK_DAYS.forEach { option ->
                        ChoiceChip(
                            label = option.displayName(),
                            selected = option == day,
                            onClick = { day = option },
                        )
                    }
                }
            }

            if (type == TaskTriggerInfoType.INTERVAL_TRIGGER) {
                ChoiceLabel(text = stringResource(R.string.task_trigger_interval_label))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    INTERVAL_OPTIONS_MINUTES.forEach { option ->
                        ChoiceChip(
                            label = formatDurationMinutes(option.toLong()),
                            selected = option == intervalMinutes,
                            onClick = { intervalMinutes = option },
                        )
                    }
                }
            }

            AfinityTextField(
                value = timeLimitText,
                onValueChange = { timeLimitText = it },
                modifier = Modifier.fillMaxWidth(),
                label = stringResource(R.string.task_trigger_time_limit_label),
                isError = !timeLimitValid,
                supportingText = stringResource(R.string.task_trigger_time_limit_hint),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.height(56.dp)) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(
                    onClick = {
                        val timeOfDayTicks = (hour * 60L + minute) * TICKS_PER_MINUTE
                        onAdd(
                            TaskTriggerInfo(
                                type = type,
                                timeOfDayTicks = if (usesTime) timeOfDayTicks else null,
                                intervalTicks =
                                    if (type == TaskTriggerInfoType.INTERVAL_TRIGGER) {
                                        intervalMinutes * TICKS_PER_MINUTE
                                    } else {
                                        null
                                    },
                                dayOfWeek =
                                    if (type == TaskTriggerInfoType.WEEKLY_TRIGGER) day else null,
                                maxRuntimeTicks = timeLimitHours?.let { it * TICKS_PER_HOUR },
                            )
                        )
                    },
                    enabled = timeLimitValid,
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Text(stringResource(R.string.action_add))
                }
            }
        }
    }
}

@Composable
private fun ChoiceLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
    )
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val checkIcon: (@Composable () -> Unit)? =
        if (selected) {
            {
                Icon(
                    painter = painterResource(id = R.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        } else {
            null
        }
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = checkIcon,
        shape = CircleShape,
        border = null,
        colors =
            FilterChipDefaults.filterChipColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerState =
        rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = DateFormat.is24HourFormat(LocalContext.current),
        )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.task_trigger_server_time),
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = { TimePicker(state = pickerState) },
        confirmButton = {
            TextButton(onClick = { onConfirm(pickerState.hour, pickerState.minute) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

private fun TaskTriggerInfoType.typeLabelRes(): Int =
    when (this) {
        TaskTriggerInfoType.DAILY_TRIGGER -> R.string.task_trigger_type_daily
        TaskTriggerInfoType.WEEKLY_TRIGGER -> R.string.task_trigger_type_weekly
        TaskTriggerInfoType.INTERVAL_TRIGGER -> R.string.task_trigger_type_interval
        TaskTriggerInfoType.STARTUP_TRIGGER -> R.string.task_trigger_type_startup
    }
