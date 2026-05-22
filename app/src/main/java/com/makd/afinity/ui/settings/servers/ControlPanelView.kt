package com.makd.afinity.ui.settings.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.settings.servers.components.SectionHeader
import com.makd.afinity.ui.settings.servers.utils.formatLastRun
import com.makd.afinity.ui.settings.servers.utils.formatTicks
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.SessionInfoDto
import org.jellyfin.sdk.model.api.TaskInfo
import org.jellyfin.sdk.model.api.TaskState

@Composable
internal fun ControlPanelView(
    serverWithCount: ServerWithUserCount,
    onBack: () -> Unit,
    viewModel: ControlPanelViewModel,
) {
    val tasks by viewModel.scheduledTasks.collectAsStateWithLifecycle()
    val sessions by viewModel.activeSessions.collectAsStateWithLifecycle()

    var showRestartConfirm by remember { mutableStateOf(false) }
    var showShutdownConfirm by remember { mutableStateOf(false) }
    val serverId = serverWithCount.server.id

    DisposableEffect(serverId) {
        viewModel.initialize(serverId)

        onDispose {}
    }

    if (showRestartConfirm) {
        AlertDialog(
            onDismissRequest = { showRestartConfirm = false },
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
                    text =
                        stringResource(
                            R.string.dialog_restart_server_message,
                            serverWithCount.server.name,
                        ),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.restartServer()
                        showRestartConfirm = false
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
                TextButton(onClick = { showRestartConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }

    if (showShutdownConfirm) {
        AlertDialog(
            onDismissRequest = { showShutdownConfirm = false },
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
                    text =
                        stringResource(
                            R.string.dialog_shutdown_server_message,
                            serverWithCount.server.name,
                        ),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.shutdownServer()
                        showShutdownConfirm = false
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
                TextButton(onClick = { showShutdownConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp),
        ) {
            IconButton(
                onClick = onBack,
                modifier =
                    Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_chevron_left),
                    contentDescription = stringResource(R.string.cd_back),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.title_control_panel),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Column(
            modifier =
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ActiveSessionsSection(
                sessions = sessions ?: emptyList(),
                baseUrl = viewModel.baseUrl,
                loading = sessions == null,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickActionButton(
                    label = stringResource(R.string.action_refresh_libraries),
                    icon = painterResource(R.drawable.ic_refresh),
                    onClick = { viewModel.refreshAllLibraries() },
                    modifier = Modifier.weight(1f),
                )
                QuickActionButton(
                    label = stringResource(R.string.action_restart),
                    icon = painterResource(R.drawable.ic_restart),
                    onClick = { showRestartConfirm = true },
                    isDangerous = true,
                    modifier = Modifier.weight(1f),
                )
                QuickActionButton(
                    label = stringResource(R.string.action_shutdown),
                    icon = painterResource(R.drawable.ic_power),
                    onClick = { showShutdownConfirm = true },
                    isDangerous = true,
                    modifier = Modifier.weight(1f),
                )
            }

            val currentTasks = tasks
            when {
                currentTasks == null ->
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                currentTasks.isNotEmpty() ->
                    ScheduledTasksSection(
                        tasks = currentTasks,
                        onRunTask = { viewModel.runTask(it) },
                        onStopTask = { viewModel.stopTask(it) },
                    )
            }
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
) {
    val contentColor =
        if (isDangerous) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val containerColor =
        if (isDangerous) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        else MaterialTheme.colorScheme.surfaceContainerHigh

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        modifier = modifier.clip(RoundedCornerShape(16.dp)).clickable { onClick() },
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = contentColor,
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
) {
    val playingSessions = sessions.filter { it.nowPlayingItem != null }
    val idleCount = sessions.count { it.nowPlayingItem == null }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SectionHeader(
                if (loading) stringResource(R.string.active_sessions_title)
                else stringResource(R.string.active_sessions_count_fmt, sessions.size)
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
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                )
            else -> {
                val pagerState = rememberPagerState { playingSessions.size }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth(),
                    pageSpacing = 12.dp,
                ) { page ->
                    PlayingSessionCard(session = playingSessions[page], baseUrl = baseUrl)
                }
                if (playingSessions.size > 1) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
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
private fun PlayingSessionCard(session: SessionInfoDto, baseUrl: String) {
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
        remember(baseUrl, session.userId) {
            val uid = session.userId
            if (uid != null && baseUrl.isNotEmpty())
                "$baseUrl/Users/$uid/Images/Primary?maxWidth=48"
            else null
        }

    val basePositionTicks = session.playState?.positionTicks ?: 0L
    val isPaused = session.playState?.isPaused ?: true

    val positionTicks = session.playState?.positionTicks
    val runtimeTicks = item.runTimeTicks

    var localPositionTicks by remember(basePositionTicks) { mutableStateOf(basePositionTicks) }

    LaunchedEffect(basePositionTicks, isPaused) {
        if (!isPaused) {
            while (true) {
                delay(1000)
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
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            if (backdropUrl != null) {
                AsyncImage(
                    imageUrl = backdropUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                )
            }

            Box(
                modifier =
                    Modifier.fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.55f),
                                0.35f to Color.Transparent,
                                1f to Color.Black.copy(alpha = 0.85f),
                            )
                        )
            )

            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
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
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 2,
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
                    if (timeText != null) {
                        Text(
                            text = timeText,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.9f),
                        )
                    }
                }

                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
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
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduledTasksSection(
    tasks: List<TaskInfo>,
    onRunTask: (String) -> Unit,
    onStopTask: (String) -> Unit,
) {
    val running = tasks.filter { it.state == TaskState.RUNNING || it.state == TaskState.CANCELLING }
    val grouped =
        tasks
            .filter { it.state != TaskState.RUNNING && it.state != TaskState.CANCELLING }
            .groupBy { it.category ?: stringResource(R.string.category_other) }
            .toSortedMap()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (running.isNotEmpty()) {
            SectionHeader(stringResource(R.string.category_running))
            running.forEach { task ->
                val taskId = task.id ?: return@forEach
                androidx.compose.runtime.key(taskId) {
                    ScheduledTaskRow(
                        task = task,
                        onRun = { onRunTask(taskId) },
                        onStop = { onStopTask(taskId) },
                    )
                }
            }
        }
        grouped.forEach { (category, categoryTasks) ->
            SectionHeader(category)
            categoryTasks
                .sortedBy { it.name }
                .forEach { task ->
                    val taskId = task.id ?: return@forEach
                    androidx.compose.runtime.key(taskId) {
                        ScheduledTaskRow(
                            task = task,
                            onRun = { onRunTask(taskId) },
                            onStop = { onStopTask(taskId) },
                        )
                    }
                }
        }
    }
}

@Composable
private fun ScheduledTaskRow(task: TaskInfo, onRun: () -> Unit, onStop: () -> Unit) {
    val isRunning = task.state == TaskState.RUNNING
    val isCancelling = task.state == TaskState.CANCELLING

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(
                            start = 16.dp,
                            end = 8.dp,
                            top = 12.dp,
                            bottom = if (isRunning) 4.dp else 12.dp,
                        ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (isRunning) {
                        Box(
                            modifier =
                                Modifier.size(6.dp).background(Color(0xFF4CAF50), CircleShape)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = task.name ?: "",
                        style =
                            MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.Medium
                            ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    task.lastExecutionResult?.let { result ->
                        Text(
                            text = formatLastRun(result.startTimeUtc, result.endTimeUtc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }

                when {
                    isCancelling ->
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp).padding(end = 8.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    isRunning ->
                        IconButton(onClick = onStop, modifier = Modifier.size(40.dp)) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_close),
                                contentDescription = stringResource(R.string.cd_stop_task),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    else ->
                        IconButton(onClick = onRun, modifier = Modifier.size(40.dp)) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_player_play_filled),
                                contentDescription = stringResource(R.string.cd_run_task),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                }
            }

            if (isRunning) {
                val progress = task.currentProgressPercentage
                Row(
                    modifier =
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = { (progress / 100.0).toFloat() },
                            modifier = Modifier.weight(1f).height(4.dp),
                            color = Color(0xFF4CAF50),
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.weight(1f).height(4.dp),
                            color = Color(0xFF4CAF50),
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        )
                    }
                    Text(
                        text = "${progress?.let { "%.1f".format(it) } ?: "0.0"}%",
                        style =
                            MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Medium
                            ),
                        color = Color(0xFF4CAF50),
                    )
                }
            }
        }
    }
}
