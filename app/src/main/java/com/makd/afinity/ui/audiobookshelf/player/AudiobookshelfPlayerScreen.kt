package com.makd.afinity.ui.audiobookshelf.player

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.R
import com.makd.afinity.ui.audiobookshelf.player.components.BookmarksSheet
import com.makd.afinity.ui.audiobookshelf.player.components.ChapterSelector
import com.makd.afinity.ui.audiobookshelf.player.components.PlaybackSpeedSelector
import com.makd.afinity.ui.audiobookshelf.player.components.PlayerControls
import com.makd.afinity.ui.audiobookshelf.player.components.SleepTimerDialog
import com.makd.afinity.ui.audiobookshelf.player.components.formatSpeed
import com.makd.afinity.ui.audiobookshelf.withAbsWidth
import com.makd.afinity.ui.components.isLandscapeWindow
import com.makd.afinity.ui.components.rememberCastChooserLauncher
import com.makd.afinity.ui.player.components.AudioCoverArt
import com.makd.afinity.ui.player.components.AudioCoverStyle
import com.makd.afinity.ui.player.components.AudioPlayerControlSlot
import com.makd.afinity.ui.player.components.AudioPlayerLayout
import com.makd.afinity.ui.player.components.AudioPlayerScaffold
import com.makd.afinity.ui.player.components.AudioPlayerTitleBlock
import com.makd.afinity.ui.player.components.AudioPlayerValueSlot
import com.makd.afinity.ui.player.components.EqualizerBottomSheet
import com.makd.afinity.ui.player.components.PlaybackStatsOverlay
import com.makd.afinity.ui.player.components.PlayerMoreRow
import com.makd.afinity.ui.player.components.PlayerMoreSectionHeader
import com.makd.afinity.ui.player.components.PlayerMoreSheet
import com.makd.afinity.ui.player.components.SwipeCoverItem
import com.makd.afinity.ui.player.components.SwipeableCover
import com.makd.afinity.ui.player.components.rememberDominantColor

