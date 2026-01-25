package com.makd.afinity.ui.livetv.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makd.afinity.data.models.livetv.AfinityProgram
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun EpgProgramCell(
    program: AfinityProgram,
    epgStartTime: LocalDateTime,
    epgEndTime: LocalDateTime,
    hourWidth: Dp,
    cellHeight: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")
    val isLive = program.isCurrentlyAiring()

    val programStart = program.startDate ?: return
    val programEnd = program.endDate ?: return

    val visibleStart = maxOf(programStart, epgStartTime)
    val visibleEnd = minOf(programEnd, epgEndTime)

    if (!visibleStart.isBefore(visibleEnd)) return

    val offsetMinutes = Duration.between(epgStartTime, visibleStart).toMinutes()
    val offsetDp = (offsetMinutes.toFloat() / 60f * hourWidth.value).dp

    val visibleDurationMinutes = Duration.between(visibleStart, visibleEnd).toMinutes()
    val widthDp = (visibleDurationMinutes.toFloat() / 60f * hourWidth.value).dp.coerceAtLeast(50.dp)

    Box(
        modifier = modifier
            .offset(x = offsetDp)
            .width(widthDp - 2.dp)
            .height(cellHeight - 2.dp)
            .padding(1.dp)
            .clip(MaterialTheme.shapes.small)
            .background(
                if (isLive) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = MaterialTheme.shapes.small
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isLive) {
                    LiveBadge()
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = program.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isLive) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isLive) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = "${programStart.format(timeFormatter)} - ${programEnd.format(timeFormatter)}",
                style = MaterialTheme.typography.labelSmall,
                color = if (isLive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1
            )

            if (isLive) {
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                ) {
                    ProgramProgressBar(program = program)
                }
            }
        }
    }
}

private fun maxOf(a: LocalDateTime, b: LocalDateTime): LocalDateTime = if (a.isAfter(b)) a else b
private fun minOf(a: LocalDateTime, b: LocalDateTime): LocalDateTime = if (a.isBefore(b)) a else b