package com.makd.afinity.ui.settings.servers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.ui.admin.components.AdminLoadContent
import com.makd.afinity.ui.admin.components.AdminPill
import com.makd.afinity.ui.admin.components.AdminScaffold
import com.makd.afinity.ui.admin.tasks.TaskTile
import com.makd.afinity.ui.admin.tasks.TaskTone
import com.makd.afinity.ui.admin.tasks.formatTaskProgress
import com.makd.afinity.ui.admin.tasks.labelRes
import com.makd.afinity.ui.admin.tasks.scheduleSummary
import com.makd.afinity.ui.admin.tasks.taskIconRes
import com.makd.afinity.ui.components.SETTINGS_EXPANDED_START_PADDING
import com.makd.afinity.ui.components.SettingsGroup
import com.makd.afinity.ui.components.SettingsItem
import com.makd.afinity.ui.settings.servers.utils.formatRelativeTime
import org.jellyfin.sdk.model.api.TaskCompletionStatus
import org.jellyfin.sdk.model.api.TaskInfo
import org.jellyfin.sdk.model.api.TaskState

private val TASK_ACTION_SIZE = 40.dp
private val TASK_ACTION_END_OFFSET = 8.dp

@Composable
fun ScheduledTasksScreen(
    onBackClick: () -> Unit,
    onOpenTask: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScheduledTasksViewModel = hiltViewModel(),
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val tasksFailed by viewModel.tasksFailed.collectAsStateWithLifecycle()
    val pendingTaskCommands by viewModel.pendingTaskCommands.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val commandFailedMessage = stringResource(R.string.session_command_failed)

    LaunchedEffect(Unit) {
        viewModel.commandError.collect { snackbarHostState.showSnackbar(commandFailedMessage) }
    }

    AdminScaffold(
        title = stringResource(R.string.title_scheduled_tasks),
        onBackClick = onBackClick,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) { contentPadding ->
        AdminLoadContent(
            data = tasks,
            failed = tasksFailed,
            onRetry = viewModel::retry,
            contentPadding = contentPadding,
        ) { currentTasks ->
            ScheduledTaskList(
                tasks = currentTasks,
                pendingTaskIds = pendingTaskCommands,
                onOpenTask = onOpenTask,
                onRunTask = viewModel::runTask,
                onStopTask = viewModel::stopTask,
                modifier = Modifier.fillMaxSize().padding(contentPadding),
            )
        }
    }
}

@Composable
private fun ScheduledTaskList(
    tasks: List<TaskInfo>,
    pendingTaskIds: Set<String>,
    onOpenTask: (String) -> Unit,
    onRunTask: (String) -> Unit,
    onStopTask: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val otherCategory = stringResource(R.string.category_other)
    val running = remember(tasks) { tasks.filter { it.id != null && it.state != TaskState.IDLE } }
    val categories =
        remember(tasks, otherCategory) {
            tasks
                .filter { it.id != null && it.state == TaskState.IDLE }
                .groupBy { it.category?.takeIf(String::isNotBlank) ?: otherCategory }
                .toSortedMap()
                .map { (category, members) -> category to members.sortedBy { it.name.orEmpty() } }
        }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (running.isNotEmpty()) {
            item(key = "running") {
                TaskGroup(
                    title = stringResource(R.string.category_running),
                    count = null,
                    tasks = running,
                    pendingTaskIds = pendingTaskIds,
                    onOpenTask = onOpenTask,
                    onRunTask = onRunTask,
                    onStopTask = onStopTask,
                )
            }
        }
        items(items = categories, key = { (category, _) -> "category:$category" }) {
            (category, members) ->
            TaskGroup(
                title = category,
                count = members.size,
                tasks = members,
                pendingTaskIds = pendingTaskIds,
                onOpenTask = onOpenTask,
                onRunTask = onRunTask,
                onStopTask = onStopTask,
            )
        }
    }
}

