package com.makd.afinity.ui.admin.activity

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.makd.afinity.R
import com.makd.afinity.data.models.admin.ActivityEntry
import com.makd.afinity.data.models.admin.ActivityFilter
import com.makd.afinity.data.models.admin.ActivityToday
import com.makd.afinity.ui.admin.components.AdminScaffold
import com.makd.afinity.ui.components.FullScreenEmpty
import com.makd.afinity.ui.components.FullScreenError
import com.makd.afinity.ui.components.FullScreenLoading
import com.makd.afinity.ui.settings.logs.LogLevelColors
import com.makd.afinity.ui.settings.logs.LogTextStyles
import com.makd.afinity.ui.settings.servers.components.SectionHeader
import com.makd.afinity.util.logging.LogLevel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val TIME_COLUMN_WIDTH = 56.dp
private val TIME_GUTTER = 12.dp
private val MARKER_SIZE = 28.dp
private val MARKER_ICON_SIZE = 15.dp
private val TEXT_GUTTER = 14.dp
private val ROW_VERTICAL_PADDING = 11.dp
private val ROW_HORIZONTAL_PADDING = 8.dp
private val RAIL_WIDTH = 1.dp
private const val MARKER_TINT_ALPHA = 0.16f
private const val LIGHT_SURFACE_LUMINANCE = 0.5f
private const val ALERT_ROW_ALPHA_ON_LIGHT = 0.08f
private val ChangeOnLight = Color(0xFF8A5A00)

private enum class ActivityTone {
    ALERT,
    CHANGE,
    PLAYBACK,
    SIGN_IN,
    QUIET,
}

private enum class ActivityKind(
    @param:DrawableRes val icon: Int,
    @param:StringRes val label: Int,
    val tone: ActivityTone,
) {
    PLAYBACK_STARTED(
        R.drawable.ic_player_play_filled,
        R.string.activity_kind_playback,
        ActivityTone.PLAYBACK,
    ),
    PLAYBACK_STOPPED(
        R.drawable.ic_player_stop_filled,
        R.string.activity_kind_playback,
        ActivityTone.PLAYBACK,
    ),
    SESSION_STARTED(R.drawable.ic_login, R.string.activity_kind_session, ActivityTone.QUIET),
    SESSION_ENDED(R.drawable.ic_logout, R.string.activity_kind_session, ActivityTone.QUIET),
    SIGN_IN(R.drawable.ic_security, R.string.activity_kind_sign_in, ActivityTone.SIGN_IN),
    USER(R.drawable.ic_user, R.string.activity_kind_user, ActivityTone.CHANGE),
    PLUGIN(R.drawable.ic_puzzle, R.string.activity_kind_plugin, ActivityTone.CHANGE),
    TASK(R.drawable.ic_schedule, R.string.activity_kind_task, ActivityTone.CHANGE),
    OTHER(R.drawable.ic_info, R.string.activity_kind_server, ActivityTone.QUIET),
}

@Composable
@ReadOnlyComposable
private fun isLightSurface(): Boolean =
    MaterialTheme.colorScheme.surface.luminance() > LIGHT_SURFACE_LUMINANCE

@Composable
@ReadOnlyComposable
private fun ActivityTone.color(): Color =
    when (this) {
        ActivityTone.ALERT ->
            if (isLightSurface()) MaterialTheme.colorScheme.error
            else LogLevelColors.content(LogLevel.ERROR)
        ActivityTone.CHANGE ->
            if (isLightSurface()) ChangeOnLight else LogLevelColors.content(LogLevel.WARN)
        ActivityTone.PLAYBACK -> MaterialTheme.colorScheme.primary
        ActivityTone.SIGN_IN -> MaterialTheme.colorScheme.tertiary
        ActivityTone.QUIET -> MaterialTheme.colorScheme.onSurfaceVariant
    }

@Composable
@ReadOnlyComposable
private fun alertRowTint(): Color =
    if (isLightSurface()) MaterialTheme.colorScheme.error.copy(alpha = ALERT_ROW_ALPHA_ON_LIGHT)
    else LogLevelColors.rowTint(LogLevel.ERROR)

