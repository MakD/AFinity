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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import com.makd.afinity.ui.settings.servers.components.SectionHeader
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val TIME_COLUMN_WIDTH = 60.dp
private val MARKER_SIZE = 22.dp
private val MARKER_ICON_SIZE = 13.dp
private val TIMELINE_STROKE = 2.dp
private val ENTRY_SPACING = 16.dp

private enum class ActivityKind(@param:DrawableRes val icon: Int) {
    ERROR(R.drawable.ic_alert_triangle),
    PLAYBACK_STARTED(R.drawable.ic_player_play_filled),
    PLAYBACK_STOPPED(R.drawable.ic_player_stop_filled),
    SESSION_STARTED(R.drawable.ic_login),
    SESSION_ENDED(R.drawable.ic_logout),
    SIGN_IN(R.drawable.ic_security),
    USER(R.drawable.ic_user),
    PLUGIN(R.drawable.ic_view_module),
    TASK(R.drawable.ic_schedule),
    OTHER(R.drawable.ic_info),
}

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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ActivityTile(
            count = today?.plays,
            isPartial = today?.isPartial == true,
            label = R.string.activity_tile_plays,
            isSelected = selected == ActivityFilter.PLAYBACK,
            onClick = { onSelect(ActivityFilter.PLAYBACK) },
            modifier = Modifier.weight(1f),
        )
        ActivityTile(
            count = today?.people,
            isPartial = false,
            label = R.string.activity_tile_people,
            isSelected = selected == ActivityFilter.PEOPLE,
            onClick = { onSelect(ActivityFilter.PEOPLE) },
            modifier = Modifier.weight(1f),
        )
        ActivityTile(
            count = today?.signIns,
            isPartial = today?.isPartial == true,
            label = R.string.activity_tile_sign_ins,
            isSelected = selected == ActivityFilter.SIGN_INS,
            onClick = { onSelect(ActivityFilter.SIGN_INS) },
            modifier = Modifier.weight(1f),
        )
        ActivityTile(
            count = today?.failedSignIns,
            isPartial = today?.isPartial == true,
            label = R.string.activity_tile_failed,
            isSelected = selected == ActivityFilter.FAILED_SIGN_INS,
            onClick = { onSelect(ActivityFilter.FAILED_SIGN_INS) },
            modifier = Modifier.weight(1f),
            isAlert = true,
        )
    }
}

@Composable
private fun ActivityTile(
    count: Int?,
    isPartial: Boolean,
    @StringRes label: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isAlert: Boolean = false,
) {
    val alerting = isAlert && (count ?: 0) > 0
    val container =
        when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            alerting -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        }
    val content =
        when {
            isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
            alerting -> MaterialTheme.colorScheme.onErrorContainer
            else -> MaterialTheme.colorScheme.onSurface
        }
    val number =
        when {
            count == null -> stringResource(R.string.activity_tile_loading)
            isPartial -> stringResource(R.string.activity_tile_at_least_fmt, count)
            else -> count.toString()
        }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = container,
        contentColor = content,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp)) {
            Text(
                text = number,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
            )
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.labelSmall,
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
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
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
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
                    val continues =
                        index + 1 < pagingItems.itemCount &&
                            pagingItems.peek(index + 1) is ActivityListItem.Entry
                    TimelineEntry(entry = item.entry, continues = continues)
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
    SectionHeader(title = label, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun TimelineEntry(entry: ActivityEntry, continues: Boolean) {
    val time =
        remember(entry.date) {
            entry.date.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        }
    val kind = entry.kind()
    val lineColor = MaterialTheme.colorScheme.outlineVariant

    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = time,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.width(TIME_COLUMN_WIDTH).padding(top = 3.dp),
        )
        Box(
            modifier =
                Modifier.width(MARKER_SIZE).fillMaxHeight().drawBehind {
                    if (continues) {
                        val x = size.width / 2
                        drawLine(
                            color = lineColor,
                            start = Offset(x, MARKER_SIZE.toPx()),
                            end = Offset(x, size.height),
                            strokeWidth = TIMELINE_STROKE.toPx(),
                        )
                    }
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            TimelineMarker(kind = kind)
        }
        Column(modifier = Modifier.weight(1f).padding(bottom = ENTRY_SPACING)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color =
                    if (entry.isError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface,
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
private fun TimelineMarker(kind: ActivityKind) {
    val container: Color
    val content: Color
    when (kind) {
        ActivityKind.ERROR -> {
            container = MaterialTheme.colorScheme.error
            content = MaterialTheme.colorScheme.onError
        }
        ActivityKind.PLAYBACK_STARTED -> {
            container = MaterialTheme.colorScheme.primary
            content = MaterialTheme.colorScheme.onPrimary
        }
        else -> {
            container = MaterialTheme.colorScheme.surfaceContainerHighest
            content = MaterialTheme.colorScheme.onSurfaceVariant
        }
    }
    Box(
        modifier = Modifier.size(MARKER_SIZE).background(container, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(id = kind.icon),
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(MARKER_ICON_SIZE),
        )
    }
}

private fun ActivityEntry.kind(): ActivityKind =
    when {
        isError -> ActivityKind.ERROR
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