private const val ABS_PLAY_METHOD_TRANSCODE = 2

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedTransitionScope.AudiobookshelfPlayerScreen(
    onNavigateBack: () -> Unit,
    animatedVisibilityScope: AnimatedVisibilityScope,
    viewModel: AudiobookshelfPlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val equalizerState by viewModel.equalizerState.collectAsStateWithLifecycle()
    val skipSilenceEnabled by viewModel.skipSilenceEnabled.collectAsStateWithLifecycle()
    val isAbsCasting by viewModel.isAbsCasting.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val launchCastChooser = rememberCastChooserLauncher()
    var showMoreSheet by rememberSaveable { mutableStateOf(false) }
    var showChapters by rememberSaveable { mutableStateOf(false) }
    var showSpeed by rememberSaveable { mutableStateOf(false) }
    var showSleepTimer by rememberSaveable { mutableStateOf(false) }
    var showEqualizer by rememberSaveable { mutableStateOf(false) }
    var showBookmarks by rememberSaveable { mutableStateOf(false) }
    val openBookmarks = {
        showBookmarks = true
        viewModel.refreshBookmarks()
    }

    val defaultColor = MaterialTheme.colorScheme.surface
    val dominantColor = rememberDominantColor(playbackState.coverUrl, defaultColor)
    val animatedColor by
        animateColorAsState(
            targetValue = dominantColor,
            animationSpec = tween(durationMillis = 800),
            label = "color",
        )

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    var hadSession by remember { mutableStateOf(false) }
    LaunchedEffect(playbackState.sessionId) {
        if (playbackState.sessionId != null) {
            hadSession = true
        } else if (hadSession) {
            onNavigateBack()
        }
    }

    val isLandscape = isLandscapeWindow()
    val hasBookmarks = bookmarks.isNotEmpty()
    val chapters = playbackState.chapters
    val chapterIndex = playbackState.currentChapterIndex

    AudioPlayerScaffold(
        isLandscape = isLandscape,
        isCasting = isAbsCasting,
        accentColor = animatedColor,
        snackbarHostState = snackbarHostState,
        onMinimize = onNavigateBack,
        onCastClick = launchCastChooser,
        onMoreClick = { showMoreSheet = true },
        castRemote = {
            AbsCastRemote(
                viewModel = viewModel,
                playbackState = playbackState,
                accentColor = animatedColor,
                hasBookmarks = hasBookmarks,
                onNavigateBack = onNavigateBack,
                onOpenMore = { showMoreSheet = true },
                onOpenChapters = { showChapters = true },
                onOpenBookmarks = openBookmarks,
                onOpenSleepTimer = { showSleepTimer = true },
                onOpenSpeed = { showSpeed = true },
            )
        },
        cover = { style ->
            AbsSwipeableCover(
                viewModel = viewModel,
                coverUrl = playbackState.coverUrl,
                chapterIndex = chapterIndex,
                chapterCount = chapters.size,
                style = style,
                animatedVisibilityScope = animatedVisibilityScope,
                modifier = Modifier.fillMaxSize(),
            )
        },
        info = {
            AudioPlayerTitleBlock(
                title =
                    playbackState.displayTitle.ifEmpty { stringResource(R.string.unknown_title) },
                subtitle = playbackState.displayAuthor ?: stringResource(R.string.unknown_author),
                accentLine = playbackState.currentChapter?.title,
                accentColor = animatedColor,
                isLandscape = isLandscape,
            )
        },
        controls = {
            PlayerControls(
                currentTime = playbackState.currentTime,
                duration = playbackState.duration,
                bufferedPosition = playbackState.bufferedPosition,
                isPlaying = playbackState.isPlaying,
                isBuffering = playbackState.isBuffering,
                audioCodec = playbackState.audioTracks.firstOrNull()?.codec?.uppercase(),
                isServerTranscode = playbackState.playMethod == ABS_PLAY_METHOD_TRANSCODE,
                onPlayPauseClick = viewModel::togglePlayPause,
                onSkipForward = viewModel::skipForward,
                onSkipBackward = viewModel::skipBackward,
                onSeek = viewModel::seekTo,
                onPreviousChapter =
                    if (chapters.isNotEmpty() && chapterIndex > 0) {
                        { viewModel.seekToChapter(chapterIndex - 1) }
                    } else null,
                onNextChapter =
                    if (chapters.isNotEmpty() && chapterIndex < chapters.lastIndex) {
                        { viewModel.seekToChapter(chapterIndex + 1) }
                    } else null,
                accentColor = animatedColor,
                currentChapter = playbackState.currentChapter,
            )
        },
        actions = {
            AudioPlayerValueSlot(
                value = "${formatSpeed(playbackState.playbackSpeed)}x",
                contentDescription = stringResource(R.string.abs_playback_speed_label),
                onClick = { showSpeed = true },
                active = playbackState.playbackSpeed != 1.0f,
                activeColor = animatedColor,
            )
            AudioPlayerControlSlot(
                painter = painterResource(id = R.drawable.ic_playlist_alt),
                contentDescription = stringResource(R.string.cd_abs_chapters),
                onClick = { showChapters = true },
            )
            AudioPlayerControlSlot(
                painter =
                    painterResource(
                        if (playbackState.hasSleepTimer) R.drawable.ic_moon_filled
                        else R.drawable.ic_moon
                    ),
                contentDescription = stringResource(R.string.cd_music_sleep_timer),
                onClick = { showSleepTimer = true },
                active = playbackState.hasSleepTimer,
                activeColor = animatedColor,
            )
            AudioPlayerControlSlot(
                painter =
                    painterResource(
                        id =
                            if (hasBookmarks) R.drawable.ic_bookmark_filled
                            else R.drawable.ic_bookmark
                    ),
                contentDescription = stringResource(R.string.cd_abs_bookmarks),
                onClick = openBookmarks,
                active = hasBookmarks,
                activeColor = animatedColor,
            )
        },
    ) {
        if (uiState.showPlaybackStats) {
            PlaybackStatsOverlay(
                stats = uiState.playbackStats,
                onClose = viewModel::togglePlaybackStats,
            )
        }

        if (showMoreSheet) {
            PlayerMoreSheet(onDismiss = { showMoreSheet = false }) {
                PlayerMoreSectionHeader(stringResource(R.string.abs_more_section_audio))
                PlayerMoreRow(
                    painter = painterResource(id = R.drawable.ic_equalizer),
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
                    painter = painterResource(id = R.drawable.ic_volume_up),
                    title = stringResource(R.string.abs_skip_silence_title),
                    value =
                        stringResource(
                            if (skipSilenceEnabled) R.string.state_toggle_on
                            else R.string.state_toggle_off
                        ),
                    onClick = { viewModel.setSkipSilence(!skipSilenceEnabled) },
                )
                PlayerMoreRow(
                    painter = painterResource(id = R.drawable.ic_info),
                    title = stringResource(R.string.cd_playback_info),
                    onClick = {
                        showMoreSheet = false
                        viewModel.togglePlaybackStats()
                    },
                )
            }
        }

        if (showBookmarks) {
            BookmarksSheet(
                bookmarks = bookmarks,
                isLoading = uiState.bookmarksLoading,
                currentTimeSeconds = playbackState.currentTime,
                onSeekToBookmark = { bookmark ->
                    viewModel.seekToBookmark(bookmark)
                    showBookmarks = false
                },
                onDeleteBookmark = viewModel::deleteBookmark,
                onCreateBookmark = viewModel::createBookmarkAtCurrentPosition,
                onDismiss = { showBookmarks = false },
            )
        }

        if (showChapters) {
            ChapterSelector(
                chapters = chapters,
                currentChapterIndex = chapterIndex,
                onChapterSelected = { index ->
                    viewModel.seekToChapter(index)
                    showChapters = false
                },
                onDismiss = { showChapters = false },
            )
        }

        if (showSpeed) {
            PlaybackSpeedSelector(
                currentSpeed = playbackState.playbackSpeed,
                onSpeedSelected = viewModel::setPlaybackSpeed,
                onDismiss = { showSpeed = false },
            )
        }

        if (showSleepTimer) {
            SleepTimerDialog(
                currentTimerEndTime = playbackState.sleepTimerEndTime,
                onTimerSelected = { minutes ->
                    viewModel.setSleepTimer(minutes)
                    showSleepTimer = false
                },
                onCancelTimer = viewModel::cancelSleepTimer,
                onDismiss = { showSleepTimer = false },
                chapterCount = chapters.size,
                currentChapterIndex = chapterIndex,
                isPodcast = playbackState.isPodcastPlaylist,
                chapterTargetRemainingSeconds = { extraChapters ->
                    val target =
                        if (chapters.isEmpty()) {
                            playbackState.duration.takeIf { it > 0.0 }
                        } else {
                            val index = chapterIndex.coerceAtLeast(0)
                            chapters.getOrNull(index + extraChapters)?.end
                        }
                    target?.minus(playbackState.currentTime)?.takeIf { it > 0.0 }
                },
                onChapterTimerSelected = { extraChapters ->
                    viewModel.setChapterSleepTimer(extraChapters)
                    showSleepTimer = false
                },
                activeChapterTarget = playbackState.sleepTimerChapterTarget,
                activeTargetRemainingSeconds = playbackState.sleepTimerRemainingSeconds,
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
                skipSilenceEnabled = skipSilenceEnabled,
                onSkipSilenceToggle = viewModel::setSkipSilence,
            )
        }
    }
}