@Composable
fun ActivityScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActivityViewModel = hiltViewModel(),
) {
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val today by viewModel.today.collectAsStateWithLifecycle()
    val hasNewActivity by viewModel.hasNewActivity.collectAsStateWithLifecycle()
    val items = viewModel.items.collectAsLazyPagingItems()

    AdminScaffold(
        title = stringResource(R.string.title_activity),
        onBackClick = onBackClick,
        modifier = modifier,
    ) { contentPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            ActivityTiles(today = today, selected = filter, onSelect = viewModel::toggleFilter)

            if (hasNewActivity) {
                NewActivityStrip(
                    onRefresh = {
                        items.refresh()
                        viewModel.onRefreshed()
                    }
                )
            }

            val refresh = items.loadState.refresh
            when {
                items.itemCount > 0 -> ActivityTimeline(pagingItems = items)
                refresh is LoadState.Error ->
                    FullScreenError(
                        message = stringResource(R.string.error_load_content_generic),
                        actionText = stringResource(R.string.action_retry),
                        onActionClick = { items.retry() },
                    )
                refresh is LoadState.Loading -> FullScreenLoading()
                else -> FullScreenEmpty(message = stringResource(R.string.activity_empty))
            }
        }
    }
}

@Composable
private fun ActivityTiles(
    today: ActivityToday?,
    selected: ActivityFilter,
    onSelect: (ActivityFilter) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ActivityTile(
            count = today?.plays,
            isPartial = today?.isPartial == true,
            label = R.string.activity_tile_plays,
            tone = ActivityTone.PLAYBACK,
            isSelected = selected == ActivityFilter.PLAYBACK,
            onClick = { onSelect(ActivityFilter.PLAYBACK) },
            modifier = Modifier.weight(1f),
        )
        ActivityTile(
            count = today?.people,
            isPartial = false,
            label = R.string.activity_tile_people,
            tone = ActivityTone.QUIET,
            isSelected = selected == ActivityFilter.PEOPLE,
            onClick = { onSelect(ActivityFilter.PEOPLE) },
            modifier = Modifier.weight(1f),
        )
        ActivityTile(
            count = today?.signIns,
            isPartial = today?.isPartial == true,
            label = R.string.activity_tile_sign_ins,
            tone = ActivityTone.SIGN_IN,
            isSelected = selected == ActivityFilter.SIGN_INS,
            onClick = { onSelect(ActivityFilter.SIGN_INS) },
            modifier = Modifier.weight(1f),
        )
        ActivityTile(
            count = today?.failedSignIns,
            isPartial = today?.isPartial == true,
            label = R.string.activity_tile_failed,
            tone = ActivityTone.ALERT,
            isSelected = selected == ActivityFilter.FAILED_SIGN_INS,
            onClick = { onSelect(ActivityFilter.FAILED_SIGN_INS) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ActivityTile(
    count: Int?,
    isPartial: Boolean,
    @StringRes label: Int,
    tone: ActivityTone,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = tone.color()
    val isActive = (count ?: 0) > 0
    val numberColor =
        when {
            tone == ActivityTone.QUIET -> MaterialTheme.colorScheme.onSurface
            isActive -> accent
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    val container =
        when {
            isSelected -> accent.copy(alpha = MARKER_TINT_ALPHA)
            tone == ActivityTone.ALERT && isActive -> alertRowTint()
            else -> Color.Transparent
        }
    val number =
        when {
            count == null -> stringResource(R.string.activity_tile_loading)
            isPartial -> stringResource(R.string.activity_tile_at_least_fmt, count)
            else -> count.toString()
        }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .background(container)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                color = numberColor,
                maxLines = 1,
            )
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NewActivityStrip(onRefresh: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.activity_new_entries),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRefresh) { Text(stringResource(R.string.action_refresh)) }
        }
    }
}

@Composable
private fun ActivityTimeline(pagingItems: LazyPagingItems<ActivityListItem>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 8.dp, top = 4.dp, end = 8.dp, bottom = 16.dp),
    ) {
        items(
            count = pagingItems.itemCount,
            key =
                pagingItems.itemKey { item ->
                    when (item) {
                        is ActivityListItem.Day -> "day-${item.date}"
                        is ActivityListItem.Entry -> item.entry.id
                    }
                },
        ) { index ->
            when (val item = pagingItems[index]) {
                is ActivityListItem.Day -> DayHeader(date = item.date)
                is ActivityListItem.Entry -> {
                    val followsEntry = index > 0 && pagingItems.peek(index - 1).isEntry()
                    val precedesEntry =
                        index + 1 < pagingItems.itemCount && pagingItems.peek(index + 1).isEntry()
                    TimelineEntry(
                        entry = item.entry,
                        followsEntry = followsEntry,
                        precedesEntry = precedesEntry,
                    )
                }
                null -> Unit
            }
        }

        when (pagingItems.loadState.append) {
            is LoadState.Loading ->
                item(key = "append_loading") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            is LoadState.Error ->
                item(key = "append_error") {
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        TextButton(onClick = { pagingItems.retry() }) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                }
            else -> Unit
        }
    }
}

