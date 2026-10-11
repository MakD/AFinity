package com.makd.afinity.ui.settings.servers

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.ui.admin.components.AdminLoadContent
import com.makd.afinity.ui.admin.components.AdminScaffold

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
            Column(
                modifier =
                    Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(contentPadding)
                        .padding(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp)
            ) {
                ScheduledTasksSection(
                    tasks = currentTasks,
                    pendingTaskIds = pendingTaskCommands,
                    onOpenTask = onOpenTask,
                    onRunTask = viewModel::runTask,
                    onStopTask = viewModel::stopTask,
                )
            }
        }
    }
}
