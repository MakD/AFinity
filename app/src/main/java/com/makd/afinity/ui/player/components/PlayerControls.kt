package com.makd.afinity.ui.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.makd.afinity.R
import com.makd.afinity.data.models.extensions.logoBlurHash
import com.makd.afinity.data.models.media.AfinityMediaStream
import com.makd.afinity.data.models.media.AfinitySegment
import com.makd.afinity.data.models.player.PlayerEvent
import com.makd.afinity.ui.components.OptimizedAsyncImage
import com.makd.afinity.ui.player.PlayerViewModel
import org.jellyfin.sdk.model.api.MediaStreamType

data class AudioStreamOption(
    val stream: AfinityMediaStream,
    val displayName: String,
    val isDefault: Boolean,
    val position: Int = 0
)

data class SubtitleStreamOption(
    val stream: AfinityMediaStream?,
    val displayName: String,
    val isDefault: Boolean,
    val index: Int,
    val isNone: Boolean = false
)

@androidx.media3.common.util.UnstableApi
@Composable
fun PlayerControls(
    uiState: PlayerViewModel.PlayerUiState,
    player: androidx.media3.common.Player,
    onPlayerEvent: (PlayerEvent) -> Unit,
    onBackClick: () -> Unit,
    onNextEpisode: () -> Unit = {},
    onPreviousEpisode: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showAudioSelector by remember { mutableStateOf(false) }
    var showSubtitleSelector by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }

    val currentItem = uiState.currentItem

    val audioStreamOptions = remember(currentItem) {
        val streams = currentItem?.sources?.firstOrNull()?.mediaStreams
            ?.filter { it.type == MediaStreamType.AUDIO }
            ?.mapIndexed { index, stream ->
                val displayName = buildString {
                    append(stream.language?.uppercase() ?: "Unknown")
                    append(" • ${stream.codec?.uppercase() ?: "N/A"}")
                    if ((stream.channels ?: 0) > 0) {
                        append(" (${stream.channels}ch)")
                    }
                }

                AudioStreamOption(
                    stream = stream,
                    displayName = displayName,
                    isDefault = stream.isDefault,
                    position = index
                )
            } ?: emptyList()
        streams
    }

    val subtitleStreamOptions = remember(currentItem, player.currentTracks) {
        val options = mutableListOf<SubtitleStreamOption>()
        options.add(
            SubtitleStreamOption(
                stream = null,
                displayName = "None",
                isDefault = false,
                index = -1,
                isNone = true
            )
        )
        player.currentTracks.groups
            .filter { it.type == androidx.media3.common.C.TRACK_TYPE_TEXT && it.isSupported }
            .forEachIndexed { index, trackGroup ->
                val format = trackGroup.mediaTrackGroup.getFormat(0)
                val displayName = format.label ?: format.language ?: "Track ${index + 1}"
                options.add(
                    SubtitleStreamOption(
                        stream = null,
                        displayName = displayName,
                        isDefault = trackGroup.isSelected,
                        index = index,
                        isNone = false
                    )
                )
            }
        options
    }

    AnimatedVisibility(
        visible = uiState.showControls,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300)),
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            TopControls(
                uiState = uiState,
                onBackClick = onBackClick,
                onAudioToggle = { showAudioSelector = !showAudioSelector },
                onSubtitleToggle = { showSubtitleSelector = !showSubtitleSelector },
                onSpeedToggle = { showSpeedDialog = !showSpeedDialog },
                onLockToggle = { onPlayerEvent(PlayerEvent.ToggleLock) },
                modifier = Modifier.align(Alignment.TopCenter)
            )

            if (!uiState.isControlsLocked) {
                CenterPlayButton(
                    isPlaying = uiState.isPlaying,
                    showPlayButton = uiState.showControls,
                    isBuffering = uiState.isBuffering,
                    onPlayPauseClick = {
                        if (uiState.isPlaying) onPlayerEvent(PlayerEvent.Pause)
                        else onPlayerEvent(PlayerEvent.Play)
                    },
                    onSeekBackward = { onPlayerEvent(PlayerEvent.SeekRelative(-10000L)) },
                    onSeekForward = { onPlayerEvent(PlayerEvent.SeekRelative(30000L)) },
                    onNextEpisode = onNextEpisode,
                    onPreviousEpisode = onPreviousEpisode,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            if (!uiState.isControlsLocked) {
                BottomControls(
                    uiState = uiState,
                    onPlayerEvent = onPlayerEvent,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
    if (uiState.showSkipButton && uiState.currentSegment != null) {
        Box(modifier = Modifier.fillMaxSize()) {
            SkipButton(
                segment = uiState.currentSegment,
                skipButtonText = uiState.skipButtonText,
                onClick = { onPlayerEvent(PlayerEvent.SkipSegment(uiState.currentSegment)) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = 16.dp,
                        bottom = if (uiState.showControls) 70.dp else 16.dp
                    )
            )
        }
    }

    if (showAudioSelector && audioStreamOptions.isNotEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    showAudioSelector = false
                }
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 80.dp, end = 56.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* Consume clicks */ }
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Color.Black.copy(alpha = 0.95f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                        .widthIn(min = 200.dp, max = 280.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Audio Track",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        audioStreamOptions.forEach { option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onPlayerEvent(PlayerEvent.SwitchToTrack(androidx.media3.common.C.TRACK_TYPE_AUDIO, option.position))
                                        showAudioSelector = false
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = uiState.audioStreamIndex == option.position,
                                    onClick = {
                                        onPlayerEvent(PlayerEvent.SwitchToTrack(androidx.media3.common.C.TRACK_TYPE_AUDIO, option.position))
                                        showAudioSelector = false
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = option.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSubtitleSelector && subtitleStreamOptions.isNotEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    showSubtitleSelector = false
                }
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 80.dp, end = 8.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* Consume clicks */ }
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Color.Black.copy(alpha = 0.95f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                        .widthIn(min = 200.dp, max = 280.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Subtitles",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        subtitleStreamOptions.forEach { option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onPlayerEvent(PlayerEvent.SwitchToTrack(androidx.media3.common.C.TRACK_TYPE_TEXT, option.index))
                                        showSubtitleSelector = false
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = uiState.subtitleStreamIndex == option.index,
                                    onClick = {
                                        onPlayerEvent(PlayerEvent.SwitchToTrack(androidx.media3.common.C.TRACK_TYPE_TEXT, option.index))
                                        showSubtitleSelector = false
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = option.displayName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSpeedDialog) {
        PlaybackSpeedDialog(
            currentSpeed = uiState.playbackSpeed,
            onSpeedChange = { speed -> onPlayerEvent(PlayerEvent.SetPlaybackSpeed(speed)) },
            onDismiss = { showSpeedDialog = false }
        )
    }
}
@androidx.media3.common.util.UnstableApi
@Composable
private fun TopControls(
    uiState: PlayerViewModel.PlayerUiState,
    onBackClick: () -> Unit,
    onLockToggle: () -> Unit,
    onPipToggle: () -> Unit = { /* TODO */ },
    onSpeedToggle: () -> Unit,
    onAudioToggle: () -> Unit = {},
    onSubtitleToggle: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.7f),
                        Color.Transparent
                    )
                )
            )
            .padding(
                start = 16.dp,
                end = 16.dp,
                bottom = 16.dp,
                top = 32.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(
                modifier = Modifier.padding(start = 16.dp),
                horizontalAlignment = Alignment.Start
            ) {
                val currentItem = uiState.currentItem

                if (currentItem is com.makd.afinity.data.models.media.AfinityMovie) {
                    if (currentItem.images?.logo != null) {
                        OptimizedAsyncImage(
                            imageUrl = currentItem.images.logo.toString(),
                            contentDescription = "Logo",
                            modifier = Modifier
                                .height(60.dp)
                                .widthIn(max = 200.dp),
                            contentScale = ContentScale.Fit,
                            blurHash = currentItem.images.logoBlurHash
                        )
                    } else {
                        Text(
                            text = currentItem.name,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (currentItem is com.makd.afinity.data.models.media.AfinityEpisode) {
                    val seasonNumber = currentItem.parentIndexNumber
                    val episodeNumber = currentItem.indexNumber
                    val episodeTitle = currentItem.name
                    val seriesName = currentItem.seriesName

                    if (seasonNumber != null && episodeNumber != null) {
                        if (currentItem.seriesLogo != null) {
                            OptimizedAsyncImage(
                                imageUrl = currentItem.seriesLogo.toString(),
                                contentDescription = "Series Logo",
                                modifier = Modifier
                                    .height(60.dp)
                                    .widthIn(max = 200.dp),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Text(
                                text = seriesName ?: "",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 18.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = "S${seasonNumber.toString().padStart(2, '0')}:E${episodeNumber.toString().padStart(2, '0')}: $episodeTitle",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onLockToggle,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        painter = painterResource(id = if (uiState.isControlsLocked) R.drawable.ic_unlock else R.drawable.ic_lock),
                        contentDescription = if (uiState.isControlsLocked) "Unlock" else "Lock",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                if (!uiState.isControlsLocked) {
                    IconButton(
                        onClick = onPipToggle,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_pip),
                            contentDescription = "PiP",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = onSpeedToggle,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_speed),
                            contentDescription = "Speed",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = onAudioToggle,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speaker,
                            contentDescription = "Audio",
                            tint = if (uiState.audioStreamIndex != null) MaterialTheme.colorScheme.primary else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = onSubtitleToggle,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_cc),
                            contentDescription = "Subtitles",
                            tint = if (uiState.subtitleStreamIndex != null) MaterialTheme.colorScheme.primary else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CenterPlayButton(
    isPlaying: Boolean,
    showPlayButton: Boolean,
    isBuffering: Boolean,
    onPlayPauseClick: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onNextEpisode: () -> Unit = {},
    onPreviousEpisode: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = showPlayButton,
        enter = scaleIn(animationSpec = tween(200)) + fadeIn(),
        exit = scaleOut(animationSpec = tween(200)) + fadeOut(),
        modifier = modifier
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPreviousEpisode,
                modifier = Modifier.size(60.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            IconButton(
                onClick = onSeekBackward,
                modifier = Modifier.size(60.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Replay10,
                    contentDescription = "Rewind 10s",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            IconButton(
                onClick = onPlayPauseClick,
                modifier = Modifier
                    .size(60.dp)
                    .background(Color.White, CircleShape)
            ) {
                if (isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        color = Color.Black,
                        strokeWidth = 3.dp
                    )
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.Black,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            IconButton(
                onClick = onSeekForward,
                modifier = Modifier.size(60.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Forward30,
                    contentDescription = "Forward 30s",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            IconButton(
                onClick = onNextEpisode,
                modifier = Modifier.size(60.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}
@androidx.media3.common.util.UnstableApi
@Composable
private fun BottomControls(
    uiState: PlayerViewModel.PlayerUiState,
    onPlayerEvent: (PlayerEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.7f)
                    )
                )
            )
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = 32.dp
            )
    ) {
        SeekBar(
            uiState = uiState,
            onPlayerEvent = onPlayerEvent
        )
    }
}
@androidx.media3.common.util.UnstableApi
@Composable
private fun SeekBar(
    uiState: PlayerViewModel.PlayerUiState,
    onPlayerEvent: (PlayerEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var draggedPosition by remember { mutableStateOf<Float?>(null) }
    val duration = uiState.duration
    val position = if (uiState.isSeeking) uiState.seekPosition else uiState.currentPosition

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(draggedPosition?.toLong() ?: position),
                color = Color.White,
                fontSize = 12.sp
            )
            Text(
                text = formatTime(duration),
                color = Color.White,
                fontSize = 12.sp
            )
        }

        Slider(
            value = draggedPosition ?: position.toFloat(),
            onValueChange = { newPosition ->
                if (!uiState.isSeeking) {
                    onPlayerEvent(PlayerEvent.OnSeekBarDragStart)
                }
                draggedPosition = newPosition
                onPlayerEvent(PlayerEvent.OnSeekBarValueChange(newPosition.toLong()))
            },
            onValueChangeFinished = {
                onPlayerEvent(PlayerEvent.OnSeekBarDragFinished)
                draggedPosition = null
            },
            valueRange = 0f..duration.toFloat().coerceAtLeast(0f),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
            ),
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(4.dp)
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
        )
    }
}

private fun formatTime(timeMs: Long): String {
    val totalSeconds = (timeMs / 1000).toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}