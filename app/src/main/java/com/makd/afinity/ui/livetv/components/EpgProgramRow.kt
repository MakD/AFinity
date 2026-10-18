package com.makd.afinity.ui.livetv.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
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
    indicators: Set<GuideIndicator>,
    colorCoded: Boolean,
    categories: Set<ProgramCategory>,
    onProgramClick: (AfinityProgram) -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalWidth = hourWidth * hours
    val epgEndTime = epgStartTime.plusHours(hours.toLong())
    val now = rememberCurrentTime()

    Box(
        modifier =
            modifier
                .width(totalWidth)
                .height(cellHeight)
                .background(MaterialTheme.colorScheme.surface)
    ) {
        val visiblePrograms =
            remember(programs, epgStartTime, epgEndTime) {
                programs
                    .filter { program ->
                        val programStart = program.startDate ?: return@filter false
                        val programEnd = program.endDate ?: return@filter false
                        programStart.isBefore(epgEndTime) && !programEnd.isBefore(epgStartTime)
                    }
                    .sortedBy { it.startDate }
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

        nowLineOffset(epgStartTime, epgEndTime, now, hourWidth)?.let { offset ->
            Box(
                modifier =
                    Modifier.offset(x = offset - NowLineWidth / 2)
                        .width(NowLineWidth)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
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
