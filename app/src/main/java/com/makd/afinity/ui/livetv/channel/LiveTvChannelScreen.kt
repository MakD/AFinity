@file:UnstableApi

package com.makd.afinity.ui.livetv.channel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.makd.afinity.R
import com.makd.afinity.data.models.extensions.backdropBlurHash
import com.makd.afinity.data.models.extensions.backdropImageUrl
import com.makd.afinity.data.models.extensions.primaryBlurHash
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.extensions.thumbBlurHash
import com.makd.afinity.data.models.extensions.thumbImageUrl
import com.makd.afinity.data.models.livetv.AfinityChannel
import com.makd.afinity.data.models.livetv.AfinityProgram
import com.makd.afinity.navigation.LocalPlayerOffset
import com.makd.afinity.ui.components.AfinityScreen
import com.makd.afinity.ui.components.AfinityScreenMode
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.ConnectedButtonGroup
import com.makd.afinity.ui.components.FullScreenError
import com.makd.afinity.ui.components.FullScreenLoading
import com.makd.afinity.ui.components.GroupButton
import com.makd.afinity.ui.item.components.shared.DetailSectionTitle
import com.makd.afinity.ui.item.components.shared.MediaLogoHeader
import com.makd.afinity.ui.item.components.shared.detailItem
import com.makd.afinity.ui.livetv.components.LiveTvDetailFrame
import com.makd.afinity.ui.livetv.components.RecordingEventsSnackbar
import com.makd.afinity.ui.livetv.components.RecordingPromptDialog
import com.makd.afinity.ui.livetv.components.TimelineRecording
import com.makd.afinity.ui.livetv.components.programTimelineItems
import com.makd.afinity.ui.livetv.components.rememberCurrentTime
import com.makd.afinity.ui.player.PlayerLauncher
import com.makd.afinity.ui.utils.rememberTopBarOpacity

@Composable
fun LiveTvChannelScreen(
    onProgramClick: (String) -> Unit,
    onHomeClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LiveTvChannelViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val canManageLiveTv by viewModel.canManageLiveTv.collectAsStateWithLifecycle()
    val busyIds by viewModel.recorder.busyIds.collectAsStateWithLifecycle()
    val prompt by viewModel.recorder.prompt.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lazyListState = rememberLazyListState()
    val topBarOpacity by rememberTopBarOpacity(lazyListState)
    val channel = uiState.channel
    val snackbarHostState = remember { SnackbarHostState() }
    val playerOffset = LocalPlayerOffset.current

    RecordingEventsSnackbar(viewModel.recorder.events, snackbarHostState)

    AfinityScreen(
        topBar = { backgroundOpacity ->
            AfinityTopAppBar(
                title = {},
                onHomeClick = onHomeClick,
                backgroundOpacity = backgroundOpacity,
            )
        },
        modifier = modifier,
        mode = AfinityScreenMode.Hero,
        scrollOpacity = { topBarOpacity },
    ) {
        when {
            uiState.isLoading -> FullScreenLoading()

            channel == null ->
                FullScreenError(
                    message = stringResource(R.string.livetv_channel_load_failed),
                    actionText = stringResource(R.string.action_retry),
                    onActionClick = viewModel::retry,
                )

            else ->
                ChannelDetailContent(
                    channel = channel,
                    uiState = uiState,
                    lazyListState = lazyListState,
                    onWatchLive = {
                        PlayerLauncher.launchLiveChannel(
                            context = context,
                            channelId = channel.id,
                            channelName = channel.name,
                        )
                    },
                    onFavoriteClick = viewModel::toggleFavorite,
                    onProgramClick = { program -> onProgramClick(program.id.toString()) },
                    onRetry = viewModel::retry,
                    recording =
                        if (canManageLiveTv) {
                            TimelineRecording(busyIds, viewModel.recorder::onRowRecordClick)
                        } else {
                            null
                        },
                )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = playerOffset),
        )
    }

    prompt?.let { current ->
        RecordingPromptDialog(
            prompt = current,
            onCancelTimer = { viewModel.recorder.cancelTimer(current.program) },
            onCancelSeries = { viewModel.recorder.cancelSeries(current.program) },
            onDismiss = viewModel.recorder::dismissPrompt,
        )
    }
}

