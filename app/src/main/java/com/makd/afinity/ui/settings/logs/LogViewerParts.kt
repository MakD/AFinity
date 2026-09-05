package com.makd.afinity.ui.settings.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makd.afinity.R
import com.makd.afinity.ui.components.AfinityTextField
import com.makd.afinity.util.logging.LogLevel

@Composable
fun TagFilterSheetContent(
    tagCounts: List<LogTagCount>,
    selected: Set<String>,
    matchCount: Int,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var filter by remember { mutableStateOf("") }
    val visible =
        remember(tagCounts, filter) {
            if (filter.isBlank()) tagCounts
            else tagCounts.filter { it.tag.contains(filter, ignoreCase = true) }
        }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.logs_tags_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 4.dp),
        )
        Text(
            text = stringResource(R.string.logs_tags_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
        )

        AfinityTextField(
            value = filter,
            onValueChange = { filter = it },
            placeholder = stringResource(R.string.logs_tags_find),
            leadingIcon = painterResource(id = R.drawable.ic_search),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp).padding(top = 8.dp)
        ) {
            items(items = visible, key = { it.tag }) { tag ->
                TagRow(
                    tag = tag,
                    checked = tag.tag in selected,
                    onToggle = { onToggle(tag.tag) },
                )
            }
        }

        Row(
            modifier =
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onClear,
                enabled = selected.isNotEmpty(),
                modifier = Modifier.height(52.dp),
                shape = RoundedCornerShape(26.dp),
            ) {
                Text(text = stringResource(R.string.logs_tags_clear))
            }
            Button(
                onClick = onDone,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(26.dp),
            ) {
                Text(text = stringResource(R.string.logs_tags_show_fmt, matchCount))
            }
        }
    }
}

@Composable
private fun TagRow(tag: LogTagCount, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .height(52.dp)
                .background(
                    if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    else Color.Transparent
                )
                .clickable(onClick = onToggle)
                .padding(start = 16.dp, end = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Text(
            text = tag.tag.uppercase(),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (tag.errors > 0) {
            SeverityBadge(count = tag.errors, level = LogLevel.ERROR)
        } else if (tag.warnings > 0) {
            SeverityBadge(count = tag.warnings, level = LogLevel.WARN)
        }
        Text(
            text = tag.total.toString(),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.End,
            modifier = Modifier.width(38.dp),
        )
    }
}

@Composable
private fun SeverityBadge(count: Int, level: LogLevel) {
    Text(
        text = count.toString(),
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        color = LogLevelColors.content(level),
        modifier =
            Modifier.clip(RoundedCornerShape(8.dp))
                .background(LogLevelColors.content(level).copy(alpha = 0.16f))
                .padding(horizontal = 7.dp, vertical = 1.dp),
    )
}

@Composable
fun LogEmptyState(
    reason: LogEmptyReason,
    query: String,
    canWiden: Boolean,
    onWiden: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val icon =
        when (reason) {
            LogEmptyReason.NO_CRASHES -> R.drawable.ic_security
            LogEmptyReason.NO_MATCHES -> R.drawable.ic_search
            else -> R.drawable.ic_logs
        }
    val title =
        when (reason) {
            LogEmptyReason.NO_CRASHES -> stringResource(R.string.logs_empty_crashes_title)
            LogEmptyReason.NO_MATCHES ->
                if (query.isBlank()) {
                    stringResource(R.string.logs_empty_filter_title)
                } else {
                    stringResource(R.string.logs_empty_search_title_fmt, query)
                }
            else -> stringResource(R.string.logs_empty_buffer_title)
        }
    val body =
        when (reason) {
            LogEmptyReason.NO_CRASHES -> stringResource(R.string.logs_empty_crashes_body)
            LogEmptyReason.NO_MATCHES -> stringResource(R.string.logs_empty_search_body)
            else -> stringResource(R.string.logs_empty_buffer_body)
        }

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.size(44.dp),
        )
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        if (canWiden && reason == LogEmptyReason.NO_MATCHES) {
            Spacer(modifier = Modifier.height(20.dp))
            TextButton(onClick = onWiden) {
                Text(text = stringResource(R.string.logs_empty_clear_filters))
            }
        }
    }
}

@Composable
fun LogCountChip(
    label: String,
    count: Int,
    tint: Color,
    selected: Boolean,
    showDot: Boolean = false,
    onClick: () -> Unit,
) {
    Box {
        Row(
            modifier =
                Modifier.height(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (selected) tint else MaterialTheme.colorScheme.surfaceContainerLow
                    )
                    .clickable(onClick = onClick)
                    .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (showDot) {
                Box(
                    modifier =
                        Modifier.size(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (selected) MaterialTheme.colorScheme.surface else tint)
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.surface else tint,
            )
            Text(
                text = compactCount(count),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color =
                    (if (selected) MaterialTheme.colorScheme.surface else tint).copy(alpha = 0.7f),
            )
        }
    }
}

private fun compactCount(value: Int): String =
    if (value >= 1000) "%.1fk".format(value / 1000f) else value.toString()