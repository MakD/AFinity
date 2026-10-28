package com.makd.afinity.ui.settings.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.data.models.admin.ActivityFilter
import com.makd.afinity.data.models.server.StorageDevice
import com.makd.afinity.ui.admin.components.AdminPill
import com.makd.afinity.ui.admin.components.AdminScaffold
import com.makd.afinity.ui.admin.config.ConfigFormIds
import com.makd.afinity.ui.admin.plugins.labelRes
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.components.SettingsDivider
import com.makd.afinity.ui.components.SettingsGroup
import com.makd.afinity.ui.components.SettingsGroupTitle
import com.makd.afinity.ui.components.SettingsItem
import com.makd.afinity.ui.settings.servers.components.SessionRemoteSheet
import com.makd.afinity.ui.settings.servers.components.playMethodLabelRes
import com.makd.afinity.ui.settings.servers.utils.formatTicks
import com.makd.afinity.util.formatFileSize
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.PluginInfo
import org.jellyfin.sdk.model.api.SessionInfoDto
import org.jellyfin.sdk.model.api.TaskCompletionStatus
import org.jellyfin.sdk.model.api.TaskInfo
import org.jellyfin.sdk.model.api.TaskState

@Composable
fun ControlPanelScreen(
    onBackClick: () -> Unit,
    onOpenUsers: () -> Unit,
    onOpenLibraries: () -> Unit,
    onOpenActivity: (ActivityFilter) -> Unit,
    onOpenDevices: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenPlugins: () -> Unit,
    onOpenTasks: () -> Unit,
    onOpenTask: (String) -> Unit,
    onOpenApiKeys: () -> Unit,
    onOpenBackups: () -> Unit,
    onOpenSettings: (formId: String) -> Unit,
    modifier: Modifier = Modifier,
    isDualPane: Boolean = false,
    viewModel: ControlPanelViewModel = hiltViewModel(),
) {
    val tasks by viewModel.scheduledTasks.collectAsStateWithLifecycle()
    val serverName by viewModel.serverName.collectAsStateWithLifecycle()
    val serverVersion by viewModel.serverVersion.collectAsStateWithLifecycle()
    val systemInfo by viewModel.systemInfo.collectAsStateWithLifecycle()
    val pluginProblems by viewModel.pluginProblems.collectAsStateWithLifecycle()
    val pluginsPendingRestart by viewModel.pluginsPendingRestart.collectAsStateWithLifecycle()
    val isCreatingBackup by viewModel.isCreatingBackup.collectAsStateWithLifecycle()
    val userCount by viewModel.userCount.collectAsStateWithLifecycle()
    val deviceCount by viewModel.deviceCount.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    if (!isDualPane) {
        CommandErrorEffect(viewModel = viewModel, snackbarHostState = snackbarHostState)
    }

    AdminScaffold(
        title = stringResource(R.string.title_control_panel),
        onBackClick = onBackClick,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) { contentPadding ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(contentPadding)
                    .padding(
                        start = 16.dp,
                        top = 16.dp,
                        end = if (isDualPane) 0.dp else 16.dp,
                        bottom = 16.dp,
                    ),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            ServerHeader(name = serverName, version = systemInfo?.version ?: serverVersion)

            if (!isDualPane) {
                ControlPanelOverview(
                    viewModel = viewModel,
                    onOpenActivity = onOpenActivity,
                    onOpenPlugins = onOpenPlugins,
                    onOpenTask = onOpenTask,
                )
            }

            val runningTasks = tasks.orEmpty().filter { it.state != TaskState.IDLE }

            SettingsGroup(
                title = stringResource(R.string.section_server),
                startPadding = 0.dp,
                endPadding = 0.dp,
            ) {
                SettingsItem(
                    icon = painterResource(id = R.drawable.ic_settings),
                    title = stringResource(R.string.config_general_title),
                    subtitle = stringResource(R.string.config_general_summary),
                    onClick = { onOpenSettings(ConfigFormIds.GENERAL) },
                )
                SettingsDivider()
                SettingsItem(
                    icon = painterResource(id = R.drawable.ic_users_group),
                    title = stringResource(R.string.title_users),
                    subtitle =
                        userCount?.let { stringResource(R.string.users_count_fmt, it) }
                            ?: stringResource(R.string.users_summary),
                    onClick = onOpenUsers,
                )
            }

            SettingsGroup(
                title = stringResource(R.string.libraries_title),
                startPadding = 0.dp,
                endPadding = 0.dp,
            ) {
                SettingsItem(
                    icon = painterResource(id = R.drawable.ic_folder),
                    title = stringResource(R.string.libraries_title),
                    subtitle = stringResource(R.string.libraries_admin_summary),
                    onClick = onOpenLibraries,
                )
                SettingsDivider()
                SettingsItem(
                    icon = painterResource(id = R.drawable.ic_layout_grid),
                    title = stringResource(R.string.config_library_display_title),
                    subtitle = stringResource(R.string.config_library_display_summary),
                    onClick = { onOpenSettings(ConfigFormIds.LIBRARY_DISPLAY) },
                )
                SettingsDivider()
                SettingsItem(
                    icon = painterResource(id = R.drawable.ic_info),
                    title = stringResource(R.string.config_metadata_title),
                    subtitle = stringResource(R.string.config_metadata_summary),
                    onClick = { onOpenSettings(ConfigFormIds.LIBRARY_METADATA) },
                )
                SettingsDivider()
                SettingsItem(
                    icon = painterResource(id = R.drawable.ic_edit),
                    title = stringResource(R.string.config_nfo_title),
                    subtitle = stringResource(R.string.config_nfo_summary),
                    onClick = { onOpenSettings(ConfigFormIds.NFO) },
                )
            }

            SettingsGroup(
                title = stringResource(R.string.config_playback_title),
                startPadding = 0.dp,
                endPadding = 0.dp,
            ) {
                SettingsItem(
                    icon = painterResource(id = R.drawable.ic_movie),
                    title = stringResource(R.string.config_transcoding_title),
                    subtitle = stringResource(R.string.config_transcoding_summary),
                    onClick = { onOpenSettings(ConfigFormIds.TRANSCODING) },
                )
                SettingsDivider()
                SettingsItem(
                    icon = painterResource(id = R.drawable.ic_replay),
                    title = stringResource(R.string.config_section_resume),
                    subtitle = stringResource(R.string.config_resume_summary),
                    onClick = { onOpenSettings(ConfigFormIds.RESUME) },
                )
                SettingsDivider()
                SettingsItem(
                    icon = painterResource(id = R.drawable.ic_speed),
                    title = stringResource(R.string.config_section_streaming),
                    subtitle = stringResource(R.string.config_streaming_summary),
                    onClick = { onOpenSettings(ConfigFormIds.STREAMING) },
                )
                SettingsDivider()
                SettingsItem(
                    icon = painterResource(id = R.drawable.ic_photo_search),
                    title = stringResource(R.string.config_trickplay_title),
                    subtitle = stringResource(R.string.config_trickplay_summary),
                    onClick = { onOpenSettings(ConfigFormIds.TRICKPLAY) },
                )
            }

            SettingsGroup(
                title = stringResource(R.string.title_devices),
                startPadding = 0.dp,
                endPadding = 0.dp,
            ) {
                SettingsItem(
                    icon = painterResource(id = R.drawable.ic_devices),
                    title = stringResource(R.string.title_devices),
                    subtitle =
                        deviceCount?.let { stringResource(R.string.devices_count_fmt, it) }
                            ?: stringResource(R.string.devices_summary),
                    onClick = onOpenDevices,
                )
                SettingsDivider()
                SettingsItem(
                    icon = painterResource(id = R.drawable.ic_history),
                    title = stringResource(R.string.title_activity),
                    subtitle = stringResource(R.string.activity_summary),
                    onClick = { onOpenActivity(ActivityFilter.ALL) },
                )
            }

            SettingsGroup(
                title = stringResource(R.string.section_advanced),
                startPadding = 0.dp,
                endPadding = 0.dp,
            ) {
                SettingsItem(
                    icon = painterResource(id = R.drawable.ic_world),
                    title = stringResource(R.string.config_networking_title),
                    subtitle = stringResource(R.string.config_networking_summary),
                    onClick = { onOpenSettings(ConfigFormIds.NETWORKING) },
                )
                SettingsDivider()
                SettingsItem(
                    icon = painterResource(id = R.drawable.ic_lock_filled),
                    title = stringResource(R.string.title_api_keys),
                    subtitle = stringResource(R.string.api_keys_summary),
                    onClick = onOpenApiKeys,
                )
                SettingsDivider()
                SettingsItem(
                    icon = painterResource(id = R.drawable.ic_database),
                    title = stringResource(R.string.title_server_backups),
                    subtitle =
                        if (isCreatingBackup) stringResource(R.string.server_backup_creating)
                        else stringResource(R.string.server_backups_summary),
                    onClick = onOpenBackups,
                )
                SettingsDivider()
                SettingsItem(
                    icon = painterResource(id = R.drawable.ic_logs),
                    title = stringResource(R.string.title_server_logs),
                    subtitle = stringResource(R.string.server_logs_summary),
                    onClick = onOpenLogs,
                )
                SettingsDivider()
                SettingsItem(
                    icon = painterResource(id = R.drawable.ic_schedule),
                    title = stringResource(R.string.title_scheduled_tasks),
                    subtitle =
                        runningTasks.firstOrNull()?.name?.let {
                            stringResource(R.string.tasks_running_fmt, it)
                        } ?: stringResource(R.string.scheduled_tasks_summary),
                    onClick = onOpenTasks,
                )
            }

            SettingsGroup(
                title = stringResource(R.string.title_plugins),
                startPadding = 0.dp,
                endPadding = 0.dp,
            ) {
                SettingsItem(
                    icon = painterResource(id = R.drawable.ic_puzzle),
                    title = stringResource(R.string.title_plugins),
                    subtitle =
                        when {
                            pluginsPendingRestart -> stringResource(R.string.plugin_status_restart)
                            pluginProblems.isNotEmpty() -> pluginProblems.first().name
                            else -> stringResource(R.string.plugins_summary)
                        },
                    onClick = onOpenPlugins,
                )
            }
        }
    }
}

