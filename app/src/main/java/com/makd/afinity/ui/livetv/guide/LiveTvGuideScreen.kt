@file:UnstableApi

package com.makd.afinity.ui.livetv.guide

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.makd.afinity.R
import com.makd.afinity.data.models.livetv.AfinityChannel
import com.makd.afinity.data.models.livetv.AfinityProgram
import com.makd.afinity.data.models.livetv.LiveTvViewOptions
import com.makd.afinity.navigation.LocalPlayerOffset
import com.makd.afinity.ui.components.AfinityScreen
import com.makd.afinity.ui.components.AfinityTopAppBar
import com.makd.afinity.ui.components.AfinityTopAppBarTitle
import com.makd.afinity.ui.components.AppBarProfile
import com.makd.afinity.ui.components.FilterFab
import com.makd.afinity.ui.components.FullScreenError
import com.makd.afinity.ui.components.FullScreenLoading
import com.makd.afinity.ui.livetv.components.EpgChannelCell
import com.makd.afinity.ui.livetv.components.EpgPendingRow
import com.makd.afinity.ui.livetv.components.EpgProgramRow
import com.makd.afinity.ui.livetv.components.EpgTimeHeader
import com.makd.afinity.ui.livetv.components.LiveTvFilterDialog
import com.makd.afinity.ui.livetv.components.programDayLabel
import com.makd.afinity.ui.livetv.components.rememberCurrentTime
import com.makd.afinity.ui.main.MainUiState
import com.makd.afinity.ui.player.PlayerLauncher
import com.makd.afinity.ui.theme.CardDimensions
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.distinctUntilChanged

private const val RENDER_SLOT_MINUTES = 30L
private const val RENDER_MARGIN_SLOTS = 2

