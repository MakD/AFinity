package com.makd.afinity.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.player.PlayerEvent
import com.makd.afinity.ui.player.components.ErrorIndicator
import com.makd.afinity.ui.player.components.GestureHandler
import com.makd.afinity.ui.player.components.MpvSurface
import com.makd.afinity.ui.player.components.PlayerControls
import com.makd.afinity.ui.player.components.PlayerIndicators
import com.makd.afinity.ui.player.components.TrickplayPreview
import com.makd.afinity.ui.player.utils.KeepScreenOn
import com.makd.afinity.ui.player.utils.PlayerSystemBarsController
import com.makd.afinity.ui.player.utils.ScreenBrightnessController
import timber.log.Timber


@androidx.media3.common.util.UnstableApi
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
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    Timber.d("Player screen resumed")
                    viewModel.onResume()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    Timber.d("Player screen paused")
                    viewModel.onPause()
                }
                else -> {}
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            Timber.d("PlayerScreen disposed")
        }
    }

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

    LaunchedEffect(navController, item) {
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
                viewModel.initializePlaylist(nextItem)
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
                onSingleTap = {
                    Timber.d("GESTURE: Single tap detected!")
                    viewModel.onSingleTap()
                },
                onDoubleTap = { isForward ->
                    Timber.d("GESTURE: Double tap detected! Forward: $isForward")
                    if (!uiState.isControlsLocked) {
                        viewModel.onDoubleTapSeek(isForward)
                    }
                },
                onBrightnessGesture = { delta ->
                    if (!uiState.isControlsLocked) {
                        viewModel.onScreenBrightnessGesture(delta)
                    }
                },
                onVolumeGesture = { delta ->
                    if (!uiState.isControlsLocked) {
                        viewModel.onVolumeGesture(delta)
                    }
                },
                onSeekGesture = { delta ->
                    if (!uiState.isControlsLocked) {
                        viewModel.onSeekGestureChange(delta)
                    }
                },
                onSeekPreview = { isActive ->
                    if (!uiState.isControlsLocked) {
                        if (isActive) {
                            viewModel.handlePlayerEvent(PlayerEvent.OnSeekBarDragStart)
                        } else {
                            viewModel.handlePlayerEvent(PlayerEvent.OnSeekBarDragFinished)
                        }
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
                                    this.player = player
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    is com.makd.afinity.player.mpv.MPVPlayer -> {
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

            PlayerControls(
                uiState = uiState,
                player = viewModel.player,
                onPlayerEvent = viewModel::handlePlayerEvent,
                onBackClick = {
                    onBackPressed()
                },
                onNextEpisode = viewModel::onNextEpisode,
                onPreviousEpisode = viewModel::onPreviousEpisode,
                modifier = Modifier.fillMaxSize()
            )

            TrickplayPreview(
                isVisible = uiState.showTrickplayPreview,
                previewImage = uiState.trickplayPreviewImage,
                positionMs = uiState.trickplayPreviewPosition,
                durationMs = uiState.duration,
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

    ScreenBrightnessController(brightness = uiState.brightnessLevel)
    KeepScreenOn(keepOn = uiState.isPlaying)
    PlayerSystemBarsController(isControlsVisible = uiState.showControls)
}