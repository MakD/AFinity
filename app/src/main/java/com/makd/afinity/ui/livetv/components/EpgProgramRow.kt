package com.makd.afinity.ui.livetv.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.makd.afinity.data.models.livetv.AfinityProgram
import com.makd.afinity.data.models.livetv.GuideIndicator
import com.makd.afinity.data.models.livetv.ProgramCategory
import java.time.Duration
import java.time.LocalDateTime

val NowLineWidth = 2.dp

@Composable
fun EpgProgramRow(
    programs: List<AfinityProgram>,
    epgStartTime: LocalDateTime,
    hours: Int,
    hourWidth: Dp,
    cellHeight: Dp,
    now: LocalDateTime,
    renderStart: LocalDateTime,
    renderEnd: LocalDateTime,
    indicators: Set<GuideIndicator>,
    colorCoded: Boolean,
    categories: Set<ProgramCategory>,
    onProgramClick: (AfinityProgram) -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalWidth = hourWidth * hours
    val epgEndTime = epgStartTime.plusHours(hours.toLong())
    val windowStart = if (renderStart.isAfter(epgStartTime)) renderStart else epgStartTime
    val windowEnd = if (renderEnd.isBefore(epgEndTime)) renderEnd else epgEndTime

    Box(
        modifier =
            modifier
                .width(totalWidth)
                .height(cellHeight)
                .background(MaterialTheme.colorScheme.surface)
    ) {
        val visiblePrograms =
            remember(programs, windowStart, windowEnd) {
                programs.filter { program ->
                    val programStart = program.startDate ?: return@filter false
                    val programEnd = program.endDate ?: return@filter false
                    programStart.isBefore(windowEnd) && !programEnd.isBefore(windowStart)
                }
            }

        visiblePrograms.forEach { program ->
            EpgProgramCell(
                program = program,
                epgStartTime = epgStartTime,
                epgEndTime = epgEndTime,
                hourWidth = hourWidth,
                cellHeight = cellHeight,
                now = now,
                indicators = indicators,
                colorCoded = colorCoded,
                showContent = program.matchesCategories(categories),
                onClick = { onProgramClick(program) },
            )
        }

        nowLineOffset(epgStartTime, epgEndTime, now, hourWidth)?.let { offset -> NowLine(offset) }
    }
}

@Composable
fun EpgPendingRow(
    epgStartTime: LocalDateTime,
    hours: Int,
    hourWidth: Dp,
    cellHeight: Dp,
    now: LocalDateTime,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val epgEndTime = epgStartTime.plusHours(hours.toLong())

    Box(
        modifier =
            modifier
                .width(hourWidth * hours)
                .height(cellHeight)
                .background(MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .height(cellHeight - 2.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
        )
        Column(
            modifier =
                Modifier.offset { IntOffset(scrollState.value, 0) }
                    .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PendingLine(width = 120.dp, height = 10.dp)
            PendingLine(width = 64.dp, height = 8.dp)
        }

        nowLineOffset(epgStartTime, epgEndTime, now, hourWidth)?.let { offset -> NowLine(offset) }
    }
}

@Composable
private fun PendingLine(width: Dp, height: Dp) {
    Box(
        modifier =
            Modifier.width(width)
                .height(height)
                .clip(RoundedCornerShape(height / 2))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    )
}

@Composable
private fun NowLine(offset: Dp) {
    Box(
        modifier =
            Modifier.offset(x = offset - NowLineWidth / 2)
                .width(NowLineWidth)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary)
    )
}

fun nowLineOffset(
    start: LocalDateTime,
    end: LocalDateTime,
    now: LocalDateTime,
    hourWidth: Dp,
): Dp? {
    if (now.isBefore(start) || !now.isBefore(end)) return null
    val minutes = Duration.between(start, now).toMinutes()
    return (minutes.toFloat() / 60f * hourWidth.value).dp
}
