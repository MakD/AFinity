@file:UnstableApi

package com.makd.afinity.ui.livetv.recordings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.makd.afinity.R
import com.makd.afinity.data.models.extensions.backdropImageUrl
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.extensions.thumbImageUrl
import com.makd.afinity.data.models.livetv.AfinityProgram
import com.makd.afinity.data.models.livetv.AfinityRecording
import com.makd.afinity.data.models.livetv.SeriesRecordingOptions
import com.makd.afinity.data.models.livetv.SeriesRecordingRule
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.navigation.LocalPlayerOffset
import com.makd.afinity.ui.components.AfinityScreen
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.AfinityTopAppBarTitle
import com.makd.afinity.ui.components.AppBarProfile
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.components.DeleteItemDialog
import com.makd.afinity.ui.components.EpisodeOverlayHandler
import com.makd.afinity.ui.components.FullScreenError
import com.makd.afinity.ui.components.FullScreenLoading
import com.makd.afinity.ui.components.formatRuntimeTicks
import com.makd.afinity.ui.item.DeleteOutcome
import com.makd.afinity.ui.item.components.shared.DetailSectionTitle
import com.makd.afinity.ui.livetv.components.RecordingEventsSnackbar
import com.makd.afinity.ui.livetv.components.RecordingPromptDialog
import com.makd.afinity.ui.livetv.components.SeriesOptionsSheet
import com.makd.afinity.ui.livetv.components.TimelineScheduled
import com.makd.afinity.ui.livetv.components.programTimelineItems
import com.makd.afinity.ui.livetv.components.rememberCurrentTime
import com.makd.afinity.ui.livetv.components.rememberProgramTimeFormatter
import com.makd.afinity.ui.main.MainUiState
import com.makd.afinity.util.DateSkeleton
import com.makd.afinity.util.localizedDateFormatter
import java.util.Locale
import java.util.UUID

private val ScreenPadding = 16.dp

@Composable
fun LiveTvRecordingsScreen(
    mainUiState: MainUiState,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    onProgramClick: (String) -> Unit,
    onItemClick: (AfinityItem) -> Unit,
    onSeriesClick: (String) -> Unit,
    onPersonClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LiveTvRecordingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val canManage by viewModel.canManageLiveTv.collectAsStateWithLifecycle()
    val busyIds by viewModel.recorder.busyIds.collectAsStateWithLifecycle()
    val prompt by viewModel.recorder.prompt.collectAsStateWithLifecycle()
    val selectedEpisode by viewModel.episodeSelection.selected.collectAsStateWithLifecycle()
    val watchlistStatus by viewModel.episodeSelection.watchlistStatus.collectAsStateWithLifecycle()
    val episodeDownload by viewModel.episodeSelection.downloadInfo.collectAsStateWithLifecycle()
    val isDownloadAllowedByServer by
        viewModel.isDownloadAllowedByServer.collectAsStateWithLifecycle()
    val canDownloadOnNetwork by viewModel.canDownloadOnNetwork.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val playerOffset = LocalPlayerOffset.current
    var deleteTarget by remember { mutableStateOf<AfinityRecording?>(null) }

    RecordingEventsSnackbar(viewModel.recorder.events, snackbarHostState)

    LifecycleResumeEffect(viewModel) {
        viewModel.setScreenVisible(true)
        onPauseOrDispose { viewModel.setScreenVisible(false) }
    }

    AfinityScreen(
        topBar = { backgroundOpacity ->
            AfinityTopAppBar(
                title = { AfinityTopAppBarTitle(stringResource(R.string.livetv_tab_recordings)) },
                onSearchClick = onSearchClick,
                profile =
                    AppBarProfile(
                        onClick = onProfileClick,
                        name = mainUiState.userName,
                        imageUrl = mainUiState.userProfileImageUrl,
                    ),
                backgroundOpacity = backgroundOpacity,
            )
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = playerOffset),
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        when {
            uiState.isLoading -> FullScreenLoading(modifier = Modifier.padding(paddingValues))

            uiState.loadFailed ->
                FullScreenError(
                    message = stringResource(R.string.livetv_recordings_load_failed),
                    actionText = stringResource(R.string.action_retry),
                    onActionClick = viewModel::retry,
                    modifier = Modifier.padding(paddingValues),
                )

            else ->
                RecordingsContent(
                    uiState = uiState,
                    canManage = canManage,
                    busyIds = busyIds,
                    onProgramClick = { program -> onProgramClick(program.id.toString()) },
                    onCancelClick = viewModel.recorder::confirmCancel,
                    onSeriesRuleClick = viewModel::openSeriesRule,
                    onRecordingClick = { recording ->
                        when (val item = recording.item) {
                            is AfinityEpisode -> viewModel.episodeSelection.select(item)
                            else -> onItemClick(item)
                        }
                    },
                    onDeleteClick = { deleteTarget = it },
                    modifier = Modifier.padding(paddingValues),
                )
        }
    }

    prompt?.let { current ->
        RecordingPromptDialog(
            prompt = current,
            onCancelTimer = { viewModel.recorder.cancelTimer(current.program) },
            onCancelSeries = { viewModel.recorder.cancelSeries(current.program) },
            onDismiss = viewModel.recorder::dismissPrompt,
        )
    }

    uiState.openSeriesRule?.let { rule ->
        SeriesOptionsSheet(
            options = rule.options,
            subtitle =
                listOfNotNull(rule.name, rule.options.channelName)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                    .ifEmpty { null },
            onSave = viewModel::saveSeriesRule,
            onCancelSeries = viewModel::cancelSeriesRule,
            onDismiss = viewModel::dismissSeriesRule,
        )
    }

    deleteTarget?.let { target ->
        DeleteRecordingDialog(
            recording = target,
            onDelete = viewModel::deleteRecording,
            onDismiss = { deleteTarget = null },
        )
    }

    EpisodeOverlayHandler(
        selectedEpisode = selectedEpisode,
        watchlistStatus = watchlistStatus,
        downloadInfo = episodeDownload,
        isDownloadAllowedByServer = isDownloadAllowedByServer,
        canDownloadOnNetwork = canDownloadOnNetwork,
        onClearSelection = viewModel.episodeSelection::clear,
        onToggleFavorite = viewModel.episodeSelection::toggleFavorite,
        onToggleWatchlist = viewModel.episodeSelection::toggleWatchlist,
        onToggleWatched = viewModel.episodeSelection::toggleWatched,
        onNavigateToSeries = onSeriesClick,
        onNavigateToPerson = onPersonClick,
    )
}

