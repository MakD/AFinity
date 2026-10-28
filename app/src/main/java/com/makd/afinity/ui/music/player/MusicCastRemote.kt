package com.makd.afinity.ui.music.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.data.models.music.AfinityLyricLine
import com.makd.afinity.data.models.music.RepeatMode
import com.makd.afinity.data.models.player.PlaybackStats
import com.makd.afinity.ui.player.cast.CastInfoBottomSheet
import com.makd.afinity.ui.player.cast.CastRemoteAction
import com.makd.afinity.ui.player.cast.CastRemoteArtwork
import com.makd.afinity.ui.player.cast.CastRemoteIconButton
import com.makd.afinity.ui.player.cast.CastRemoteLayout
import com.makd.afinity.ui.player.cast.CastRemoteTitle
import com.makd.afinity.ui.player.cast.CastRemoteTransport
import com.makd.afinity.ui.player.cast.formatCastClock
import com.makd.afinity.ui.player.components.SwipeCoverItem
import com.makd.afinity.ui.player.components.SwipeableCover

@Composable
fun MusicCastRemote(
    viewModel: MusicPlayerViewModel,
    coverUrl: String?,
    accentColor: Color,
    showLyrics: Boolean,
    lyrics: List<AfinityLyricLine>,
    lyricsLoading: Boolean,
    onNavigateBack: () -> Unit,
    onNavigateToAlbum: (String) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onOpenQueue: () -> Unit,
    onOpenMore: () -> Unit,
) {
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val castState by viewModel.castManager.castState.collectAsStateWithLifecycle()
    val swipeEnabled by viewModel.swipeToSkipEnabled.collectAsStateWithLifecycle()
    var showDeviceSheet by remember { mutableStateOf(false) }

    val track = playbackState.currentTrack
    val artist = track?.let { it.artist ?: it.artists.firstOrNull() }?.takeIf { it.isNotBlank() }
    val album = track?.album?.takeIf { it.isNotBlank() }
    val durationMs = playbackState.durationMs.toFloat().coerceAtLeast(1f)

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
                title = track?.name ?: stringResource(R.string.music_player_nothing_playing),
                accentColor = accentColor,
                overline = album,
                meta = artist,
                onOverlineClick =
                    track
                        ?.albumId
                        ?.takeIf { album != null }
                        ?.let { id -> { onNavigateToAlbum(id.toString()) } },
                onMetaClick = track?.artistId?.let { id -> { onNavigateToArtist(id.toString()) } },
                trailing = {
                    IconButton(onClick = viewModel::toggleCurrentTrackFavorite) {
                        Icon(
                            painter =
                                painterResource(
                                    if (track?.favorite == true) R.drawable.ic_favorite_filled
                                    else R.drawable.ic_favorite
                                ),
                            contentDescription = stringResource(R.string.cd_favorite),
                            tint =
                                if (track?.favorite == true) Color.Red
                                else Color.White.copy(alpha = 0.85f),
                        )
                    }
                },
            )
        },
        transport = {
            CastRemoteTransport(
                position = playbackState.positionMs.toFloat(),
                valueRange = 0f..durationMs,
                formatTime = { formatCastClock(it.toLong()) },
                accentColor = accentColor,
                isPlaying = playbackState.isPlaying,
                isBuffering = playbackState.isBuffering,
                onSeek = { viewModel.seekTo(it.toLong()) },
                onPlayPause = viewModel::togglePlayPause,
                rewindPainter = painterResource(R.drawable.ic_rewind_backward_30),
                rewindContentDescription = stringResource(R.string.cd_music_seek_backward),
                onRewind = { viewModel.seekBackward() },
                forwardPainter = painterResource(R.drawable.ic_rewind_forward_30),
                forwardContentDescription = stringResource(R.string.cd_music_seek_forward),
                onForward = { viewModel.seekForward() },
                onPrevious = viewModel::skipPrevious,
                previousContentDescription = stringResource(R.string.cd_previous),
                onNext = viewModel::skipNext,
                nextContentDescription = stringResource(R.string.cd_next),
                centerLabel = playbackState.audioCodec?.let { PlaybackStats.friendlyCodecName(it) },
            )
        },
        actions = {
            CastRemoteAction(
                painter = painterResource(R.drawable.ic_playlist_alt),
                label = stringResource(R.string.cd_music_queue),
                onClick = onOpenQueue,
                accentColor = accentColor,
            )
            CastRemoteAction(
                painter = painterResource(R.drawable.ic_article),
                label = stringResource(R.string.cd_music_lyrics),
                onClick = viewModel::toggleLyrics,
                accentColor = accentColor,
                active = showLyrics,
            )
            CastRemoteAction(
                painter = painterResource(R.drawable.ic_arrows_shuffle),
                label = stringResource(R.string.cd_music_shuffle),
                onClick = viewModel::toggleShuffle,
                accentColor = accentColor,
                active = playbackState.shuffled,
            )
            CastRemoteAction(
                painter =
                    painterResource(
                        when (playbackState.repeatMode) {
                            RepeatMode.OFF -> R.drawable.ic_repeat_off
                            RepeatMode.ALL -> R.drawable.ic_repeat
                            RepeatMode.ONE -> R.drawable.ic_repeat_once
                        }
                    ),
                label = stringResource(R.string.cd_music_repeat),
                onClick = viewModel::cycleRepeatMode,
                accentColor = accentColor,
                active = playbackState.repeatMode != RepeatMode.OFF,
            )
        },
        artworkContentDescription = track?.name,
        artwork = { modifier ->
            SwipeableCover(
                current = SwipeCoverItem(track?.id ?: Unit, coverUrl),
                enabled = swipeEnabled,
                hasPrevious = true,
                hasNext = true,
                onPrevious = viewModel::skipToPreviousTrack,
                onNext = viewModel::skipNext,
                horizontalOverflow = 0.dp,
                modifier = modifier,
            ) { url, _ ->
                CastRemoteArtwork(
                    artworkUrl = url,
                    contentDescription = track?.name,
                    accentColor = accentColor,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        },
        artworkOverlay = {
            if (showLyrics) {
                Box(
                    modifier =
                        Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.62f))
                )
                MusicLyricsView(
                    lyrics = lyrics,
                    positionMs = playbackState.positionMs,
                    isPlaying = playbackState.isPlaying,
                    isLoading = lyricsLoading,
                    onSeek = viewModel::seekTo,
                    accentColor = accentColor,
                    modifier =
                        Modifier.matchParentSize()
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(WindowInsetsSides.Top)
                            )
                            .padding(start = 16.dp, end = 16.dp, top = 56.dp, bottom = 16.dp),
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
