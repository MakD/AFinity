package com.makd.afinity.ui.livetv.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun EpgTimeHeader(
    startTime: LocalDateTime,
    visibleHours: Int,
    hourWidth: Dp,
    modifier: Modifier = Modifier
) {
    val timeFormatter = DateTimeFormatter.ofPattern("h a")

    Row(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .height(40.dp)
    ) {
        repeat(visibleHours + 1) { hourOffset ->
            val time = startTime.plusHours(hourOffset.toLong())
            val isNow = LocalDateTime.now().hour == time.hour &&
                    LocalDateTime.now().toLocalDate() == time.toLocalDate()

            Box(
                modifier = Modifier
                    .width(hourWidth)
                    .height(40.dp)
                    .background(
                        if (isNow) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        else MaterialTheme.colorScheme.surface
                    )
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = time.format(timeFormatter),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isNow) FontWeight.Bold else FontWeight.Normal,
                    color = if (isNow) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}