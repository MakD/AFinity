package com.makd.afinity.ui.audiobookshelf.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.player.audiobookshelf.AudiobookshelfPlaybackState
import com.makd.afinity.ui.audiobookshelf.player.components.formatSpeed
import com.makd.afinity.ui.audiobookshelf.withAbsWidth
import com.makd.afinity.ui.player.cast.CastInfoBottomSheet
import com.makd.afinity.ui.player.cast.CastRemoteAction
import com.makd.afinity.ui.player.cast.CastRemoteArtwork
import com.makd.afinity.ui.player.cast.CastRemoteIconButton
import com.makd.afinity.ui.player.cast.CastRemoteLayout
import com.makd.afinity.ui.player.cast.CastRemoteTitle
import com.makd.afinity.ui.player.cast.CastRemoteTransport
import com.makd.afinity.ui.player.cast.CastRemoteValueAction
import com.makd.afinity.ui.player.cast.formatCastClock
import com.makd.afinity.ui.player.components.SwipeCoverItem
import com.makd.afinity.ui.player.components.SwipeableCover

@Composable
fun AbsCastRemote(
    viewModel: AudiobookshelfPlayerViewModel,
    playbackState: AudiobookshelfPlaybackState,
    accentColor: Color,
    hasBookmarks: Boolean,
    onNavigateBack: () -> Unit,
    onOpenMore: () -> Unit,
    onOpenChapters: () -> Unit,
    onOpenBookmarks: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenSpeed: () -> Unit,
) {
    val castState by viewModel.castManager.castState.collectAsStateWithLifecycle()
    val swipeEnabled by viewModel.swipeToSkipEnabled.collectAsStateWithLifecycle()
    var showDeviceSheet by remember { mutableStateOf(false) }

    val windowWidthPx = LocalWindowInfo.current.containerSize.width
    val coverUrl =
        remember(playbackState.coverUrl, windowWidthPx) {
            playbackState.coverUrl?.withAbsWidth(windowWidthPx)
        }

    val chapters = playbackState.chapters
    val chapterIndex = playbackState.currentChapterIndex
    val chapter = playbackState.currentChapter
    val rangeStart = chapter?.start ?: 0.0
    val rangeEnd = (chapter?.end ?: playbackState.duration).coerceAtLeast(rangeStart + 1.0)
    val title = playbackState.displayTitle.ifEmpty { stringResource(R.string.unknown_title) }
    val author = playbackState.displayAuthor ?: stringResource(R.string.unknown_author)

    CastRemoteLayout(
        artworkUrl = coverUrl,
        accentColor = accentColor,
        deviceName = castState.deviceName ?: stringResource(R.string.unknown_device),
        onMinimize = onNavigateBack,
        onDeviceClick = { showDeviceSheet = true },
        moreButton = {
            CastRemoteIconButton(
                painter = painterResource(R.drawable.ic_dots_vertical),
                contentDescription = stringResource(R.string.cd_music_more_options),
                onClick = onOpenMore,
            )
        },
        titleBlock = {
            CastRemoteTitle(
                title = title,
                accentColor = accentColor,
                overline = chapter?.title?.takeIf { it.isNotBlank() },
                meta =
                    listOfNotNull(author, timeLeftLabel(playbackState.remainingTime))
                        .joinToString(" · "),
            )
        },
        transport = {
            CastRemoteTransport(
                position = playbackState.currentTime.toFloat(),
                valueRange = rangeStart.toFloat()..rangeEnd.toFloat(),
                formatTime = { formatCastClock((it * 1000).toLong()) },
                accentColor = accentColor,
                isPlaying = playbackState.isPlaying,
                isBuffering = playbackState.isBuffering,
                onSeek = { viewModel.seekTo(it.toDouble()) },
                onPlayPause = viewModel::togglePlayPause,
                rewindPainter = painterResource(R.drawable.ic_rewind_backward_30),
                rewindContentDescription = stringResource(R.string.cd_rewind_30),
                onRewind = viewModel::skipBackward,
                forwardPainter = painterResource(R.drawable.ic_rewind_forward_30),
                forwardContentDescription = stringResource(R.string.cd_forward_30),
                onForward = viewModel::skipForward,
                onPrevious =
                    if (chapters.isNotEmpty()) {
                        { if (chapterIndex > 0) viewModel.seekToChapter(chapterIndex - 1) }
                    } else null,
                previousContentDescription = stringResource(R.string.cd_previous),
                onNext =
                    if (chapters.isNotEmpty()) {
                        {
                            if (chapterIndex < chapters.lastIndex) {
                                viewModel.seekToChapter(chapterIndex + 1)
                            }
                        }
                    } else null,
                nextContentDescription = stringResource(R.string.cd_next),
                centerLabel = playbackState.audioTracks.firstOrNull()?.codec?.uppercase(),
            )
        },
        actions = {
            CastRemoteAction(
                painter = painterResource(R.drawable.ic_playlist_alt),
                label = stringResource(R.string.cd_abs_chapters),
                onClick = onOpenChapters,
                accentColor = accentColor,
            )
            CastRemoteAction(
                painter =
                    painterResource(
                        if (hasBookmarks) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark
                    ),
                label = stringResource(R.string.cd_abs_bookmarks),
                onClick = onOpenBookmarks,
                accentColor = accentColor,
                active = hasBookmarks,
            )
            CastRemoteAction(
                painter =
                    painterResource(
                        if (playbackState.hasSleepTimer) R.drawable.ic_moon_filled
                        else R.drawable.ic_moon
                    ),
                label = stringResource(R.string.cd_music_sleep_timer),
                onClick = onOpenSleepTimer,
                accentColor = accentColor,
                active = playbackState.hasSleepTimer,
            )
            CastRemoteValueAction(
                value = "${formatSpeed(playbackState.playbackSpeed)}x",
                label = stringResource(R.string.cd_playback_speed),
                onClick = onOpenSpeed,
                accentColor = accentColor,
                active = playbackState.playbackSpeed != 1.0f,
            )
        },
        artworkContentDescription = title,
        artwork = { modifier ->
            SwipeableCover(
                current = SwipeCoverItem(chapterIndex, coverUrl),
                enabled = swipeEnabled,
                hasPrevious = chapters.isNotEmpty() && chapterIndex > 0,
                hasNext = chapters.isNotEmpty() && chapterIndex < chapters.lastIndex,
                onPrevious = { viewModel.seekToChapter(chapterIndex - 1) },
                onNext = { viewModel.seekToChapter(chapterIndex + 1) },
                horizontalOverflow = 0.dp,
                modifier = modifier,
            ) { url, _ ->
                CastRemoteArtwork(
                    artworkUrl = url,
                    contentDescription = title,
                    accentColor = accentColor,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        },
    )

    if (showDeviceSheet) {
        CastInfoBottomSheet(
            castState = castState,
            castManager = viewModel.castManager,
            onStopCasting = { viewModel.castManager.disconnect() },
            onDismiss = { showDeviceSheet = false },
            showBitrate = false,
        )
    }
}

@Composable
private fun timeLeftLabel(remainingSeconds: Double): String? {
    val totalMinutes = (remainingSeconds / 60.0).toInt()
    if (totalMinutes <= 0) return null
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        stringResource(R.string.meta_time_remaining_hours_minutes, hours, minutes)
    } else {
        stringResource(R.string.meta_time_remaining_minutes, minutes)
    }
}
