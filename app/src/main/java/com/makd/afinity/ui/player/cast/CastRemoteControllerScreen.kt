package com.makd.afinity.ui.player.cast

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.makd.afinity.R
import com.makd.afinity.cast.CastManager
import com.makd.afinity.cast.CastSessionState
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.ui.player.PlayerViewModel
import com.makd.afinity.ui.player.components.PlaybackSpeedDialog
import com.makd.afinity.ui.player.components.rememberDominantColor
import org.jellyfin.sdk.model.api.MediaStreamType

@UnstableApi
@Composable
fun CastRemoteControllerScreen(
    castState: CastSessionState,
    castManager: CastManager,
    onMinimize: () -> Unit,
    onStopPlayback: () -> Unit,
    onStopCasting: () -> Unit,
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
) {
    val currentItem = castState.currentItem ?: return
    var showDeviceSheet by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showTracksSheet by remember { mutableStateOf(false) }
    var showEpisodeList by remember { mutableStateOf(false) }
    var showChapters by remember { mutableStateOf(false) }

    val playlistState by viewModel.playlistState.collectAsStateWithLifecycle()
    val artworkUrl = currentItem.images.primary?.toString()
    val dominantColor = rememberDominantColor(artworkUrl, MaterialTheme.colorScheme.primary)
    val accentColor by
        animateColorAsState(
            targetValue = dominantColor,
            animationSpec = tween(durationMillis = 800),
            label = "castAccent",
        )

    val chapters = currentItem.chapters
    val duration = castState.duration.toFloat().coerceAtLeast(1f)
    val position = castState.currentPosition
    val showEpisodes = currentItem is AfinityEpisode && playlistState.queue.size > 1

    CastRemoteLayout(
        artworkUrl = artworkUrl,
        accentColor = accentColor,
        deviceName = castState.deviceName ?: stringResource(R.string.unknown_device),
        onMinimize = onMinimize,
        onDeviceClick = { showDeviceSheet = true },
        moreButton = {
            Box {
                CastRemoteIconButton(
                    painter = painterResource(R.drawable.ic_dots_vertical),
                    contentDescription = stringResource(R.string.cd_music_more_options),
                    onClick = { showMoreMenu = true },
                )
                DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.cd_playback_speed)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_speed),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            showMoreMenu = false
                            showSpeedDialog = true
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.cast_stop_playback)) },
                        leadingIcon = {
                            Icon(
                                painter = painterResource(R.drawable.ic_player_stop_filled),
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            showMoreMenu = false
                            onStopPlayback()
                        },
                    )
                }
            }
        },
        titleBlock = {
            CastRemoteTitle(
                title = currentItem.name,
                accentColor = accentColor,
                overline = episodeOverline(currentItem),
                meta = streamSummary(castState, currentItem),
            )
        },
        transport = {
            CastRemoteTransport(
                position = position.toFloat(),
                valueRange = 0f..duration,
                formatTime = { formatCastClock(it.toLong()) },
                accentColor = accentColor,
                isPlaying = castState.isPlaying,
                isBuffering = castState.isBuffering,
                onSeek = { castManager.seekTo(it.toLong()) },
                onPlayPause = {
                    if (castState.isPlaying) castManager.pause() else castManager.play()
                },
                rewindPainter = painterResource(R.drawable.ic_rewind_backward_10),
                rewindContentDescription = stringResource(R.string.cd_rewind_10),
                onRewind = { castManager.seekTo((position - 10_000).coerceAtLeast(0)) },
                forwardPainter = painterResource(R.drawable.ic_rewind_forward_30),
                forwardContentDescription = stringResource(R.string.cd_forward_30),
                onForward = {
                    castManager.seekTo((position + 30_000).coerceAtMost(castState.duration))
                },
                onPrevious =
                    if (chapters.isNotEmpty()) castManager::seekToPreviousChapter else null,
                previousContentDescription = stringResource(R.string.cd_previous),
                onNext = if (chapters.isNotEmpty()) castManager::seekToNextChapter else null,
                nextContentDescription = stringResource(R.string.cd_next),
                markFractions =
                    chapters.map { it.startPosition / duration }.filter { it > 0f && it < 1f },
            )
        },
        actions = {
            if (showEpisodes) {
                CastRemoteAction(
                    painter = painterResource(R.drawable.ic_episodes_list),
                    label = stringResource(R.string.player_up_next),
                    onClick = { showEpisodeList = true },
                    accentColor = accentColor,
                )
            }
            if (chapters.isNotEmpty()) {
                CastRemoteAction(
                    painter = painterResource(R.drawable.ic_list_details),
                    label = stringResource(R.string.chapters_title),
                    onClick = { showChapters = true },
                    accentColor = accentColor,
                )
            }
            CastRemoteAction(
                painter = painterResource(R.drawable.ic_subtitles),
                label = stringResource(R.string.cast_audio_and_subs),
                onClick = { showTracksSheet = true },
                accentColor = accentColor,
            )
        },
        modifier = modifier,
        artworkContentDescription = currentItem.name,
    )

    if (showDeviceSheet) {
        CastInfoBottomSheet(
            castState = castState,
            castManager = castManager,
            onStopCasting = onStopCasting,
            onDismiss = { showDeviceSheet = false },
        )
    }

    if (showSpeedDialog) {
        PlaybackSpeedDialog(
            currentSpeed = castState.playbackSpeed,
            onSpeedChange = { newSpeed -> castManager.setPlaybackSpeed(newSpeed) },
            onDismiss = { showSpeedDialog = false },
        )
    }

    if (showTracksSheet) {
        CastTracksSheet(
            castState = castState,
            castManager = castManager,
            onDismiss = { showTracksSheet = false },
        )
    }

    if (showChapters && chapters.isNotEmpty()) {
        CastChaptersSheet(
            chapters = chapters,
            currentPositionMs = position,
            onChapterClick = { chapter -> castManager.seekTo(chapter.startPosition) },
            onDismiss = { showChapters = false },
        )
    }

    if (showEpisodeList && playlistState.queue.isNotEmpty()) {
        CastEpisodeListSheet(
            episodes = playlistState.queue,
            currentItemId = currentItem.id,
            isPlaying = castState.isPlaying,
            onEpisodeClick = { episode -> viewModel.jumpToEpisode(episode.id) },
            onDismiss = { showEpisodeList = false },
        )
    }
}

