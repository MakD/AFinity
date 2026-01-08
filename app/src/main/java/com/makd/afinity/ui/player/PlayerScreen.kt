package com.makd.afinity.ui.player

import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.player.PlayerEvent
import com.makd.afinity.data.models.player.SubtitlePreferences
import com.makd.afinity.player.mpv.MPVPlayer
import com.makd.afinity.ui.player.components.ErrorIndicator
import com.makd.afinity.ui.player.components.GestureHandler
import com.makd.afinity.ui.player.components.MpvSurface
import com.makd.afinity.ui.player.components.PlayerControls
import com.makd.afinity.ui.player.components.PlayerIndicators
import com.makd.afinity.ui.player.components.TrickplayPreview
import com.makd.afinity.ui.player.utils.KeepScreenOn
import com.makd.afinity.ui.player.utils.PlayerSystemBarsController
import com.makd.afinity.ui.player.utils.RequiresBrightnessPermission
import com.makd.afinity.ui.player.utils.ScreenBrightnessController
import timber.log.Timber


@UnstableApi
@Composable
fun PlayerScreen(
    item: AfinityItem,
    mediaSourceId: String,
    audioStreamIndex: Int? = null,
    subtitleStreamIndex: Int? = null,
    startPositionMs: Long = 0L,
    onBackPressed: () -> Unit,
    navController: androidx.navigation.NavController? = null,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val context = androidx.compose.ui.platform.LocalContext.current
    val preferencesRepository = remember {
        dagger.hilt.android.EntryPointAccessors.fromApplication(
            context.applicationContext,
            com.makd.afinity.di.PreferencesEntryPoint::class.java
        ).preferencesRepository()
    }
    val subtitlePrefs by preferencesRepository.getSubtitlePreferencesFlow()
        .collectAsStateWithLifecycle(
            initialValue = SubtitlePreferences.DEFAULT
        )
    var seekOriginTime by remember { mutableLongStateOf(0L) }
    var dragStartVolume by remember { mutableStateOf(-1) }
    var dragStartBrightness by remember { mutableFloatStateOf(-1f) }
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(item.id, mediaSourceId) {
        Timber.d("Loading media: ${item.name}")
        viewModel.handlePlayerEvent(
            PlayerEvent.LoadMedia(
                item = item,
                mediaSourceId = mediaSourceId,
                audioStreamIndex = audioStreamIndex,
                subtitleStreamIndex = subtitleStreamIndex,
                startPositionMs = startPositionMs
            )
        )
    }

    LaunchedEffect(item) {
        Timber.d("Initializing playlist for item: ${item.name} (${item.id})")
        viewModel.initializePlaylist(item)
    }

    LaunchedEffect(navController) {
        viewModel.setAutoplayCallback { nextItem ->
            try {
                nextItem.sources.forEachIndexed { index, source ->
                    Timber.d("Source $index: ${source.id} (${source.type})")
                }

                val mediaSourceId = nextItem.sources.firstOrNull()?.id
                if (mediaSourceId == null) {
                    Timber.e("No media source available for next item: ${nextItem.name}")
                    return@setAutoplayCallback
                }

                viewModel.handlePlayerEvent(
                    PlayerEvent.LoadMedia(
                        item = nextItem,
                        mediaSourceId = mediaSourceId,
                        audioStreamIndex = null,
                        subtitleStreamIndex = null,
                        startPositionMs = 0L
                    )
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to load next item: ${nextItem.name}")
            }
        }
    }

    var hasNavigatedBack by remember { mutableStateOf(false) }

    BackHandler {
        if (!hasNavigatedBack) {
            hasNavigatedBack = true
            Timber.d("Back button pressed - calling onBackPressed")
            viewModel.stopPlayback()
            onBackPressed()
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        GestureHandler(
            onSingleTap = { viewModel.onSingleTap() },
            onDoubleTap = { isForward ->
                if (!uiState.isControlsLocked) viewModel.onDoubleTapSeek(isForward)
            },
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
                        viewModel.handlePlayerEvent(PlayerEvent.OnSeekBarDragFinished)
                    }
                }
            },
            onSeekGesture = { delta ->
                if (!uiState.isControlsLocked) {
                    val timeShiftMs = (delta * uiState.duration).toLong()
                    val targetTime = (seekOriginTime + timeShiftMs).coerceIn(0L, uiState.duration)
                    viewModel.updateTrickplayPreview(targetTime)
                }
            },
            modifier = Modifier.fillMaxSize()
        ) {
            when (val player = viewModel.player) {
                is androidx.media3.exoplayer.ExoPlayer -> {
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { context ->
                            androidx.media3.ui.PlayerView(context).apply {
                                useController = false
                                subtitleView?.visibility = android.view.View.GONE
                                this.player = player
                                viewModel.setPlayerView(this)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                is MPVPlayer -> {
                    MpvSurface(
                        modifier = Modifier.fillMaxSize(),
                        onSurfaceCreated = {
                            Timber.d("MPV surface created in player screen")
                        },
                        onSurfaceDestroyed = {
                            Timber.d("MPV surface destroyed in player screen")
                        }
                    )
                }
            }
        }

        if (viewModel.player is androidx.media3.exoplayer.ExoPlayer) {
            ExoPlayerSubtitles(
                player = viewModel.player as androidx.media3.exoplayer.ExoPlayer,
                subtitlePrefs = subtitlePrefs,
                modifier = Modifier.fillMaxSize()
            )
        }

        PlayerControls(
            uiState = uiState,
            player = viewModel.player,
            onPlayerEvent = viewModel::handlePlayerEvent,
            onBackClick = {
                onBackPressed()
            },
            onNextClick = viewModel::onNextChapterOrEpisode,
            onPreviousClick = viewModel::onPreviousChapterOrEpisode,
            onPipToggle = {
                viewModel.handlePlayerEvent(PlayerEvent.EnterPictureInPicture)
            },
        )

        TrickplayPreview(
            isVisible = uiState.showTrickplayPreview,
            previewImage = uiState.trickplayPreviewImage,
            positionMs = uiState.trickplayPreviewPosition,
            durationMs = uiState.duration,
            chapters = uiState.chapters,
            modifier = Modifier.fillMaxSize()
        )

        PlayerIndicators(
            uiState = uiState,
            modifier = Modifier.fillMaxSize()
        )

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
                        startPositionMs = startPositionMs
                    )
                )
            },
            modifier = Modifier.align(Alignment.Center)
        )
    }

    RequiresBrightnessPermission {
        ScreenBrightnessController(brightness = uiState.brightnessLevel)
    }
    KeepScreenOn(keepOn = uiState.isPlaying)
    PlayerSystemBarsController(isControlsVisible = uiState.showControls)
}

