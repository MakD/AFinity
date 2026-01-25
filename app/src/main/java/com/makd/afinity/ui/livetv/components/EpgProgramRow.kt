package com.makd.afinity.ui.livetv.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.makd.afinity.data.models.livetv.AfinityChannel
import com.makd.afinity.data.models.livetv.AfinityProgram
import java.time.LocalDateTime

@Composable
fun EpgProgramRow(
    channel: AfinityChannel,
    programs: List<AfinityProgram>,
    epgStartTime: LocalDateTime,
    visibleHours: Int,
    hourWidth: Dp,
    cellHeight: Dp,
    onProgramClick: (AfinityProgram) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalWidth = hourWidth * (visibleHours + 1)
    val epgEndTime = epgStartTime.plusHours(visibleHours.toLong() + 1)

    Box(
        modifier = modifier
            .width(totalWidth)
            .height(cellHeight)
            .background(MaterialTheme.colorScheme.surface)
    ) {
        val visiblePrograms = programs.filter { program ->
            val programStart = program.startDate ?: return@filter false
            val programEnd = program.endDate ?: return@filter false
            programStart.isBefore(epgEndTime) && programEnd.isAfter(epgStartTime)
        }.sortedBy { it.startDate }

        visiblePrograms.forEach { program ->
            EpgProgramCell(
                program = program,
                epgStartTime = epgStartTime,
                epgEndTime = epgEndTime,
                hourWidth = hourWidth,
                cellHeight = cellHeight,
                onClick = { onProgramClick(program) }
            )
        }
    }
}