@Composable
private fun episodeOverline(item: AfinityItem): String? {
    if (item !is AfinityEpisode) return null
    val episodeNumber =
        if (item.indexNumberEnd != null && item.indexNumberEnd != item.indexNumber) {
            "${item.indexNumber}-E${item.indexNumberEnd}"
        } else {
            "${item.indexNumber}"
        }
    return stringResource(
        R.string.episode_season_episode_fmt,
        item.parentIndexNumber,
        episodeNumber,
        item.seriesName,
    )
}

@Composable
private fun streamSummary(castState: CastSessionState, item: AfinityItem): String {
    val method =
        stringResource(
            if (castState.playMethod == "Transcode") R.string.session_method_transcode
            else R.string.session_method_direct_play
        )
    val source =
        item.sources.firstOrNull { it.id == castState.mediaSourceId } ?: item.sources.firstOrNull()
    val streams = source?.mediaStreams.orEmpty()
    val audio =
        streams
            .filter { it.type == MediaStreamType.AUDIO }
            .let { audioStreams ->
                castState.audioStreamIndex?.let { index ->
                    audioStreams.firstOrNull { it.index == index }
                } ?: audioStreams.firstOrNull { it.isDefault } ?: audioStreams.firstOrNull()
            }
            ?.let { stream ->
                listOfNotNull(
                        stream.language.uppercase().takeIf { it.isNotBlank() },
                        stream.channelLayout?.takeIf { it.isNotBlank() },
                    )
                    .joinToString(" ")
                    .takeIf { it.isNotBlank() }
            }
    val subtitle =
        castState.subtitleStreamIndex
            ?.let { index ->
                streams.firstOrNull { it.type == MediaStreamType.SUBTITLE && it.index == index }
            }
            ?.let { stream ->
                val language = stream.language.uppercase().takeIf { it.isNotBlank() }
                listOfNotNull(stringResource(R.string.player_subtitle_title), language)
                    .joinToString(" ")
            }
    return listOfNotNull(method, audio, subtitle).joinToString(" · ")
}
