package com.makd.afinity.ui.player.components

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.makd.afinity.R
import com.makd.afinity.data.models.extensions.logoBlurHash
import com.makd.afinity.data.models.extensions.logoImageUrlWithTransparency
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityMediaStream
import com.makd.afinity.data.models.player.PlayerEvent
import com.makd.afinity.ui.components.OptimizedAsyncImage
import com.makd.afinity.ui.player.PlayerViewModel
import org.jellyfin.sdk.model.api.MediaStreamType
import kotlin.math.abs

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

@OptIn(UnstableApi::class)
@Composable
fun PlayerControls(
    uiState: PlayerViewModel.PlayerUiState,
    player: Player,
    onPlayerEvent: (PlayerEvent) -> Unit,
    onBackClick: () -> Unit,
    onNextClick: () -> Unit = {},
    onPreviousClick: () -> Unit = {},
    onPipToggle: () -> Unit = {},
    playlistQueue: List<com.makd.afinity.data.models.media.AfinityItem> = emptyList(),
    currentPlaylistIndex: Int = -1,
    onJumpToEpisode: (java.util.UUID) -> Unit = {}
) {
    var showAudioSelector by remember { mutableStateOf(false) }
    var showSubtitleSelector by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showEpisodeSwitcher by remember { mutableStateOf(false) }

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
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        AnimatedVisibility(
            visible = if (uiState.logoAutoHide) {
                uiState.showControls && !uiState.isInPictureInPictureMode
            } else {
                true
            },
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.7f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(start = 16.dp, end = 16.dp, top = 32.dp, bottom = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.padding(start = 60.dp),
                        horizontalAlignment = Alignment.Start
                    ) {
                        val currentItem = uiState.currentItem

                        if (currentItem is com.makd.afinity.data.models.media.AfinityMovie) {
                            if (currentItem.images?.logo != null) {
                                OptimizedAsyncImage(
                                    imageUrl = currentItem.images.logoImageUrlWithTransparency.toString(),
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

                        if (currentItem is AfinityEpisode) {
                            val seasonNumber = currentItem.parentIndexNumber
                            val episodeNumber = currentItem.indexNumber
                            val episodeTitle = currentItem.name
                            val seriesName = currentItem.seriesName

                            if (seasonNumber != null && episodeNumber != null) {
                                Column(
                                    horizontalAlignment = Alignment.Start,
                                    modifier = Modifier.wrapContentWidth()
                                ) {
                                    if (currentItem.seriesLogo != null) {
                                        val logoUrl = currentItem.seriesLogo.toString().let { url ->
                                            if (url.contains("?")) "$url&format=png" else "$url?format=png"
                                        }
                                        OptimizedAsyncImage(
                                            imageUrl = logoUrl,
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
                                        text = "S${
                                            seasonNumber.toString().padStart(2, '0')
                                        }:E${
                                            episodeNumber.toString().padStart(2, '0')
                                        }: $episodeTitle",
                                        color = Color.White.copy(alpha = 0.8f),
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = uiState.showControls && !uiState.isInPictureInPictureMode,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300)),
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                TopControls(
                    modifier = Modifier.align(Alignment.TopCenter),
                    uiState = uiState,
                    onPlayerEvent = onPlayerEvent,
                    onBackClick = onBackClick,
                    onLockToggle = { onPlayerEvent(PlayerEvent.ToggleLock) },
                    onPipToggle = onPipToggle
                )

                if (!uiState.isControlsLocked && !uiState.isInPictureInPictureMode) {
                    CenterPlayButton(
                        uiState = uiState,
                        isPlaying = uiState.isPlaying,
                        showPlayButton = uiState.showControls,
                        isBuffering = uiState.isBuffering,
                        onPlayPauseClick = {
                            if (uiState.isPlaying) onPlayerEvent(PlayerEvent.Pause)
                            else onPlayerEvent(PlayerEvent.Play)
                        },
                        onSeekBackward = { onPlayerEvent(PlayerEvent.SeekRelative(-10000L)) },
                        onSeekForward = { onPlayerEvent(PlayerEvent.SeekRelative(30000L)) },
                        onNextClick = onNextClick,
                        onPreviousClick = onPreviousClick,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    BottomControls(
                        uiState = uiState,
                        onPlayerEvent = onPlayerEvent,
                        onSpeedToggle = { showSpeedDialog = !showSpeedDialog },
                        onAudioToggle = { showAudioSelector = !showAudioSelector },
                        onSubtitleToggle = { showSubtitleSelector = !showSubtitleSelector },
                        onEpisodeSwitcherToggle = { showEpisodeSwitcher = !showEpisodeSwitcher },
                        showEpisodeSwitcherButton = currentItem is AfinityEpisode && playlistQueue.size > 1,
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }
        AnimatedVisibility(
            visible = uiState.isSeeking && !uiState.showControls && !uiState.isInPictureInPictureMode,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                            )
                        )
                        .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp)
                ) {
                    SeekBar(
                        uiState = uiState,
                        onPlayerEvent = onPlayerEvent
                    )
                    Spacer(modifier = Modifier.height(48.dp))
                }
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
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 110.dp, end = 56.dp)
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
                        .heightIn(max = 400.dp)
                ) {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
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
                                        onPlayerEvent(
                                            PlayerEvent.SwitchToTrack(
                                                androidx.media3.common.C.TRACK_TYPE_AUDIO,
                                                option.position
                                            )
                                        )
                                        showAudioSelector = false
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = uiState.audioStreamIndex == option.position,
                                    onClick = {
                                        onPlayerEvent(
                                            PlayerEvent.SwitchToTrack(
                                                androidx.media3.common.C.TRACK_TYPE_AUDIO,
                                                option.position
                                            )
                                        )
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
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 110.dp, end = 8.dp)
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
                        .heightIn(max = 400.dp)
                ) {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
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
                                        onPlayerEvent(
                                            PlayerEvent.SwitchToTrack(
                                                androidx.media3.common.C.TRACK_TYPE_TEXT,
                                                option.index
                                            )
                                        )
                                        showSubtitleSelector = false
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = uiState.subtitleStreamIndex == option.index,
                                    onClick = {
                                        onPlayerEvent(
                                            PlayerEvent.SwitchToTrack(
                                                androidx.media3.common.C.TRACK_TYPE_TEXT,
                                                option.index
                                            )
                                        )
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

    if (showEpisodeSwitcher && playlistQueue.isNotEmpty()) {
        EpisodeSwitcher(
            episodes = playlistQueue,
            currentIndex = currentPlaylistIndex,
            isPlaying = uiState.isPlaying,
            onEpisodeClick = { episodeId ->
                onJumpToEpisode(episodeId)
                showEpisodeSwitcher = false
            },
            onDismiss = { showEpisodeSwitcher = false }
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun TopControls(
    modifier: Modifier = Modifier,
    uiState: PlayerViewModel.PlayerUiState,
    onPlayerEvent: (PlayerEvent) -> Unit,
    onBackClick: () -> Unit,
    onLockToggle: () -> Unit,
    onPipToggle: () -> Unit = { /* TODO */ },
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
                top = 32.dp
            )
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
                    painter = painterResource(R.drawable.ic_chevron_left),
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
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
                        painter = painterResource(id = if (uiState.isControlsLocked) R.drawable.ic_unlock_player else R.drawable.ic_lock_player),
                        contentDescription = if (uiState.isControlsLocked) "Unlock" else "Lock",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                if (!uiState.isControlsLocked && !uiState.isInPictureInPictureMode) {
                    IconButton(
                        onClick = onPipToggle,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_pip),
                            contentDescription = "Enter Picture-in-Picture",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun CenterPlayButton(
    modifier: Modifier = Modifier,
    uiState: PlayerViewModel.PlayerUiState,
    isPlaying: Boolean,
    showPlayButton: Boolean,
    isBuffering: Boolean,
    onPlayPauseClick: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onNextClick: () -> Unit,
    onPreviousClick: () -> Unit
) {
    val hasChapters = uiState.chapters.isNotEmpty()
    val isEpisode = uiState.currentItem is AfinityEpisode
    val showSkipButtons = isEpisode || hasChapters

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
            if (showSkipButtons) {
                IconButton(
                    onClick = onPreviousClick,
                    modifier = Modifier.size(60.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_player_skip_back),
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            IconButton(
                onClick = onSeekBackward,
                modifier = Modifier.size(60.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_rewind_backward_10),
                    contentDescription = "Rewind 10s",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            IconButton(
                onClick = onPlayPauseClick,
                modifier = Modifier.size(60.dp)
            ) {
                if (isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        color = Color.White,
                        strokeWidth = 4.dp
                    )
                } else {
                    Icon(
                        painter = if (isPlaying) painterResource(id = R.drawable.ic_player_pause_filled)
                        else painterResource(id = R.drawable.ic_player_play_filled),
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            IconButton(
                onClick = onSeekForward,
                modifier = Modifier.size(60.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_rewind_forward_30),
                    contentDescription = "Forward 30s",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            if (showSkipButtons) {
                IconButton(
                    onClick = onNextClick,
                    modifier = Modifier.size(60.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_player_skip_forward),
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun BottomControls(
    uiState: PlayerViewModel.PlayerUiState,
    onPlayerEvent: (PlayerEvent) -> Unit,
    onSpeedToggle: () -> Unit,
    onAudioToggle: () -> Unit,
    onSubtitleToggle: () -> Unit,
    onEpisodeSwitcherToggle: () -> Unit = {},
    showEpisodeSwitcherButton: Boolean = false,
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
                top = 10.dp,
                bottom = 10.dp
            )
    ) {
        Column {
            SeekBar(
                uiState = uiState,
                onPlayerEvent = onPlayerEvent
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (showEpisodeSwitcherButton) {
                        IconButton(
                            onClick = onEpisodeSwitcherToggle,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_episodes_list),
                                contentDescription = "Episodes",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
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
                            painter = painterResource(id = R.drawable.ic_audio),
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
                            painter = painterResource(id = R.drawable.ic_subtitles),
                            contentDescription = "Subtitles",
                            tint = if (uiState.subtitleStreamIndex != null) MaterialTheme.colorScheme.primary else Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = { onPlayerEvent(PlayerEvent.CycleVideoZoomMode) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            painter = uiState.videoZoomMode.getIconPainter(),
                            contentDescription = "Aspect Ratio",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun SeekBar(
    uiState: PlayerViewModel.PlayerUiState,
    onPlayerEvent: (PlayerEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var draggedPosition by remember { mutableStateOf<Float?>(null) }
    val duration = uiState.duration
    val position = if (uiState.isSeeking) uiState.seekPosition else uiState.currentPosition

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        modifier = Modifier.height(6.dp),
                        thumbTrackGapSize = 6.dp
                    )
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp)
                            .height(6.dp)
                    ) {
                        if (duration > 0) {
                            val progress = (sliderState.value - sliderState.valueRange.start) /
                                    (sliderState.valueRange.endInclusive - sliderState.valueRange.start)

                            val activeWidthPx = size.width * progress
                            val gapSafetyOffset = 10.dp.toPx()

                            clipRect(
                                left = (activeWidthPx + gapSafetyOffset).coerceAtMost(size.width),
                                top = 0f,
                                right = size.width,
                                bottom = size.height
                            ) {
                                uiState.chapters.forEach { chapter ->
                                    val fraction =
                                        chapter.startPosition.toFloat() / duration.toFloat()
                                    val x = size.width * fraction

                                    drawCircle(
                                        color = Color.White.copy(alpha = 0.8f),
                                        radius = 2.dp.toPx(),
                                        center = Offset(
                                            x,
                                            size.height / 2
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            },
            modifier = Modifier
                .weight(1f)
                .height(18.dp)
                .padding(start = 8.dp)
        )

        Box(
            modifier = Modifier
                .width(50.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    onPlayerEvent(PlayerEvent.ToggleRemainingTime)
                },
            contentAlignment = Alignment.CenterEnd
        ) {
            Text(
                text = if (uiState.showRemainingTime) {
                    "-${formatTime((duration - position).coerceAtLeast(0L))}"
                } else {
                    formatTime(position)
                },
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 1
            )
        }
    }
}

private fun formatTime(timeMs: Long): String {
    val totalSeconds = abs(timeMs / 1000).toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}