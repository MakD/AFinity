package com.makd.afinity.ui.player.cast

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.makd.afinity.R
import com.makd.afinity.cast.CastManager
import com.makd.afinity.cast.CastSessionState
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.ui.audiobookshelf.player.util.rememberDominantColor
import com.makd.afinity.ui.components.AsyncImage
import com.makd.afinity.ui.player.PlayerViewModel
import com.makd.afinity.ui.player.components.PlaybackSpeedDialog
import org.jellyfin.sdk.model.api.MediaStreamType
import java.util.Locale
import kotlin.math.abs

data class CastBitrateOption(val label: String, val bitrate: Int)

@UnstableApi
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
    var showEpisodeList by remember { mutableStateOf(false) }

    val playlistState by viewModel.playlistState.collectAsStateWithLifecycle()
    val currentItem = castState.currentItem
    val isEpisode = currentItem is AfinityEpisode
    val posterUrl = currentItem?.images?.primary?.toString()

    val defaultColor = MaterialTheme.colorScheme.surface
    val dominantColor = rememberDominantColor(posterUrl, defaultColor)
    val animatedColor by
        animateColorAsState(
            targetValue = dominantColor,
            animationSpec = tween(durationMillis = 800),
            label = "color",
        )

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors =
                            listOf(
                                animatedColor.copy(alpha = 0.8f),
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.1f),
                                Color.Black.copy(alpha = 0.9f),
                            )
                    )
                )
    ) {
        Column(
            modifier =
                Modifier.fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(
                        horizontal = if (isLandscape) 32.dp else 24.dp,
                        vertical = 16.dp,
                    ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_keyboard_arrow_down),
                        contentDescription = stringResource(R.string.cd_back),
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }

                Text(
                    text = stringResource(R.string.abs_now_playing),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    letterSpacing = 2.sp,
                    modifier = Modifier.align(Alignment.Center),
                )

                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onStopCasting) {
                        Icon(
                            painter = painterResource(R.drawable.ic_cast_connected),
                            contentDescription = stringResource(R.string.cast_stop),
                            tint = animatedColor,
                            modifier = Modifier.size(24.dp),
                        )
                    }
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

            Row(
                modifier = Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_cast_connected),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(12.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = castState.deviceName ?: stringResource(R.string.unknown_device),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (isLandscape) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.weight(0.45f).fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CastCoverArt(
                            currentItem = currentItem,
                            posterUrl = posterUrl,
                            isLandscape = true,
                        )
                    }

                    Column(
                        modifier = Modifier.weight(0.55f).fillMaxHeight(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CastTitleSection(
                            currentItem = currentItem,
                            isEpisode = isEpisode,
                            animatedColor = animatedColor,
                            isLandscape = true,
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        CastSeekSection(
                            castState = castState,
                            seekDragPosition = seekDragPosition,
                            onSeekDragChange = { seekDragPosition = it },
                            onSeekDragFinished = {
                                seekDragPosition?.let { pos ->
                                    castManager.seekTo(pos.toLong())
                                    seekDragPosition = null
                                }
                            },
                            animatedColor = animatedColor,
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        CastControlsRow(
                            castState = castState,
                            castManager = castManager,
                            animatedColor = animatedColor,
                        )
                        Spacer(modifier = Modifier.height(32.dp))
                        CastOptionsRow(
                            animatedColor = animatedColor,
                            showEpisodeListButton = isEpisode && playlistState.queue.size > 1,
                            onShowEpisodeList = { showEpisodeList = true },
                            onShowSpeed = { showSpeedDialog = true },
                            onShowQuality = { showQualityDialog = true },
                            onShowAudio = { showAudioDialog = true },
                            onShowSubtitle = { showSubtitleDialog = true },
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(24.dp))

                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CastCoverArt(
                        currentItem = currentItem,
                        posterUrl = posterUrl,
                        isLandscape = false,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                CastTitleSection(
                    currentItem = currentItem,
                    isEpisode = isEpisode,
                    animatedColor = animatedColor,
                    isLandscape = false,
                )

                Spacer(modifier = Modifier.height(12.dp))

                CastSeekSection(
                    castState = castState,
                    seekDragPosition = seekDragPosition,
                    onSeekDragChange = { seekDragPosition = it },
                    onSeekDragFinished = {
                        seekDragPosition?.let { pos ->
                            castManager.seekTo(pos.toLong())
                            seekDragPosition = null
                        }
                    },
                    animatedColor = animatedColor,
                )

                Spacer(modifier = Modifier.height(24.dp))

                CastControlsRow(
                    castState = castState,
                    castManager = castManager,
                    animatedColor = animatedColor,
                )

                Spacer(modifier = Modifier.height(44.dp))

                CastOptionsRow(
                    animatedColor = animatedColor,
                    showEpisodeListButton = isEpisode && playlistState.queue.size > 1,
                    onShowEpisodeList = { showEpisodeList = true },
                    onShowSpeed = { showSpeedDialog = true },
                    onShowQuality = { showQualityDialog = true },
                    onShowAudio = { showAudioDialog = true },
                    onShowSubtitle = { showSubtitleDialog = true },
                )

                Spacer(modifier = Modifier.height(24.dp))
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

    if (showEpisodeList && playlistState.queue.isNotEmpty()) {
        CastEpisodeListSheet(
            episodes = playlistState.queue,
            currentItemId = currentItem?.id,
            isPlaying = castState.isPlaying,
            onEpisodeClick = { episode -> viewModel.jumpToEpisode(episode.id) },
            onDismiss = { showEpisodeList = false },
        )
    }
}

@Composable
private fun CastCoverArt(currentItem: AfinityItem?, posterUrl: String?, isLandscape: Boolean) {
    if (currentItem == null) return

    val isEpisode = currentItem is AfinityEpisode
    val aspectRatio = if (isEpisode) 16f / 9f else 2f / 3f
    val posterFraction =
        when {
            isEpisode && isLandscape -> 0.75f
            isEpisode -> 0.95f
            isLandscape -> 0.5f
            else -> 0.65f
        }
    val cornerRadius = if (isLandscape) 24.dp else 32.dp
    val elevation = if (isLandscape) 16.dp else 24.dp

    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val screenWidthDp = with(density) { windowInfo.containerSize.width.toDp() }
    val targetWidth = screenWidthDp * posterFraction
    val targetHeight = targetWidth / aspectRatio

    Surface(
        modifier =
            Modifier.fillMaxWidth(posterFraction)
                .aspectRatio(aspectRatio)
                .shadow(
                    elevation = elevation,
                    shape = RoundedCornerShape(cornerRadius),
                    spotColor = Color.Black,
                ),
        shape = RoundedCornerShape(cornerRadius),
        color = Color.Transparent,
    ) {
        if (posterUrl != null) {
            AsyncImage(
                imageUrl = posterUrl,
                contentDescription = currentItem.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                blurHash = null,
                targetWidth = targetWidth,
                targetHeight = targetHeight,
            )
        } else {
            Box(
                modifier =
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    }
}

@Composable
private fun CastTitleSection(
    currentItem: AfinityItem?,
    isEpisode: Boolean,
    animatedColor: Color,
    isLandscape: Boolean,
) {
    if (currentItem == null) return

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = currentItem.name,
            style =
                if (isLandscape)
                    MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                else
                    MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        if (currentItem is AfinityEpisode && currentItem.seriesName != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = currentItem.seriesName!!,
                style =
                    if (isLandscape) MaterialTheme.typography.bodyMedium
                    else MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        val episodeLabel =
            if (currentItem is AfinityEpisode) {
                val s = currentItem.parentIndexNumber?.toString()?.padStart(2, '0')
                val e = currentItem.indexNumber?.toString()?.padStart(2, '0')
                if (s != null && e != null) "S${s}E${e}" else null
            } else null

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = episodeLabel ?: " ",
            style = MaterialTheme.typography.labelLarge,
            color = animatedColor,
            maxLines = 1,
            modifier =
                Modifier.alpha(if (episodeLabel != null) 1f else 0f)
                    .basicMarquee(iterations = Int.MAX_VALUE, velocity = 30.dp),
        )
    }
}

@Composable
private fun CastSeekSection(
    castState: CastSessionState,
    seekDragPosition: Float?,
    onSeekDragChange: (Float) -> Unit,
    onSeekDragFinished: () -> Unit,
    animatedColor: Color,
) {
    val displayPosition = seekDragPosition ?: castState.currentPosition.toFloat()
    val duration = castState.duration.toFloat().coerceAtLeast(1f)
    val chapters = castState.currentItem?.chapters ?: emptyList()

    Slider(
        value = displayPosition,
        onValueChange = onSeekDragChange,
        onValueChangeFinished = onSeekDragFinished,
        valueRange = 0f..duration,
        colors =
            SliderDefaults.colors(
                thumbColor = animatedColor,
                activeTrackColor = animatedColor,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f),
            ),
        track = { sliderState ->
            Box(modifier = Modifier.fillMaxWidth().height(18.dp), contentAlignment = Alignment.Center) {
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(4.dp),
                    colors =
                        SliderDefaults.colors(
                            activeTrackColor = animatedColor,
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                        ),
                )
                if (chapters.isNotEmpty()) {
                    Canvas(modifier = Modifier.fillMaxWidth().height(4.dp)) {
                        chapters.forEach { chapter ->
                            val x = (chapter.startPosition.toFloat() / duration) * size.width
                            drawCircle(
                                color = Color.White.copy(alpha = 0.7f),
                                radius = 2.dp.toPx(),
                                center = Offset(x, size.height / 2),
                            )
                        }
                    }
                }
            }
        },
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
}

@Composable
private fun CastControlsRow(
    castState: CastSessionState,
    castManager: CastManager,
    animatedColor: Color,
) {
    val hasChapters = (castState.currentItem?.chapters?.size ?: 0) > 0

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasChapters) {
            IconButton(
                onClick = { castManager.seekToPreviousChapter() },
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_player_skip_back),
                    contentDescription = stringResource(R.string.cd_previous),
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }

            Spacer(modifier = Modifier.width(16.dp))
        }

        IconButton(
            onClick = { castManager.seekTo((castState.currentPosition - 10_000).coerceAtLeast(0)) },
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
                    .background(animatedColor)
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
                    color = Color.White,
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
                    tint = Color.White,
                    modifier = Modifier.size(36.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(32.dp))

        IconButton(
            onClick = {
                castManager.seekTo(
                    (castState.currentPosition + 30_000).coerceAtMost(castState.duration)
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

        if (hasChapters) {
            Spacer(modifier = Modifier.width(16.dp))

            IconButton(
                onClick = { castManager.seekToNextChapter() },
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_player_skip_forward),
                    contentDescription = stringResource(R.string.cd_next),
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Composable
private fun CastOptionsRow(
    animatedColor: Color,
    showEpisodeListButton: Boolean,
    onShowEpisodeList: () -> Unit,
    onShowSpeed: () -> Unit,
    onShowQuality: () -> Unit,
    onShowAudio: () -> Unit,
    onShowSubtitle: () -> Unit,
) {
    Row(
        modifier =
            Modifier.clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.1f))
                .padding(vertical = 4.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showEpisodeListButton) {
            IconButton(onClick = onShowEpisodeList) {
                Icon(
                    painter = painterResource(R.drawable.ic_episodes_list),
                    contentDescription = stringResource(R.string.cd_episode_list),
                    tint = animatedColor,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        IconButton(onClick = onShowSpeed) {
            Icon(
                painter = painterResource(R.drawable.ic_speed),
                contentDescription = stringResource(R.string.cd_playback_speed),
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(24.dp),
            )
        }
        IconButton(onClick = onShowQuality) {
            Icon(
                painter = painterResource(R.drawable.ic_settings),
                contentDescription = "Quality",
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(24.dp),
            )
        }
        IconButton(onClick = onShowAudio) {
            Icon(
                painter = painterResource(R.drawable.ic_audio),
                contentDescription = stringResource(R.string.cd_audio_track),
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(24.dp),
            )
        }
        IconButton(onClick = onShowSubtitle) {
            Icon(
                painter = painterResource(R.drawable.ic_subtitles),
                contentDescription = "Subtitles",
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(24.dp),
            )
        }
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
                items(bitrateOptions, key = { it.bitrate }) { option ->
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
                                                enableHevc = castState.enableHevc,
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
                items(audioStreams, key = { it.index }) { stream ->
                    val isSelected =
                        castState.audioStreamIndex?.let { it == stream.index } ?: stream.isDefault
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
                                                enableHevc = castState.enableHevc,
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
                    val anyStreamSelected =
                        castState.subtitleStreamIndex != null &&
                            subtitleStreams.any { it.index == castState.subtitleStreamIndex }
                    val isOff = !anyStreamSelected
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
                                                enableHevc = castState.enableHevc,
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

                items(subtitleStreams, key = { it.index }) { stream ->
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
                                                enableHevc = castState.enableHevc,
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
