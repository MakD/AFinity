package com.makd.afinity.ui.admin.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makd.afinity.R
import com.makd.afinity.ui.settings.logs.LogTextStyles
import com.makd.afinity.util.logging.LabelPart

private val RowStartPadding = 20.dp
private val TimeColumnWidth = 50.sp
private val TimeGutter = 12.dp
private val RailColumnWidth = 18.dp
private val FirstLineHeight = 18.sp
private const val COLLAPSED_MESSAGE_LINES = 4
private const val COLLAPSED_TRACE_LINES = 3
private const val HIGHLIGHT_ALPHA = 0.26f
private const val ROW_CONTENT_TYPE = "event"
private const val VARIABLE_LABEL = "…"

@Composable
internal fun ServerLogList(
    view: ServerLogView,
    query: String,
    expandedKey: String?,
    listState: LazyListState,
    contentPadding: PaddingValues,
    onRowClick: (String) -> Unit,
    onCopy: (Int) -> Unit,
    onCopyWithContext: (Int) -> Unit,
    onFilterTag: (String) -> Unit,
    onShowAll: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = contentPadding,
    ) {
        items(
            count = view.size,
            key = { index -> view.keyAt(index) },
            contentType = { ROW_CONTENT_TYPE },
        ) { index ->
            val row = remember(view, index) { view.rowAt(index) }
            val entry = remember(view, row.entryIndex) { view.document.entry(row.entryIndex) }
            ServerLogRowItem(
                row = row,
                entry = entry,
                query = query,
                expanded = row.key == expandedKey,
                onClick = { onRowClick(row.key) },
                onCopy = { onCopy(row.entryIndex) },
                onCopyWithContext = { onCopyWithContext(row.entryIndex) },
                onFilterTag = { onFilterTag(row.tag) },
                onShowAll = { onShowAll(row.groupId) },
            )
        }
    }
}

@Composable
private fun ServerLogRowItem(
    row: ServerLogRow,
    entry: ServerLogEntry,
    query: String,
    expanded: Boolean,
    onClick: () -> Unit,
    onCopy: () -> Unit,
    onCopyWithContext: () -> Unit,
    onFilterTag: () -> Unit,
    onShowAll: () -> Unit,
) {
    val tint = ServerLogColors.content(row.level)
    val grouped = row.count > 1
    val railColor = MaterialTheme.colorScheme.outlineVariant
    val density = LocalDensity.current
    val timeWidth = with(density) { TimeColumnWidth.toDp() }
    val firstLineHeight = with(density) { FirstLineHeight.toDp() }
    val railCenterPx =
        with(density) { (RowStartPadding + timeWidth + TimeGutter + RailColumnWidth / 2).toPx() }
    val railWidthPx = with(density) { 1.dp.toPx() }

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .clickable(onClick = onClick)
                .drawBehind {
                    val center =
                        if (layoutDirection == LayoutDirection.Ltr) railCenterPx
                        else size.width - railCenterPx
                    drawRect(
                        color = railColor,
                        topLeft = Offset(center - railWidthPx / 2f, 0f),
                        size = Size(railWidthPx, size.height),
                    )
                }
                .padding(start = RowStartPadding)
    ) {
        Text(
            text = row.time,
            style = LogTextStyles.metaSmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier =
                Modifier.width(timeWidth)
                    .height(firstLineHeight)
                    .wrapContentHeight(Alignment.CenterVertically),
        )

        Spacer(modifier = Modifier.width(TimeGutter))

        Box(
            modifier = Modifier.width(RailColumnWidth).height(firstLineHeight),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier.size(17.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .border(2.dp, tint, CircleShape)
            )
            Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(tint))
        }

        Column(modifier = Modifier.weight(1f).padding(start = 14.dp, end = 20.dp, bottom = 20.dp)) {
            Row(
                modifier = Modifier.height(firstLineHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.tag.uppercase(),
                    style = LogTextStyles.metaSmall,
                    fontWeight = FontWeight.Medium,
                    color = tint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (grouped) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${row.count}x",
                        style = LogTextStyles.pill,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.surface,
                        maxLines = 1,
                        modifier =
                            Modifier.clip(RoundedCornerShape(9.dp))
                                .background(tint)
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }

            Text(
                text =
                    if (grouped && !expanded) {
                        labelText(row.label, row.truncated)
                    } else {
                        highlighted(entry.message, query)
                    },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_MESSAGE_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )

            entry.trace?.let { trace ->
                Box(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ServerLogColors.container(row.level))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = trace,
                        style = LogTextStyles.trace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_TRACE_LINES,
                        overflow = TextOverflow.Ellipsis,
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
                        text = row.times,
                        style = LogTextStyles.metaSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }

            if (expanded) {
                RowActions(
                    repeatCount = if (grouped) row.count else 0,
                    onCopy = onCopy,
                    onCopyWithContext = onCopyWithContext,
                    onFilterTag = onFilterTag,
                    onShowAll = onShowAll,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RowActions(
    repeatCount: Int,
    onCopy: () -> Unit,
    onCopyWithContext: () -> Unit,
    onFilterTag: () -> Unit,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
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
        if (repeatCount > 1) {
            RowAction(
                icon = R.drawable.ic_menu,
                label = stringResource(R.string.server_log_show_all_fmt, repeatCount),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onShowAll,
            )
        }
    }
}

@Composable
private fun RowAction(icon: Int, label: String, color: Color, onClick: () -> Unit) {
    Row(
        modifier =
            Modifier.clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 4.dp),
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
private fun labelText(parts: List<LabelPart>, truncated: Boolean): AnnotatedString {
    val variableStyle = SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)
    return remember(parts, truncated, variableStyle) {
        buildAnnotatedString {
            parts.forEach { part ->
                when (part) {
                    is LabelPart.Literal -> append(part.text)
                    LabelPart.Variable -> withStyle(variableStyle) { append(VARIABLE_LABEL) }
                }
            }
            if (truncated && parts.lastOrNull() != LabelPart.Variable) {
                if (length > 0) append(' ')
                withStyle(variableStyle) { append(VARIABLE_LABEL) }
            }
        }
    }
}

@Composable
private fun highlighted(text: String, query: String): AnnotatedString {
    val style =
        SpanStyle(
            background = MaterialTheme.colorScheme.primary.copy(alpha = HIGHLIGHT_ALPHA),
            color = MaterialTheme.colorScheme.onSurface,
        )
    return remember(text, query, style) {
        val needle = query.trim()
        if (needle.isEmpty()) {
            AnnotatedString(text)
        } else {
            buildAnnotatedString {
                var cursor = 0
                while (cursor < text.length) {
                    val found = text.indexOf(needle, cursor, ignoreCase = true)
                    if (found < 0) break
                    append(text, cursor, found)
                    withStyle(style) { append(text, found, found + needle.length) }
                    cursor = found + needle.length
                }
                append(text, cursor, text.length)
            }
        }
    }
}
