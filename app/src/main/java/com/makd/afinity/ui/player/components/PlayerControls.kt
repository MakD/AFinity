package com.makd.afinity.ui.player.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectDragGestures
import com.makd.afinity.ui.components.OptimizedAsyncImage
import androidx.compose.runtime.*
import com.makd.afinity.data.models.media.AfinityMediaStream
import com.makd.afinity.data.models.player.PlayerState
import com.makd.afinity.ui.player.PlayerUiState
import org.jellyfin.sdk.model.api.MediaStreamType
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.makd.afinity.data.models.extensions.logoBlurHash
import com.makd.afinity.data.models.media.AfinitySegment
import com.makd.afinity.R
import kotlin.math.roundToInt

data class AudioStreamOption(
    val stream: AfinityMediaStream,
    val displayName: String,
    val isDefault: Boolean
)

data class SubtitleStreamOption(
    val stream: AfinityMediaStream?,
    val displayName: String,
    val isDefault: Boolean,
    val isNone: Boolean = false
)

@Composable
fun PlayerControls(
    playerState: PlayerState,
    uiState: PlayerUiState,
    onPlayPauseClick: () -> Unit,
    onSeekBarChange: (Long) -> Unit,
    onTrickplayPreview: (Long, Boolean) -> Unit = { _, _ -> },
    onBackClick: () -> Unit,
    onFullscreenToggle: () -> Unit,
    onAudioTrackSelect: (Int?) -> Unit,
    onSubtitleTrackSelect: (Int?) -> Unit,
    onPlaybackSpeedChange: (Float) -> Unit,
    onLockToggle: () -> Unit,
    onSkipSegment: (AfinitySegment) -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onNextEpisode: () -> Unit = {},
    onPreviousEpisode: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showAudioSelector by remember { mutableStateOf(false) }
    var showSubtitleSelector by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }

    val currentItem = playerState.currentItem

    val audioStreamOptions = remember(currentItem) {
        currentItem?.sources?.firstOrNull()?.mediaStreams
            ?.filter { it.type == MediaStreamType.AUDIO }
            ?.map { stream ->
                val displayName = buildString {
                    append(stream.language.ifEmpty { "Unknown" })
                    append(" • ${stream.codec.uppercase()}")
                    if ((stream.channels ?: 0) > 0) {
                        append(" (${stream.channels}ch)")
                    }
                }

                AudioStreamOption(
                    stream = stream,
                    displayName = displayName,
                    isDefault = stream.isDefault
                )
            } ?: emptyList()
    }

    val subtitleStreamOptions = remember(currentItem) {
        val options = mutableListOf<SubtitleStreamOption>()

        options.add(
            SubtitleStreamOption(
                stream = null,
                displayName = "None",
                isDefault = playerState.subtitleStreamIndex == null,
                isNone = true
            )
        )

        currentItem?.sources?.firstOrNull()?.mediaStreams
            ?.filter { it.type == MediaStreamType.SUBTITLE }
            ?.forEach { stream ->
                val displayName = buildString {
                    append(stream.language.ifEmpty { "Unknown" })
                    append(" • ${stream.codec.uppercase()}")

                    if (stream.title.contains("forced", ignoreCase = true)) {
                        append(" (Forced)")
                    } else if (stream.isExternal) {
                        append(" (External)")
                    }
                }

                options.add(
                    SubtitleStreamOption(
                        stream = stream,
                        displayName = displayName,
                        isDefault = stream.isDefault,
                        isNone = false
                    )
                )
            }

        options
    }
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = uiState.showControls,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                TopControls(
                    playerState = playerState,
                    onBackClick = onBackClick,
                    onAudioToggle = { showAudioSelector = !showAudioSelector },
                    onSubtitleToggle = { showSubtitleSelector = !showSubtitleSelector },
                    onSpeedToggle = { showSpeedDialog = !showSpeedDialog },
                    onLockToggle = onLockToggle,
                    modifier = Modifier.align(Alignment.TopCenter)
                )

                if (!playerState.isControlsLocked) {
                    CenterPlayButton(
                        isPlaying = playerState.isPlaying,
                        showPlayButton = uiState.showPlayButton,
                        isBuffering = uiState.showBuffering,
                        onPlayPauseClick = onPlayPauseClick,
                        onSeekBackward = onSeekBackward,
                        onSeekForward = onSeekForward,
                        onNextEpisode = onNextEpisode,
                        onPreviousEpisode = onPreviousEpisode,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                if (!playerState.isControlsLocked) {
                    BottomControls(
                        playerState = playerState,
                        onSeekBarChange = onSeekBarChange,
                        onTrickplayPreview = onTrickplayPreview,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
        if (uiState.showSkipButton && uiState.currentSegment != null && !uiState.showControls) {
            SkipButton(
                segment = uiState.currentSegment!!,
                skipButtonText = uiState.skipButtonText,
                onClick = onSkipSegment,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
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
                                        onAudioTrackSelect(option.stream.index)
                                        showAudioSelector = false
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = playerState.audioStreamIndex == option.stream.index,
                                    onClick = {
                                        onAudioTrackSelect(option.stream.index)
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
                                        val selectedIndex = if (option.isNone) null else option.stream?.index
                                        onSubtitleTrackSelect(selectedIndex)
                                        showSubtitleSelector = false
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = if (option.isNone) {
                                        playerState.subtitleStreamIndex == null
                                    } else {
                                        playerState.subtitleStreamIndex == option.stream?.index
                                    },
                                    onClick = {
                                        val selectedIndex = if (option.isNone) null else option.stream?.index
                                        onSubtitleTrackSelect(selectedIndex)
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
            currentSpeed = playerState.playbackSpeed,
            onSpeedChange = { speed ->
                onPlaybackSpeedChange(speed)
            },
            onDismiss = { showSpeedDialog = false }
        )
    }
}

@Composable
private fun TopControls(
    playerState: PlayerState,
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
                    val currentItem = playerState.currentItem

                    if (currentItem is com.makd.afinity.data.models.media.AfinityMovie) {
                        if (currentItem?.images?.logo != null) {
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
                                text = currentItem?.name ?: "",
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
                                    text = seriesName,
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
                if (playerState.isControlsLocked) {
                    IconButton(
                        onClick = onLockToggle,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_unlock),
                            contentDescription = "Unlock",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = onLockToggle,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_lock),
                            contentDescription = "Lock",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                if (!playerState.isControlsLocked) {
                    IconButton(
                        onClick = onPipToggle,
                        modifier = Modifier
                            .size(40.dp)
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
                        modifier = Modifier
                            .size(40.dp)
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
                        modifier = Modifier
                            .size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speaker,
                            contentDescription = "Audio",
                            tint = if (playerState.audioStreamIndex != null)
                                MaterialTheme.colorScheme.primary else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = onSubtitleToggle,
                        modifier = Modifier
                            .size(40.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_cc),
                            contentDescription = "Subtitles",
                            tint = if (playerState.subtitleStreamIndex != null)
                                MaterialTheme.colorScheme.primary else Color.White,
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
                modifier = Modifier
                    .size(60.dp)
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
                modifier = Modifier
                    .size(60.dp)
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
                    .background(
                        Color.White,
                        CircleShape
                    )
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
                modifier = Modifier
                    .size(60.dp)
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
                modifier = Modifier
                    .size(60.dp)
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

@Composable
private fun BottomControls(
    playerState: PlayerState,
    onSeekBarChange: (Long) -> Unit,
    onTrickplayPreview: (Long, Boolean) -> Unit,
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
            currentPosition = playerState.currentPosition,
            duration = playerState.duration,
            onSeekBarChange = onSeekBarChange,
            onPreviewChange = onTrickplayPreview
        )
    }
}

@Composable
private fun SeekBar(
    currentPosition: Long,
    duration: Long,
    onSeekBarChange: (Long) -> Unit,
    onPreviewChange: ((Long, Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableStateOf(0f) }
    var lastUpdateTime by remember { mutableLongStateOf(0L) }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        val currentProgress = if (duration > 0) {
            (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

        val displayProgress = if (isDragging) dragProgress else currentProgress
        val displayPosition = if (isDragging) (dragProgress * duration).toLong() else currentPosition

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(displayPosition),
                color = Color.White,
                fontSize = 12.sp
            )
            Text(
                text = formatTime(duration),
                color = Color.White,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragProgress = (offset.x / size.width).coerceIn(0f, 1f)
                            val newPosition = (dragProgress * duration).toLong()
                            onSeekBarChange(newPosition)
                            onPreviewChange?.invoke(newPosition, true)
                            lastUpdateTime = System.currentTimeMillis()
                        },
                        onDrag = { _, dragAmount ->
                            dragProgress = (dragProgress + dragAmount.x / size.width).coerceIn(0f, 1f)
                            val newPosition = (dragProgress * duration).toLong()
                            onSeekBarChange(newPosition)
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastUpdateTime > 50) {
                                onPreviewChange?.invoke(newPosition, true)
                                lastUpdateTime = currentTime
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                            onPreviewChange?.invoke(0, false)
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val tapProgress = (offset.x / size.width).coerceIn(0f, 1f)
                        val newPosition = (tapProgress * duration).toLong()
                        onSeekBarChange(newPosition)
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        Color.White.copy(alpha = 0.3f),
                        RoundedCornerShape(2.dp)
                    )
                    .align(Alignment.Center)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth(displayProgress)
                    .height(4.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(2.dp)
                    )
                    .align(Alignment.CenterStart)
            )
        }
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