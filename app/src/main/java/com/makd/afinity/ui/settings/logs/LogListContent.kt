package com.makd.afinity.ui.settings.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makd.afinity.R
import com.makd.afinity.util.logging.LabelPart
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TimeColumnWidth = 50.dp
private val RailColumnWidth = 18.dp
private val TagColumnWidth = 84.dp
private val RibbonWidth = 3.dp
private const val GapContentType = "gap"
private const val LaunchContentType = "launch"

@Composable
fun LogListContent(
    rows: List<TimelineRow>,
    density: LogDensity,
    expandedKey: String?,
    listState: LazyListState,
    contentPadding: PaddingValues,
    onRowClick: (TimelineRow.Event) -> Unit,
    onCopy: (TimelineRow.Event) -> Unit,
    onCopyWithContext: (TimelineRow.Event) -> Unit,
    onFilterTag: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val compactFormatter = remember { SimpleDateFormat("mm:ss.SSS", Locale.US) }
    val fullFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = contentPadding,
    ) {
        items(
            items = rows,
            key = { row -> row.key },
            contentType = { row ->
                when (row) {
                    is TimelineRow.Gap -> GapContentType
                    is TimelineRow.Launch -> LaunchContentType
                    is TimelineRow.Event -> density
                }
            },
        ) { row ->
            when (row) {
                is TimelineRow.Gap -> GapRow(row)
                is TimelineRow.Launch -> LaunchRow(row, fullFormatter)
                is TimelineRow.Event ->
                    when (density) {
                        LogDensity.COMPACT ->
                            if (row.key == expandedKey) {
                                ExpandedRow(
                                    row = row,
                                    formatter = fullFormatter,
                                    onCollapse = { onRowClick(row) },
                                    onCopy = { onCopy(row) },
                                    onCopyWithContext = { onCopyWithContext(row) },
                                    onFilterTag = { onFilterTag(row.entry.tag) },
                                )
                            } else {
                                CompactRow(row, compactFormatter) { onRowClick(row) }
                            }
                        LogDensity.COMFORTABLE ->
                            ComfortableRow(
                                row = row,
                                formatter = fullFormatter,
                                expanded = row.key == expandedKey,
                                onClick = { onRowClick(row) },
                                onCopy = { onCopy(row) },
                                onCopyWithContext = { onCopyWithContext(row) },
                                onFilterTag = { onFilterTag(row.entry.tag) },
                            )
                    }
            }
        }
    }
}

@Composable
private fun CompactRow(
    row: TimelineRow.Event,
    formatter: SimpleDateFormat,
    onClick: () -> Unit,
) {
    val entry = row.entry
    val tint = LogLevelColors.content(entry.level)

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(LogLevelColors.rowTint(entry.level))
                .clickable(onClick = onClick)
    ) {
        Box(
            modifier =
                Modifier.width(RibbonWidth)
                    .fillMaxHeight()
                    .background(LogLevelColors.ribbon(entry.level))
        )
        Text(
            text = formatter.format(Date(entry.timeMillis)),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(start = 10.dp),
        )
        Text(
            text = entry.tag.uppercase(),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            lineHeight = 17.sp,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(TagColumnWidth).padding(start = 8.dp),
        )
        Text(
            text = highlighted(compactMessage(entry.message, entry.stackTrace), row.highlights),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 8.dp, end = 12.dp),
        )
    }
}