@Composable
private fun ChannelDetailContent(
    channel: AfinityChannel,
    uiState: LiveTvChannelUiState,
    lazyListState: LazyListState,
    onWatchLive: () -> Unit,
    onFavoriteClick: () -> Unit,
    onProgramClick: (AfinityProgram) -> Unit,
    onRetry: () -> Unit,
    recording: TimelineRecording?,
) {
    val now = rememberCurrentTime()
    val schedule = uiState.schedule.filter { it.endDate?.isAfter(now) != false }
    val endOfGuideText = stringResource(R.string.livetv_end_of_guide)
    val currentProgram = channel.currentProgram
    val artChain =
        remember(channel.id, currentProgram?.id) {
            val programImages = currentProgram?.images
            listOfNotNull(
                programImages?.backdropImageUrl?.let { it to programImages.backdropBlurHash },
                programImages?.thumbImageUrl?.let { it to programImages.thumbBlurHash },
                programImages?.primaryImageUrl?.let { it to programImages.primaryBlurHash },
                channel.images.backdropImageUrl?.let { it to channel.images.backdropBlurHash },
                channel.images.thumbImageUrl?.let { it to channel.images.thumbBlurHash },
            )
        }
    val heroChain =
        remember(artChain, channel.id) {
            artChain.ifEmpty {
                listOfNotNull(
                    channel.images.primaryImageUrl?.let { it to channel.images.primaryBlurHash }
                )
            }
        }

    LiveTvDetailFrame(
        heroChain = heroChain,
        backdropDescription = stringResource(R.string.cd_backdrop_fmt, channel.name),
        lazyListState = lazyListState,
        header = { isLandscape ->
            ChannelHeader(
                channel = channel,
                isLandscape = isLandscape,
                onWatchLive = onWatchLive,
                onFavoriteClick = onFavoriteClick,
            )
        },
        items = { horizontalPadding ->
            detailItem("schedule_title", horizontalPadding) {
                DetailSectionTitle(
                    text = stringResource(R.string.livetv_schedule),
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            when {
                uiState.isScheduleLoading && schedule.isEmpty() ->
                    item(key = "schedule_loading") {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                uiState.scheduleFailed && schedule.isEmpty() ->
                    item(key = "schedule_error") {
                        FullScreenError(
                            message = stringResource(R.string.livetv_schedule_load_failed),
                            actionText = stringResource(R.string.action_retry),
                            onActionClick = onRetry,
                        )
                    }

                schedule.isEmpty() ->
                    detailItem("schedule_empty", horizontalPadding) {
                        Text(
                            text = stringResource(R.string.livetv_schedule_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                else ->
                    programTimelineItems(
                        keyPrefix = "schedule_",
                        programs = schedule,
                        now = now,
                        horizontalPadding = horizontalPadding,
                        onProgramClick = onProgramClick,
                        recording = recording,
                        endOfGuideText = endOfGuideText,
                    )
            }
        },
    )
}

@Composable
private fun ColumnScope.ChannelHeader(
    channel: AfinityChannel,
    isLandscape: Boolean,
    onWatchLive: () -> Unit,
    onFavoriteClick: () -> Unit,
) {
    val logoUrl = channel.images.primaryImageUrl

    MediaLogoHeader(logoUrl = logoUrl, name = channel.name, isLandscape = isLandscape)

    val subtitle =
        listOfNotNull(
                channel.name.takeIf { logoUrl != null },
                channel.channelNumber?.let {
                    stringResource(R.string.livetv_channel_number_fmt, it)
                },
            )
            .joinToString(" · ")
    if (subtitle.isNotEmpty()) {
        Text(
            text = subtitle,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = if (isLandscape) TextAlign.Start else TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    ConnectedButtonGroup(
        buttons =
            listOf(
                GroupButton.Wide(
                    text = stringResource(R.string.livetv_action_watch_live),
                    icon = painterResource(id = R.drawable.ic_player_play_filled),
                    onClick = onWatchLive,
                    filled = true,
                ),
                GroupButton.IconOnly(
                    icon =
                        painterResource(
                            id =
                                if (channel.favorite) R.drawable.ic_favorite_filled
                                else R.drawable.ic_favorite
                        ),
                    contentDescription =
                        stringResource(
                            if (channel.favorite) R.string.cd_favorite_remove
                            else R.string.cd_favorite_add
                        ),
                    onClick = onFavoriteClick,
                    iconTint = if (channel.favorite) Color.Red else null,
                ),
            ),
        modifier = if (isLandscape) Modifier.widthIn(max = 480.dp) else Modifier,
    )
}
