@file:UnstableApi

package com.makd.afinity.ui.livetv.program

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.makd.afinity.R
import com.makd.afinity.data.models.extensions.backdropBlurHash
import com.makd.afinity.data.models.extensions.backdropImageUrl
import com.makd.afinity.data.models.extensions.logoImageUrlWithTransparency
import com.makd.afinity.data.models.extensions.primaryBlurHash
import com.makd.afinity.data.models.extensions.primaryImageUrl
import com.makd.afinity.data.models.extensions.thumbBlurHash
import com.makd.afinity.data.models.extensions.thumbImageUrl
import com.makd.afinity.data.models.livetv.AfinityProgram
import com.makd.afinity.data.models.livetv.GuideIndicator
import com.makd.afinity.data.models.livetv.LiveTvSimilarItem
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.navigation.Destination
import com.makd.afinity.navigation.LocalPlayerOffset
import com.makd.afinity.navigation.LocalShowRatings
import com.makd.afinity.navigation.LocalSkipServerImageResize
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.components.ConnectedButtonGroup
import com.makd.afinity.ui.components.FullScreenError
import com.makd.afinity.ui.components.FullScreenLoading
import com.makd.afinity.ui.components.GroupButton
import com.makd.afinity.ui.components.MediaItemCard
import com.makd.afinity.ui.components.formatRuntimeTicks
import com.makd.afinity.ui.components.ratings.communityRatingOf
import com.makd.afinity.ui.item.components.shared.DetailSectionTitle
import com.makd.afinity.ui.item.components.shared.ExternalLinksSection
import com.makd.afinity.ui.item.components.shared.MediaLogoHeader
import com.makd.afinity.ui.item.components.shared.MetadataDot
import com.makd.afinity.ui.item.components.shared.MetadataFlowRow
import com.makd.afinity.ui.item.components.shared.MetadataText
import com.makd.afinity.ui.item.components.shared.MetadataTimeLeft
import com.makd.afinity.ui.item.components.shared.OverviewSection
import com.makd.afinity.ui.item.components.shared.RatingsAndReviews
import com.makd.afinity.ui.item.components.shared.VideoMetadataChip
import com.makd.afinity.ui.item.components.shared.detailItem
import com.makd.afinity.ui.livetv.components.LiveBadge
import com.makd.afinity.ui.livetv.components.LiveTvDetailFrame
import com.makd.afinity.ui.livetv.components.ProgramCard
import com.makd.afinity.ui.livetv.components.ProgramIndicatorBadge
import com.makd.afinity.ui.livetv.components.RecordingEventsSnackbar
import com.makd.afinity.ui.livetv.components.RecordingOptionsDialog
import com.makd.afinity.ui.livetv.components.RecordingPromptDialog
import com.makd.afinity.ui.livetv.components.RecordingRed
import com.makd.afinity.ui.livetv.components.SeriesOptionsSheet
import com.makd.afinity.ui.livetv.components.TimelineRecording
import com.makd.afinity.ui.livetv.components.minutesToTicks
import com.makd.afinity.ui.livetv.components.programDuration
import com.makd.afinity.ui.livetv.components.programEpisodeNumber
import com.makd.afinity.ui.livetv.components.programTimeRange
import com.makd.afinity.ui.livetv.components.programTimelineItems
import com.makd.afinity.ui.livetv.components.rememberCurrentTime
import com.makd.afinity.ui.livetv.components.rememberProgramTimeFormatter
import com.makd.afinity.ui.player.PlayerLauncher
import com.makd.afinity.ui.theme.CardDimensions.landscapeWidth
import com.makd.afinity.ui.theme.CardDimensions.portraitWidth
import com.makd.afinity.ui.utils.rememberTopBarOpacity
import com.makd.afinity.util.DateSkeleton
import com.makd.afinity.util.localizedDateFormatter
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import org.jellyfin.sdk.model.api.ProgramAudio
import org.jellyfin.sdk.model.api.RecordingStatus