@Composable
private fun TaskGroup(
    title: String,
    count: Int?,
    tasks: List<TaskInfo>,
    pendingTaskIds: Set<String>,
    onOpenTask: (String) -> Unit,
    onRunTask: (String) -> Unit,
    onStopTask: (String) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 32.dp, end = 32.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (count != null) AdminPill(text = count.toString())
        }
        SettingsGroup {
            tasks.forEachIndexed { index, task ->
                val taskId = task.id ?: return@forEachIndexed
                key(taskId) {
                    if (index > 0) TaskDivider()
                    TaskRow(
                        task = task,
                        isPending = taskId in pendingTaskIds,
                        onOpen = { onOpenTask(taskId) },
                        onRun = { onRunTask(taskId) },
                        onStop = { onStopTask(taskId) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun TaskDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = SETTINGS_EXPANDED_START_PADDING, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
    )
}

@Composable
internal fun TaskRow(
    task: TaskInfo,
    isPending: Boolean,
    onOpen: () -> Unit,
    onRun: () -> Unit,
    onStop: () -> Unit,
) {
    val context = LocalContext.current
    val isRunning = task.state == TaskState.RUNNING
    val isCancelling = task.state == TaskState.CANCELLING
    val isActive = isRunning || isCancelling
    val result = task.lastExecutionResult
    val badResult = result?.takeIf { !isActive && it.status != TaskCompletionStatus.COMPLETED }
    val progress = task.currentProgressPercentage

    val subtitle =
        when {
            isCancelling -> stringResource(R.string.task_stopping)
            isRunning ->
                progress?.let { formatTaskProgress(it) }
                    ?: stringResource(R.string.category_running)
            badResult != null ->
                listOfNotNull(
                        stringResource(badResult.status.labelRes()),
                        formatRelativeTime(context, badResult.endTimeUtc),
                        badResult.errorMessage?.trim()?.ifEmpty { null },
                    )
                    .joinToString(" · ")
            else ->
                listOf(
                        task.triggers.orEmpty().scheduleSummary(),
                        result?.let { formatRelativeTime(context, it.endTimeUtc) }
                            ?: stringResource(R.string.task_never_run),
                    )
                    .joinToString(" · ")
        }

    SettingsItem(
        leading = {
            TaskTile(
                iconRes = taskIconRes(task.key),
                tone =
                    when {
                        isActive -> TaskTone.ACTIVE
                        badResult != null -> TaskTone.ERROR
                        else -> TaskTone.NEUTRAL
                    },
            )
        },
        title = task.name.orEmpty(),
        subtitle = subtitle,
        subtitleMaxLines = 1,
        subtitleColor =
            when {
                isActive -> MaterialTheme.colorScheme.primary
                badResult != null -> MaterialTheme.colorScheme.error
                else -> null
            },
        onClick = onOpen,
        trailing = {
            TaskRowAction(
                isRunning = isRunning,
                isBusy = isCancelling || isPending,
                onRun = onRun,
                onStop = onStop,
            )
        },
    )

    if (isRunning) {
        val progressModifier =
            Modifier.fillMaxWidth()
                .padding(start = SETTINGS_EXPANDED_START_PADDING, end = 16.dp, bottom = 14.dp)
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
}

@Composable
private fun TaskRowAction(
    isRunning: Boolean,
    isBusy: Boolean,
    onRun: () -> Unit,
    onStop: () -> Unit,
) {
    val actionModifier = Modifier.offset(x = TASK_ACTION_END_OFFSET).size(TASK_ACTION_SIZE)
    when {
        isBusy ->
            Box(modifier = actionModifier, contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        isRunning ->
            IconButton(onClick = onStop, modifier = actionModifier) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_close),
                    contentDescription = stringResource(R.string.cd_stop_task),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
            }
        else ->
            IconButton(onClick = onRun, modifier = actionModifier) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_player_play_filled),
                    contentDescription = stringResource(R.string.cd_run_task),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
    }
}
