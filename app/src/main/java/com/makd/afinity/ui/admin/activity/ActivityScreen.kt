package com.makd.afinity.ui.admin.activity

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import com.makd.afinity.ui.admin.components.AdminScaffold
import com.makd.afinity.ui.components.FullScreenEmpty
import com.makd.afinity.ui.components.FullScreenError
import com.makd.afinity.ui.components.FullScreenLoading
import com.makd.afinity.ui.settings.servers.components.SectionHeader
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ActivityScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActivityViewModel = hiltViewModel(),
) {
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val hasNewActivity by viewModel.hasNewActivity.collectAsStateWithLifecycle()
    val items = viewModel.items.collectAsLazyPagingItems()

    AdminScaffold(
        title = stringResource(R.string.title_activity),
        onBackClick = onBackClick,
        modifier = modifier,
    ) { contentPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            ActivityFilterRow(selected = filter, onSelect = viewModel::setFilter)

            if (hasNewActivity) {
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
                        TextButton(
                            onClick = {
                                items.refresh()
                                viewModel.onRefreshed()
                            }
                        ) {
                            Text(stringResource(R.string.action_refresh))
                        }
                    }
                }
            }

            val refresh = items.loadState.refresh
            when {
                items.itemCount > 0 -> ActivityList(pagingItems = items)
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
private fun ActivityFilterRow(selected: ActivityFilter, onSelect: (ActivityFilter) -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ActivityFilter.entries.forEach { filter ->
            val isSelected = filter == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(filter) },
                label = { Text(stringResource(filter.labelRes())) },
                leadingIcon =
                    if (isSelected) {
                        {
                            Icon(
                                painterResource(R.drawable.ic_check),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else null,
                shape = CircleShape,
                border = null,
                colors =
                    FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            )
        }
    }
}

@Composable
private fun ActivityList(pagingItems: LazyPagingItems<ActivityListItem>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
                is ActivityListItem.Entry -> ActivityRow(entry = item.entry)
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
    SectionHeader(label)
}

@Composable
private fun ActivityRow(entry: ActivityEntry) {
    val time =
        remember(entry.date) {
            entry.date.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        }
    val accent =
        if (entry.isError) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                painter = painterResource(id = entry.iconRes()),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color =
                        if (entry.isError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry.detail != null) {
                    Text(
                        text = entry.detail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = time,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@StringRes
private fun ActivityFilter.labelRes(): Int =
    when (this) {
        ActivityFilter.ALL -> R.string.filter_all
        ActivityFilter.ALERTS -> R.string.activity_filter_alerts
        ActivityFilter.SIGN_INS -> R.string.activity_filter_sign_ins
        ActivityFilter.PLAYBACK -> R.string.activity_filter_playback
    }

@DrawableRes
private fun ActivityEntry.iconRes(): Int =
    when {
        isError -> R.drawable.ic_alert_triangle
        type.contains("Playback", ignoreCase = true) -> R.drawable.ic_player_play_filled
        type.contains("Authentication", ignoreCase = true) -> R.drawable.ic_user
        type.contains("Session", ignoreCase = true) -> R.drawable.ic_devices
        else -> R.drawable.ic_info
    }
