package com.makd.afinity.ui.player.cast

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makd.afinity.R
import com.makd.afinity.cast.CastManager
import com.makd.afinity.cast.CastSessionState
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.player.PlayerViewModel
import java.util.Locale
import kotlin.math.abs

@Composable
fun CastRemoteControllerScreen(
    castState: CastSessionState,
    castManager: CastManager,
    onBackClick: () -> Unit,
    onStopCasting: () -> Unit,
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
) {
    var showSettings by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }

    val currentItem = castState.currentItem

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black)
                .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBackClick, modifier = Modifier.size(36.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_left),
                        contentDescription = stringResource(R.string.cd_back),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text =
                            stringResource(R.string.cast_connected_to, castState.deviceName ?: ""),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { showInfo = true }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_cast_connected),
                        contentDescription = stringResource(R.string.cast_info),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
                IconButton(onClick = { showSettings = true }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_speed),
                        contentDescription = stringResource(R.string.cast_settings),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (currentItem != null) {
                    val posterUrl = currentItem.images.primary
                    if (posterUrl != null) {
                        AsyncImage(
                            imageUrl = posterUrl.toString(),
                            contentDescription = currentItem.name,
                            modifier = Modifier.height(280.dp).width(190.dp),
                            contentScale = ContentScale.Crop,
                            blurHash = null,
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = currentItem.name,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (currentItem is AfinityEpisode) {
                        val seasonNum = currentItem.parentIndexNumber
                        val episodeNum = currentItem.indexNumber
                        if (seasonNum != null && episodeNum != null) {
                            Text(
                                text =
                                    stringResource(
                                        R.string.player_episode_header_fmt,
                                        seasonNum.toString().padStart(2, '0'),
                                        episodeNum.toString().padStart(2, '0'),
                                        currentItem.seriesName ?: "",
                                    ),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = formatCastTime(castState.currentPosition),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                    )
                    Slider(
                        value = castState.currentPosition.toFloat(),
                        onValueChange = { newPos -> castManager.seekTo(newPos.toLong()) },
                        valueRange = 0f..castState.duration.toFloat().coerceAtLeast(1f),
                        colors =
                            SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                            ),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text =
                            "-${formatCastTime((castState.duration - castState.currentPosition).coerceAtLeast(0))}",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            castManager.seekTo((castState.currentPosition - 15000).coerceAtLeast(0))
                        },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_rewind_backward_10),
                            contentDescription = stringResource(R.string.cd_rewind_10),
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    IconButton(
                        onClick = {
                            if (castState.isPlaying) castManager.pause() else castManager.play()
                        },
                        modifier = Modifier.size(64.dp),
                    ) {
                        if (castState.isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(40.dp),
                                color = Color.White,
                                strokeWidth = 4.dp,
                            )
                        } else {
                            Icon(
                                painter =
                                    painterResource(
                                        if (castState.isPlaying) R.drawable.ic_player_pause_filled
                                        else R.drawable.ic_player_play_filled
                                    ),
                                contentDescription =
                                    if (castState.isPlaying) stringResource(R.string.cd_pause)
                                    else stringResource(R.string.cd_play),
                                tint = Color.White,
                                modifier = Modifier.size(48.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    IconButton(
                        onClick = {
                            castManager.seekTo(
                                (castState.currentPosition + 30000).coerceAtMost(castState.duration)
                            )
                        },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_rewind_forward_30),
                            contentDescription = stringResource(R.string.cd_forward_30),
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    TextButton(onClick = onStopCasting) {
                        Icon(
                            painter = painterResource(R.drawable.ic_cast),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.cast_stop),
                            color = Color.White,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
    }

    if (showSettings) {
        CastPlaybackSettingsSheet(
            castState = castState,
            castManager = castManager,
            viewModel = viewModel,
            onDismiss = { showSettings = false },
        )
    }

    if (showInfo) {
        CastInfoBottomSheet(
            castState = castState,
            castManager = castManager,
            onStopCasting = onStopCasting,
            onDismiss = { showInfo = false },
        )
    }
}

private fun formatCastTime(timeMs: Long): String {
    val totalSeconds = abs(timeMs / 1000).toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}