@Composable
fun LiveTvProgramScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    viewModel: LiveTvProgramViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val indicators by viewModel.indicators.collectAsStateWithLifecycle()
    val canManageLiveTv by viewModel.canManageLiveTv.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lazyListState = rememberLazyListState()
    val topBarOpacity by rememberTopBarOpacity(lazyListState)
    val program = uiState.program
    val busyIds by viewModel.recorder.busyIds.collectAsStateWithLifecycle()
    val prompt by viewModel.recorder.prompt.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val playerOffset = LocalPlayerOffset.current

    RecordingEventsSnackbar(viewModel.recorder.events, snackbarHostState)

    Box(modifier = modifier.fillMaxSize()) {
        when {
            uiState.isLoading -> FullScreenLoading()

            program == null ->
                FullScreenError(
                    message = stringResource(R.string.livetv_program_load_failed),
                    actionText = stringResource(R.string.action_retry),
                    onActionClick = viewModel::retry,
                )

            else ->
                ProgramDetailContent(
                    program = program,
                    upNext = uiState.upNext,
                    similarItems = uiState.similarItems,
                    indicators = indicators,
                    recording =
                        RecordingActions(
                            canManage = canManageLiveTv,
                            busyIds = busyIds,
                            onToggle = viewModel::toggleRecording,
                            onToggleSeries = viewModel::toggleSeriesRecording,
                            onOptions = viewModel::openRecordingOptions,
                            onRowRecord = viewModel.recorder::onRowRecordClick,
                        ),
                    lazyListState = lazyListState,
                    widthSizeClass = widthSizeClass,
                    onWatchLive = {
                        PlayerLauncher.launchLiveChannel(
                            context = context,
                            channelId = program.channelId,
                            channelName = program.channelName ?: program.name,
                        )
                    },
                    onChannelClick = {
                        navController.navigate(
                            Destination.createLiveTvChannelRoute(program.channelId.toString())
                        )
                    },
                    onProgramClick = { next ->
                        navController.navigate(
                            Destination.createLiveTvProgramRoute(next.id.toString())
                        )
                    },
                    onSimilarItemClick = { related ->
                        navController.navigate(
                            Destination.createItemDetailRoute(
                                itemId = related.id.toString(),
                                itemType =
                                    when (related) {
                                        is AfinityShow -> "Series"
                                        is AfinitySeason -> "Season"
                                        else -> null
                                    },
                                seriesId = (related as? AfinitySeason)?.seriesId?.toString(),
                            )
                        )
                    },
                )
        }

        AfinityTopAppBar(
            title = {},
            onHomeClick = {
                navController.navigate(Destination.HOME.route) {
                    popUpTo(Destination.HOME.route) { inclusive = false }
                    launchSingleTop = true
                }
            },
            backgroundOpacity = { topBarOpacity },
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier.align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = playerOffset),
        )
    }

    uiState.recordingOptions?.let { padding ->
        RecordingOptionsDialog(
            padding = padding,
            onSave = viewModel::saveRecordingOptions,
            onCancelRecording = viewModel::toggleRecording,
            onDismiss = viewModel::dismissRecordingOptions,
        )
    }

    uiState.seriesOptions?.let { options ->
        SeriesOptionsSheet(
            options = options,
            subtitle =
                listOfNotNull(program?.name, program?.channelName)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                    .ifEmpty { null },
            onSave = viewModel::saveSeriesOptions,
            onCancelSeries = viewModel::cancelSeriesFromOptions,
            onDismiss = viewModel::dismissSeriesOptions,
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

private class RecordingActions(
    val canManage: Boolean,
    val busyIds: Set<UUID>,
    val onToggle: () -> Unit,
    val onToggleSeries: () -> Unit,
    val onOptions: () -> Unit,
    val onRowRecord: (AfinityProgram) -> Unit,
)

@Composable
private fun ProgramDetailContent(
    program: AfinityProgram,
    upNext: List<AfinityProgram>,
    similarItems: List<LiveTvSimilarItem>,
    indicators: Set<GuideIndicator>,
    recording: RecordingActions,
    lazyListState: LazyListState,
    widthSizeClass: WindowWidthSizeClass,
    onWatchLive: () -> Unit,
    onChannelClick: () -> Unit,
    onProgramClick: (AfinityProgram) -> Unit,
    onSimilarItemClick: (AfinityItem) -> Unit,
) {
    val now = rememberCurrentTime()
    val heroChain =
        remember(program.id) {
            listOfNotNull(
                program.images.backdropImageUrl?.let { it to program.images.backdropBlurHash },
                program.images.thumbImageUrl?.let { it to program.images.thumbBlurHash },
                program.images.primaryImageUrl?.let { it to program.images.primaryBlurHash },
            )
        }

    LiveTvDetailFrame(
        heroChain = heroChain,
        backdropDescription = stringResource(R.string.cd_backdrop_fmt, program.name),
        lazyListState = lazyListState,
        header = { isLandscape ->
            ProgramHeader(
                program = program,
                indicators = indicators,
                recording = recording,
                now = now,
                isLandscape = isLandscape,
                onWatchLive = onWatchLive,
                onChannelClick = onChannelClick,
            )
        },
        items = { horizontalPadding ->
            programDetailItems(
                program = program,
                upNext = upNext,
                similarItems = similarItems,
                now = now,
                horizontalPadding = horizontalPadding,
                widthSizeClass = widthSizeClass,
                recording = recording,
                onProgramClick = onProgramClick,
                onSimilarItemClick = onSimilarItemClick,
            )
        },
    )
}

@Composable
private fun ColumnScope.ProgramHeader(
    program: AfinityProgram,
    indicators: Set<GuideIndicator>,
    recording: RecordingActions,
    now: LocalDateTime,
    isLandscape: Boolean,
    onWatchLive: () -> Unit,
    onChannelClick: () -> Unit,
) {
    val isAiring = program.isCurrentlyAiring(now)
    val textAlign = if (isLandscape) TextAlign.Start else TextAlign.Center
    val webpLogo = !LocalSkipServerImageResize.current

    MediaLogoHeader(
        logoUrl = program.images.logoImageUrlWithTransparency(webp = webpLogo),
        name = program.name,
        isLandscape = isLandscape,
    )

    program.seriesName
        ?.takeIf { it.isNotBlank() && it != program.name }
        ?.let { seriesName ->
            Text(
                text = seriesName,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth(),
            )
        }

    program.episodeTitle
        ?.takeIf { it.isNotBlank() }
        ?.let { episodeTitle ->
            Text(
                text = episodeTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth(),
            )
        }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        ProgramBadges(program = program, isAiring = isAiring, indicators = indicators)
        ProgramMetadataLine(program = program, isAiring = isAiring, now = now)

        program.originalAirDate?.let { airDate ->
            val formatter = remember {
                localizedDateFormatter(Locale.getDefault(), DateSkeleton.MONTH_DAY_YEAR)
            }
            Text(
                text =
                    stringResource(
                        R.string.livetv_original_air_date_fmt,
                        airDate.format(formatter),
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = textAlign,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    ProgramChannelLine(
        program = program,
        isLandscape = isLandscape,
        onChannelClick = onChannelClick,
    )

    if (!isAiring) {
        ProgramStartsIn(program = program, now = now, isLandscape = isLandscape)
    }

    ProgramActionGroup(
        program = program,
        isAiring = isAiring,
        recording = recording,
        onWatchLive = onWatchLive,
        modifier = if (isLandscape) Modifier.widthIn(max = 480.dp) else Modifier,
    )
}

@Composable
private fun ProgramActionGroup(
    program: AfinityProgram,
    isAiring: Boolean,
    recording: RecordingActions,
    onWatchLive: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val busy = program.id in recording.busyIds
    val hasTimer = program.hasActiveTimer
    val hasSeriesTimer = program.seriesTimerId != null
    val recordLabel =
        stringResource(
            when {
                !hasTimer -> R.string.livetv_action_record
                program.timerStatus == RecordingStatus.IN_PROGRESS -> R.string.livetv_stop_recording
                else -> R.string.livetv_do_not_record
            }
        )
    val recordIcon = painterResource(id = R.drawable.ic_record)
    val recordTint = if (hasTimer) RecordingRed else null
    val onRecord = recording.onToggle.takeUnless { busy }

    val buttons = buildList {
        if (isAiring) {
            add(
                GroupButton.Wide(
                    text = stringResource(R.string.livetv_action_watch_live),
                    icon = painterResource(id = R.drawable.ic_player_play_filled),
                    onClick = onWatchLive,
                    filled = true,
                )
            )
        }
        if (recording.canManage) {
            if (isAiring) {
                add(GroupButton.IconOnly(recordIcon, recordLabel, onRecord, recordTint))
            } else {
                add(GroupButton.Wide(recordLabel, recordIcon, onRecord, iconTint = recordTint))
            }
            if (program.isSeries) {
                add(
                    GroupButton.IconOnly(
                        icon = painterResource(id = R.drawable.ic_record_series),
                        contentDescription =
                            stringResource(
                                if (hasSeriesTimer) R.string.livetv_cancel_series
                                else R.string.livetv_action_record_series
                            ),
                        onClick = recording.onToggleSeries.takeUnless { busy },
                        iconTint = if (hasSeriesTimer) RecordingRed else null,
                    )
                )
            }
            if (hasTimer || hasSeriesTimer) {
                add(
                    GroupButton.IconOnly(
                        icon = painterResource(id = R.drawable.ic_settings),
                        contentDescription =
                            stringResource(
                                if (hasSeriesTimer) R.string.livetv_series_settings
                                else R.string.livetv_recording_options
                            ),
                        onClick = recording.onOptions.takeUnless { busy },
                    )
                )
            }
        }
    }

    ConnectedButtonGroup(buttons = buttons, modifier = modifier)
}

@Composable
private fun ProgramBadges(
    program: AfinityProgram,
    isAiring: Boolean,
    indicators: Set<GuideIndicator>,
) {
    val showLive = program.isLive && !isAiring && GuideIndicator.LIVE in indicators
    val showPremiere = program.isPremiere && GuideIndicator.PREMIERE in indicators
    val showNew = program.isNew && GuideIndicator.NEW in indicators
    val showRepeat = program.isRepeat && GuideIndicator.REPEAT in indicators
    val showHd = program.isHd && GuideIndicator.HD in indicators
    val audio = program.audio?.let(::audioLabel)
    val tags = program.tags

    if (
        !showLive &&
            !showPremiere &&
            !showNew &&
            !showRepeat &&
            !showHd &&
            audio == null &&
            tags.isEmpty()
    ) {
        return
    }

    MetadataFlowRow {
        if (showLive) ProgramIndicatorBadge(GuideIndicator.LIVE)
        if (showPremiere) ProgramIndicatorBadge(GuideIndicator.PREMIERE)
        if (showNew) ProgramIndicatorBadge(GuideIndicator.NEW)
        if (showRepeat) ProgramIndicatorBadge(GuideIndicator.REPEAT)
        if (showHd) VideoMetadataChip(stringResource(R.string.meta_res_hd))
        audio?.let { VideoMetadataChip(it) }
        tags.forEach { tag -> VideoMetadataChip(tag) }
    }
}

@Composable
private fun ProgramMetadataLine(program: AfinityProgram, isAiring: Boolean, now: LocalDateTime) {
    val datePattern = stringResource(R.string.livetv_guide_date_fmt)
    val dateFormatter = remember(datePattern) { DateTimeFormatter.ofPattern(datePattern) }
    val timeRange = programTimeRange(program, rememberProgramTimeFormatter())
    val dateTime =
        program.startDate?.let { start ->
            timeRange?.let {
                stringResource(R.string.livetv_date_time_fmt, start.format(dateFormatter), it)
            }
        }
    val leadingParts =
        listOfNotNull(
            programEpisodeNumber(program),
            dateTime,
            programDuration(program),
            program.productionYear?.toString(),
            program.officialRating,
        )
    val genres = program.genres.take(2).joinToString(", ").takeIf { it.isNotEmpty() }

    MetadataFlowRow {
        var needsSeparator = false
        val start = program.startDate
        val end = program.endDate
        if (isAiring && start != null && end != null) {
            LiveBadge()
            MetadataTimeLeft(
                progress = program.getProgressPercent(now) / 100f,
                remainingTicks = minutesToTicks(Duration.between(now, end).toMinutes()),
            )
            needsSeparator = true
        }

        leadingParts.forEach { part ->
            if (needsSeparator) MetadataDot()
            MetadataText(part)
            needsSeparator = true
        }

        genres?.let {
            if (needsSeparator) MetadataDot()
            MetadataText(it)
        }
    }
}

@Composable
private fun ProgramChannelLine(
    program: AfinityProgram,
    isLandscape: Boolean,
    onChannelClick: () -> Unit,
) {
    val channelName = program.channelName ?: return

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isLandscape) Alignment.CenterStart else Alignment.Center,
    ) {
        Row(
            modifier =
                Modifier.clip(MaterialTheme.shapes.small)
                    .clickable(role = Role.Button, onClick = onChannelClick)
                    .padding(vertical = 4.dp, horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProgramChannelLineContent(program = program, channelName = channelName)
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun RowScope.ProgramChannelLineContent(program: AfinityProgram, channelName: String) {
    program.channelLogo?.let { logo ->
        AsyncImage(
            imageUrl = logo.toString(),
            contentDescription = null,
            modifier = Modifier.size(width = 40.dp, height = 24.dp),
            contentScale = ContentScale.Fit,
            targetWidth = 40.dp,
            targetHeight = 24.dp,
        )
    }
    program.channelNumber?.let { number ->
        Text(
            text = number,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    Text(
        text = channelName,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f, fill = false),
    )
}

@Composable
private fun ProgramStartsIn(program: AfinityProgram, now: LocalDateTime, isLandscape: Boolean) {
    val start = program.startDate ?: return
    val minutesUntil = Duration.between(now, start).toMinutes()
    if (minutesUntil < 0 || minutesUntil >= MINUTES_PER_DAY) return

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.spacedBy(
                8.dp,
                if (isLandscape) Alignment.Start else Alignment.CenterHorizontally,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_clock),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text =
                stringResource(
                    R.string.livetv_starts_in_fmt,
                    formatRuntimeTicks(minutesToTicks(minutesUntil.coerceAtLeast(1))),
                ),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

private fun LazyListScope.programDetailItems(
    program: AfinityProgram,
    upNext: List<AfinityProgram>,
    similarItems: List<LiveTvSimilarItem>,
    now: LocalDateTime,
    horizontalPadding: Dp,
    widthSizeClass: WindowWidthSizeClass,
    recording: RecordingActions,
    onProgramClick: (AfinityProgram) -> Unit,
    onSimilarItemClick: (AfinityItem) -> Unit,
) {
    detailItem("overview", horizontalPadding) { OverviewSection(overview = program.overview) }

    detailItem("external_links", horizontalPadding) {
        ExternalLinksSection(externalUrls = program.externalUrls)
    }

    communityRatingOf(program.communityRating)?.let { rating ->
        detailItem("ratings", horizontalPadding) {
            if (LocalShowRatings.current) RatingsAndReviews(orderedRatings = listOf(rating))
        }
    }

    if (upNext.isNotEmpty()) {
        detailItem("up_next", horizontalPadding) {
            DetailSectionTitle(
                text =
                    program.channelName?.let { stringResource(R.string.livetv_up_next_on_fmt, it) }
                        ?: stringResource(R.string.livetv_up_next),
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        programTimelineItems(
            keyPrefix = "up_next_",
            programs = upNext,
            now = now,
            horizontalPadding = horizontalPadding,
            onProgramClick = onProgramClick,
            recording =
                if (recording.canManage) {
                    TimelineRecording(recording.busyIds, recording.onRowRecord)
                } else {
                    null
                },
        )
    }

    if (similarItems.isNotEmpty()) {
        detailItem("similar", horizontalPadding) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DetailSectionTitle(text = stringResource(R.string.similar_items_title))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(items = similarItems, key = { it.id.toString() }) { similar ->
                        when (similar) {
                            is LiveTvSimilarItem.Program ->
                                ProgramCard(
                                    program = similar.program,
                                    onClick = { onProgramClick(similar.program) },
                                    cardWidth = widthSizeClass.landscapeWidth,
                                )
                            is LiveTvSimilarItem.Library ->
                                MediaItemCard(
                                    item = similar.item,
                                    onClick = { onSimilarItemClick(similar.item) },
                                    cardWidth = widthSizeClass.portraitWidth,
                                )
                        }
                    }
                }
            }
        }
    }
}

private fun audioLabel(audio: ProgramAudio): String =
    when (audio) {
        ProgramAudio.MONO -> "Mono"
        ProgramAudio.STEREO -> "Stereo"
        ProgramAudio.DOLBY -> "Dolby"
        ProgramAudio.DOLBY_DIGITAL -> "Dolby Digital"
        ProgramAudio.THX -> "THX"
        ProgramAudio.ATMOS -> "Atmos"
    }

private const val MINUTES_PER_DAY = 24 * 60L
