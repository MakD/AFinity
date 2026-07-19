package com.makd.afinity.ui.player

import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.navigation.NavController
import com.makd.afinity.data.models.livetv.LiveTvPlaybackInfo
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.player.PlayerEvent
import com.makd.afinity.data.models.player.SubtitlePreferences
import com.makd.afinity.player.mpv.MPVPlayer
import com.makd.afinity.ui.player.cast.CastRemoteControllerScreen
import com.makd.afinity.ui.player.components.BufferingIndicator
import com.makd.afinity.ui.player.components.ErrorIndicator
import com.makd.afinity.ui.player.components.GestureHandler
import com.makd.afinity.ui.player.components.MpvSurface
import com.makd.afinity.ui.player.components.PlaybackStatsOverlay
import com.makd.afinity.ui.player.components.PlayerControls
import com.makd.afinity.ui.player.components.PlayerIndicators
import com.makd.afinity.ui.player.components.SyncPlayGroupSheet
import com.makd.afinity.ui.player.components.SyncPlayWaitingOverlay
import com.makd.afinity.ui.player.components.TrickplayPreview
import com.makd.afinity.ui.player.components.VersionPickerSheet
import com.makd.afinity.ui.player.utils.KeepScreenOn
import com.makd.afinity.ui.player.utils.PlayerSystemBarsController
import com.makd.afinity.ui.player.utils.ScreenBrightnessController
import io.github.peerless2012.ass.media.kt.withAssSupport
import kotlinx.coroutines.flow.map
import org.jellyfin.sdk.model.api.GroupStateType
import timber.log.Timber
import java.util.UUID