@Composable
private fun SharedTransitionScope.AbsSwipeableCover(
    viewModel: AudiobookshelfPlayerViewModel,
    coverUrl: String?,
    chapterIndex: Int,
    chapterCount: Int,
    style: AudioCoverStyle,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier,
) {
    val swipeEnabled by viewModel.swipeToSkipEnabled.collectAsStateWithLifecycle()
    val coverWidthPx = with(LocalDensity.current) { AudioPlayerLayout.CoverMaxSize.roundToPx() }
    val item = remember { SwipeCoverItem(key = Unit, value = Unit) }

    SwipeableCover(
        current = item,
        enabled = swipeEnabled,
        hasPrevious = chapterCount > 0 && chapterIndex > 0,
        hasNext = chapterCount > 0 && chapterIndex < chapterCount - 1,
        onPrevious = { viewModel.seekToChapter(chapterIndex - 1) },
        onNext = { viewModel.seekToChapter(chapterIndex + 1) },
        horizontalOverflow = style.horizontalOverflow,
        modifier = modifier,
    ) { _, _ ->
        AudioCoverArt(
            imageUrl = coverUrl?.withAbsWidth(coverWidthPx),
            blurHash = null,
            placeholderIcon = painterResource(R.drawable.ic_book),
            style = style,
            modifier =
                AudioPlayerLayout.CoverSizeCap.aspectRatio(1f)
                    .sharedElement(
                        sharedContentState =
                            rememberSharedContentState(key = "cover-${coverUrl ?: "default"}"),
                        animatedVisibilityScope = animatedVisibilityScope,
                    ),
        )
    }
}