@Composable
private fun RecordingsContent(
    uiState: LiveTvRecordingsUiState,
    canManage: Boolean,
    busyIds: Set<UUID>,
    onProgramClick: (AfinityProgram) -> Unit,
    onCancelClick: (AfinityProgram) -> Unit,
    onSeriesRuleClick: (SeriesRecordingRule) -> Unit,
    onRecordingClick: (AfinityRecording) -> Unit,
    onDeleteClick: (AfinityRecording) -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = rememberCurrentTime()
    val playerOffset = LocalPlayerOffset.current
    val scheduledTitle = stringResource(R.string.livetv_scheduled)
    val seriesTitle = stringResource(R.string.livetv_series)
    val recordedTitle = stringResource(R.string.livetv_recorded)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp + playerOffset),
    ) {
        if (uiState.scheduled.isNotEmpty()) {
            item(key = "scheduled_title") { SectionTitle(scheduledTitle) }
            programTimelineItems(
                keyPrefix = "scheduled_",
                programs = uiState.scheduled,
                now = now,
                horizontalPadding = ScreenPadding,
                onProgramClick = onProgramClick,
                recording = null,
                scheduled =
                    TimelineScheduled(
                        busyIds = busyIds,
                        onCancelClick = onCancelClick.takeIf { canManage },
                    ),
            )
        }

        if (uiState.seriesRules.isNotEmpty()) {
            item(key = "series_title") { SectionTitle(seriesTitle) }
            items(uiState.seriesRules, key = { "series_${it.id}" }) { rule ->
                SeriesRuleRow(
                    rule = rule,
                    onClick = if (canManage) ({ onSeriesRuleClick(rule) }) else null,
                )
            }
        }

        item(key = "recorded_title") { SectionTitle(recordedTitle) }
        if (uiState.recordings.isEmpty()) {
            item(key = "recorded_empty") { NoRecordingsCard() }
        } else {
            items(uiState.recordings, key = { "recorded_${it.item.id}" }) { recording ->
                RecordedRow(
                    recording = recording,
                    onClick = { onRecordingClick(recording) },
                    onDeleteClick =
                        if (recording.canDelete) ({ onDeleteClick(recording) }) else null,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    DetailSectionTitle(
        text = text,
        modifier =
            Modifier.padding(
                start = ScreenPadding,
                end = ScreenPadding,
                top = 16.dp,
                bottom = 12.dp,
            ),
    )
}

@Composable
private fun SeriesRuleRow(rule: SeriesRecordingRule, onClick: (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier =
            Modifier.fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(start = ScreenPadding, end = 8.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Box(
            modifier =
                Modifier.size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            AsyncImage(
                imageUrl = rule.imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rule.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = seriesRuleSummary(rule.options),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onClick != null) {
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(12.dp).size(20.dp),
            )
        }
    }
}

@Composable
private fun seriesRuleSummary(options: SeriesRecordingOptions): String {
    val timeFormatter = rememberProgramTimeFormatter()
    val startDate = options.startDate
    return listOfNotNull(
            stringResource(
                if (options.recordNewOnly) R.string.livetv_new_episodes_only
                else R.string.livetv_all_episodes
            ),
            when {
                options.recordAnyChannel -> stringResource(R.string.livetv_all_channels)
                options.channelName != null ->
                    stringResource(R.string.livetv_channel_name_only, options.channelName)
                else -> stringResource(R.string.livetv_one_channel)
            },
            when {
                options.recordAnyTime -> stringResource(R.string.livetv_anytime)
                startDate != null ->
                    stringResource(R.string.livetv_around_time, startDate.format(timeFormatter))
                else -> null
            },
        )
        .joinToString(" · ")
}

@Composable
private fun RecordedRow(
    recording: AfinityRecording,
    onClick: () -> Unit,
    onDeleteClick: (() -> Unit)?,
) {
    val item = recording.item
    val episode = item as? AfinityEpisode
    val title = episode?.seriesName?.takeIf { it.isNotBlank() } ?: item.name
    val timeFormatter = rememberProgramTimeFormatter()
    val dateFormatter = remember {
        localizedDateFormatter(Locale.getDefault(), DateSkeleton.WEEKDAY_MONTH_DAY)
    }
    val recordedAt = recording.recordedAt
    val subtitle =
        listOfNotNull(
                episode?.let {
                    stringResource(
                        R.string.playlist_entry_episode_fmt,
                        it.parentIndexNumber,
                        it.indexNumber,
                    )
                },
                recordedAt?.format(dateFormatter),
                recordedAt?.format(timeFormatter),
                item.runtimeTicks.takeIf { it > 0 }?.let { formatRuntimeTicks(it) },
            )
            .joinToString(" · ")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier =
            Modifier.fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = ScreenPadding, end = 4.dp, top = 10.dp, bottom = 10.dp),
    ) {
        Box(
            modifier =
                Modifier.size(width = 112.dp, height = 63.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            AsyncImage(
                imageUrl =
                    item.images.thumbImageUrl
                        ?: item.images.backdropImageUrl
                        ?: item.images.primaryImageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onDeleteClick != null) {
            IconButton(onClick = onDeleteClick) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.admin_action_delete),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun NoRecordingsCard() {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_folder),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(32.dp),
            )
            Text(
                text = stringResource(R.string.livetv_no_recordings),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.livetv_no_recordings_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun DeleteRecordingDialog(
    recording: AfinityRecording,
    onDelete: (UUID, (DeleteOutcome) -> Unit) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var deleting by remember(recording.item.id) { mutableStateOf(false) }
    var errorRes by remember(recording.item.id) { mutableStateOf<Int?>(null) }

    DeleteItemDialog(
        targetName = recording.item.name,
        message = stringResource(R.string.admin_delete_message_item),
        deleting = deleting,
        errorRes = errorRes,
        onConfirm = {
            deleting = true
            errorRes = null
            onDelete(recording.item.id) { outcome ->
                deleting = false
                when (outcome) {
                    DeleteOutcome.DELETED -> {
                        Toast.makeText(context, R.string.admin_delete_success, Toast.LENGTH_SHORT)
                            .show()
                        onDismiss()
                    }

                    DeleteOutcome.STILL_RUNNING -> {
                        Toast.makeText(
                                context,
                                R.string.admin_delete_still_running,
                                Toast.LENGTH_LONG,
                            )
                            .show()
                        onDismiss()
                    }

                    DeleteOutcome.NOT_ALLOWED -> errorRes = R.string.admin_delete_not_allowed

                    DeleteOutcome.FAILED -> errorRes = R.string.admin_delete_failed
                }
            }
        },
        onDismiss = onDismiss,
    )
}