@Composable
private fun ComfortableRow(
    row: TimelineRow.Event,
    formatter: SimpleDateFormat,
    expanded: Boolean,
    onClick: () -> Unit,
    onCopy: () -> Unit,
    onCopyWithContext: () -> Unit,
    onFilterTag: () -> Unit,
) {
    val entry = row.entry
    val tint = LogLevelColors.content(entry.level)
    val grouped = row.count > 1

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clickable(onClick = onClick)
                .padding(start = 20.dp)
    ) {
        Text(
            text = row.relativeLabel ?: formatter.format(Date(entry.timeMillis)),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.End,
            modifier = Modifier.width(TimeColumnWidth).padding(top = 3.dp),
        )

        Box(modifier = Modifier.width(RailColumnWidth).fillMaxHeight()) {
            Box(
                modifier =
                    Modifier.align(Alignment.TopCenter)
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant)
            )
            Box(
                modifier =
                    Modifier.align(Alignment.TopCenter)
                        .padding(top = 1.dp)
                        .size(17.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(2.dp, tint, CircleShape)
            )
            Box(
                modifier =
                    Modifier.align(Alignment.TopCenter)
                        .padding(top = 5.dp)
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(tint)
            )
        }

        Column(modifier = Modifier.weight(1f).padding(start = 14.dp, end = 20.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = entry.tag.uppercase(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = tint,
                )
                if (grouped) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${row.count}x",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.surface,
                        modifier =
                            Modifier.clip(RoundedCornerShape(9.dp))
                                .background(tint)
                                .padding(horizontal = 7.dp, vertical = 1.dp),
                    )
                }
            }

            Text(
                text =
                    if (grouped) {
                        buildLabel(row.label)
                    } else {
                        highlighted(entry.message, row.highlights)
                    },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            )

            entry.stackTrace?.let { trace ->
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(LogLevelColors.container(entry.level))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = trace,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (grouped) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier =
                            Modifier.weight(1f)
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    Text(
                        text = row.occurrenceTimes.joinToString(" · ") { formatter.format(Date(it)) },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (expanded) {
                RowActions(
                    onCopy = onCopy,
                    onCopyWithContext = onCopyWithContext,
                    onFilterTag = onFilterTag,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

@Composable
private fun ExpandedRow(
    row: TimelineRow.Event,
    formatter: SimpleDateFormat,
    onCollapse: () -> Unit,
    onCopy: () -> Unit,
    onCopyWithContext: () -> Unit,
    onFilterTag: () -> Unit,
) {
    val entry = row.entry
    val tint = LogLevelColors.content(entry.level)

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(LogLevelColors.expandedTint(entry.level))
    ) {
        Box(
            modifier =
                Modifier.width(RibbonWidth)
                    .fillMaxHeight()
                    .background(LogLevelColors.ribbon(entry.level))
        )
        Column(
            modifier =
                Modifier.weight(1f)
                    .clickable(onClick = onCollapse)
                    .padding(start = 10.dp, end = 12.dp, top = 11.dp, bottom = 10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = entry.level.name,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = LogLevelColors.container(entry.level),
                    modifier =
                        Modifier.clip(RoundedCornerShape(8.dp))
                            .background(tint)
                            .padding(horizontal = 7.dp, vertical = 1.dp),
                )
                Text(
                    text = formatter.format(Date(entry.timeMillis)),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.outline,
                )
                Text(
                    text = entry.tag.uppercase(),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = tint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Text(
                text = highlighted(entry.message, row.highlights),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 7.dp),
            )

            entry.stackTrace?.let { trace ->
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(top = 9.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(LogLevelColors.container(entry.level))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = trace,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            RowActions(
                onCopy = onCopy,
                onCopyWithContext = onCopyWithContext,
                onFilterTag = onFilterTag,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun RowActions(
    onCopy: () -> Unit,
    onCopyWithContext: () -> Unit,
    onFilterTag: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RowAction(
            icon = R.drawable.ic_copy,
            label = stringResource(R.string.logs_copy),
            color = MaterialTheme.colorScheme.primary,
            onClick = onCopy,
        )
        RowAction(
            icon = R.drawable.ic_logs,
            label = stringResource(R.string.logs_copy_with_context),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onCopyWithContext,
        )
        RowAction(
            icon = R.drawable.ic_filter,
            label = stringResource(R.string.logs_only_this_tag),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            onClick = onFilterTag,
        )
    }
}

@Composable
private fun RowAction(icon: Int, label: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            painter = painterResource(id = icon),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

@Composable
private fun LaunchRow(row: TimelineRow.Launch, formatter: SimpleDateFormat) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier =
                Modifier.weight(1f)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        )
        Text(
            text =
                stringResource(
                    R.string.logs_launch_marker_fmt,
                    row.version,
                    formatter.format(Date(row.timeMillis)),
                ),
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.outline,
        )
        Box(
            modifier =
                Modifier.weight(1f)
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        )
    }
}

@Composable
private fun GapRow(row: TimelineRow.Gap) {
    Row(modifier = Modifier.fillMaxWidth().padding(start = 20.dp)) {
        Text(
            text = formatGap(row.durationMillis),
            fontFamily = FontFamily.Monospace,
            fontSize = 9.sp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
            textAlign = TextAlign.End,
            modifier = Modifier.width(TimeColumnWidth).padding(top = 14.dp),
        )
        Column(
            modifier = Modifier.width(RailColumnWidth).height(40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            repeat(4) {
                Box(
                    modifier =
                        Modifier.width(1.dp)
                            .height(4.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
    }
}

@Composable
private fun buildLabel(parts: List<LabelPart>): AnnotatedString = buildAnnotatedString {
    parts.forEach { part ->
        when (part) {
            is LabelPart.Literal -> append(part.text)
            LabelPart.Variable ->
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                    append("n")
                }
        }
    }
}

@Composable
private fun highlighted(text: String, ranges: List<IntRange>): AnnotatedString {
    if (ranges.isEmpty()) return AnnotatedString(text)
    val style =
        SpanStyle(
            background = MaterialTheme.colorScheme.primary.copy(alpha = 0.26f),
            color = MaterialTheme.colorScheme.onSurface,
        )
    return buildAnnotatedString {
        var cursor = 0
        ranges.forEach { range ->
            if (range.first > text.length || range.last >= text.length) return@forEach
            if (range.first > cursor) append(text.substring(cursor, range.first))
            withStyle(style) { append(text.substring(range.first, range.last + 1)) }
            cursor = range.last + 1
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
}

private fun compactMessage(message: String, stackTrace: String?): String {
    if (stackTrace == null) return message
    val frames = stackTrace.lines().size
    return "$message  +$frames frames"
}

private fun formatGap(durationMillis: Long): String {
    val seconds = durationMillis / 1000
    return if (seconds >= 60) "${seconds / 60}m ${seconds % 60}s" else "${seconds}s"
}