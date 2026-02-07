package com.makd.afinity.ui.livetv.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.livetv.models.ProgramWithChannel
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun ProgramCard(
    programWithChannel: ProgramWithChannel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    now: LocalDateTime = LocalDateTime.now(),
    cardWidth: Dp = 200.dp,
) {
    val timePattern = stringResource(R.string.livetv_time_pattern)
    val timeFormatter = remember(timePattern) { DateTimeFormatter.ofPattern(timePattern) }

    val program = programWithChannel.program
    val channel = programWithChannel.channel

    val isLive =
        remember(program, now) {
            program.startDate != null &&
                program.endDate != null &&
                now.isAfter(program.startDate) &&
                now.isBefore(program.endDate)
        }

    val progressPercentage =
        remember(program, now) {
            if (isLive && program.startDate != null && program.endDate != null) {
                val totalSeconds =
                    ChronoUnit.SECONDS.between(program.startDate, program.endDate).toFloat()
                val elapsedSeconds = ChronoUnit.SECONDS.between(program.startDate, now).toFloat()
                if (totalSeconds > 0) (elapsedSeconds / totalSeconds).coerceIn(0f, 1f) else 0f
            } else {
                0f
            }
        }

    Column(modifier = modifier.width(cardWidth)) {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            colors =
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val programImage = program.images.thumb ?: program.images.primary
                val channelImage = channel.images.thumb ?: channel.images.primary
                val imageUrl = programImage ?: channelImage

                val showChannelOverlay = programImage != null && channel.images.primary != null

                if (imageUrl != null) {
                    AsyncImage(
                        imageUrl = imageUrl.toString(),
                        contentDescription = program.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_live_tv_nav),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                    }
                }

                if (isLive) {
                    Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) { LiveBadge() }
                }

                if (showChannelOverlay) {
                    Box(
                        modifier =
                            Modifier.align(Alignment.BottomStart)
                                .padding(8.dp)
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                    RoundedCornerShape(6.dp),
                                )
                                .padding(4.dp)
                    ) {
                        AsyncImage(
                            imageUrl = channel.images.primary.toString(),
                            contentDescription = channel.name,
                            modifier = Modifier.size(28.dp),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }

                if (isLive) {
                    LinearProgressIndicator(
                        progress = { progressPercentage },
                        modifier =
                            Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Black.copy(alpha = 0.3f),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = program.name,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            channel.channelNumber?.let { number ->
                Text(
                    text = number,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = channel.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        val timeRangeFmt = stringResource(R.string.livetv_epg_time_range)

        val timeText =
            remember(program.startDate, program.endDate, timeFormatter, timeRangeFmt) {
                val start = program.startDate
                val end = program.endDate

                if (start != null && end != null) {
                    String.format(
                        timeRangeFmt,
                        start.format(timeFormatter),
                        end.format(timeFormatter),
                    )
                } else if (start != null) {
                    start.format(timeFormatter)
                } else {
                    ""
                }
            }

        if (timeText.isNotEmpty()) {
            Text(
                text = timeText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