private fun ActivityListItem?.isEntry(): Boolean = this is ActivityListItem.Entry

@Composable
private fun DayHeader(date: LocalDate) {
    val today = LocalDate.now()
    val label =
        when (date) {
            today -> stringResource(R.string.time_today)
            today.minusDays(1) -> stringResource(R.string.time_yesterday)
            else ->
                remember(date) {
                    date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                }
        }
    SectionHeader(
        title = label,
        modifier = Modifier.padding(start = ROW_HORIZONTAL_PADDING, top = 8.dp, bottom = 6.dp),
    )
}

@Composable
private fun TimelineEntry(entry: ActivityEntry, followsEntry: Boolean, precedesEntry: Boolean) {
    val time =
        remember(entry.date) {
            entry.date.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        }
    val kind = entry.kind()
    val tone = if (entry.isError) ActivityTone.ALERT else kind.tone
    val tint = tone.color()
    val railColor = MaterialTheme.colorScheme.outlineVariant
    val rowTint = if (entry.isError) alertRowTint() else Color.Transparent

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clip(RoundedCornerShape(14.dp))
                .background(rowTint)
                .padding(horizontal = ROW_HORIZONTAL_PADDING)
    ) {
        Box(
            modifier =
                Modifier.width(TIME_COLUMN_WIDTH)
                    .padding(top = ROW_VERTICAL_PADDING)
                    .height(MARKER_SIZE),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                text = time,
                style = LogTextStyles.meta,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.End,
                maxLines = 1,
            )
        }
        Spacer(modifier = Modifier.width(TIME_GUTTER))
        Box(
            modifier =
                Modifier.width(MARKER_SIZE).fillMaxHeight().drawBehind {
                    val x = size.width / 2
                    val markerCenter = (ROW_VERTICAL_PADDING + MARKER_SIZE / 2).toPx()
                    val top = if (followsEntry) 0f else markerCenter
                    val bottom = if (precedesEntry) size.height else markerCenter
                    if (bottom > top) {
                        drawLine(
                            color = railColor,
                            start = Offset(x, top),
                            end = Offset(x, bottom),
                            strokeWidth = RAIL_WIDTH.toPx(),
                        )
                    }
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            TimelineMarker(
                icon = if (entry.isError) R.drawable.ic_alert_triangle else kind.icon,
                tint = tint,
                modifier = Modifier.padding(top = ROW_VERTICAL_PADDING),
            )
        }
        Column(
            modifier =
                Modifier.weight(1f)
                    .padding(
                        start = TEXT_GUTTER,
                        top = ROW_VERTICAL_PADDING,
                        bottom = ROW_VERTICAL_PADDING,
                    ),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = stringResource(kind.label).uppercase(),
                style = LogTextStyles.metaSmall,
                fontWeight = FontWeight.Medium,
                color = tint,
            )
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (entry.detail != null) {
                Text(
                    text = entry.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TimelineMarker(@DrawableRes icon: Int, tint: Color, modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .size(MARKER_SIZE)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
                .background(tint.copy(alpha = MARKER_TINT_ALPHA), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(MARKER_ICON_SIZE),
        )
    }
}

private fun ActivityEntry.kind(): ActivityKind =
    when {
        type.contains("Playback", ignoreCase = true) ->
            if (type.contains("Stopped", ignoreCase = true)) ActivityKind.PLAYBACK_STOPPED
            else ActivityKind.PLAYBACK_STARTED
        type.equals("SessionStarted", ignoreCase = true) -> ActivityKind.SESSION_STARTED
        type.equals("SessionEnded", ignoreCase = true) -> ActivityKind.SESSION_ENDED
        type.contains("Authentication", ignoreCase = true) -> ActivityKind.SIGN_IN
        type.startsWith("User", ignoreCase = true) -> ActivityKind.USER
        type.startsWith("Plugin", ignoreCase = true) -> ActivityKind.PLUGIN
        type.contains("Task", ignoreCase = true) -> ActivityKind.TASK
        else -> ActivityKind.OTHER
    }
