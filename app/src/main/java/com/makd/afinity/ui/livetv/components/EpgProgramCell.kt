package com.makd.afinity.ui.livetv.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.livetv.AfinityProgram
import com.makd.afinity.data.models.livetv.GuideIndicator
import java.time.Duration
import java.time.LocalDateTime

@Composable
fun EpgProgramCell(
    program: AfinityProgram,
    epgStartTime: LocalDateTime,
    epgEndTime: LocalDateTime,
    hourWidth: Dp,
    cellHeight: Dp,
    now: LocalDateTime,
    indicators: Set<GuideIndicator>,
    colorCoded: Boolean,
    showContent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val programStart = program.startDate ?: return
    val programEnd = program.endDate ?: return

    val visibleStart = maxOf(programStart, epgStartTime)
    val visibleEnd = minOf(programEnd, epgEndTime)

    if (!visibleStart.isBefore(visibleEnd)) return

    val isAiring = program.isCurrentlyAiring(now)
    val timeFormatter = rememberProgramTimeFormatter()

    val offsetMinutes = Duration.between(epgStartTime, visibleStart).toMinutes()
    val offsetDp = (offsetMinutes.toFloat() / 60f * hourWidth.value).dp

    val visibleDurationMinutes = Duration.between(visibleStart, visibleEnd).toMinutes()
    val widthDp = (visibleDurationMinutes.toFloat() / 60f * hourWidth.value).dp

    val categoryColor =
        if (showContent && colorCoded && !isAiring) program.categoryColor() else null
    val containerColor =
        when {
            isAiring -> MaterialTheme.colorScheme.primaryContainer
            categoryColor != null -> categoryColor
            else -> MaterialTheme.colorScheme.surfaceVariant
        }
    val contentColor =
        when {
            isAiring -> MaterialTheme.colorScheme.onPrimaryContainer
            categoryColor != null -> Color.White
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    val indicator = program.guideIndicator(indicators)
    val showHd = program.isHd && GuideIndicator.HD in indicators

    Box(
        modifier =
            modifier
                .offset(x = offsetDp)
                .width((widthDp - 2.dp).coerceAtLeast(1.dp))
                .height(cellHeight - 2.dp)
                .clip(MaterialTheme.shapes.small)
                .background(containerColor)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.small,
                )
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        if (!showContent) return@Box
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = program.name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isAiring) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = contentColor,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    indicator?.let { ProgramIndicatorBadge(it) }
                    if (showHd) ProgramIndicatorBadge(GuideIndicator.HD)
                    ProgramTimerIcon(program = program, size = 14.dp)
                }

                val timeText =
                    if (isAiring) {
                        stringResource(R.string.livetv_until_fmt, programEnd.format(timeFormatter))
                    } else {
                        programTimeRange(program, timeFormatter)
                    }
                timeText?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (isAiring) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(3.dp)) {
                        ProgramProgressBar(program = program)
                    }
                }
            }
        }
    }
}

private fun maxOf(a: LocalDateTime, b: LocalDateTime): LocalDateTime = if (a.isAfter(b)) a else b

private fun minOf(a: LocalDateTime, b: LocalDateTime): LocalDateTime = if (a.isBefore(b)) a else b