@Composable
fun LiveTvGuideScreen(
    mainUiState: MainUiState,
    onSearchClick: () -> Unit,
    onProfileClick: () -> Unit,
    onChannelClick: (String) -> Unit,
    onProgramClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    viewModel: LiveTvGuideViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val viewOptions by viewModel.viewOptions.collectAsStateWithLifecycle()

    LifecycleResumeEffect(viewModel) {
        viewModel.setScreenVisible(true)
        onPauseOrDispose { viewModel.setScreenVisible(false) }
    }

    AfinityScreen(
        topBar = { backgroundOpacity ->
            AfinityTopAppBar(
                title = { AfinityTopAppBarTitle(stringResource(R.string.livetv_tab_guide)) },
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
        modifier = modifier,
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            GuideContentWithFilters(
                uiState = uiState,
                viewOptions = viewOptions,
                onChannelClick = onChannelClick,
                onProgramClick = onProgramClick,
                viewModel = viewModel,
                widthSizeClass = widthSizeClass,
            )
        }
    }
}

@Composable
private fun BoxScope.GuideContentWithFilters(
    uiState: LiveTvGuideUiState,
    viewOptions: LiveTvViewOptions,
    onChannelClick: (String) -> Unit,
    onProgramClick: (String) -> Unit,
    viewModel: LiveTvGuideViewModel,
    widthSizeClass: WindowWidthSizeClass,
) {
    val context = LocalContext.current
    val playerOffset = LocalPlayerOffset.current
    var showFilters by rememberSaveable { mutableStateOf(false) }
    val activeCount = viewOptions.activeCount(includeGuideOptions = true)

    LiveTvGuideContent(
        uiState = uiState,
        viewOptions = viewOptions,
        onChannelClick = { channel -> onChannelClick(channel.id.toString()) },
        onChannelLongClick = { channel ->
            PlayerLauncher.launchLiveChannel(
                context = context,
                channelId = channel.id,
                channelName = channel.name,
            )
        },
        onProgramClick = { program -> onProgramClick(program.id.toString()) },
        onJumpToNow = viewModel::jumpToNow,
        onSelectDay = viewModel::selectDay,
        onScrollConsumed = viewModel::onScrollConsumed,
        onVisibleChannelsChanged = viewModel::onVisibleChannelsChanged,
        onRetry = viewModel::retry,
        modifier = Modifier.fillMaxSize(),
        widthSizeClass = widthSizeClass,
    )

    FilterFab(
        active = activeCount > 0,
        onClick = { showFilters = true },
        badgeCount = activeCount,
        modifier =
            Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 16.dp + playerOffset),
    )

    if (showFilters) {
        LiveTvFilterDialog(
            options = viewOptions,
            showGuideOptions = true,
            onApply = viewModel::applyViewOptions,
            onDismiss = { showFilters = false },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LiveTvGuideContent(
    uiState: LiveTvGuideUiState,
    viewOptions: LiveTvViewOptions,
    onChannelClick: (AfinityChannel) -> Unit,
    onChannelLongClick: (AfinityChannel) -> Unit,
    onProgramClick: (AfinityProgram) -> Unit,
    onJumpToNow: () -> Unit,
    onSelectDay: (LocalDate, LocalTime) -> Unit,
    onScrollConsumed: () -> Unit,
    onVisibleChannelsChanged: (Int, Int) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
) {
    if (uiState.loadFailed && uiState.epgPrograms.isEmpty()) {
        FullScreenError(
            message = stringResource(R.string.livetv_guide_load_failed),
            modifier = modifier,
            actionText = stringResource(R.string.action_retry),
            onActionClick = onRetry,
        )
        return
    }

    val playerOffset = LocalPlayerOffset.current
    val channelCellWidth: Dp = CardDimensions.epgChannelCellWidth
    val hourWidth: Dp = CardDimensions.epgHourWidth

    val rowHeight: Dp = CardDimensions.epgRowHeight
    val headerHeight: Dp = 40.dp

    val horizontalScrollState = rememberScrollState()
    val hourWidthPx = with(LocalDensity.current) { hourWidth.toPx() }

    LaunchedEffect(uiState.scrollToTime, uiState.epgStartTime) {
        val target = uiState.scrollToTime ?: return@LaunchedEffect
        val minutes = Duration.between(uiState.epgStartTime, target).toMinutes().coerceAtLeast(0)
        horizontalScrollState.scrollTo((minutes / 60f * hourWidthPx).roundToInt())
        onScrollConsumed()
    }

    if (uiState.isEpgLoading && uiState.epgChannels.isEmpty()) {
        FullScreenLoading(modifier = modifier)
        return
    }

    val now = rememberCurrentTime()
    val renderSlots by
        remember(horizontalScrollState, hourWidthPx) {
            derivedStateOf {
                val slotPx = hourWidthPx * RENDER_SLOT_MINUTES / 60f
                val left = horizontalScrollState.value
                val right = left + horizontalScrollState.viewportSize
                (left / slotPx).toInt()..(right / slotPx).toInt()
            }
        }
    val renderStart =
        uiState.epgStartTime.plusMinutes(
            (renderSlots.first - RENDER_MARGIN_SLOTS) * RENDER_SLOT_MINUTES
        )
    val renderEnd =
        uiState.epgStartTime.plusMinutes(
            (renderSlots.last + 1 + RENDER_MARGIN_SLOTS) * RENDER_SLOT_MINUTES
        )

    val listState = rememberLazyListState()
    val currentOnVisibleChannelsChanged by rememberUpdatedState(onVisibleChannelsChanged)
    LaunchedEffect(listState) {
        snapshotFlow {
            val lastItem = listState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: 0
            (listState.firstVisibleItemIndex - 1).coerceAtLeast(0) to
                (lastItem - 1).coerceAtLeast(0)
        }
            .distinctUntilChanged()
            .collect { (first, last) -> currentOnVisibleChannelsChanged(first, last) }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            GuideDayPicker(
                days = uiState.days,
                selectedDay = uiState.selectedDay,
                onSelectDay = { day ->
                    onSelectDay(
                        day,
                        leftEdgeTime(uiState.epgStartTime, horizontalScrollState, hourWidthPx)
                            .toLocalTime(),
                    )
                },
                modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
            )

            Button(
                onClick = onJumpToNow,
                colors =
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text(stringResource(R.string.action_now))
            }
        }

        if (uiState.isWindowLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else if (uiState.loadFailed) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.livetv_guide_load_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(bottom = playerOffset),
        ) {
            stickyHeader {
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .height(headerHeight)
                            .background(MaterialTheme.colorScheme.surface)
                ) {
                    Box(
                        modifier =
                            Modifier.width(channelCellWidth)
                                .height(headerHeight)
                                .background(MaterialTheme.colorScheme.surface)
                    )
                    Box(modifier = Modifier.weight(1f).horizontalScroll(horizontalScrollState)) {
                        EpgTimeHeader(
                            startTime = uiState.epgStartTime,
                            hours = uiState.epgHours,
                            hourWidth = hourWidth,
                        )
                    }
                }
            }

            if (uiState.epgChannels.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = stringResource(R.string.livetv_empty_generic),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            items(items = uiState.epgChannels, key = { it.id }) { channel ->
                Row(modifier = Modifier.fillMaxWidth().height(rowHeight)) {
                    EpgChannelCell(
                        channel = channel,
                        onClick = { onChannelClick(channel) },
                        cellWidth = channelCellWidth,
                        cellHeight = rowHeight,
                        onLongClick = { onChannelLongClick(channel) },
                    )

                    val channelPrograms = uiState.epgPrograms[channel.id]
                    Box(modifier = Modifier.weight(1f).horizontalScroll(horizontalScrollState)) {
                        if (channelPrograms == null) {
                            EpgPendingRow(
                                epgStartTime = uiState.epgStartTime,
                                hours = uiState.epgHours,
                                hourWidth = hourWidth,
                                cellHeight = rowHeight,
                                now = now,
                                scrollState = horizontalScrollState,
                            )
                        } else {
                            EpgProgramRow(
                                programs = channelPrograms,
                                epgStartTime = uiState.epgStartTime,
                                hours = uiState.epgHours,
                                hourWidth = hourWidth,
                                cellHeight = rowHeight,
                                now = now,
                                renderStart = renderStart,
                                renderEnd = renderEnd,
                                indicators = viewOptions.indicators,
                                colorCoded = viewOptions.colorCodedBackgrounds,
                                categories = viewOptions.guideCategories,
                                onProgramClick = onProgramClick,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideDayPicker(
    days: List<LocalDate>,
    selectedDay: LocalDate,
    onSelectDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = rememberCurrentTime()
    val datePattern = stringResource(R.string.livetv_guide_date_fmt)
    val dateFormatter = remember(datePattern) { DateTimeFormatter.ofPattern(datePattern) }
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        TextButton(onClick = { expanded = true }, enabled = days.size > 1) {
            Icon(
                painter = painterResource(id = R.drawable.ic_calendar),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = dayTitle(selectedDay, now, dateFormatter),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp),
            )
            if (days.size > 1) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_keyboard_arrow_down),
                    contentDescription = null,
                    modifier = Modifier.padding(start = 4.dp).size(18.dp),
                )
            }
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            days.forEach { day ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = dayTitle(day, now, dateFormatter),
                            fontWeight = if (day == selectedDay) FontWeight.Bold else null,
                        )
                    },
                    trailingIcon =
                        if (day == selectedDay) {
                            {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_check),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        } else null,
                    onClick = {
                        expanded = false
                        if (day != selectedDay) onSelectDay(day)
                    },
                )
            }
        }
    }
}

@Composable
private fun dayTitle(day: LocalDate, now: LocalDateTime, dateFormatter: DateTimeFormatter): String {
    val label = programDayLabel(day.atStartOfDay(), now)
    val date = day.format(dateFormatter)
    return if (label == date) date else stringResource(R.string.livetv_date_time_fmt, label, date)
}

private fun leftEdgeTime(
    start: LocalDateTime,
    scrollState: ScrollState,
    hourWidthPx: Float,
): LocalDateTime = start.plusMinutes((scrollState.value / hourWidthPx * 60f).toLong())