@OptIn(UnstableApi::class)
@Composable
private fun ExoPlayerSubtitles(
    player: androidx.media3.exoplayer.ExoPlayer,
    subtitlePrefs: SubtitlePreferences,
    modifier: Modifier = Modifier
) {
    var subtitleView: androidx.media3.ui.SubtitleView? by remember {
        mutableStateOf(null)
    }

    AndroidView(
        factory = { context ->
            androidx.media3.ui.SubtitleView(context).apply {
                setApplyEmbeddedStyles(false)
                setApplyEmbeddedFontSizes(false)
                subtitleView = this
            }
        },
        update = { view ->
            val baseTypeface = Typeface.SANS_SERIF
            val typeface = if (subtitlePrefs.bold) {
                Typeface.create(baseTypeface, Typeface.BOLD)
            } else {
                baseTypeface
            }

            val customStyle = CaptionStyleCompat(
                subtitlePrefs.textColor,
                subtitlePrefs.backgroundColor,
                subtitlePrefs.windowColor,
                subtitlePrefs.outlineStyle.toExoPlayerEdgeType(),
                subtitlePrefs.outlineColor,
                typeface
            )
            view.setStyle(customStyle)
            view.setFractionalTextSize(subtitlePrefs.toExoPlayerFractionalSize())
        },
        modifier = modifier
    )

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onCues(
                cueGroup: CueGroup
            ) {
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