@UnstableApi
@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
    item: AfinityItem,
    mediaSourceId: String,
    audioStreamIndex: Int? = null,
    subtitleStreamIndex: Int? = null,
    startPositionMs: Long = 0L,
    seasonId: UUID? = null,
    shuffle: Boolean = false,
    isLiveChannel: Boolean = false,
    liveStreamUrl: String? = null,
    livePlaybackInfo: LiveTvPlaybackInfo? = null,
    onBackPressed: () -> Unit,
    navController: NavController? = null,
    viewModel: PlayerViewModel = hiltViewModel(),
    syncPlayViewModel: SyncPlayViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val syncPlayState by syncPlayViewModel.syncPlayState.collectAsStateWithLifecycle()
    val syncPlayUiState by syncPlayViewModel.uiState.collectAsStateWithLifecycle()
    val syncPlayMemberInfo by syncPlayViewModel.memberInfoMap.collectAsStateWithLifecycle()
    val playlistState by
        viewModel.playlistState.collectAsStateWithLifecycle(initialValue = PlaylistState())

    val context = androidx.compose.ui.platform.LocalContext.current
    val preferencesRepository = remember {
        dagger.hilt.android.EntryPointAccessors.fromApplication(
                context.applicationContext,
                com.makd.afinity.di.PreferencesEntryPoint::class.java,
            )
            .preferencesRepository()
    }
    val subtitlePrefs by
        preferencesRepository
            .getSubtitlePreferencesFlow()
            .collectAsStateWithLifecycle(initialValue = SubtitlePreferences.DEFAULT)
    var seekOriginTime by remember { mutableLongStateOf(0L) }
    var dragStartVolume by remember { mutableIntStateOf(-1) }
    var dragStartBrightness by remember { mutableFloatStateOf(-1f) }
    var showVersionPicker by remember { mutableStateOf(false) }
    LocalLifecycleOwner.current

    LaunchedEffect(Unit) { viewModel.closePlayerEvent.collect { onBackPressed() } }

    LaunchedEffect(item.id, mediaSourceId, isLiveChannel, liveStreamUrl, uiState.isPlayerReady) {
        if (!uiState.isPlayerReady) return@LaunchedEffect

        if (isLiveChannel && liveStreamUrl != null && livePlaybackInfo != null) {
            Timber.d("Loading live channel: ${item.name}")
            viewModel.handlePlayerEvent(
                PlayerEvent.LoadLiveChannel(
                    channelId = item.id,
                    channelName = item.name,
                    streamUrl = liveStreamUrl,
                    playbackInfo = livePlaybackInfo,
                )
            )
        } else {
            Timber.d("Initializing playlist and playing media: ${item.name}")
            viewModel.initializePlaylistAndPlay(
                item = item,
                mediaSourceId = mediaSourceId,
                audioStreamIndex = audioStreamIndex,
                subtitleStreamIndex = subtitleStreamIndex,
                startPositionMs = startPositionMs,
                seasonId = seasonId,
                shuffle = shuffle,
            )
        }
    }
    LaunchedEffect(Unit) {
        viewModel.syncPlayInterceptor = SyncPlayInterceptor { event ->
            syncPlayViewModel.handleLocalPlayerEvent(event)
        }
        syncPlayViewModel.setPlayerActions(
            object : SyncPlayPlayerActions {
                override fun executePlay() = viewModel.executeScheduledPlay()

                override fun executePause() = viewModel.executeScheduledPause()

                override fun executeSeek(positionMs: Long) =
                    viewModel.executeScheduledSeek(positionMs)

                override val currentPositionMs: Long
                    get() = viewModel.player.currentPosition

                override val currentIsPlaying: Boolean
                    get() = viewModel.player.isPlaying

                override val currentItemId: UUID?
                    get() = viewModel.currentPlayingItemId
            }
        )
        syncPlayViewModel.setBufferingFlow(viewModel.uiState.map { it.isBuffering })
    }

    LaunchedEffect(Unit) {
        syncPlayViewModel.effects.collect { effect ->
            when (effect) {
                is SyncPlayEffect.LoadContent -> viewModel.handlePlayerEvent(
                    PlayerEvent.LoadMedia(
                        item = effect.item,
                        mediaSourceId = effect.mediaSourceId,
                        startPositionMs = effect.startPositionMs,
                    )
                )
                is SyncPlayEffect.GroupJoined -> syncPlayViewModel.dismissGroupSheet()
                else -> {}
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> syncPlayViewModel.onAppBackground()
                Lifecycle.Event.ON_RESUME -> syncPlayViewModel.onAppForeground()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var hasNavigatedBack by remember { mutableStateOf(false) }

    BackHandler {
        if (!hasNavigatedBack) {
            hasNavigatedBack = true
            if (syncPlayState.isInGroup) syncPlayViewModel.leaveGroup()
            viewModel.stopPlayback()
            onBackPressed()
        }
    }
    val castState by viewModel.castManager.castState.collectAsStateWithLifecycle()
    val isDarkTheme = isSystemInDarkTheme()

    val mediaRouteButton = remember {
        androidx.mediarouter.app.MediaRouteButton(context).also { button ->
            com.google.android.gms.cast.framework.CastButtonFactory.setUpMediaRouteButton(
                context,
                button,
            )
            button.visibility = android.view.View.GONE
        }
    }
    AndroidView(factory = { mediaRouteButton })
    LaunchedEffect(uiState.showCastChooser) {
        if (uiState.showCastChooser) {
            mediaRouteButton.performClick()
            viewModel.dismissCastChooser()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (!uiState.isPlayerReady) {
            BufferingIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (castState.isConnected && castState.currentItem != null) {
            CastRemoteControllerScreen(
                castState = castState,
                castManager = viewModel.castManager,
                onBackClick = {
                    if (!hasNavigatedBack) {
                        hasNavigatedBack = true
                        viewModel.castManager.stop()
                        onBackPressed()
                    }
                },
                onStopCasting = { viewModel.stopCasting() },
                viewModel = viewModel,
            )
        } else {
            GestureHandler(
                isSeekEnabled = !uiState.isPlayingIntro,
                onSingleTap = { viewModel.onSingleTap() },
                onDoubleTap = { isForward ->
                    if (!uiState.isControlsLocked) viewModel.onDoubleTapSeek(isForward)
                },
                onLongPressStart = { if (!uiState.isControlsLocked) viewModel.onLongPressStart() },
                onLongPressEnd = { if (!uiState.isControlsLocked) viewModel.onLongPressEnd() },
                onVolumeGesture = { percent, isActive ->
                    if (!uiState.isControlsLocked) {
                        if (!isActive) {
                            dragStartVolume = -1
                        } else {
                            if (dragStartVolume == -1) {
                                dragStartVolume = uiState.volumeLevel
                            }

                            val addedVolume = (percent * 50).toInt()
                            val targetVolume = (dragStartVolume + addedVolume).coerceIn(0, 100)

                            viewModel.handlePlayerEvent(PlayerEvent.SetVolume(targetVolume))
                        }
                    }
                },
                onBrightnessGesture = { percent, isActive ->
                    if (!uiState.isControlsLocked) {
                        if (!isActive) {
                            dragStartBrightness = -1f
                        } else {
                            if (dragStartBrightness == -1f) {
                                dragStartBrightness = uiState.brightnessLevel
                            }
                            val targetBrightness = (dragStartBrightness + percent).coerceIn(0f, 1f)
                            viewModel.onScreenBrightnessGesture(targetBrightness, isAbsolute = true)
                        }
                    }
                },
                onSeekPreview = { isActive ->
                    if (!uiState.isControlsLocked) {
                        if (isActive) {
                            seekOriginTime = viewModel.player.currentPosition
                            viewModel.handlePlayerEvent(PlayerEvent.OnSeekBarDragStart)
                        } else {
                            viewModel.handlePlayerEvent(PlayerEvent.OnSeekBarDragFinished(viewModel.uiState.value.seekPosition))
                        }
                    }
                },
                onSeekGesture = { delta ->
                    if (!uiState.isControlsLocked) {
                        val timeShiftMs = (delta * uiState.duration).toLong()
                        val targetTime =
                            (seekOriginTime + timeShiftMs).coerceIn(0L, uiState.duration)
                        viewModel.updateTrickplayPreview(targetTime)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                when (val player = viewModel.player) {
                    is ExoPlayer -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        useController = false
                                        if (viewModel.isAssActive) {
                                            subtitleView?.apply {
                                                setApplyEmbeddedStyles(false)
                                                setApplyEmbeddedFontSizes(false)
                                                viewModel.assOverlayHandler?.let {
                                                    withAssSupport(it)
                                                }
                                            }
                                        } else {
                                            subtitleView?.visibility = android.view.View.GONE
                                        }
                                        this.player = player
                                    }
                                },
                                update = { view ->
                                    view.resizeMode = uiState.videoZoomMode.toExoPlayerResizeMode()
                                    if (viewModel.isAssActive) {
                                        view.subtitleView?.applySubtitleStyle(subtitlePrefs)
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }

                    is MPVPlayer -> {
                        MpvSurface(
                            modifier = Modifier.fillMaxSize(),
                            mpv = player.mpv,
                            videoOutput = viewModel.mpvVideoOutputValue,
                            onSurfaceCreated = { Timber.d("MPV surface created in player screen") },
                            onSurfaceDestroyed = {
                                Timber.d("MPV surface destroyed in player screen")
                            },
                        )
                    }
                }
            }

            if (viewModel.player is ExoPlayer && !viewModel.isAssActive) {
                ExoPlayerSubtitles(
                    player = viewModel.player as ExoPlayer,
                    subtitlePrefs = subtitlePrefs,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            PlayerControls(
                uiState = uiState,
                player = viewModel.player,
                onPlayerEvent = viewModel::handlePlayerEvent,
                onBackClick = {
                    if (!hasNavigatedBack) {
                        hasNavigatedBack = true
                        if (syncPlayState.isInGroup) syncPlayViewModel.leaveGroup()
                        viewModel.stopPlayback()
                        onBackPressed()
                    }
                },
                onNextClick = viewModel::onNextChapterOrEpisode,
                onPreviousClick = viewModel::onPreviousChapterOrEpisode,
                onPipToggle = { viewModel.handlePlayerEvent(PlayerEvent.EnterPictureInPicture) },
                playlistQueue = playlistState.queue,
                currentPlaylistIndex = playlistState.currentIndex,
                playlistContentStartIndex = playlistState.contentStartIndex,
                onJumpToEpisode = viewModel::jumpToEpisode,
                onVersionToggleRequest = { showVersionPicker = !showVersionPicker },
                isSyncPlay = syncPlayState.isInGroup,
                onSyncPlayClick = { syncPlayViewModel.toggleGroupSheet() },
                syncPlayMembers = syncPlayState.members,
                syncPlayGroupName = syncPlayState.groupName,
                syncPlayMemberInfo = syncPlayMemberInfo,
            )

            if (syncPlayState.isInGroup && syncPlayState.groupState == GroupStateType.WAITING) {
                SyncPlayWaitingOverlay(modifier = Modifier.fillMaxSize())
            }

            TrickplayPreview(
                isVisible = uiState.showTrickplayPreview,
                previewImage = uiState.trickplayPreviewImage,
                positionMs = uiState.trickplayPreviewPosition,
                durationMs = uiState.duration,
                chapters = uiState.chapters,
                modifier = Modifier.fillMaxSize(),
            )

            PlayerIndicators(uiState = uiState, modifier = Modifier.fillMaxSize())

            ErrorIndicator(
                isVisible = uiState.showError,
                errorMessage = uiState.errorMessage,
                onRetryClick = {
                    viewModel.handlePlayerEvent(
                        PlayerEvent.LoadMedia(
                            item = item,
                            mediaSourceId = mediaSourceId,
                            audioStreamIndex = audioStreamIndex,
                            subtitleStreamIndex = subtitleStreamIndex,
                            startPositionMs = startPositionMs,
                        )
                    )
                },
                modifier = Modifier.align(Alignment.Center),
            )

            if (uiState.showPlaybackStats) {
                PlaybackStatsOverlay(
                    stats = uiState.playbackStats,
                    onClose = { viewModel.handlePlayerEvent(PlayerEvent.TogglePlaybackStats) },
                )
            }

            if (showVersionPicker && uiState.availableSources.size > 1) {
                Box(
                    modifier =
                        Modifier.fillMaxSize().clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            showVersionPicker = false
                        }
                ) {
                    Box(
                        modifier =
                            Modifier.align(Alignment.BottomEnd)
                                .padding(bottom = 110.dp, end = 56.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    /* consume */
                                }
                    ) {
                        VersionPickerSheet(
                            sources = uiState.availableSources,
                            currentSourceId = uiState.currentMediaSourceId,
                            onVersionSelected = { source ->
                                viewModel.handlePlayerEvent(PlayerEvent.SwitchVersion(source.id))
                                showVersionPicker = false
                            },
                            onDismiss = { showVersionPicker = false },
                        )
                    }
                }
            }
        }
    }

    if (syncPlayUiState.showGroupSheet) {
        SyncPlayGroupSheet(
            syncPlayState = syncPlayState,
            uiState = syncPlayUiState,
            onCreateGroup = { name -> syncPlayViewModel.createGroup(name) },
            onJoinGroup = { id -> syncPlayViewModel.joinGroup(id) },
            onLeaveGroup = { syncPlayViewModel.leaveGroup() },
            onRefreshGroups = { syncPlayViewModel.loadGroups() },
            onDismiss = { syncPlayViewModel.dismissGroupSheet() },
        )
    }

    ScreenBrightnessController(brightness = uiState.brightnessLevel)
    KeepScreenOn(keepOn = uiState.isPlaying)
    PlayerSystemBarsController(isControlsVisible = uiState.showControls)
}

@OptIn(UnstableApi::class)
@Composable
private fun ExoPlayerSubtitles(
    player: ExoPlayer,
    subtitlePrefs: SubtitlePreferences,
    modifier: Modifier = Modifier,
) {
    var subtitleView: SubtitleView? by remember { mutableStateOf(null) }
    AndroidView(
        factory = { ctx ->
            SubtitleView(ctx).apply {
                setApplyEmbeddedStyles(false)
                setApplyEmbeddedFontSizes(false)
                subtitleView = this
            }
        },
        update = { view -> view.applySubtitleStyle(subtitlePrefs) },
        modifier = modifier,
    )

    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onCues(cueGroup: CueGroup) {
                    subtitleView?.setCues(cueGroup.cues)
                }
            }

        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
            subtitleView?.setCues(emptyList())
        }
    }
}

@OptIn(UnstableApi::class)
private fun SubtitleView.applySubtitleStyle(subtitlePrefs: SubtitlePreferences) {
    val baseTypeface = Typeface.SANS_SERIF
    val typeface =
        if (subtitlePrefs.bold) {
            Typeface.create(baseTypeface, Typeface.BOLD)
        } else {
            baseTypeface
        }

    val customStyle =
        CaptionStyleCompat(
            subtitlePrefs.textColor,
            subtitlePrefs.backgroundColor,
            subtitlePrefs.windowColor,
            subtitlePrefs.outlineStyle.toExoPlayerEdgeType(),
            subtitlePrefs.outlineColor,
            typeface,
        )
    setStyle(customStyle)
    setFractionalTextSize(subtitlePrefs.toExoPlayerFractionalSize())
}
