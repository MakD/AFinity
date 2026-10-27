package com.makd.afinity.ui.music.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.data.models.music.RadioSeed
import com.makd.afinity.data.models.music.RepeatMode
import com.makd.afinity.data.models.player.MusicQuality
import com.makd.afinity.data.models.player.PlaybackStats
import com.makd.afinity.ui.components.isLandscapeWindow
import com.makd.afinity.ui.components.rememberCastChooserLauncher
import com.makd.afinity.ui.music.components.AddToPlaylistDialog
import com.makd.afinity.ui.music.components.AddToPlaylistResult
import com.makd.afinity.ui.music.components.AddToPlaylistViewModel
import com.makd.afinity.ui.music.components.RadioModeBottomSheet
import com.makd.afinity.ui.music.player.components.MusicPlayerControls
import com.makd.afinity.ui.player.components.AudioCoverArt
import com.makd.afinity.ui.player.components.AudioCoverStyle
import com.makd.afinity.ui.player.components.AudioPlayerControlSlot
import com.makd.afinity.ui.player.components.AudioPlayerLayout
import com.makd.afinity.ui.player.components.AudioPlayerScaffold
import com.makd.afinity.ui.player.components.AudioPlayerTitleBlock
import com.makd.afinity.ui.player.components.EqualizerBottomSheet
import com.makd.afinity.ui.player.components.PlaybackStatsOverlay
import com.makd.afinity.ui.player.components.PlayerMoreDivider
import com.makd.afinity.ui.player.components.PlayerMoreRow
import com.makd.afinity.ui.player.components.PlayerMoreSectionHeader
import com.makd.afinity.ui.player.components.PlayerMoreSheet
import com.makd.afinity.ui.player.components.SwipeCoverItem
import com.makd.afinity.ui.player.components.SwipeableCover
import com.makd.afinity.ui.player.components.musicQualityShortLabel
import com.makd.afinity.ui.player.components.rememberDominantColor
import java.util.UUID
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.MusicPlayerScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAlbum: (String) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    animatedVisibilityScope: AnimatedVisibilityScope,
    viewModel: MusicPlayerViewModel = hiltViewModel(),
    addToPlaylistViewModel: AddToPlaylistViewModel = hiltViewModel(),
) {
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val equalizerState by viewModel.equalizerState.collectAsStateWithLifecycle()
    val musicQuality by viewModel.musicQuality.collectAsStateWithLifecycle()
    val neverTranscode by viewModel.neverTranscode.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val showLyrics by viewModel.showLyrics.collectAsStateWithLifecycle()
    val lyricsLoading by viewModel.lyricsLoading.collectAsStateWithLifecycle()
    val isMusicCasting by viewModel.isMusicCasting.collectAsStateWithLifecycle()
    val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()
    val radioState by viewModel.radioState.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val showPlaybackStats by viewModel.showPlaybackStats.collectAsStateWithLifecycle()
    val playbackStats by viewModel.playbackStats.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var showQueue by rememberSaveable { mutableStateOf(false) }
    var showSleepTimer by rememberSaveable { mutableStateOf(false) }
    var showEqualizer by rememberSaveable { mutableStateOf(false) }
    var showQualitySheet by rememberSaveable { mutableStateOf(false) }
    var showAddToPlaylist by rememberSaveable { mutableStateOf(false) }
    var showMoreSheet by rememberSaveable { mutableStateOf(false) }
    var radioSeed by remember { mutableStateOf<RadioSeed?>(null) }
    val launchCastChooser = rememberCastChooserLauncher()

    var hadTrack by remember { mutableStateOf(false) }
    LaunchedEffect(playbackState.currentTrack) {
        if (playbackState.currentTrack != null) {
            hadTrack = true
        } else if (hadTrack) {
            onNavigateBack()
        }
    }

    val coverUrl = playbackState.currentTrack?.images?.primary?.toString()
    val coverBlurHash = playbackState.currentTrack?.images?.primaryImageBlurHash
    val defaultColor = MaterialTheme.colorScheme.surface
    val dominantColor = rememberDominantColor(coverUrl, defaultColor)
    val animatedColor by
        animateColorAsState(
            targetValue = dominantColor,
            animationSpec = tween(durationMillis = 800),
            label = "color",
        )

    val isLandscape = isLandscapeWindow()

    AudioPlayerScaffold(
        isLandscape = isLandscape,
        isCasting = isMusicCasting,
        accentColor = animatedColor,
        snackbarHostState = snackbarHostState,
        onMinimize = onNavigateBack,
        onCastClick = launchCastChooser,
        onMoreClick = { showMoreSheet = true },
        castRemote = {
            MusicCastRemote(
                viewModel = viewModel,
                coverUrl = coverUrl,
                accentColor = animatedColor,
                showLyrics = showLyrics,
                lyrics = lyrics,
                lyricsLoading = lyricsLoading,
                onNavigateBack = onNavigateBack,
                onNavigateToAlbum = onNavigateToAlbum,
                onNavigateToArtist = onNavigateToArtist,
                onOpenQueue = { showQueue = true },
                onOpenMore = { showMoreSheet = true },
            )
        },
        cover = { style ->
            AnimatedContent(
                targetState = showLyrics,
                transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) },
                label = "lyricsToggle",
                modifier = Modifier.fillMaxSize(),
            ) { lyricsVisible ->
                if (lyricsVisible) {
                    MusicLyricsView(
                        lyrics = lyrics,
                        positionMs = playbackState.positionMs,
                        isPlaying = playbackState.isPlaying,
                        isLoading = lyricsLoading,
                        onSeek = viewModel::seekTo,
                        accentColor = animatedColor,
                    )
                } else {
                    MusicSwipeableCover(
                        viewModel = viewModel,
                        coverUrl = coverUrl,
                        coverBlurHash = coverBlurHash,
                        currentTrackId = playbackState.currentTrack?.id,
                        repeatMode = playbackState.repeatMode,
                        isMusicCasting = isMusicCasting,
                        style = style,
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                }
            }
        },
        info = {
            val track = playbackState.currentTrack
            val album = track?.album
            AudioPlayerTitleBlock(
                title = track?.name ?: stringResource(R.string.music_player_nothing_playing),
                subtitle = track?.let { it.artist ?: it.artists.firstOrNull() ?: "" } ?: "",
                accentLine = album,
                accentColor = animatedColor,
                isLandscape = isLandscape,
                onSubtitleClick =
                    track?.artistId?.let { id -> { onNavigateToArtist(id.toString()) } },
                onAccentLineClick =
                    track
                        ?.albumId
                        ?.takeIf { album != null }
                        ?.let { id -> { onNavigateToAlbum(id.toString()) } },
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
                                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        )
                    }
                },
            )
        },
        controls = {
            MusicPlayerControls(
                positionMs = playbackState.positionMs,
                durationMs = playbackState.durationMs,
                bufferedPositionMs = playbackState.bufferedPositionMs,
                isPlaying = playbackState.isPlaying,
                isBuffering = playbackState.isBuffering,
                onPlayPauseClick = viewModel::togglePlayPause,
                onPrevious = viewModel::skipPrevious,
                onNext = viewModel::skipNext,
                onSeek = viewModel::seekTo,
                onSeekBackward = viewModel::seekBackward,
                onSeekForward = viewModel::seekForward,
                accentColor = animatedColor,
                audioCodec = playbackState.audioCodec?.let { PlaybackStats.friendlyCodecName(it) },
                isServerTranscode = playbackState.isServerTranscode,
            )
        },
        actions = {
            AudioPlayerControlSlot(
                painter = painterResource(R.drawable.ic_arrows_shuffle),
                contentDescription = stringResource(R.string.cd_music_shuffle),
                onClick = viewModel::toggleShuffle,
                active = playbackState.shuffled,
                activeColor = animatedColor,
            )
            AudioPlayerControlSlot(
                painter =
                    painterResource(
                        when (playbackState.repeatMode) {
                            RepeatMode.OFF -> R.drawable.ic_repeat_off
                            RepeatMode.ALL -> R.drawable.ic_repeat
                            RepeatMode.ONE -> R.drawable.ic_repeat_once
                        }
                    ),
                contentDescription = stringResource(R.string.cd_music_repeat),
                onClick = viewModel::cycleRepeatMode,
                active = playbackState.repeatMode != RepeatMode.OFF,
                activeColor = animatedColor,
            )
            AudioPlayerControlSlot(
                painter = painterResource(R.drawable.ic_article),
                contentDescription = stringResource(R.string.cd_music_lyrics),
                onClick = viewModel::toggleLyrics,
                active = showLyrics,
                activeColor = animatedColor,
            )
            AudioPlayerControlSlot(
                painter = painterResource(R.drawable.ic_playlist_alt),
                contentDescription = stringResource(R.string.cd_music_queue),
                onClick = { showQueue = true },
            )
        },
    ) {
        if (showQueue) {
            MusicQueueSheet(onDismiss = { showQueue = false }, viewModel = viewModel)
        }

        if (showMoreSheet) {
            val sleepTimerActive = playbackState.sleepTimerEndMs != null
            PlayerMoreSheet(onDismiss = { showMoreSheet = false }) {
                PlayerMoreSectionHeader(stringResource(R.string.music_more_section_audio))
                PlayerMoreRow(
                    painter = painterResource(R.drawable.ic_equalizer),
                    title = stringResource(R.string.cd_equalizer),
                    value =
                        stringResource(
                            if (equalizerState.isEnabled) R.string.state_toggle_on
                            else R.string.state_toggle_off
                        ),
                    onClick = {
                        showMoreSheet = false
                        showEqualizer = true
                    },
                )
                PlayerMoreRow(
                    painter = painterResource(R.drawable.ic_speed),
                    title = stringResource(R.string.music_player_quality_title),
                    value =
                        musicQualityShortLabel(
                            if (neverTranscode) MusicQuality.ORIGINAL else musicQuality
                        ),
                    onClick = {
                        showMoreSheet = false
                        showQualitySheet = true
                    },
                    enabled = !neverTranscode,
                )
                PlayerMoreRow(
                    painter =
                        painterResource(
                            if (sleepTimerActive) R.drawable.ic_moon_filled else R.drawable.ic_moon
                        ),
                    title = stringResource(R.string.cd_music_sleep_timer),
                    value =
                        if (sleepTimerActive) null else stringResource(R.string.state_toggle_off),
                    onClick = {
                        showMoreSheet = false
                        showSleepTimer = true
                    },
                )

                PlayerMoreDivider()
                PlayerMoreSectionHeader(stringResource(R.string.music_more_section_track))
                PlayerMoreRow(
                    painter = painterResource(R.drawable.ic_playlist_add),
                    title = stringResource(R.string.cd_music_add_to_playlist),
                    enabled = !isOffline && playbackState.currentTrack != null,
                    onClick = {
                        showMoreSheet = false
                        addToPlaylistViewModel.reset()
                        showAddToPlaylist = true
                    },
                )
                PlayerMoreRow(
                    painter = painterResource(R.drawable.ic_compass),
                    title = stringResource(R.string.cd_music_instant_mix),
                    enabled = !isOffline && playbackState.currentTrack != null,
                    onClick = {
                        showMoreSheet = false
                        playbackState.currentTrack?.id?.let(viewModel::playInstantMix)
                    },
                )
                PlayerMoreRow(
                    painter = painterResource(R.drawable.ic_radio),
                    title = stringResource(R.string.cd_start_radio),
                    enabled = !isOffline && playbackState.currentTrack != null,
                    onClick = {
                        showMoreSheet = false
                        playbackState.currentTrack?.let { track ->
                            radioSeed =
                                RadioSeed(
                                    trackId = track.id,
                                    albumId = track.albumId,
                                    sourceTracks = queue,
                                )
                        }
                    },
                )

                PlayerMoreDivider()
                PlayerMoreRow(
                    painter = painterResource(R.drawable.ic_info),
                    title = stringResource(R.string.cd_playback_info),
                    onClick = {
                        showMoreSheet = false
                        viewModel.togglePlaybackStats()
                    },
                )
            }
        }

        if (showPlaybackStats) {
            PlaybackStatsOverlay(
                stats = playbackStats,
                onClose = viewModel::togglePlaybackStats,
            )
        }

        if (showEqualizer) {
            EqualizerBottomSheet(
                state = equalizerState,
                onEnabled = viewModel::setEqEnabled,
                onPresetSelected = viewModel::applyEqPreset,
                onBandChanged = viewModel::setEqBandGain,
                onVolumeBoostChanged = viewModel::setVolumeBoost,
                onDismiss = { showEqualizer = false },
                presets = viewModel.equalizerPresets,
            )
        }

        if (showQualitySheet) {
            MusicQualitySheet(
                currentQuality = musicQuality,
                onSelect = viewModel::setMusicQuality,
                onDismiss = { showQualitySheet = false },
            )
        }

        if (showSleepTimer) {
            MusicSleepTimerSheet(
                activeTimerEndMs = playbackState.sleepTimerEndMs,
                onSetTimer = viewModel::setSleepTimer,
                onCancel = viewModel::cancelSleepTimer,
                onDismiss = { showSleepTimer = false },
            )
        }

        radioSeed?.let { seed ->
            RadioModeBottomSheet(
                seed = seed,
                onDismiss = { radioSeed = null },
                onSelectMode = { s, mode ->
                    viewModel.startRadio(s, mode)
                    radioSeed = null
                },
            )
        }

        if (showAddToPlaylist) {
            val currentTrackId = playbackState.currentTrack?.id
            if (currentTrackId != null) {
                val addedToPlaylistFmt =
                    stringResource(R.string.music_snackbar_added_to_playlist_fmt)
                val createdPlaylistFmt =
                    stringResource(R.string.music_snackbar_created_playlist_fmt)
                AddToPlaylistDialog(
                    trackIds = listOf(currentTrackId),
                    viewModel = addToPlaylistViewModel,
                    onDismiss = { showAddToPlaylist = false },
                    onResult = { result ->
                        showAddToPlaylist = false
                        val message =
                            when (result) {
                                is AddToPlaylistResult.Added ->
                                    addedToPlaylistFmt.format(result.playlistName)
                                is AddToPlaylistResult.Created ->
                                    createdPlaylistFmt.format(result.playlistName)
                                is AddToPlaylistResult.Error -> result.message
                            }
                        scope.launch { snackbarHostState.showSnackbar(message) }
                    },
                )
            }
        }
    }
}

