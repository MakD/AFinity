package com.makd.afinity.ui.admin.tasks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.ui.admin.components.AdminLoadContent
import com.makd.afinity.ui.admin.components.AdminScaffold
import com.makd.afinity.ui.components.FullScreenEmpty
import com.makd.afinity.ui.components.SettingsDivider
import com.makd.afinity.ui.components.SettingsGroup
import com.makd.afinity.ui.components.SettingsItem
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.api.TaskCompletionStatus
import org.jellyfin.sdk.model.api.TaskInfo
import org.jellyfin.sdk.model.api.TaskState
import org.jellyfin.sdk.model.api.TaskTriggerInfo

private const val DISABLED_ALPHA = 0.38f

private val TriggerSaver =
    Saver<TaskTriggerInfo?, String>(
        save = { trigger ->
            trigger?.let { Json.encodeToString(TaskTriggerInfo.serializer(), it) }
        },
        restore = { saved -> Json.decodeFromString(TaskTriggerInfo.serializer(), saved) },
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
        onReplaceTrigger = viewModel::replaceTrigger,
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
    onReplaceTrigger: (TaskTriggerInfo, TaskTriggerInfo) -> Unit,
    onRemoveTrigger: (TaskTriggerInfo) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showScheduleDialog by rememberSaveable { mutableStateOf(false) }
    var editedTrigger by
        rememberSaveable(stateSaver = TriggerSaver) { mutableStateOf<TaskTriggerInfo?>(null) }

    if (showScheduleDialog) {
        val editing = editedTrigger
        val closeDialog = {
            showScheduleDialog = false
            editedTrigger = null
        }
        TaskScheduleDialog(
            initial = editing,
            onSave = { trigger ->
                if (editing == null) onAddTrigger(trigger) else onReplaceTrigger(editing, trigger)
                closeDialog()
            },
            onDismiss = closeDialog,
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
                Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
                    Column(
                        modifier =
                            Modifier.weight(1f)
                                .fillMaxWidth()
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
                                modifier = Modifier.padding(horizontal = 32.dp),
                            )
                        }
                        LastRunGroup(task = task)
                        ScheduleGroup(
                            triggers = task.triggers.orEmpty(),
                            enabled = !state.isSavingTriggers,
                            onAdd = {
                                editedTrigger = null
                                showScheduleDialog = true
                            },
                            onEdit = { trigger ->
                                editedTrigger = trigger
                                showScheduleDialog = true
                            },
                            onRemove = onRemoveTrigger,
                        )
                    }
                    RunBar(
                        task = task,
                        isPending = state.isRunCommandPending,
                        onRun = onRun,
                        onStop = onStop,
                    )
                }
            }
        }
    }
}

