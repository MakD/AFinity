package com.makd.afinity.ui.player

import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import com.makd.afinity.R
import com.makd.afinity.cast.CastEvent
import com.makd.afinity.cast.CastManager
import com.makd.afinity.data.manager.PlaybackStateManager
import com.makd.afinity.data.models.livetv.AfinityChannel
import com.makd.afinity.data.models.livetv.ChannelType
import com.makd.afinity.data.models.media.AfinityChapter
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinitySegment
import com.makd.afinity.data.models.media.AfinitySegmentType
import com.makd.afinity.data.models.media.AfinitySourceType
import com.makd.afinity.data.models.player.GestureConfig
import com.makd.afinity.data.models.player.PlayerEvent
import com.makd.afinity.data.models.player.SubtitleOutlineStyle
import com.makd.afinity.data.models.player.SubtitlePreferences
import com.makd.afinity.data.models.player.Trickplay
import com.makd.afinity.data.models.player.VideoZoomMode
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.JellyfinRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.download.JellyfinDownloadRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.playback.PlaybackRepository
import com.makd.afinity.data.repository.segments.SegmentsRepository
import com.makd.afinity.player.audiobookshelf.AudiobookshelfPlayer
import com.makd.afinity.player.mpv.MPVPlayer
import com.makd.afinity.ui.player.utils.VolumeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.MediaStreamType
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@androidx.media3.common.util.UnstableApi
@HiltViewModel
class PlayerViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val application: Application,
    private val playbackRepository: PlaybackRepository,
    private val playbackStateManager: PlaybackStateManager,
    val castManager: CastManager,
    private val mediaRepository: MediaRepository,
    private val jellyfinRepository: JellyfinRepository,
    private val segmentsRepository: SegmentsRepository,
    private val preferencesRepository: PreferencesRepository,
    private val playlistManager: PlaylistManager,
    private val downloadRepository: JellyfinDownloadRepository,
    private val appDataRepository: AppDataRepository,
    private val apiClient: ApiClient,
    private val audiobookshelfPlayer: AudiobookshelfPlayer,
) : ViewModel(), Player.Listener {

    lateinit var player: Player
        private set

    private var hasStoppedPlayback = false
    private var currentSessionId: String? = null
    private val volumeManager: VolumeManager by lazy { VolumeManager(context) }

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    val playlistState: StateFlow<PlaylistState> = playlistManager.playlistState

    val gestureConfig = GestureConfig()

    private var controlsHideJob: Job? = null
    private var currentMediaSegments: List<AfinitySegment> = emptyList()
    private var segmentCheckingJob: Job? = null

    private var progressReportingJob: Job? = null
    private var currentItem: AfinityItem? = null
    private var currentTrickplay: Trickplay? = null

    private var playerView: PlayerView? = null
    private var currentZoomMode: VideoZoomMode = VideoZoomMode.FIT
    private var isVideoPortrait: Boolean = false
    private var isOrientationOverridden: Boolean = false

    var onAutoplayNextEpisode: ((AfinityItem) -> Unit)? = null
    var enterPictureInPicture: (() -> Unit)? = null
    var updatePipParams: (() -> Unit)? = null
    private val screenAspectRatio: Float by lazy {
        val metrics = context.resources.displayMetrics
        metrics.widthPixels.toFloat() / metrics.heightPixels.toFloat()
    }

    init {
        viewModelScope.launch {
            appDataRepository.isInitialDataLoaded.collect { isLoaded ->
                if (!isLoaded) {
                    Timber.d("Session cleared, stopping playback")
                    stopPlayback()
                    player.stop()
                    player.clearMediaItems()
                    updateUiState { PlayerUiState() }
                }
            }
        }
        initializePlayer()
        startPositionUpdateLoop()
        startProgressReporting()
        initializeVideoZoomMode()
        initializeLogoAutoHide()
        initializeBackgroundPlay()
        observeCastState()
    }

    private fun initializeVideoZoomMode() {
        viewModelScope.launch {
            try {
                currentZoomMode = preferencesRepository.getDefaultVideoZoomMode()
                updateUiState { it.copy(videoZoomMode = currentZoomMode) }
                applyZoomMode(currentZoomMode)
            } catch (e: Exception) {
                Timber.e(e, "Failed to load default video zoom mode, using FIT")
                currentZoomMode = VideoZoomMode.FIT
                updateUiState { it.copy(videoZoomMode = currentZoomMode) }
                applyZoomMode(VideoZoomMode.FIT)
            }
        }
    }

    private fun initializeLogoAutoHide() {
        viewModelScope.launch {
            val logoAutoHide = preferencesRepository.getLogoAutoHide()
            updateUiState { it.copy(logoAutoHide = logoAutoHide) }
        }
    }

    private fun observeCastState() {
        viewModelScope.launch {
            castManager.castState.collect { castState ->
                updateUiState { it.copy(isCasting = castState.isConnected) }
                if (castState.isConnected && castState.currentItem == null) {
                    startCasting()
                }
            }
        }
        viewModelScope.launch {
            castManager.castEvents.collect { event ->
                when (event) {
                    is CastEvent.Connected -> {
                        Timber.d("Cast connected to: ${event.deviceName}")
                    }
                    is CastEvent.Disconnected -> {
                        Timber.d("Cast disconnected")
                        updateUiState { it.copy(isCasting = false) }
                    }
                    is CastEvent.PlaybackStarted -> {
                        Timber.d("Cast playback started")
                    }
                    is CastEvent.PlaybackError -> {
                        Timber.e("Cast error: ${event.message}")
                        updateUiState { it.copy(isCasting = false) }
                    }
                }
            }
        }
    }

    fun startCasting() {
        val item = currentItem ?: return
        val mediaSourceId = item.sources.firstOrNull()?.id ?: return
        val serverBaseUrl = apiClient.baseUrl ?: return

        player.pause()

        viewModelScope.launch {
            val enableHevc = preferencesRepository.getCastHevcEnabled()
            val maxBitrate = preferencesRepository.getCastMaxBitrate()

            castManager.loadMedia(
                item = item,
                serverBaseUrl = serverBaseUrl,
                mediaSourceId = mediaSourceId,
                audioStreamIndex = _uiState.value.audioStreamIndex,
                subtitleStreamIndex = _uiState.value.subtitleStreamIndex,
                startPositionMs = player.currentPosition,
                maxBitrate = maxBitrate,
                enableHevc = enableHevc,
            )
        }
    }

    fun stopCasting() {
        castManager.stop()
        updateUiState { it.copy(isCasting = false) }
    }

    fun dismissCastChooser() {
        updateUiState { it.copy(showCastChooser = false) }
    }

    private fun initializeBackgroundPlay() {
        viewModelScope.launch {
            preferencesRepository.getPipBackgroundPlayFlow().collect { enabled ->
                updateUiState { it.copy(pipBackgroundPlay = enabled) }
            }
        }
    }

    private fun getSystemBrightness(): Float {
        return try {
            val brightnessMode =
                Settings.System.getInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                )

            if (brightnessMode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
                Timber.d("System brightness is in automatic mode. Returning default brightness.")
                0.5f
            } else {
                val brightness =
                    Settings.System.getInt(
                        context.contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS,
                    )
                brightness / 255.0f
            }
        } catch (e: Settings.SettingNotFoundException) {
            Timber.e(e, "System brightness setting not found. Returning default brightness.")
            0.5f
        } catch (e: Exception) {
            Timber.e(e, "Failed to get system brightness. Returning default brightness.")
            0.5f
        }
    }

    private fun startPositionUpdateLoop() {
        viewModelScope.launch {
            while (true) {
                delay(100)
                val isMpvLive = player is MPVPlayer && _uiState.value.isLiveChannel
                if ((player.isPlaying || isMpvLive) && !_uiState.value.isSeeking) {
                    updatePlayerState()
                }
            }
        }
    }

    private fun startProgressReporting() {
        progressReportingJob?.cancel()
        progressReportingJob =
            viewModelScope.launch {
                while (true) {
                    delay(5000L)
                    if (_uiState.value.isLiveChannel) continue

                    currentItem?.let { item ->
                        currentSessionId?.let { sessionId ->
                            try {
                                val positionTicks = player.currentPosition * 10000
                                val isPaused = !player.isPlaying

                                playbackStateManager.updatePlaybackPosition(player.currentPosition)

                                playbackRepository.reportPlaybackProgress(
                                    itemId = item.id,
                                    sessionId = sessionId,
                                    positionTicks = positionTicks,
                                    isPaused = isPaused,
                                    playMethod = "DirectPlay",
                                )
                                Timber.d(
                                    "Reported progress: ${player.currentPosition}ms, paused: $isPaused"
                                )
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to report periodic progress")
                            }
                        }
                    }
                }
            }
    }

    private fun initializePlayer() {
        val useExoPlayer =
            kotlinx.coroutines.runBlocking { preferencesRepository.useExoPlayer.first() }

        player =
            if (useExoPlayer) {
                createExoPlayer()
            } else {
                createMPVPlayer()
            }

        player.addListener(this@PlayerViewModel)
        Timber.d("Player initialized: ${player.javaClass.simpleName}")
    }

    private fun createExoPlayer(): ExoPlayer {
        val audioAttributes =
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(C.USAGE_MEDIA)
                .build()

        val (preferredAudioLang, preferredSubLang) =
            kotlinx.coroutines.runBlocking {
                Pair(
                    preferencesRepository.getPreferredAudioLanguage(),
                    preferencesRepository.getPreferredSubtitleLanguage(),
                )
            }

        val trackSelector = DefaultTrackSelector(context)
        trackSelector.setParameters(
            trackSelector.buildUponParameters().setTunnelingEnabled(true).apply {
                if (preferredAudioLang.isNotEmpty()) {
                    setPreferredAudioLanguage(preferredAudioLang)
                }
                if (preferredSubLang.isNotEmpty()) {
                    setPreferredTextLanguage(preferredSubLang)
                }
            }
        )

        Timber.d(
            "ExoPlayer preferences: preferredAudioLang=$preferredAudioLang, preferredSubLang=$preferredSubLang"
        )

        val renderersFactory =
            DefaultRenderersFactory(context)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        return ExoPlayer.Builder(context, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setTrackSelector(trackSelector)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .setPauseAtEndOfMediaItems(true)
            .build()
    }

    var mpvVideoOutputValue: String = "gpu"
        private set

    private fun createMPVPlayer(): MPVPlayer {
        val audioAttributes =
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(C.USAGE_MEDIA)
                .build()

        val (
            mpvHwDec,
            mpvVideoOutput,
            mpvAudioOutput,
            subtitlePrefs,
            preferredAudioLang,
            preferredSubLang) =
            kotlinx.coroutines.runBlocking {
                val hwDec = preferencesRepository.getMpvHwDec()
                val vo = preferencesRepository.getMpvVideoOutput()
                val ao = preferencesRepository.getMpvAudioOutput()
                val subs = preferencesRepository.getSubtitlePreferences()
                val audioLang = preferencesRepository.getPreferredAudioLanguage()
                val subLang = preferencesRepository.getPreferredSubtitleLanguage()
                MpvPrefsSnapshot(hwDec.value, vo.value, ao.value, subs, audioLang, subLang)
            }

        mpvVideoOutputValue = mpvVideoOutput

        Timber.d(
            "MPV preferences: hwdec=$mpvHwDec, vo=$mpvVideoOutput, ao=$mpvAudioOutput, alang=$preferredAudioLang, slang=$preferredSubLang"
        )

        val mpvPlayer =
            MPVPlayer.Builder(application)
                .setAudioAttributes(audioAttributes, true)
                .setSeekBackIncrementMs(10000)
                .setSeekForwardIncrementMs(10000)
                .setPauseAtEndOfMediaItems(true)
                .setVideoOutput(mpvVideoOutput)
                .setAudioOutput(mpvAudioOutput)
                .setHwDec(mpvHwDec)
                .build()

        mpvPlayer.setOption("sub-ass-override", "strip")
        mpvPlayer.setOption("sub-use-margins", "yes")
        mpvPlayer.setOption("sub-color", SubtitlePreferences.colorToMpvHex(subtitlePrefs.textColor))
        mpvPlayer.setOption("sub-font-size", subtitlePrefs.toMpvFontSize().toString())
        mpvPlayer.setOption("sub-bold", if (subtitlePrefs.bold) "yes" else "no")
        mpvPlayer.setOption("sub-italic", if (subtitlePrefs.italic) "yes" else "no")

        when (subtitlePrefs.outlineStyle) {
            SubtitleOutlineStyle.OUTLINE -> {
                mpvPlayer.setOption("sub-border-style", "outline-and-shadow")
                mpvPlayer.setOption("sub-border-size", subtitlePrefs.outlineSize.toString())
                mpvPlayer.setOption(
                    "sub-border-color",
                    SubtitlePreferences.colorToMpvHex(subtitlePrefs.outlineColor),
                )
                mpvPlayer.setOption("sub-shadow-offset", "0")
                mpvPlayer.setOption("sub-shadow-color", "#00000000")
            }

            SubtitleOutlineStyle.DROP_SHADOW -> {
                mpvPlayer.setOption("sub-border-style", "outline-and-shadow")
                mpvPlayer.setOption("sub-border-size", "0")
                mpvPlayer.setOption("sub-border-color", "#00000000")
                mpvPlayer.setOption("sub-shadow-offset", subtitlePrefs.outlineSize.toString())
                mpvPlayer.setOption(
                    "sub-shadow-color",
                    SubtitlePreferences.colorToMpvHex(subtitlePrefs.outlineColor),
                )
            }

            SubtitleOutlineStyle.BACKGROUND_BOX -> {
                mpvPlayer.setOption("sub-border-style", "background-box")
                mpvPlayer.setOption(
                    "sub-back-color",
                    SubtitlePreferences.colorToMpvHex(subtitlePrefs.backgroundColor),
                )
                mpvPlayer.setOption("sub-spacing", "0.5")
            }

            else -> {
                mpvPlayer.setOption("sub-border-style", "outline-and-shadow")
                mpvPlayer.setOption("sub-border-size", "0")
                mpvPlayer.setOption("sub-border-color", "#00000000")
                mpvPlayer.setOption("sub-shadow-offset", "0")
                mpvPlayer.setOption("sub-shadow-color", "#00000000")
            }
        }

        mpvPlayer.setOption("sub-align-y", subtitlePrefs.verticalPosition.mpvValue)
        mpvPlayer.setOption("sub-align-x", subtitlePrefs.horizontalAlignment.mpvValue)

        if (preferredAudioLang.isNotEmpty()) {
            mpvPlayer.setOption("alang", preferredAudioLang)
        }
        if (preferredSubLang.isNotEmpty()) {
            mpvPlayer.setOption("slang", preferredSubLang)
        }

        return mpvPlayer
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        updatePlayerState()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            updateUiState { it.copy(isLoading = false) }
            if (_uiState.value.showControls) {
                startControlsAutoHide()
            }
        }
        updatePlayerState()
        updatePipParams?.invoke()
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
            viewModelScope.launch {
                val nextItem = playlistManager.next()
                if (nextItem != null) {
                    Timber.d("Episode ended, auto-advancing to: ${nextItem.name}")
                    onAutoplayNextEpisode?.invoke(nextItem)
                } else {
                    Timber.d("Episode ended, no next item in queue")
                }
            }
        }
        updatePlayerState()
    }

    private var lastKnownPosition = 0L
    private var lastKnownDuration = 0L
    private var mpvStreamActiveTime = 0L
    private var mpvLiveAutoHideTriggered = false

    private fun updatePlayerState() {
        val position = player.currentPosition.coerceAtLeast(0)
        val duration = player.duration.coerceAtLeast(0)
        val isPlaying = player.isPlaying
        val playbackState = player.playbackState
        val isLiveChannel = _uiState.value.isLiveChannel
        val isMpv = player is MPVPlayer
        val wasActuallyPlaying = _uiState.value.isPlaying

        val isActuallyPlaying =
            if (isMpv && isLiveChannel) {
                val positionAdvancing = position > lastKnownPosition && position > 0
                val durationAdvancing = duration > lastKnownDuration && duration > 0
                val streamActive = positionAdvancing || durationAdvancing

                if (streamActive || isPlaying) {
                    mpvStreamActiveTime = System.currentTimeMillis()
                    true
                } else {
                    val recentlyActive = System.currentTimeMillis() - mpvStreamActiveTime < 10000
                    recentlyActive && duration > 0
                }
            } else {
                isPlaying
            }

        if (
            isMpv &&
                isLiveChannel &&
                isActuallyPlaying &&
                !wasActuallyPlaying &&
                !mpvLiveAutoHideTriggered
        ) {
            mpvLiveAutoHideTriggered = true
            if (_uiState.value.showControls) {
                startControlsAutoHide()
            }
        }

        val isBuffering = !isActuallyPlaying && playbackState == Player.STATE_BUFFERING

        lastKnownPosition = position
        lastKnownDuration = duration

        _uiState.value =
            _uiState.value.copy(
                isPlaying = isActuallyPlaying,
                isPaused = !isActuallyPlaying && playbackState == Player.STATE_READY,
                isBuffering = isBuffering,
                currentPosition = position,
                duration = duration,
            )
    }

    fun handlePlayerEvent(event: PlayerEvent) {
        viewModelScope.launch {
            when (event) {
                is PlayerEvent.Play -> player.play()
                is PlayerEvent.Pause -> player.pause()
                is PlayerEvent.Seek -> player.seekTo(event.positionMs)
                is PlayerEvent.SeekRelative -> {
                    showControls()
                    val newPos =
                        (player.currentPosition + event.deltaMs).coerceIn(0, player.duration)
                    player.seekTo(newPos)
                }

                is PlayerEvent.SetVolume -> {
                    volumeManager.setVolume(event.volume)

                    updateUiState {
                        it.copy(showVolumeIndicator = true, volumeLevel = event.volume)
                    }

                    volumeHideJob?.cancel()
                    volumeHideJob =
                        viewModelScope.launch {
                            delay(2000)
                            updateUiState { it.copy(showVolumeIndicator = false) }
                        }
                }

                is PlayerEvent.SetBrightness -> {
                    /* Handle at UI level */
                }

                is PlayerEvent.SetPlaybackSpeed -> {
                    player.setPlaybackSpeed(event.speed)
                    updateUiState { it.copy(playbackSpeed = event.speed) }
                }

                is PlayerEvent.SwitchToTrack -> switchToTrack(event.trackType, event.index)
                is PlayerEvent.ToggleControls -> toggleControls()
                is PlayerEvent.ToggleLock -> onLockToggle()
                is PlayerEvent.ToggleFullscreen -> {
                    /* Handled at UI level */
                }

                is PlayerEvent.EnterPictureInPicture -> enterPictureInPicture()
                is PlayerEvent.LoadMedia -> {
                    updateUiState { it.copy(isLoading = true) }
                    loadMedia(
                        event.item,
                        event.mediaSourceId,
                        event.audioStreamIndex,
                        event.subtitleStreamIndex,
                        event.startPositionMs,
                    )
                }

                is PlayerEvent.LoadLiveChannel -> {
                    updateUiState { it.copy(isLoading = true, isLiveChannel = true) }
                    loadLiveChannel(event.channelId, event.channelName, event.streamUrl)
                }

                is PlayerEvent.SkipSegment -> {
                    updateUiState { it.copy(currentSegment = null, showSkipButton = false) }
                    if (event.segment.type == AfinitySegmentType.OUTRO) {
                        viewModelScope.launch {
                            val nextItem = playlistManager.getNextItem()
                            if (nextItem != null) {
                                onNextEpisode()
                            } else {
                                handlePlayerEvent(PlayerEvent.Seek(event.segment.endTicks))
                            }
                        }
                    } else {
                        handlePlayerEvent(PlayerEvent.Seek(event.segment.endTicks))
                    }
                }

                is PlayerEvent.Stop -> player.stop()
                is PlayerEvent.OnSeekBarDragStart -> {
                    updateUiState { it.copy(isSeeking = true) }
                    onSeekBarPreview(player.currentPosition, true)
                }

                is PlayerEvent.OnSeekBarValueChange -> {
                    updateUiState { it.copy(seekPosition = event.positionMs) }
                    onSeekBarPreview(event.positionMs, true)
                }

                is PlayerEvent.OnSeekBarDragFinished -> {
                    val finalPos = uiState.value.seekPosition
                    player.seekTo(finalPos)
                    updateUiState {
                        it.copy(
                            isSeeking = false,
                            showTrickplayPreview = false,
                            trickplayPreviewImage = null,
                            trickplayPreviewPosition = 0L,
                            currentPosition = finalPos,
                        )
                    }
                    onSeekBarPreview(0, false)
                }

                is PlayerEvent.ToggleRemainingTime -> {
                    updateUiState { it.copy(showRemainingTime = !it.showRemainingTime) }
                }

                is PlayerEvent.SetVideoZoomMode -> {
                    applyZoomMode(event.mode)
                }

                is PlayerEvent.CycleVideoZoomMode -> {
                    cycleZoomMode()
                }

                is PlayerEvent.CycleScreenRotation -> {
                    toggleScreenRotation()
                }

                is PlayerEvent.RequestCastDeviceSelection -> {
                    updateUiState { it.copy(showCastChooser = true) }
                }
            }
        }
    }

    private fun applyZoomMode(mode: VideoZoomMode) {
        when (player) {
            is ExoPlayer -> {
                playerView?.resizeMode = mode.toExoPlayerResizeMode()
            }

            is MPVPlayer -> {
                applyMPVZoomMode(mode)
            }
        }

        currentZoomMode = mode
        updateUiState { it.copy(videoZoomMode = mode) }
    }

    private fun applyMPVZoomMode(mode: VideoZoomMode) {
        val mpvPlayer = player as? MPVPlayer ?: return

        when (mode) {
            VideoZoomMode.FIT -> {
                mpvPlayer.setOption("video-aspect-override", "-1")
                mpvPlayer.setOption("panscan", "0.0")
            }

            VideoZoomMode.ZOOM -> {
                mpvPlayer.setOption("video-aspect-override", "-1")
                mpvPlayer.setOption("panscan", "1.0")
            }

            VideoZoomMode.STRETCH -> {
                mpvPlayer.setOption("video-unscaled", "1")
                mpvPlayer.setOption("panscan", "0.0")
                mpvPlayer.setOption("video-aspect-override", screenAspectRatio.toString())
            }
        }
    }

    fun cycleZoomMode() {
        applyZoomMode(currentZoomMode.toggle())
    }

    private fun toggleScreenRotation() {
        isOrientationOverridden = !isOrientationOverridden
        val effectivePortrait = if (isOrientationOverridden) !isVideoPortrait else isVideoPortrait
        updateUiState { it.copy(resolvedOrientation = computeOrientation(effectivePortrait)) }
    }

    fun setPlayerView(view: PlayerView) {
        playerView = view
        applyZoomMode(currentZoomMode)
    }

    fun getPlayerView(): PlayerView? = playerView

    private fun stopAudiobookshelfIfPlaying() {
        if (audiobookshelfPlayer.isPlaying()) {
            Timber.d("Stopping Audiobookshelf playback before starting Jellyfin playback")
            audiobookshelfPlayer.pause()
            viewModelScope.launch { audiobookshelfPlayer.closeSession() }
        }
    }

    private suspend fun loadMedia(
        item: AfinityItem,
        mediaSourceId: String,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
        startPositionMs: Long,
    ) {
        stopAudiobookshelfIfPlaying()
        try {
            val fullItem: AfinityItem =
                if (item.sources.isEmpty()) {
                    Timber.d("Item ${item.name} has no sources, fetching full details...")
                    try {
                        (jellyfinRepository.getItemById(item.id) as? AfinityItem) ?: item
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to fetch full item details")
                        item
                    }
                } else {
                    item
                }

            currentItem = fullItem

            val chapters = fullItem.chapters
            updateUiState { it.copy(chapters = chapters) }
            val mediaSource =
                fullItem.sources.firstOrNull {
                    if (mediaSourceId.isBlank()) true else it.id == mediaSourceId
                } ?: fullItem.sources.firstOrNull()

            val actualMediaSourceId = mediaSource?.id

            if (actualMediaSourceId == null) {
                Timber.e("No media source found for item: ${fullItem.name}")
                updateUiState {
                    it.copy(
                        isLoading = false,
                        showError = true,
                        errorMessage = context.getString(R.string.error_no_media_sources),
                    )
                }
                return
            }

            val videoStream =
                mediaSource.mediaStreams.firstOrNull { it.type == MediaStreamType.VIDEO }
            if (videoStream != null) {
                val w = videoStream.width
                val h = videoStream.height
                if (w != null && h != null && w > 0 && h > 0) {
                    isVideoPortrait = h > w
                    isOrientationOverridden = false
                    updateUiState {
                        it.copy(resolvedOrientation = computeOrientation(isVideoPortrait))
                    }
                    Timber.d(
                        "Pre-set orientation from metadata: ${w}x${h}, isPortrait=$isVideoPortrait"
                    )
                }
            }

            val audioStreams = mediaSource.mediaStreams.filter { it.type == MediaStreamType.AUDIO }
            val audioPosition =
                if (audioStreamIndex != null) {
                    audioStreams.indexOfFirst { it.index == audioStreamIndex }.takeIf { it >= 0 }
                } else {
                    null
                }

            updateUiState {
                it.copy(
                    currentItem = fullItem,
                    audioStreamIndex = audioPosition,
                    subtitleStreamIndex = subtitleStreamIndex,
                )
            }
            currentSessionId = UUID.randomUUID().toString()

            playbackStateManager.trackPlaybackSession(
                sessionId = currentSessionId!!,
                itemId = fullItem.id,
                mediaSourceId = actualMediaSourceId,
            )
            playbackStateManager.trackCurrentItem(fullItem.id)

            coroutineScope {
                val useLocalSource = mediaSource.type == AfinitySourceType.LOCAL

                val streamUrlDeferred =
                    async(Dispatchers.IO) {
                        if (useLocalSource) {
                            mediaSource.path?.let { "file://$it" }
                        } else {
                            playbackRepository.getStreamUrl(
                                itemId = fullItem.id,
                                mediaSourceId = actualMediaSourceId,
                                audioStreamIndex = audioStreamIndex,
                                subtitleStreamIndex = null,
                                videoStreamIndex = null,
                                maxStreamingBitrate = null,
                                startTimeTicks = null,
                            )
                        }
                    }

                val segmentsJob = launch(Dispatchers.IO) { loadSegments(fullItem.id) }
                val trickplayJob = launch(Dispatchers.IO) { loadTrickplayData() }
                val reportStartJob =
                    launch(Dispatchers.IO) {
                        if (!useLocalSource) {
                            reportPlaybackStart(fullItem)
                        }
                    }

                val streamUrl = streamUrlDeferred.await()
                if (streamUrl.isNullOrBlank()) {
                    Timber.e("Stream URL is null or empty")
                    updateUiState {
                        it.copy(
                            isLoading = false,
                            showError = true,
                            errorMessage = context.getString(R.string.error_load_stream),
                        )
                    }
                    return@coroutineScope
                }

                val externalSubtitles =
                    if (useLocalSource) {
                        val itemDir = downloadRepository.getItemDownloadDirectory(fullItem.id)
                        val subtitlesDir = java.io.File(itemDir, "subtitles")
                        if (subtitlesDir.exists()) {
                            val files = subtitlesDir.listFiles()
                            files?.mapNotNull { subtitleFile ->
                                try {
                                    val extension = subtitleFile.extension
                                    val mimeType =
                                        when (extension.lowercase()) {
                                            "srt" -> MimeTypes.APPLICATION_SUBRIP
                                            "vtt" -> MimeTypes.TEXT_VTT
                                            "ass",
                                            "ssa" -> MimeTypes.TEXT_SSA
                                            else -> MimeTypes.TEXT_UNKNOWN
                                        }

                                    val language =
                                        subtitleFile.nameWithoutExtension.split("_").firstOrNull()
                                            ?: context.getString(R.string.track_unknown)

                                    MediaItem.SubtitleConfiguration.Builder(
                                            "file://${subtitleFile.absolutePath}".toUri()
                                        )
                                        .setLabel(language)
                                        .setMimeType(mimeType)
                                        .setLanguage(language)
                                        .build()
                                } catch (_: Exception) {
                                    null
                                }
                            } ?: emptyList()
                        } else {
                            emptyList()
                        }
                    } else {
                        mediaSource.mediaStreams
                            .filter { stream ->
                                stream.type == MediaStreamType.SUBTITLE && stream.isExternal
                            }
                            .mapNotNull { stream ->
                                try {
                                    val subtitleUrl =
                                        "${apiClient.baseUrl}/Videos/${fullItem.id}/${actualMediaSourceId}/Subtitles/${stream.index}/Stream.srt"
                                    MediaItem.SubtitleConfiguration.Builder(subtitleUrl.toUri())
                                        .setLabel(
                                            stream.displayTitle
                                                ?: stream.language
                                                ?: context.getString(
                                                    R.string.track_number_fmt,
                                                    stream.index,
                                                )
                                        )
                                        .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                                        .setLanguage(stream.language ?: "eng")
                                        .build()
                                } catch (_: Exception) {
                                    null
                                }
                            }
                    }

                val mediaItem =
                    MediaItem.Builder()
                        .setMediaId(fullItem.id.toString())
                        .setUri(streamUrl)
                        .setMediaMetadata(MediaMetadata.Builder().setTitle(fullItem.name).build())
                        .setSubtitleConfigurations(externalSubtitles)
                        .build()

                withContext(Dispatchers.Main) {
                    player.setMediaItems(listOf(mediaItem), 0, startPositionMs)
                    player.prepare()
                    player.play()
                    showControls()
                }

                segmentsJob.join()
                trickplayJob.join()
                reportStartJob.join()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load media")
            updateUiState {
                it.copy(
                    isLoading = false,
                    showError = true,
                    errorMessage = context.getString(R.string.error_unexpected),
                )
            }
        }
        updateCurrentTrackSelections()
    }

    private suspend fun loadLiveChannel(channelId: UUID, channelName: String, streamUrl: String) {
        stopAudiobookshelfIfPlaying()
        mpvLiveAutoHideTriggered = false

        try {
            Timber.d("Loading live channel: $channelName ($channelId)")
            Timber.d("Stream URL: $streamUrl")
            val userAgent =
                "AFinity/${com.makd.afinity.BuildConfig.VERSION_NAME} (Android; ExoPlayer)"

            if (player is MPVPlayer) {
                val mpv = player as MPVPlayer
                mpv.setOption("user-agent", userAgent)
                mpv.setOption("http-header-fields", "allow-cross-protocol-redirects: true")

                withContext(Dispatchers.Main) {
                    val mediaItem =
                        MediaItem.Builder()
                            .setMediaId(channelId.toString())
                            .setUri(streamUrl)
                            .setMediaMetadata(MediaMetadata.Builder().setTitle(channelName).build())
                            .build()

                    player.setMediaItem(mediaItem)
                    player.prepare()
                    player.play()
                    showControls()
                }
            } else if (player is ExoPlayer) {
                val dataSourceFactory =
                    androidx.media3.datasource.DefaultHttpDataSource.Factory()
                        .setUserAgent(userAgent)
                        .setAllowCrossProtocolRedirects(true)
                        .setKeepPostFor302Redirects(true)
                        .setConnectTimeoutMs(15000)
                        .setReadTimeoutMs(15000)

                val mediaItem =
                    MediaItem.Builder()
                        .setMediaId(channelId.toString())
                        .setUri(streamUrl)
                        .setMimeType(MimeTypes.APPLICATION_M3U8)
                        .setMediaMetadata(MediaMetadata.Builder().setTitle(channelName).build())
                        .build()

                val hlsMediaSource =
                    androidx.media3.exoplayer.hls.HlsMediaSource.Factory(dataSourceFactory)
                        .setAllowChunklessPreparation(true)
                        .createMediaSource(mediaItem)

                withContext(Dispatchers.Main) {
                    (player as ExoPlayer).setMediaSource(hlsMediaSource)
                    player.prepare()
                    player.play()
                    showControls()
                }
            }

            val channelItem =
                AfinityChannel(
                    id = channelId,
                    name = channelName,
                    images = AfinityImages(),
                    channelType = ChannelType.TV,
                    channelNumber = null,
                    serviceName = null,
                )

            currentItem = channelItem

            updateUiState {
                it.copy(isLoading = false, isLiveChannel = true, currentItem = channelItem)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load live channel")
            updateUiState {
                it.copy(
                    isLoading = false,
                    showError = true,
                    errorMessage = context.getString(R.string.error_load_live_channel),
                )
            }
        }
    }

    private fun loadTrickplayData() {
        val currentItem = uiState.value.currentItem ?: return

        val trickplayInfo =
            when (currentItem) {
                is AfinityEpisode -> {
                    currentItem.trickplayInfo?.values?.firstOrNull()
                }

                is com.makd.afinity.data.models.media.AfinityMovie -> {
                    currentItem.trickplayInfo?.values?.firstOrNull()
                }

                else -> {
                    if (currentItem is com.makd.afinity.data.models.media.AfinitySources) {
                        currentItem.trickplayInfo?.values?.firstOrNull()
                    } else {
                        null
                    }
                }
            }

        trickplayInfo?.let { info ->
            viewModelScope.launch {
                try {
                    withContext(Dispatchers.Default) {
                        val thumbnailsPerTile = info.tileWidth * info.tileHeight
                        val maxTileIndex =
                            kotlin.math
                                .ceil(info.thumbnailCount.toDouble() / thumbnailsPerTile)
                                .toInt()

                        val individualThumbnails = mutableListOf<ImageBitmap>()

                        for (tileIndex in 0..maxTileIndex) {
                            val imageData =
                                mediaRepository.getTrickplayData(
                                    currentItem.id,
                                    info.width,
                                    tileIndex,
                                )
                            if (imageData != null) {
                                val tileBitmap =
                                    BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
                                for (offsetY in
                                    0 until (info.height * info.tileHeight) step info.height) {
                                    for (offsetX in
                                        0 until (info.width * info.tileWidth) step info.width) {
                                        try {
                                            val thumbnail =
                                                Bitmap.createBitmap(
                                                    tileBitmap,
                                                    offsetX,
                                                    offsetY,
                                                    info.width,
                                                    info.height,
                                                )
                                            individualThumbnails.add(thumbnail.asImageBitmap())
                                            if (individualThumbnails.size >= info.thumbnailCount)
                                                break
                                        } catch (_: Exception) {
                                            Timber.w(
                                                "Failed to crop thumbnail at offset ($offsetX, $offsetY)"
                                            )
                                        }
                                    }
                                    if (individualThumbnails.size >= info.thumbnailCount) break
                                }
                            } else {
                                Timber.d("Failed to load tile $tileIndex")
                                break
                            }

                            if (individualThumbnails.size >= info.thumbnailCount) break
                        }

                        if (individualThumbnails.isNotEmpty()) {
                            currentTrickplay =
                                Trickplay(interval = info.interval, images = individualThumbnails)
                        } else {
                            Timber.d(
                                "No trickplay thumbnails could be extracted for ${currentItem.name}"
                            )
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load trickplay data")
                }
            }
        }
    }

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        super.onVideoSizeChanged(videoSize)
        if (videoSize.width > 0 && videoSize.height > 0) {
            isVideoPortrait = videoSize.height > videoSize.width
            Timber.d(
                "Video size changed: ${videoSize.width}x${videoSize.height}, isPortrait=$isVideoPortrait"
            )
            if (!isOrientationOverridden) {
                updateUiState { it.copy(resolvedOrientation = computeOrientation(isVideoPortrait)) }
            }
        }
    }

    private fun computeOrientation(portrait: Boolean): Int {
        return if (portrait) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    override fun onTracksChanged(tracks: Tracks) {
        super.onTracksChanged(tracks)
        updateCurrentTrackSelections()
    }

    fun switchToTrack(trackType: @C.TrackType Int, index: Int) {
        if (index == -1) {
            player.trackSelectionParameters =
                player.trackSelectionParameters
                    .buildUpon()
                    .clearOverridesOfType(trackType)
                    .setTrackTypeDisabled(trackType, true)
                    .build()
        } else {
            val tracksGroups =
                player.currentTracks.groups.filter { it.type == trackType && it.isSupported }

            if (index < tracksGroups.size) {
                player.trackSelectionParameters =
                    player.trackSelectionParameters
                        .buildUpon()
                        .setOverrideForType(
                            TrackSelectionOverride(tracksGroups[index].mediaTrackGroup, 0)
                        )
                        .setTrackTypeDisabled(trackType, false)
                        .build()
            }
        }
        updateCurrentTrackSelections()
    }

    private fun updateCurrentTrackSelections() {
        val currentAudioTrackIndex =
            player.currentTracks.groups
                .filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }
                .indexOfFirst { it.isSelected }
                .takeIf { it != -1 }

        val currentSubtitleTrackIndex =
            player.currentTracks.groups
                .filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }
                .indexOfFirst { it.isSelected }
                .takeIf { it != -1 }

        updateUiState {
            it.copy(
                audioStreamIndex = currentAudioTrackIndex,
                subtitleStreamIndex = currentSubtitleTrackIndex,
            )
        }
    }

    private suspend fun reportPlaybackStart(item: AfinityItem) {
        try {
            currentSessionId?.let { sessionId ->
                playbackRepository.reportPlaybackStart(
                    itemId = item.id,
                    sessionId = sessionId,
                    mediaSourceId = item.sources.firstOrNull()?.id ?: "",
                    audioStreamIndex = 0,
                    subtitleStreamIndex = null,
                    playMethod = "DirectPlay",
                    canSeek = true,
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to report playback start")
        }
    }

    private suspend fun loadSegments(itemId: UUID) {
        currentMediaSegments =
            try {
                segmentsRepository.getSegments(itemId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to load segments for item $itemId")
                emptyList()
            }

        if (currentMediaSegments.isNotEmpty()) {
            startSegmentMonitoring()
        } else {
            Timber.d("No segments found for item $itemId")
        }
    }

    private fun startSegmentMonitoring() {
        segmentCheckingJob?.cancel()
        segmentCheckingJob =
            viewModelScope.launch {
                delay(2000L)
                while (true) {
                    updateCurrentSegment()
                    delay(1000L)
                }
            }
    }

    private suspend fun updateCurrentSegment() {
        if (currentMediaSegments.isEmpty()) return

        val currentPositionMs = uiState.value.currentPosition

        val currentSegment =
            currentMediaSegments.find { segment ->
                currentPositionMs in segment.startTicks..<(segment.endTicks - 100L)
            }

        currentSegment?.let { segment ->
            val skipIntroEnabled = preferencesRepository.getSkipIntroEnabled()
            val skipOutroEnabled = preferencesRepository.getSkipOutroEnabled()

            val shouldShowSkipButton =
                when (segment.type) {
                    AfinitySegmentType.INTRO -> skipIntroEnabled
                    AfinitySegmentType.OUTRO -> skipOutroEnabled
                    else -> false
                }

            if (shouldShowSkipButton) {
                val skipButtonText = getSkipButtonText(segment)
                updateUiState {
                    it.copy(
                        currentSegment = segment,
                        skipButtonText = skipButtonText,
                        showSkipButton = true,
                    )
                }
            } else {
                updateUiState { it.copy(currentSegment = null, showSkipButton = false) }
            }
        } ?: run { updateUiState { it.copy(currentSegment = null, showSkipButton = false) } }
    }

    private fun getSkipButtonText(segment: AfinitySegment): String {
        return when (segment.type) {
            AfinitySegmentType.INTRO -> context.getString(R.string.skip_intro)
            AfinitySegmentType.OUTRO -> context.getString(R.string.skip_outro)
            AfinitySegmentType.RECAP -> context.getString(R.string.skip_recap)
            AfinitySegmentType.PREVIEW -> context.getString(R.string.skip_preview)
            AfinitySegmentType.COMMERCIAL -> context.getString(R.string.skip_commercial)
            else -> context.getString(R.string.skip_generic)
        }
    }

    fun initializePlaylist(item: AfinityItem, seasonId: UUID? = null, shuffle: Boolean = false) {
        viewModelScope.launch {
            playlistManager.initializePlaylist(item, seasonId)
            if (shuffle) {
                playlistManager.shuffleQueue()
            }
        }
    }

    fun clearPlaylist() {
        playlistManager.clearQueue()
    }

    fun setAutoplayCallback(callback: (AfinityItem) -> Unit) {
        onAutoplayNextEpisode = callback
    }

    private fun toggleControls() {
        val shouldShow = !_uiState.value.showControls
        if (shouldShow) {
            showControls()
        } else {
            hideControls()
        }
    }

    fun onSingleTap() {
        handlePlayerEvent(PlayerEvent.ToggleControls)
    }

    fun onDoubleTapSeek(isForward: Boolean) {
        val delta = if (isForward) 10000L else -10000L
        handlePlayerEvent(PlayerEvent.SeekRelative(delta))
    }

    private fun onLockToggle() {
        updateUiState { it.copy(isControlsLocked = !it.isControlsLocked) }
    }

    fun onNextEpisode() {
        viewModelScope.launch {
            playlistManager.getNextItem()?.let { nextItem ->
                playQueueItem(nextItem)
                playlistManager.next()
            }
        }
    }

    fun onPreviousEpisode() {
        viewModelScope.launch {
            playlistManager.getPreviousItem()?.let { prevItem ->
                playQueueItem(prevItem)
                playlistManager.previous()
            }
        }
    }

    fun jumpToEpisode(episodeId: UUID) {
        viewModelScope.launch {
            playlistManager.jumpToItem(episodeId)?.let { targetItem -> playQueueItem(targetItem) }
        }
    }

    fun onNextChapterOrEpisode() {
        val chapters = uiState.value.chapters
        if (chapters.isNotEmpty()) {
            val currentPos = player.currentPosition
            val nextChapter = chapters.find { it.startPosition > currentPos + 1000 }

            if (nextChapter != null) {
                handlePlayerEvent(PlayerEvent.Seek(nextChapter.startPosition))
                return
            }
        }
        onNextEpisode()
    }

    fun onPreviousChapterOrEpisode() {
        val chapters = uiState.value.chapters
        if (chapters.isNotEmpty()) {
            val currentPos = player.currentPosition
            val currentChapterIndex = chapters.indexOfLast { it.startPosition <= currentPos }

            if (currentChapterIndex != -1) {
                val currentChapter = chapters[currentChapterIndex]
                val targetPosition =
                    if (currentPos - currentChapter.startPosition > 3000) {
                        currentChapter.startPosition
                    } else if (currentChapterIndex > 0) {
                        chapters[currentChapterIndex - 1].startPosition
                    } else {
                        0L
                    }
                handlePlayerEvent(PlayerEvent.Seek(targetPosition))
                return
            }
        }
        onPreviousEpisode()
    }

    private fun playQueueItem(item: AfinityItem) {
        handlePlayerEvent(
            PlayerEvent.LoadMedia(
                item = item,
                mediaSourceId = item.sources.firstOrNull()?.id ?: "",
                audioStreamIndex = null,
                subtitleStreamIndex = null,
                startPositionMs = 0L,
            )
        )
    }

    private var brightnessHideJob: Job? = null

    fun onScreenBrightnessGesture(value: Float, isAbsolute: Boolean = false) {
        var currentLevel = _uiState.value.brightnessLevel
        if (currentLevel < 0f) currentLevel = getSystemBrightness()

        val newBrightness =
            if (isAbsolute) {
                value.coerceIn(0.0f, 1.0f)
            } else {
                (currentLevel + value * gestureConfig.brightnessStepSize).coerceIn(0.0f, 1.0f)
            }

        updateUiState { it.copy(showBrightnessIndicator = true, brightnessLevel = newBrightness) }

        brightnessHideJob?.cancel()
        brightnessHideJob =
            viewModelScope.launch {
                delay(2000)
                updateUiState { it.copy(showBrightnessIndicator = false) }
            }
    }

    private var volumeHideJob: Job? = null

    fun updateTrickplayPreview(targetTime: Long) {
        val duration = uiState.value.duration
        if (duration <= 0) return
        val safeTime = targetTime.coerceIn(0L, duration)
        handlePlayerEvent(PlayerEvent.OnSeekBarValueChange(safeTime))
    }

    private fun onSeekBarPreview(position: Long, isActive: Boolean) {
        if (!isActive) {
            updateUiState {
                it.copy(
                    showTrickplayPreview = false,
                    trickplayPreviewImage = null,
                    trickplayPreviewPosition = 0L,
                )
            }
            return
        }

        currentTrickplay?.let { trickplay ->
            if (trickplay.images.isEmpty() || trickplay.interval <= 0) return

            val rawIndex = (position / trickplay.interval).toInt()
            val index = rawIndex.coerceIn(0, trickplay.images.size - 1)

            updateUiState {
                it.copy(
                    showTrickplayPreview = true,
                    trickplayPreviewImage = trickplay.images[index],
                    trickplayPreviewPosition = position,
                )
            }
        }
    }

    fun onResume() {
        if (!_uiState.value.isCasting) {
            player.play()
        }
    }

    fun onPause() {
        if (!_uiState.value.isCasting) {
            player.pause()
        }
    }

    fun stopPlayback() {
        if (hasStoppedPlayback) return
        hasStoppedPlayback = true
        progressReportingJob?.cancel()

        val finalPosition = player.currentPosition
        val item = currentItem

        if (item != null) {
            playbackStateManager.notifyPlaybackStopped(item.id, finalPosition)
        } else {
            Timber.w("stopPlayback called but currentItem is null")
        }
    }

    private fun updateUiState(update: (PlayerUiState) -> PlayerUiState) {
        _uiState.value = update(_uiState.value)
    }

    fun showControls() {
        updateUiState {
            it.copy(showControls = true, showPlayButton = !uiState.value.isControlsLocked)
        }
        startControlsAutoHide()
    }

    fun hideControls() {
        updateUiState { it.copy(showControls = false, showPlayButton = false) }
        controlsHideJob?.cancel()
    }

    private fun startControlsAutoHide() {
        controlsHideJob?.cancel()
        controlsHideJob =
            viewModelScope.launch {
                delay(3000)
                if (
                    uiState.value.isPlaying &&
                        uiState.value.showControls &&
                        !uiState.value.isSeeking
                ) {
                    hideControls()
                } else if (uiState.value.isSeeking) {
                    startControlsAutoHide()
                }
            }
    }

    override fun onCleared() {
        super.onCleared()

        clearPlaylist()
        stopPlayback()

        progressReportingJob?.cancel()
        segmentCheckingJob?.cancel()
        controlsHideJob?.cancel()
        player.removeListener(this)
        player.release()
    }

    fun onPipModeChanged(isInPictureInPictureMode: Boolean) {
        _uiState.value = _uiState.value.copy(isInPictureInPictureMode = isInPictureInPictureMode)

        if (isInPictureInPictureMode) {
            hideControls()
        } else {
            showControls()
        }
    }

    fun enterPictureInPicture() {
        enterPictureInPicture?.invoke()
    }

    data class PlayerUiState(
        val isPlaying: Boolean = false,
        val isPaused: Boolean = false,
        val isBuffering: Boolean = false,
        val isLoading: Boolean = false,
        val currentPosition: Long = 0L,
        val duration: Long = 0L,
        val showControls: Boolean = false,
        val isFullscreen: Boolean = false,
        val isControlsLocked: Boolean = false,
        val currentSegment: AfinitySegment? = null,
        val isSeekPreviewActive: Boolean = false,
        val seekPreviewPosition: Long = 0L,
        val playbackSpeed: Float = 1f,
        val audioStreamIndex: Int? = null,
        val subtitleStreamIndex: Int? = null,
        val showPlayButton: Boolean = true,
        val showBuffering: Boolean = false,
        val showError: Boolean = false,
        val errorMessage: String? = null,
        val brightnessLevel: Float = -1.0f,
        val showTrickplayPreview: Boolean = false,
        val trickplayPreviewImage: ImageBitmap? = null,
        val trickplayPreviewPosition: Long = 0L,
        val chapters: List<AfinityChapter> = emptyList(),
        val currentItem: AfinityItem? = null,
        val showSkipButton: Boolean = false,
        val skipButtonText: String = "Skip",
        val showSeekIndicator: Boolean = false,
        val seekDirection: Int = 0,
        val showBrightnessIndicator: Boolean = false,
        val showVolumeIndicator: Boolean = false,
        val volumeLevel: Int = 50,
        val isSeeking: Boolean = false,
        val seekPosition: Long = 0L,
        val dragStartPosition: Long = 0L,
        val showRemainingTime: Boolean = false,
        val isInPictureInPictureMode: Boolean = false,
        val pipBackgroundPlay: Boolean = true,
        val isControlsVisible: Boolean = true,
        val videoZoomMode: VideoZoomMode = VideoZoomMode.FIT,
        val resolvedOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
        val logoAutoHide: Boolean = false,
        val isLiveChannel: Boolean = false,
        val isCasting: Boolean = false,
        val showCastChooser: Boolean = false,
    )
}

private data class MpvPrefsSnapshot(
    val hwDec: String,
    val videoOutput: String,
    val audioOutput: String,
    val subtitlePrefs: SubtitlePreferences,
    val preferredAudioLanguage: String,
    val preferredSubtitleLanguage: String,
)
