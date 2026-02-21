package com.makd.afinity.ui.player.cast

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
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
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.player.PlayerViewModel
import com.makd.afinity.ui.player.components.PlaybackSpeedDialog
import org.jellyfin.sdk.model.api.MediaStreamType
import java.util.Locale
import kotlin.math.abs

data class CastBitrateOption(val label: String, val bitrate: Int)

@Composable
fun CastRemoteControllerScreen(
    castState: CastSessionState,
    castManager: CastManager,
    onBackClick: () -> Unit,
    onStopCasting: () -> Unit,
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
) {
    var showInfo by remember { mutableStateOf(false) }
    var seekDragPosition by remember { mutableStateOf<Float?>(null) }

    var showSpeedDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }

    val currentItem = castState.currentItem
    val posterUrl = currentItem?.images?.primary?.toString()

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (posterUrl != null) {
            AsyncImage(
                imageUrl = posterUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().blur(100.dp).alpha(0.6f),
                contentScale = ContentScale.Crop,
                blurHash = null,
            )
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
        }

        Column(
            modifier =
                Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = if (isLandscape) 8.dp else 24.dp, vertical = 16.dp),
            verticalArrangement = if (isLandscape) Arrangement.Top else Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_cast_connected),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = castState.deviceName ?: "Unknown Device",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_chevron_left),
                        contentDescription = stringResource(R.string.cd_back),
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }

                Row(modifier = Modifier.align(Alignment.CenterEnd)) {
                    IconButton(onClick = { showInfo = true }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_info_outlined),
                            contentDescription = stringResource(R.string.cast_info),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            if (isLandscape) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(0.45f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CastMediaInfo(currentItem, posterUrl, isLandscape = true)
                    }

                    Spacer(modifier = Modifier.width(32.dp))

                    Column(
                        modifier = Modifier.weight(0.55f).fillMaxHeight(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CastPlaybackControls(
                            castState = castState,
                            castManager = castManager,
                            seekDragPosition = seekDragPosition,
                            onSeekDragChange = { seekDragPosition = it },
                            onSeekDragFinished = {
                                seekDragPosition?.let { pos ->
                                    castManager.seekTo(pos.toLong())
                                    seekDragPosition = null
                                }
                            },
                            onStopCasting = onStopCasting,
                            onShowSpeed = { showSpeedDialog = true },
                            onShowQuality = { showQualityDialog = true },
                            onShowAudio = { showAudioDialog = true },
                            onShowSubtitle = { showSubtitleDialog = true },
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CastMediaInfo(currentItem, posterUrl, isLandscape = false)
                }

                CastPlaybackControls(
                    castState = castState,
                    castManager = castManager,
                    seekDragPosition = seekDragPosition,
                    onSeekDragChange = { seekDragPosition = it },
                    onSeekDragFinished = {
                        seekDragPosition?.let { pos ->
                            castManager.seekTo(pos.toLong())
                            seekDragPosition = null
                        }
                    },
                    onStopCasting = onStopCasting,
                    onShowSpeed = { showSpeedDialog = true },
                    onShowQuality = { showQualityDialog = true },
                    onShowAudio = { showAudioDialog = true },
                    onShowSubtitle = { showSubtitleDialog = true },
                )
            }
        }
    }

    if (showSpeedDialog) {
        PlaybackSpeedDialog(
            currentSpeed = castState.playbackSpeed,
            onSpeedChange = { newSpeed -> castManager.setPlaybackSpeed(newSpeed) },
            onDismiss = { showSpeedDialog = false },
        )
    }

    if (showQualityDialog) {
        CastQualitySelectionDialog(
            castState = castState,
            castManager = castManager,
            onDismiss = { showQualityDialog = false },
        )
    }

    if (showAudioDialog) {
        CastAudioSelectionDialog(
            castState = castState,
            castManager = castManager,
            onDismiss = { showAudioDialog = false },
        )
    }

    if (showSubtitleDialog) {
        CastSubtitleSelectionDialog(
            castState = castState,
            castManager = castManager,
            onDismiss = { showSubtitleDialog = false },
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

@Composable
private fun CastMediaInfo(currentItem: AfinityItem?, posterUrl: String?, isLandscape: Boolean) {
    if (currentItem == null) return

    val posterFraction = if (isLandscape) 0.5f else 0.65f

    if (posterUrl != null) {
        Surface(
            modifier = Modifier.fillMaxWidth(posterFraction).aspectRatio(2f / 3f),
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 16.dp,
            color = Color.Transparent,
        ) {
            AsyncImage(
                imageUrl = posterUrl,
                contentDescription = currentItem.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                blurHash = null,
            )
        }
    } else {
        Box(
            modifier =
                Modifier.fillMaxWidth(posterFraction)
                    .aspectRatio(2f / 3f)
                    .background(Color.DarkGray, RoundedCornerShape(16.dp))
        )
    }

    Spacer(modifier = Modifier.height(if (isLandscape) 16.dp else 32.dp))

    Text(
        text = currentItem.name,
        color = Color.White,
        fontSize = if (isLandscape) 20.sp else 24.sp,
        fontWeight = FontWeight.ExtraBold,
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
                color = Color.White.copy(alpha = 0.6f),
                fontSize = if (isLandscape) 13.sp else 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun CastPlaybackControls(
    castState: CastSessionState,
    castManager: CastManager,
    seekDragPosition: Float?,
    onSeekDragChange: (Float) -> Unit,
    onSeekDragFinished: () -> Unit,
    onStopCasting: () -> Unit,
    onShowSpeed: () -> Unit,
    onShowQuality: () -> Unit,
    onShowAudio: () -> Unit,
    onShowSubtitle: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        val displayPosition = seekDragPosition ?: castState.currentPosition.toFloat()

        Slider(
            value = displayPosition,
            onValueChange = onSeekDragChange,
            onValueChangeFinished = onSeekDragFinished,
            valueRange = 0f..castState.duration.toFloat().coerceAtLeast(1f),
            colors =
                SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                ),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatCastTime(displayPosition.toLong()),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text =
                    "-${formatCastTime((castState.duration - displayPosition.toLong()).coerceAtLeast(0))}",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    castManager.seekTo((castState.currentPosition - 10000).coerceAtLeast(0))
                },
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_rewind_backward_10),
                    contentDescription = stringResource(R.string.cd_rewind_10),
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }

            Spacer(modifier = Modifier.width(32.dp))

            Box(
                modifier =
                    Modifier.size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {
                                if (castState.isPlaying) castManager.pause() else castManager.play()
                            },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                if (castState.isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 3.dp,
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
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(32.dp))

            IconButton(
                onClick = {
                    castManager.seekTo(
                        (castState.currentPosition + 30000).coerceAtMost(castState.duration)
                    )
                },
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_rewind_forward_30),
                    contentDescription = stringResource(R.string.cd_forward_30),
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onShowSpeed) {
                Icon(
                    painter = painterResource(R.drawable.ic_speed),
                    contentDescription = "Playback Speed",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
            IconButton(onClick = onShowQuality) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = "Quality/Bitrate",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
            IconButton(onClick = onShowAudio) {
                Icon(
                    painter = painterResource(R.drawable.ic_audio),
                    contentDescription = "Audio Track",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
            IconButton(onClick = onShowSubtitle) {
                Icon(
                    painter = painterResource(R.drawable.ic_subtitles),
                    contentDescription = "Subtitles",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Surface(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            shape = RoundedCornerShape(50),
            color = Color.White.copy(alpha = 0.1f),
            contentColor = Color.White,
        ) {
            Row(
                modifier =
                    Modifier.clickable(onClick = onStopCasting)
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_cast_connected),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.cast_stop),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun CastQualitySelectionDialog(
    castState: CastSessionState,
    castManager: CastManager,
    onDismiss: () -> Unit,
) {
    val currentItem = castState.currentItem ?: return

    val bitrateOptions =
        listOf(
            CastBitrateOption("Max (16 Mbps)", 16_000_000),
            CastBitrateOption("8 Mbps", 8_000_000),
            CastBitrateOption("4 Mbps", 4_000_000),
            CastBitrateOption("2 Mbps", 2_000_000),
            CastBitrateOption("1 Mbps", 1_000_000),
        )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cast_quality)) },
        text = {
            LazyColumn {
                items(bitrateOptions) { option ->
                    val isSelected = castState.castBitrate == option.bitrate
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .clickable {
                                    if (!isSelected) {
                                        castState.serverBaseUrl?.let { baseUrl ->
                                            castManager.changeBitrate(
                                                bitrate = option.bitrate,
                                                item = currentItem,
                                                serverBaseUrl = baseUrl,
                                                mediaSourceId = castState.mediaSourceId ?: "",
                                                audioStreamIndex = castState.audioStreamIndex,
                                                subtitleStreamIndex = castState.subtitleStreamIndex,
                                                enableHevc = false,
                                            )
                                        }
                                    }
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null,
                            colors =
                                RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                ),
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text = option.label, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
fun CastAudioSelectionDialog(
    castState: CastSessionState,
    castManager: CastManager,
    onDismiss: () -> Unit,
) {
    val currentItem = castState.currentItem ?: return
    val mediaSource =
        currentItem.sources.firstOrNull { it.id == castState.mediaSourceId }
            ?: currentItem.sources.firstOrNull()
            ?: return

    val audioStreams = mediaSource.mediaStreams.filter { it.type == MediaStreamType.AUDIO }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_audio_title)) },
        text = {
            LazyColumn {
                items(audioStreams) { stream ->
                    val isSelected = stream.index == castState.audioStreamIndex
                    val displayName = buildString {
                        append(stream.language?.uppercase() ?: "Unknown")
                        append(" - ${stream.codec?.uppercase() ?: "N/A"}")
                        if ((stream.channels ?: 0) > 0) {
                            append(" (${stream.channels}ch)")
                        }
                    }

                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .clickable {
                                    if (!isSelected) {
                                        castState.serverBaseUrl?.let { baseUrl ->
                                            castManager.switchAudioTrack(
                                                audioStreamIndex = stream.index,
                                                item = currentItem,
                                                serverBaseUrl = baseUrl,
                                                mediaSourceId = mediaSource.id ?: "",
                                                subtitleStreamIndex = castState.subtitleStreamIndex,
                                                maxBitrate = castState.castBitrate,
                                                enableHevc = false,
                                            )
                                        }
                                    }
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null,
                            colors =
                                RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                ),
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = displayName,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}

@Composable
fun CastSubtitleSelectionDialog(
    castState: CastSessionState,
    castManager: CastManager,
    onDismiss: () -> Unit,
) {
    val currentItem = castState.currentItem ?: return
    val mediaSource =
        currentItem.sources.firstOrNull { it.id == castState.mediaSourceId }
            ?: currentItem.sources.firstOrNull()
            ?: return

    val subtitleStreams = mediaSource.mediaStreams.filter { it.type == MediaStreamType.SUBTITLE }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_subtitle_title)) },
        text = {
            LazyColumn {
                item {
                    val isOff = castState.subtitleStreamIndex == null
                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .clickable {
                                    if (!isOff) {
                                        castState.serverBaseUrl?.let { baseUrl ->
                                            castManager.switchSubtitleTrack(
                                                subtitleStreamIndex = null,
                                                item = currentItem,
                                                serverBaseUrl = baseUrl,
                                                mediaSourceId = mediaSource.id ?: "",
                                                audioStreamIndex = castState.audioStreamIndex,
                                                maxBitrate = castState.castBitrate,
                                                enableHevc = false,
                                            )
                                        }
                                    }
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isOff,
                            onClick = null,
                            colors =
                                RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                ),
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = stringResource(R.string.track_none),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                items(subtitleStreams) { stream ->
                    val isSelected = stream.index == castState.subtitleStreamIndex
                    val displayName = buildString {
                        append(stream.displayTitle ?: stream.language?.uppercase() ?: "Unknown")
                        stream.codec?.let { append(" ($it)") }
                    }

                    Row(
                        modifier =
                            Modifier.fillMaxWidth()
                                .clickable {
                                    if (!isSelected) {
                                        castState.serverBaseUrl?.let { baseUrl ->
                                            castManager.switchSubtitleTrack(
                                                subtitleStreamIndex = stream.index,
                                                item = currentItem,
                                                serverBaseUrl = baseUrl,
                                                mediaSourceId = mediaSource.id ?: "",
                                                audioStreamIndex = castState.audioStreamIndex,
                                                maxBitrate = castState.castBitrate,
                                                enableHevc = false,
                                            )
                                        }
                                    }
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null,
                            colors =
                                RadioButtonDefaults.colors(
                                    selectedColor = MaterialTheme.colorScheme.primary
                                ),
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = displayName,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
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
