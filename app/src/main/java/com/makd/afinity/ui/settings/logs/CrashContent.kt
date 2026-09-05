package com.makd.afinity.ui.settings.logs

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makd.afinity.R
import com.makd.afinity.util.logging.CrashReport
import com.makd.afinity.util.logging.LogLevel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun CrashListContent(
    crashes: List<CrashReport>,
    newCount: Int,
    contentPadding: PaddingValues,
    onOpen: (CrashReport) -> Unit,
    modifier: Modifier = Modifier,
) {
    val formatter = remember { SimpleDateFormat("HH:mm", Locale.US) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(items = crashes, key = { _, report -> report.id }) { index, report ->
            CrashCard(
                report = report,
                isNew = index < newCount,
                formatter = formatter,
                onClick = { onOpen(report) },
            )
        }
    }
}

@Composable
private fun CrashCard(
    report: CrashReport,
    isNew: Boolean,
    formatter: SimpleDateFormat,
    onClick: () -> Unit,
) {
    val tint = LogLevelColors.content(LogLevel.ERROR)
    val accent = if (isNew) tint else tint.copy(alpha = 0.45f)

    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(IntrinsicSize.Min)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(accent))
        Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 15.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = report.simpleExceptionClass,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isNew) {
                    Text(
                        text = stringResource(R.string.logs_crash_new),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = LogLevelColors.container(LogLevel.ERROR),
                        modifier =
                            Modifier.clip(RoundedCornerShape(8.dp))
                                .background(tint)
                                .padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }

            report.exceptionMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            report.topAppFrame?.let { frame ->
                Text(
                    text = frame,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 7.dp),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = crashSubtitle(report, formatter),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    painter = painterResource(id = R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
fun CrashDetailContent(
    report: CrashReport,
    isExporting: Boolean,
    onCopyForIssue: () -> Unit,
    onShare: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val tint = LogLevelColors.content(LogLevel.ERROR)
    val stampFormatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US) }
    var logsExpanded by remember(report.id) { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier =
                Modifier.weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = contentPadding.calculateTopPadding())
        ) {
            Text(
                text = report.simpleExceptionClass,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = tint,
            )
            report.exceptionMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(top = 18.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(horizontal = 14.dp)
            ) {
                MetaRow(
                    stringResource(R.string.logs_crash_meta_time),
                    stampFormatter.format(Date(report.timeMillis)),
                    first = true,
                )
                report.thread?.let {
                    MetaRow(stringResource(R.string.logs_crash_meta_thread), it)
                }
                report.build?.let { MetaRow(stringResource(R.string.logs_crash_meta_build), it) }
                report.device?.let { MetaRow(stringResource(R.string.logs_crash_meta_device), it) }
            }

            Text(
                text = stringResource(R.string.logs_crash_stack_trace),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
            )
            Box(
                modifier =
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(LogLevelColors.container(LogLevel.ERROR))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(
                    text = report.stackTrace,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (report.precedingLineCount > 0) {
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { logsExpanded = !logsExpanded }
                            .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        painter =
                            painterResource(
                                id =
                                    if (logsExpanded) R.drawable.ic_keyboard_arrow_up
                                    else R.drawable.ic_keyboard_arrow_down
                            ),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.logs_crash_preceding),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text =
                            stringResource(
                                R.string.logs_save_lines_fmt,
                                report.precedingLineCount,
                            ),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }

                if (logsExpanded) {
                    Box(
                        modifier =
                            Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(LogLevelColors.consoleSurface)
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = report.precedingLogs,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp)
                    .padding(bottom = contentPadding.calculateBottomPadding() + 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onCopyForIssue,
                enabled = !isExporting,
                modifier = Modifier.weight(1.6f).height(52.dp),
                shape = RoundedCornerShape(26.dp),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_copy),
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(modifier = Modifier.width(9.dp))
                Text(text = stringResource(R.string.logs_crash_copy_issue))
            }
            OutlinedButton(
                onClick = onShare,
                enabled = !isExporting,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(26.dp),
            ) {
                Text(text = stringResource(R.string.logs_crash_share))
            }
        }
    }
}

@Composable
private fun MetaRow(key: String, value: String, first: Boolean = false) {
    if (!first) {
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().height(36.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = key,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.width(74.dp),
        )
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun crashSubtitle(report: CrashReport, formatter: SimpleDateFormat): String {
    val day = relativeDay(report.timeMillis)
    val time = formatter.format(Date(report.timeMillis))
    val tail = listOfNotNull(report.thread, report.build).joinToString(" · ")
    return if (tail.isBlank()) "$day $time" else "$day $time · $tail"
}

@Composable
private fun relativeDay(timeMillis: Long): String {
    val days = daysAgo(timeMillis)
    return when {
        days <= 0L -> stringResource(R.string.logs_crash_today)
        days == 1L -> stringResource(R.string.logs_crash_yesterday)
        else -> stringResource(R.string.logs_crash_days_ago_fmt, days)
    }
}

private fun daysAgo(timeMillis: Long): Long {
    val start = Calendar.getInstance()
    start.timeInMillis = timeMillis
    start.set(Calendar.HOUR_OF_DAY, 0)
    start.set(Calendar.MINUTE, 0)
    start.set(Calendar.SECOND, 0)
    start.set(Calendar.MILLISECOND, 0)

    val today = Calendar.getInstance()
    today.set(Calendar.HOUR_OF_DAY, 0)
    today.set(Calendar.MINUTE, 0)
    today.set(Calendar.SECOND, 0)
    today.set(Calendar.MILLISECOND, 0)

    return (today.timeInMillis - start.timeInMillis) / 86_400_000L
}