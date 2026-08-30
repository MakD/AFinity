package com.makd.afinity.ui.settings.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makd.afinity.util.logging.LabelPart
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val TimeColumnWidth = 50.dp
private val RailColumnWidth = 18.dp
private val RibbonWidth = 3.dp
private const val GapContentType = "gap"

@Composable
fun LogListContent(
    rows: List<TimelineRow>,
    density: LogDensity,
    listState: LazyListState,
    contentPadding: PaddingValues,
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
            key = { row ->
                when (row) {
                    is TimelineRow.Event -> row.key
                    is TimelineRow.Gap -> row.key
                }
            },
            contentType = { row -> if (row is TimelineRow.Gap) GapContentType else density },
        ) { row ->
            when (row) {
                is TimelineRow.Gap -> GapRow(row)
                is TimelineRow.Event ->
                    when (density) {
                        LogDensity.COMPACT -> CompactRow(row, compactFormatter)
                        LogDensity.COMFORTABLE -> ComfortableRow(row, fullFormatter)
                    }
            }
        }
    }
}

@Composable
private fun CompactRow(row: TimelineRow.Event, formatter: SimpleDateFormat) {
    val entry = row.entry
    val tint = LogLevelColors.content(entry.level)

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .height(IntrinsicSize.Min)
                .background(LogLevelColors.rowTint(entry.level))
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
            modifier = Modifier.padding(start = 8.dp),
        )
        Text(
            text = compactMessage(entry.message, entry.stackTrace),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(start = 8.dp, end = 12.dp),
        )
    }
}

@Composable
private fun ComfortableRow(row: TimelineRow.Event, formatter: SimpleDateFormat) {
    val entry = row.entry
    val tint = LogLevelColors.content(entry.level)
    val grouped = row.count > 1

    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(start = 20.dp)) {
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
                text = buildLabel(row.label),
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
                        text =
                            row.occurrenceTimes.joinToString(" · ") {
                                formatter.format(Date(it))
                            },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
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

private fun compactMessage(message: String, stackTrace: String?): String {
    if (stackTrace == null) return message
    val frames = stackTrace.lines().size
    return "$message  +$frames frames"
}

private fun formatGap(durationMillis: Long): String {
    val seconds = durationMillis / 1000
    return if (seconds >= 60) "${seconds / 60}m ${seconds % 60}s" else "${seconds}s"
}