private data class MusicCoverImage(val url: String?, val blurHash: String?)

private fun AfinityTrack.toCoverImage() =
    MusicCoverImage(url = images.primary?.toString(), blurHash = images.primaryImageBlurHash)

private fun neighbourIndex(queueSize: Int, index: Int, step: Int, repeatMode: RepeatMode): Int? {
    if (queueSize < 2) return null
    val target = index + step
    return when {
        target in 0 until queueSize -> target
        repeatMode == RepeatMode.ALL -> Math.floorMod(target, queueSize)
        else -> null
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.MusicSwipeableCover(
    viewModel: MusicPlayerViewModel,
    coverUrl: String?,
    coverBlurHash: String?,
    currentTrackId: UUID?,
    repeatMode: RepeatMode,
    isMusicCasting: Boolean,
    style: AudioCoverStyle,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val swipeEnabled by viewModel.swipeToSkipEnabled.collectAsStateWithLifecycle()
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val currentIndex by viewModel.currentIndex.collectAsStateWithLifecycle()

    val canSlide =
        !isMusicCasting &&
            currentTrackId != null &&
            queue.getOrNull(currentIndex)?.id == currentTrackId
    val previousIndex =
        if (canSlide) neighbourIndex(queue.size, currentIndex, -1, repeatMode) else null
    val nextIndex = if (canSlide) neighbourIndex(queue.size, currentIndex, 1, repeatMode) else null
    val coverModifier = AudioPlayerLayout.CoverSizeCap.aspectRatio(1f)

    SwipeableCover(
        current = SwipeCoverItem(currentIndex, MusicCoverImage(coverUrl, coverBlurHash)),
        enabled = swipeEnabled,
        hasPrevious = isMusicCasting || previousIndex != null,
        hasNext = isMusicCasting || nextIndex != null,
        onPrevious = viewModel::skipToPreviousTrack,
        onNext = viewModel::skipNext,
        horizontalOverflow = style.horizontalOverflow,
        modifier = Modifier.fillMaxSize(),
        previous = previousIndex?.let { SwipeCoverItem(it, queue[it].toCoverImage()) },
        next = nextIndex?.let { SwipeCoverItem(it, queue[it].toCoverImage()) },
    ) { image, isCurrent ->
        AudioCoverArt(
            imageUrl = image.url,
            blurHash = image.blurHash,
            placeholderIcon = painterResource(R.drawable.ic_music),
            style = style,
            modifier =
                if (isCurrent) {
                    coverModifier.sharedElement(
                        sharedContentState =
                            rememberSharedContentState(
                                key = "music-cover-${image.url ?: "default"}"
                            ),
                        animatedVisibilityScope = animatedVisibilityScope,
                    )
                } else {
                    coverModifier
                },
        )
    }
}
