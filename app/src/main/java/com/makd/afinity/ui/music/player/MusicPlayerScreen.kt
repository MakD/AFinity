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
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.data.models.music.RadioSeed
import com.makd.afinity.data.models.music.RepeatMode
import com.makd.afinity.ui.audiobookshelf.player.components.EqualizerBottomSheet
import com.makd.afinity.ui.audiobookshelf.player.util.rememberDominantColor
import com.makd.afinity.ui.components.AFinitySnackbar
import com.makd.afinity.ui.components.isLandscapeWindow
import com.makd.afinity.ui.components.rememberCastChooserLauncher
import com.makd.afinity.ui.music.components.AddToPlaylistDialog
import com.makd.afinity.ui.music.components.AddToPlaylistResult
import com.makd.afinity.ui.music.components.AddToPlaylistViewModel
import com.makd.afinity.ui.music.components.RadioModeBottomSheet
import com.makd.afinity.ui.music.player.components.MusicPlayerControls
import com.makd.afinity.ui.player.components.AudioPlayerControlRow
import com.makd.afinity.ui.player.components.AudioPlayerControlSlot
import com.makd.afinity.ui.player.components.AudioPlayerLayout
import com.makd.afinity.ui.player.components.PlaybackStatsOverlay
import com.makd.afinity.ui.player.components.PlayerMoreDivider
import com.makd.afinity.ui.player.components.PlayerMoreRow
import com.makd.afinity.ui.player.components.PlayerMoreSectionHeader
import com.makd.afinity.ui.player.components.PlayerMoreSheet
import com.makd.afinity.ui.player.components.musicQualityShortLabel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.MusicPlayerScreen(
    onNavigateBack: () -> Unit,
    animatedVisibilityScope: AnimatedVisibilityScope,
    viewModel: MusicPlayerViewModel = hiltViewModel(),
    addToPlaylistViewModel: AddToPlaylistViewModel = hiltViewModel(),
) {
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val equalizerState by viewModel.equalizerState.collectAsStateWithLifecycle()
    val musicQuality by viewModel.musicQuality.collectAsStateWithLifecycle()
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
    var showQueue by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }
    var showQualitySheet by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }
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

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState, snackbar = { AFinitySnackbar(it) })
        },
        containerColor = Color.Transparent,
    ) { paddingValues ->
        Box(
            modifier =
                Modifier.fillMaxSize()
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
            if (isLandscape) {
                MusicPlayerLandscape(
                    viewModel = viewModel,
                    coverUrl = coverUrl,
                    coverBlurHash = coverBlurHash,
                    animatedColor = animatedColor,
                    showLyrics = showLyrics,
                    lyrics = lyrics,
                    lyricsLoading = lyricsLoading,
                    onNavigateBack = onNavigateBack,
                    onOpenQueue = { showQueue = true },
                    onOpenMore = { showMoreSheet = true },
                    onCastClick = launchCastChooser,
                    isMusicCasting = isMusicCasting,
                    paddingValues = paddingValues,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            } else {
                MusicPlayerPortrait(
                    viewModel = viewModel,
                    coverUrl = coverUrl,
                    coverBlurHash = coverBlurHash,
                    animatedColor = animatedColor,
                    showLyrics = showLyrics,
                    lyrics = lyrics,
                    lyricsLoading = lyricsLoading,
                    onNavigateBack = onNavigateBack,
                    onOpenQueue = { showQueue = true },
                    onOpenMore = { showMoreSheet = true },
                    onCastClick = launchCastChooser,
                    isMusicCasting = isMusicCasting,
                    paddingValues = paddingValues,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }
        }

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
                            if (equalizerState.isEnabled) R.string.state_on else R.string.state_off
                        ),
                    onClick = {
                        showMoreSheet = false
                        showEqualizer = true
                    },
                )
                PlayerMoreRow(
                    painter = painterResource(R.drawable.ic_speed),
                    title = stringResource(R.string.music_player_quality_title),
                    value = musicQualityShortLabel(musicQuality),
                    onClick = {
                        showMoreSheet = false
                        showQualitySheet = true
                    },
                )
                PlayerMoreRow(
                    painter =
                        painterResource(
                            if (sleepTimerActive) R.drawable.ic_moon_filled else R.drawable.ic_moon
                        ),
                    title = stringResource(R.string.cd_music_sleep_timer),
                    value = if (sleepTimerActive) null else stringResource(R.string.state_off),
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
                AddToPlaylistDialog(
                    trackIds = listOf(currentTrackId),
                    viewModel = addToPlaylistViewModel,
                    onDismiss = { showAddToPlaylist = false },
                    onResult = { result ->
                        showAddToPlaylist = false
                        val message =
                            when (result) {
                                is AddToPlaylistResult.Added ->
                                    "Added to \"${result.playlistName}\""
                                is AddToPlaylistResult.Created ->
                                    "Created \"${result.playlistName}\""
                                is AddToPlaylistResult.Error -> result.message
                                else -> null
                            }
                        message?.let { scope.launch { snackbarHostState.showSnackbar(it) } }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.MusicPlayerPortrait(
    viewModel: MusicPlayerViewModel,
    coverUrl: String?,
    coverBlurHash: String? = null,
    animatedColor: Color,
    showLyrics: Boolean,
    lyrics: List<com.makd.afinity.data.models.music.AfinityLyricLine>,
    lyricsLoading: Boolean,
    onNavigateBack: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenMore: () -> Unit = {},
    onCastClick: () -> Unit = {},
    isMusicCasting: Boolean = false,
    paddingValues: PaddingValues,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.align(Alignment.CenterStart),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_keyboard_arrow_down),
                    contentDescription = stringResource(R.string.cd_music_minimize),
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
            Text(
                stringResource(R.string.music_player_now_playing),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
                letterSpacing = 2.sp,
                modifier = Modifier.align(Alignment.Center),
            )
            Row(
                modifier = Modifier.align(Alignment.CenterEnd),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onCastClick) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            painter = painterResource(R.drawable.ic_cast_devices),
                            contentDescription = stringResource(R.string.cd_cast),
                            tint = if (isMusicCasting) animatedColor else Color.White,
                        )
                        Box(
                            modifier =
                                Modifier.size(4.dp)
                                    .align(Alignment.BottomCenter)
                                    .alpha(if (isMusicCasting) 1f else 0f)
                                    .background(animatedColor, CircleShape)
                        )
                    }
                }
                IconButton(onClick = onOpenMore) {
                    Icon(
                        painter = painterResource(R.drawable.ic_dots_vertical),
                        contentDescription = stringResource(R.string.cd_music_more_options),
                        tint = Color.White,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
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
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Surface(
                            modifier =
                                AudioPlayerLayout.CoverSizeCap.aspectRatio(1f)
                                    .sharedElement(
                                        sharedContentState =
                                            rememberSharedContentState(
                                                key = "music-cover-${coverUrl ?: "default"}"
                                            ),
                                        animatedVisibilityScope = animatedVisibilityScope,
                                    )
                                    .shadow(
                                        elevation = 24.dp,
                                        shape = RoundedCornerShape(32.dp),
                                        spotColor = Color.Black,
                                    ),
                            shape = RoundedCornerShape(32.dp),
                            color = Color.Transparent,
                        ) {
                            if (coverUrl != null || coverBlurHash != null) {
                                val coverSizeDp = AudioPlayerLayout.CoverMaxSize
                                com.makd.afinity.ui.components.AsyncImage(
                                    imageUrl = coverUrl,
                                    contentDescription = null,
                                    blurHash = coverBlurHash,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                    targetWidth = coverSizeDp,
                                    targetHeight = coverSizeDp,
                                )
                            } else {
                                Box(
                                    modifier =
                                        Modifier.fillMaxSize()
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_music),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(64.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text =
                        playbackState.currentTrack?.name
                            ?: stringResource(R.string.music_player_nothing_playing),
                    style =
                        MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold
                        ),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text =
                        playbackState.currentTrack?.let {
                            it.artist ?: it.artists.firstOrNull() ?: ""
                        } ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val album = playbackState.currentTrack?.album
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = album ?: " ",
                    style = MaterialTheme.typography.labelLarge,
                    color = animatedColor,
                    maxLines = 1,
                    modifier =
                        Modifier.alpha(if (album != null) 1f else 0f)
                            .basicMarquee(iterations = Int.MAX_VALUE, velocity = 30.dp),
                )
            }
            IconButton(onClick = viewModel::toggleCurrentTrackFavorite) {
                Icon(
                    painter =
                        painterResource(
                            if (playbackState.currentTrack?.favorite == true)
                                R.drawable.ic_favorite_filled
                            else R.drawable.ic_favorite
                        ),
                    contentDescription = stringResource(R.string.cd_favorite),
                    tint =
                        if (playbackState.currentTrack?.favorite == true) Color.Red
                        else Color.White.copy(alpha = 0.8f),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

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
        )

        Spacer(modifier = Modifier.height(28.dp))

        AudioPlayerControlRow(modifier = Modifier.padding(bottom = 16.dp)) {
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
                onClick = onOpenQueue,
            )
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SharedTransitionScope.MusicPlayerLandscape(
    viewModel: MusicPlayerViewModel,
    coverUrl: String?,
    coverBlurHash: String? = null,
    animatedColor: Color,
    showLyrics: Boolean,
    lyrics: List<com.makd.afinity.data.models.music.AfinityLyricLine>,
    lyricsLoading: Boolean,
    onNavigateBack: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenMore: () -> Unit = {},
    onCastClick: () -> Unit = {},
    isMusicCasting: Boolean = false,
    paddingValues: PaddingValues,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()

    Row(
        modifier =
            Modifier.fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 32.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.weight(0.45f).fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = showLyrics,
                transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) },
                label = "lyricsToggleLandscape",
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
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Surface(
                            modifier =
                                AudioPlayerLayout.CoverSizeCap.aspectRatio(1f)
                                    .sharedElement(
                                        sharedContentState =
                                            rememberSharedContentState(
                                                key = "music-cover-${coverUrl ?: "default"}"
                                            ),
                                        animatedVisibilityScope = animatedVisibilityScope,
                                    )
                                    .shadow(
                                        elevation = 16.dp,
                                        shape = RoundedCornerShape(24.dp),
                                        spotColor = Color.Black,
                                    ),
                            shape = RoundedCornerShape(24.dp),
                            color = Color.Transparent,
                        ) {
                            if (coverUrl != null || coverBlurHash != null) {
                                val coverSizeDp = AudioPlayerLayout.CoverMaxSize
                                com.makd.afinity.ui.components.AsyncImage(
                                    imageUrl = coverUrl,
                                    contentDescription = null,
                                    blurHash = coverBlurHash,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                    targetWidth = coverSizeDp,
                                    targetHeight = coverSizeDp,
                                )
                            } else {
                                Box(
                                    modifier =
                                        Modifier.fillMaxSize()
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_music),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(48.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier.weight(0.55f).fillMaxHeight().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_keyboard_arrow_down),
                        contentDescription = stringResource(R.string.cd_music_minimize),
                        tint = Color.White,
                    )
                }
                Text(
                    stringResource(R.string.music_player_now_playing),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    letterSpacing = 2.sp,
                    modifier = Modifier.align(Alignment.Center),
                )
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onCastClick) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_cast_devices),
                                contentDescription = stringResource(R.string.cd_cast),
                                tint = if (isMusicCasting) animatedColor else Color.White,
                            )
                            Box(
                                modifier =
                                    Modifier.size(4.dp)
                                        .align(Alignment.BottomCenter)
                                        .alpha(if (isMusicCasting) 1f else 0f)
                                        .background(animatedColor, CircleShape)
                            )
                        }
                    }
                    IconButton(onClick = onOpenMore) {
                        Icon(
                            painter = painterResource(R.drawable.ic_dots_vertical),
                            contentDescription = stringResource(R.string.cd_music_more_options),
                            tint = Color.White,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text =
                            playbackState.currentTrack?.name
                                ?: stringResource(R.string.music_player_nothing_playing),
                        style =
                            MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text =
                            playbackState.currentTrack?.let {
                                it.artist ?: it.artists.firstOrNull() ?: ""
                            } ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val albumLandscape = playbackState.currentTrack?.album
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = albumLandscape ?: " ",
                        style = MaterialTheme.typography.labelLarge,
                        color = animatedColor,
                        maxLines = 1,
                        modifier =
                            Modifier.alpha(if (albumLandscape != null) 1f else 0f)
                                .basicMarquee(iterations = Int.MAX_VALUE, velocity = 30.dp),
                    )
                }
                IconButton(onClick = viewModel::toggleCurrentTrackFavorite) {
                    Icon(
                        painter =
                            painterResource(
                                if (playbackState.currentTrack?.favorite == true)
                                    R.drawable.ic_favorite_filled
                                else R.drawable.ic_favorite
                            ),
                        contentDescription = stringResource(R.string.cd_favorite),
                        tint =
                            if (playbackState.currentTrack?.favorite == true) Color.Red
                            else Color.White.copy(alpha = 0.8f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

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
            )

            Spacer(modifier = Modifier.height(16.dp))

            AudioPlayerControlRow {
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
                    onClick = onOpenQueue,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