@Composable
private fun LastRunGroup(task: TaskInfo) {
    val isCancelling = task.state == TaskState.CANCELLING
    val isActive = task.state == TaskState.RUNNING || isCancelling

    if (isActive) {
        val progress = task.currentProgressPercentage
        SettingsGroup {
            SettingsItem(
                leading = { TaskTile(iconRes = R.drawable.ic_refresh, tone = TaskTone.ACTIVE) },
                title =
                    if (isCancelling) stringResource(R.string.task_stopping)
                    else stringResource(R.string.category_running),
                trailing = { if (progress != null) FactValue(text = formatTaskProgress(progress)) },
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
        return
    }

    val result = task.lastExecutionResult
    val errorMessage =
        result?.takeIf { it.status == TaskCompletionStatus.FAILED }?.errorMessage?.trim().orEmpty()

    SettingsGroup(title = stringResource(R.string.task_last_run)) {
        SettingsItem(
            icon = painterResource(id = R.drawable.ic_circle_check_outline),
            title = stringResource(R.string.task_result_label),
            trailing = {
                ResultPill(
                    text = stringResource(result?.status?.labelRes() ?: R.string.task_never_run),
                    tone =
                        when (result?.status) {
                            null -> TaskTone.NEUTRAL
                            TaskCompletionStatus.COMPLETED -> TaskTone.DONE
                            else -> TaskTone.ERROR
                        },
                )
            },
        )
        if (result != null) {
            SettingsDivider()
            SettingsItem(
                icon = painterResource(id = R.drawable.ic_clock),
                title = stringResource(R.string.task_finished_label),
                trailing = { FactValue(text = formatFinishTime(result.endTimeUtc)) },
            )
            SettingsDivider()
            SettingsItem(
                icon = painterResource(id = R.drawable.ic_timer),
                title = stringResource(R.string.task_took_label),
                trailing = {
                    FactValue(
                        text =
                            formatRunDuration(result.startTimeUtc, result.endTimeUtc)
                                ?: stringResource(R.string.task_duration_under_second)
                    )
                },
            )
        }
        if (errorMessage.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                modifier =
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            ) {
                SelectionContainer {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FactValue(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.End,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ResultPill(text: String, tone: TaskTone) {
    val colors = MaterialTheme.colorScheme
    val container =
        when (tone) {
            TaskTone.DONE -> colors.tertiaryContainer
            TaskTone.ERROR -> colors.errorContainer
            TaskTone.ACTIVE -> colors.primaryContainer
            TaskTone.NEUTRAL -> colors.surfaceContainerHighest
        }
    val content =
        when (tone) {
            TaskTone.DONE -> colors.onTertiaryContainer
            TaskTone.ERROR -> colors.onErrorContainer
            TaskTone.ACTIVE -> colors.onPrimaryContainer
            TaskTone.NEUTRAL -> colors.onSurfaceVariant
        }
    Surface(shape = RoundedCornerShape(8.dp), color = container) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
            color = content,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun RunBar(task: TaskInfo, isPending: Boolean, onRun: () -> Unit, onStop: () -> Unit) {
    val isRunning = task.state == TaskState.RUNNING
    val isCancelling = task.state == TaskState.CANCELLING
    val isActive = isRunning || isCancelling

    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        Button(
            onClick = if (isRunning) onStop else onRun,
            enabled = !isCancelling && !isPending,
            colors =
                if (isActive) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            shape = RoundedCornerShape(24.dp),
            modifier =
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).height(48.dp),
        ) {
            Icon(
                painter =
                    painterResource(
                        id = if (isActive) R.drawable.ic_close else R.drawable.ic_player_play_filled
                    ),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text =
                    if (isActive) stringResource(R.string.task_stop_run)
                    else stringResource(R.string.task_run_now)
            )
        }
    }
}

@Composable
private fun ScheduleGroup(
    triggers: List<TaskTriggerInfo>,
    enabled: Boolean,
    onAdd: () -> Unit,
    onEdit: (TaskTriggerInfo) -> Unit,
    onRemove: (TaskTriggerInfo) -> Unit,
) {
    val contentAlpha = if (enabled) 1f else DISABLED_ALPHA

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsGroup(title = stringResource(R.string.task_schedule_title)) {
            if (triggers.isEmpty()) {
                Text(
                    text = stringResource(R.string.task_schedule_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                )
                ScheduleDivider()
            }
            triggers.forEach { trigger ->
                SettingsItem(
                    leading = {
                        TaskTile(iconRes = trigger.type.iconRes(), tone = TaskTone.NEUTRAL)
                    },
                    title = trigger.label(),
                    subtitle =
                        trigger.maxRuntimeTicks?.let { ticks ->
                            stringResource(
                                R.string.task_trigger_time_limit_fmt,
                                formatDurationTicks(ticks),
                            )
                        },
                    onClick = { onEdit(trigger) },
                    enabled = enabled,
                    trailing = {
                        IconButton(
                            onClick = { onRemove(trigger) },
                            enabled = enabled,
                            modifier = Modifier.offset(x = 8.dp).size(40.dp),
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_delete),
                                contentDescription = stringResource(R.string.cd_remove_schedule),
                                tint = MaterialTheme.colorScheme.error.copy(alpha = contentAlpha),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                )
                ScheduleDivider()
            }
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .clickable(enabled = enabled, role = Role.Button, onClick = onAdd)
                        .padding(16.dp)
                        .alpha(contentAlpha),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TaskTile(iconRes = R.drawable.ic_add, tone = TaskTone.ACTIVE)
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = stringResource(R.string.task_schedule_add),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            text = stringResource(R.string.task_triggers_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp),
        )
    }
}

@Composable
private fun ScheduleDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 72.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
    )
}
