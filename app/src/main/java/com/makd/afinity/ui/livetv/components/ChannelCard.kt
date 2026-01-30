package com.makd.afinity.ui.livetv.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.makd.afinity.R
import com.makd.afinity.data.models.livetv.AfinityChannel
import com.makd.afinity.ui.components.AsyncImage

@Composable
fun ChannelCard(
    channel: AfinityChannel,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier,
    showProgramOverlays: Boolean = true
) {
    val currentProgram = channel.currentProgram

    val showProgramImage = showProgramOverlays &&
            (currentProgram?.images?.primary != null || currentProgram?.images?.thumb != null)
    val displayImageUrl = if (showProgramImage) {
        currentProgram?.images?.thumb ?: currentProgram?.images?.primary
    } else {
        channel.images.primary ?: channel.images.thumb
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (displayImageUrl != null) {
                    val imageMod = if (!showProgramImage) {
                        Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    } else {
                        Modifier.fillMaxSize()
                    }

                    AsyncImage(
                        imageUrl = displayImageUrl.toString(),
                        contentDescription = channel.name,
                        modifier = imageMod,
                        contentScale = if (showProgramImage) ContentScale.Crop else ContentScale.Fit
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_live_tv_nav),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                        )
                    }
                }

                if (showProgramImage && channel.images.primary != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(4.dp)
                    ) {
                        AsyncImage(
                            imageUrl = channel.images.primary.toString(),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                }

                IconButton(
                    onClick = onFavoriteClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(28.dp),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                        contentColor = if (channel.favorite) androidx.compose.ui.graphics.Color.Red else MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (channel.favorite) R.drawable.ic_favorite_filled else R.drawable.ic_favorite
                        ),
                        contentDescription = stringResource(R.string.cd_favorite),
                        modifier = Modifier.size(16.dp)
                    )
                }

                if (showProgramOverlays && currentProgram?.isCurrentlyAiring() == true) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                    ) {
                        LiveBadge()
                    }
                }

                if (showProgramOverlays && currentProgram?.isCurrentlyAiring() == true) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                    ) {
                        ProgramProgressBar(program = currentProgram)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            channel.channelNumber?.let { number ->
                Text(
                    text = number,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = channel.name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        val timePattern = stringResource(R.string.livetv_time_pattern)
        val programName = currentProgram?.name ?: stringResource(R.string.livetv_unknown_program)
        val start = currentProgram?.startDate
        val end = currentProgram?.endDate

        val text = if (start != null && end != null) {
            val formatter = java.time.format.DateTimeFormatter.ofPattern(timePattern)
            stringResource(
                R.string.livetv_program_time_fmt,
                programName,
                start.format(formatter),
                end.format(formatter)
            )
        } else {
            programName
        }

        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