@Composable
fun ControlPanelOverviewScreen(
    onOpenActivity: (ActivityFilter) -> Unit,
    onOpenPlugins: () -> Unit,
    onOpenTask: (String) -> Unit,
    viewModel: ControlPanelViewModel,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    CommandErrorEffect(viewModel = viewModel, snackbarHostState = snackbarHostState)

    AdminScaffold(
        title = stringResource(R.string.control_panel_overview),
        onBackClick = null,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    ) { contentPadding ->
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(contentPadding)
                    .padding(16.dp)
        ) {
            ControlPanelOverview(
                viewModel = viewModel,
                onOpenActivity = onOpenActivity,
                onOpenPlugins = onOpenPlugins,
                onOpenTask = onOpenTask,
            )
        }
    }
}

@Composable
private fun CommandErrorEffect(
    viewModel: ControlPanelViewModel,
    snackbarHostState: SnackbarHostState,
) {
    val commandFailedMessage = stringResource(R.string.session_command_failed)

    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.commandError.collect { snackbarHostState.showSnackbar(commandFailedMessage) }
    }
}

@Composable
private fun ControlPanelOverview(
    viewModel: ControlPanelViewModel,
    onOpenActivity: (ActivityFilter) -> Unit,
    onOpenPlugins: () -> Unit,
    onOpenTask: (String) -> Unit,
) {
    val tasks by viewModel.scheduledTasks.collectAsStateWithLifecycle()
    val sessions by viewModel.activeSessions.collectAsStateWithLifecycle()
    val isLibraryRefreshing by viewModel.isLibraryRefreshing.collectAsStateWithLifecycle()
    val pendingPause by viewModel.pendingPause.collectAsStateWithLifecycle()
    val pendingTaskCommands by viewModel.pendingTaskCommands.collectAsStateWithLifecycle()
    val sessionsFailed by viewModel.sessionsFailed.collectAsStateWithLifecycle()
    val tasksFailed by viewModel.tasksFailed.collectAsStateWithLifecycle()
    val serverName by viewModel.serverName.collectAsStateWithLifecycle()
    val systemInfo by viewModel.systemInfo.collectAsStateWithLifecycle()
    val pluginProblems by viewModel.pluginProblems.collectAsStateWithLifecycle()
    val pluginsPendingRestart by viewModel.pluginsPendingRestart.collectAsStateWithLifecycle()
    val failedSignIns by viewModel.failedSignIns.collectAsStateWithLifecycle()
    val fullDisks by viewModel.fullDisks.collectAsStateWithLifecycle()

    val remoteSessionId by viewModel.remoteSessionId.collectAsStateWithLifecycle()
    val pendingPowerAction by viewModel.pendingPowerAction.collectAsStateWithLifecycle()

    val remoteSession = sessions?.firstOrNull { it.id != null && it.id == remoteSessionId }

    if (remoteSession != null) {
        SessionRemoteSheet(
            session = remoteSession,
            baseUrl = viewModel.baseUrl,
            isOwnSession = remoteSession.userId == viewModel.currentUserId,
            pendingPause = remoteSession.id?.let { pendingPause[it] },
            onDismiss = { viewModel.showRemote(null) },
            onTogglePause = { viewModel.togglePause(remoteSession) },
            onSeekBy = { viewModel.seekBy(remoteSession, it) },
            onSeekTo = { ticks -> remoteSession.id?.let { viewModel.seekTo(it, ticks) } },
            onPlaystate = { command ->
                remoteSession.id?.let { viewModel.sendPlaystate(it, command) }
            },
            onSetVolume = { volume -> remoteSession.id?.let { viewModel.setVolume(it, volume) } },
            onToggleMute = {
                remoteSession.id?.let {
                    viewModel.toggleMute(it, remoteSession.playState?.isMuted == true)
                }
            },
            onSendMessage = { text -> remoteSession.id?.let { viewModel.sendMessage(it, text) } },
        )
    }

    if (pendingPowerAction == ServerPowerAction.RESTART) {
        AlertDialog(
            onDismissRequest = { viewModel.confirmPowerAction(null) },
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_restart),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.dialog_restart_server_title),
                    style =
                        MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.dialog_restart_server_message, serverName),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.restartServer()
                        viewModel.confirmPowerAction(null)
                    },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                ) {
                    Text(stringResource(R.string.action_restart))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.confirmPowerAction(null) }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }

    if (pendingPowerAction == ServerPowerAction.SHUTDOWN) {
        AlertDialog(
            onDismissRequest = { viewModel.confirmPowerAction(null) },
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_power),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.dialog_shutdown_server_title),
                    style =
                        MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.dialog_shutdown_server_message, serverName),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.shutdownServer()
                        viewModel.confirmPowerAction(null)
                    },
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                ) {
                    Text(stringResource(R.string.action_shutdown))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.confirmPowerAction(null) }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        if (sessionsFailed || tasksFailed) {
            LoadFailedCard(onRetry = viewModel::retry)
        }

        val restartRequired = systemInfo?.hasPendingRestart == true || pluginsPendingRestart
        val failedTasks =
            tasks.orEmpty().filter { it.lastExecutionResult?.status == TaskCompletionStatus.FAILED }
        val needsAttention =
            restartRequired ||
                failedSignIns > 0 ||
                fullDisks.isNotEmpty() ||
                pluginProblems.isNotEmpty() ||
                failedTasks.isNotEmpty()
        if (needsAttention) {
            AttentionCard(
                restartRequired = restartRequired,
                failedSignIns = failedSignIns,
                fullDisks = fullDisks,
                pluginProblems = pluginProblems,
                failedTasks = failedTasks,
                onRestart = { viewModel.confirmPowerAction(ServerPowerAction.RESTART) },
                onOpenSignIns = { onOpenActivity(ActivityFilter.FAILED_SIGN_INS) },
                onOpenPlugins = onOpenPlugins,
                onOpenTask = onOpenTask,
            )
        }

        if (!sessionsFailed) {
            ActiveSessionsSection(
                sessions = sessions ?: emptyList(),
                baseUrl = viewModel.baseUrl,
                loading = sessions == null,
                pendingPause = pendingPause,
                onOpenRemote = { viewModel.showRemote(it.id) },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuickActionButton(
                label = stringResource(R.string.action_refresh_libraries),
                icon = painterResource(R.drawable.ic_refresh),
                onClick = { viewModel.refreshAllLibraries() },
                isLoading = isLibraryRefreshing,
                enabled = !isLibraryRefreshing,
                modifier = Modifier.weight(1f),
            )
            QuickActionButton(
                label = stringResource(R.string.action_restart),
                icon = painterResource(R.drawable.ic_restart),
                onClick = { viewModel.confirmPowerAction(ServerPowerAction.RESTART) },
                isDangerous = true,
                modifier = Modifier.weight(1f),
            )
            QuickActionButton(
                label = stringResource(R.string.action_shutdown),
                icon = painterResource(R.drawable.ic_power),
                onClick = { viewModel.confirmPowerAction(ServerPowerAction.SHUTDOWN) },
                isDangerous = true,
                modifier = Modifier.weight(1f),
            )
        }

        val runningTasks = tasks.orEmpty().filter { it.state != TaskState.IDLE }
        if (runningTasks.isNotEmpty()) {
            SettingsGroup(
                title = stringResource(R.string.category_running),
                startPadding = 0.dp,
                endPadding = 0.dp,
            ) {
                runningTasks.forEachIndexed { index, task ->
                    val taskId = task.id ?: return@forEachIndexed
                    androidx.compose.runtime.key(taskId) {
                        if (index > 0) TaskDivider()
                        TaskRow(
                            task = task,
                            isPending = taskId in pendingTaskCommands,
                            onOpen = { onOpenTask(taskId) },
                            onRun = {},
                            onStop = { viewModel.stopTask(taskId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ServerHeader(name: String, version: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier.size(48.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(14.dp),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_server),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (version != null) {
                Text(
                    text = stringResource(R.string.server_version_fmt, version),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AttentionCard(
    restartRequired: Boolean,
    failedSignIns: Int,
    fullDisks: List<StorageDevice>,
    pluginProblems: List<PluginInfo>,
    failedTasks: List<TaskInfo>,
    onRestart: () -> Unit,
    onOpenSignIns: () -> Unit,
    onOpenPlugins: () -> Unit,
    onOpenTask: (String) -> Unit,
) {
    Column {
        SettingsGroupTitle(title = stringResource(R.string.section_needs_attention))
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                var shown = 0
                if (restartRequired) {
                    shown++
                    SettingsItem(
                        icon = painterResource(id = R.drawable.ic_restart),
                        title = stringResource(R.string.attention_restart_required),
                        subtitle = stringResource(R.string.attention_restart_required_summary),
                        trailing = {
                            TextButton(onClick = onRestart) {
                                Text(stringResource(R.string.action_restart))
                            }
                        },
                    )
                }
                if (failedSignIns > 0) {
                    if (shown++ > 0) SettingsDivider()
                    SettingsItem(
                        icon = painterResource(id = R.drawable.ic_security),
                        title = stringResource(R.string.attention_failed_sign_ins),
                        subtitle = stringResource(R.string.attention_failed_sign_ins_summary),
                        onClick = onOpenSignIns,
                        trailing = { AdminPill(text = failedSignIns.toString()) },
                    )
                }
                val context = LocalContext.current
                fullDisks.forEach { disk ->
                    if (shown++ > 0) SettingsDivider()
                    SettingsItem(
                        icon = painterResource(id = R.drawable.ic_database),
                        title = disk.label,
                        subtitle =
                            stringResource(
                                R.string.attention_disk_full_fmt,
                                formatFileSize(context, disk.freeSpace),
                            ),
                    )
                }
                pluginProblems.forEach { plugin ->
                    if (shown++ > 0) SettingsDivider()
                    SettingsItem(
                        icon = painterResource(id = R.drawable.ic_puzzle),
                        title = plugin.name,
                        subtitle = plugin.status.labelRes()?.let { stringResource(it) },
                        onClick = onOpenPlugins,
                    )
                }
                failedTasks.forEach { task ->
                    val taskId = task.id ?: return@forEach
                    if (shown++ > 0) SettingsDivider()
                    SettingsItem(
                        icon = painterResource(id = R.drawable.ic_alert_triangle),
                        title = task.name.orEmpty(),
                        subtitle = stringResource(R.string.attention_task_failed),
                        onClick = { onOpenTask(taskId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadFailedCard(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.error_load_content_generic),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f).padding(vertical = 16.dp),
            )
            TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        }
    }
}

@Composable
private fun QuickActionButton(
    label: String,
    icon: Painter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDangerous: Boolean = false,
    isLoading: Boolean = false,
    enabled: Boolean = true,
) {
    val contentColor =
        if (isDangerous) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val containerColor =
        if (isDangerous) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        else MaterialTheme.colorScheme.surfaceContainerLow

    val alpha = if (enabled) 1f else 0.5f

    Surface(
        onClick = onClick,
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(24.dp),
        color = containerColor.copy(alpha = containerColor.alpha * alpha),
        modifier = modifier.clip(RoundedCornerShape(24.dp)),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = contentColor,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    painter = icon,
                    contentDescription = label,
                    tint = contentColor.copy(alpha = alpha),
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = contentColor.copy(alpha = alpha),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ActiveSessionsSection(
    sessions: List<SessionInfoDto>,
    baseUrl: String,
    loading: Boolean = false,
    pendingPause: Map<String, Boolean> = emptyMap(),
    onOpenRemote: (SessionInfoDto) -> Unit = {},
) {
    val playingSessions = sessions.filter { it.nowPlayingItem != null }
    val idleCount = sessions.count { it.nowPlayingItem == null }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text =
                    if (loading) stringResource(R.string.active_sessions_title)
                    else stringResource(R.string.active_sessions_count_fmt, sessions.size),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
            if (idleCount > 0) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_devices),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = stringResource(R.string.active_sessions_idle_fmt, idleCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }

        when {
            loading && playingSessions.isEmpty() ->
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            sessions.isEmpty() ->
                Text(
                    text = stringResource(R.string.active_sessions_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp),
                )
            else -> {
                val pagerState = rememberPagerState { playingSessions.size }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth(),
                    pageSpacing = 12.dp,
                ) { page ->
                    val session = playingSessions[page]
                    PlayingSessionCard(
                        session = session,
                        baseUrl = baseUrl,
                        pendingPause = session.id?.let { pendingPause[it] },
                        onOpenRemote = { onOpenRemote(session) },
                    )
                }
                if (playingSessions.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repeat(playingSessions.size) { index ->
                            val selected = pagerState.currentPage == index
                            Box(
                                modifier =
                                    Modifier.padding(horizontal = 3.dp)
                                        .size(if (selected) 8.dp else 5.dp)
                                        .background(
                                            color =
                                                if (selected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.outlineVariant,
                                            shape = CircleShape,
                                        )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayingSessionCard(
    session: SessionInfoDto,
    baseUrl: String,
    pendingPause: Boolean? = null,
    onOpenRemote: () -> Unit = {},
) {
    val item = session.nowPlayingItem ?: return

    val backdropUrl =
        remember(baseUrl, item.id, item.backdropImageTags, item.imageTags) {
            val backdropTag = item.backdropImageTags?.firstOrNull()
            if (backdropTag != null && baseUrl.isNotEmpty()) {
                "$baseUrl/Items/${item.id}/Images/Backdrop?tag=$backdropTag&maxWidth=600"
            } else {
                val primaryTag = item.imageTags?.get(ImageType.PRIMARY)
                if (primaryTag != null && baseUrl.isNotEmpty())
                    "$baseUrl/Items/${item.id}/Images/Primary?tag=$primaryTag&maxWidth=600"
                else null
            }
        }

    val clientIconUrl = session.capabilities?.iconUrl

    val userAvatarUrl =
        remember(baseUrl, session.userId, session.userPrimaryImageTag) {
            val uid = session.userId
            val tag = session.userPrimaryImageTag
            if (baseUrl.isNotEmpty() && tag != null) {
                "$baseUrl/Users/$uid/Images/Primary?tag=$tag&maxWidth=48"
            } else {
                null
            }
        }

    val basePositionTicks = session.playState?.positionTicks ?: 0L
    val isPaused = session.playState?.isPaused ?: true
    val displayPaused = pendingPause ?: isPaused

    val runtimeTicks = item.runTimeTicks

    var localPositionTicks by remember(basePositionTicks) { mutableLongStateOf(basePositionTicks) }

    LaunchedEffect(basePositionTicks, isPaused) {
        if (!isPaused) {
            while (true) {
                delay(1.seconds)
                localPositionTicks += 10_000_000L
            }
        }
    }
    val progress =
        if (runtimeTicks != null && runtimeTicks > 0) {
            (localPositionTicks.toDouble() / runtimeTicks.toDouble()).toFloat().coerceIn(0f, 1f)
        } else null

    val deviceInfo = buildString {
        append(session.deviceName ?: stringResource(R.string.unknown_device))
        session.client?.let { append(" · $it") }
    }
    val title = item.name ?: stringResource(R.string.unknown_title)
    val year = item.productionYear?.toString()
    val timeText =
        if (runtimeTicks != null) {
            "${formatTicks(localPositionTicks)} / ${formatTicks(runtimeTicks)}"
        } else null
    val userName = session.userName ?: stringResource(R.string.unknown_user)

    ElevatedCard(
        onClick = onOpenRemote,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp)) {
            if (backdropUrl != null) {
                AsyncImage(
                    imageUrl = backdropUrl,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier =
                        Modifier.matchParentSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                )
            }

            Box(
                modifier =
                    Modifier.matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.55f),
                                0.35f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.85f),
                            )
                        )
            )

            Column(modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (clientIconUrl != null) {
                        AsyncImage(
                            imageUrl = clientIconUrl,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp).clip(RoundedCornerShape(3.dp)),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_devices),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = deviceInfo,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    val playMethod = session.playMethodLabelRes()
                    if (playMethod != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(playMethod),
                            style =
                                MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                            color = Color.White,
                            maxLines = 1,
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = title,
                        style =
                            MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = year ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (displayPaused) {
                                SessionPausedTag()
                            }
                            if (timeText != null) {
                                Text(
                                    text = timeText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.9f),
                                )
                            }
                        }
                    }

                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp),
                            color =
                                if (displayPaused) Color.White.copy(alpha = 0.7f)
                                else MaterialTheme.colorScheme.primary,
                            trackColor = Color.White.copy(alpha = 0.25f),
                        )
                    }

                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (userAvatarUrl != null) {
                            AsyncImage(
                                imageUrl = userAvatarUrl,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            Box(
                                modifier =
                                    Modifier.size(22.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = userName.first().uppercaseChar().toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            }
                        }
                        Text(
                            text = userName,
                            style =
                                MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionPausedTag() {
    Surface(shape = RoundedCornerShape(8.dp), color = Color.White.copy(alpha = 0.22f)) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_player_pause_filled),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = stringResource(R.string.session_paused),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
            )
        }
    }
}
