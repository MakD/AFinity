package com.makd.afinity.ui.player

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import com.makd.afinity.data.manager.PlaybackStateManager
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.models.player.Trickplay
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinitySegment
import com.makd.afinity.data.models.media.AfinitySegmentType
import com.makd.afinity.data.models.player.GestureConfig
import com.makd.afinity.data.models.player.PlayerEvent
import com.makd.afinity.data.models.player.PlayerState
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.player.PlayerRepository
import com.makd.afinity.data.repository.segments.SegmentsRepository
import com.makd.afinity.ui.player.utils.VolumeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject


@androidx.media3.common.util.UnstableApi
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    val playerRepository: PlayerRepository,
    private val playbackStateManager: PlaybackStateManager,
    private val mediaRepository: MediaRepository,
    private val segmentsRepository: SegmentsRepository,
    private val preferencesRepository: PreferencesRepository,
    private val playlistManager: PlaylistManager
) : ViewModel() {

    private var hasStoppedPlayback = false
    private var currentSessionId: String? = null
    private val volumeManager: VolumeManager by lazy { VolumeManager(context) }

    val playerState: StateFlow<PlayerState> = playerRepository.playerState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PlayerState()
        )

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    val gestureConfig = GestureConfig()

    private var controlsHideJob: Job? = null

    private var currentMediaSegments: List<AfinitySegment> = emptyList()
    private var segmentCheckingJob: Job? = null

    var onAutoplayNextEpisode: ((AfinityItem) -> Unit)? = null

    val playlistState = playlistManager.playlistState

    init {
        viewModelScope.launch {
            playerState.collect { state ->
                updateUiState { uiState ->
                    uiState.copy(
                        showPlayButton = uiState.showControls,
                        showBuffering = state.isBuffering,
                        showError = state.error != null,
                        errorMessage = state.error?.message
                    )
                }
            }
        }

        when (val repo = playerRepository) {
            is com.makd.afinity.data.repository.player.LibMpvPlayerRepository -> {
                repo.setOnPlaybackCompleted { completedItem ->
                    handlePlaybackCompleted(completedItem)
                }
            }
            is com.makd.afinity.data.repository.player.ExoPlayerRepository -> {
                repo.setOnPlaybackCompleted { completedItem ->
                    handlePlaybackCompleted(completedItem)
                }
            }
        }
    }

    private var currentTrickplay: Trickplay? = null

    fun handlePlayerEvent(event: PlayerEvent) {
        viewModelScope.launch {
            when (event) {
                is PlayerEvent.Play -> playerRepository.play()
                is PlayerEvent.Pause -> playerRepository.pause()
                is PlayerEvent.Seek -> playerRepository.seekTo(event.positionMs)
                is PlayerEvent.SeekRelative -> playerRepository.seekRelative(event.deltaMs)
                is PlayerEvent.SetVolume -> playerRepository.setVolume(event.volume)
                is PlayerEvent.SetBrightness -> playerRepository.setBrightness(event.brightness)
                is PlayerEvent.SetPlaybackSpeed -> playerRepository.setPlaybackSpeed(event.speed)
                is PlayerEvent.SelectAudioTrack -> playerRepository.selectAudioTrack(event.index)
                is PlayerEvent.SelectSubtitleTrack -> playerRepository.selectSubtitleTrack(event.index)
                is PlayerEvent.ToggleControls -> toggleControls()
                is PlayerEvent.ToggleFullscreen -> playerRepository.toggleFullscreen()
                is PlayerEvent.LoadMedia -> {
                    hasStoppedPlayback = false
                    currentSessionId = UUID.randomUUID().toString()
                    Timber.d("🎬 Starting new playback session: $currentSessionId")
                    playbackStateManager.trackCurrentItem(event.item.id)
                    val success = playerRepository.loadMedia(
                        item = event.item,
                        mediaSourceId = event.mediaSourceId,
                        audioStreamIndex = event.audioStreamIndex,
                        subtitleStreamIndex = event.subtitleStreamIndex,
                        startPositionMs = event.startPositionMs
                    )
                    if (success) {
                        loadTrickplayData()
                        loadSegments(event.item.id)
                        showControls()
                    } else {
                        Timber.e("Failed to load media: ${event.item.name}")
                    }
                }
                is PlayerEvent.SkipSegment -> {
                    handlePlayerEvent(PlayerEvent.Seek(event.segment.endTicks))
                    updateUiState { it.copy(currentSegment = null, showSkipButton = false) }
                }
                is PlayerEvent.Stop -> {
                    if (hasStoppedPlayback) {
                        Timber.d("Stop already processed for session: $currentSessionId - IGNORING")
                        return@launch
                    }

                    hasStoppedPlayback = true
                    Timber.d("Processing STOP for session: $currentSessionId")
                    handleStopEvent()
                }
            }
        }
    }

    private suspend fun handleStopEvent() {
        try {
            controlsHideJob?.cancel()
            segmentCheckingJob?.cancel()

            playerRepository.stop()
            playerRepository.reportPlaybackStop()

            _uiState.value = PlayerUiState()

            Timber.d("Playback stopped and reported to Jellyfin")
        } catch (e: Exception) {
            Timber.e(e, "Error during playback stop")
        }
    }

    private suspend fun loadSegments(itemId: UUID) {
        currentMediaSegments = try {
            segmentsRepository.getSegments(itemId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load segments for item $itemId")
            emptyList()
        }

        if (currentMediaSegments.isNotEmpty()) {
            Timber.d("Loaded ${currentMediaSegments.size} segments for item $itemId")
            startSegmentMonitoring()
        } else {
            Timber.d("No segments found for item $itemId")
        }
    }

    private fun startSegmentMonitoring() {
        segmentCheckingJob?.cancel()
        segmentCheckingJob = viewModelScope.launch {
            delay(2000L)
            while (true) {
                updateCurrentSegment()
                delay(1000L)
            }
        }
    }

    private suspend fun updateCurrentSegment() {
        if (currentMediaSegments.isEmpty()) return

        val currentPositionMs = playerState.value.currentPosition

        val currentSegment = currentMediaSegments.find { segment ->
            currentPositionMs in segment.startTicks..<(segment.endTicks - 100L)
        }

        currentSegment?.let { segment ->
            val skipIntroEnabled = preferencesRepository.getSkipIntroEnabled()
            val skipOutroEnabled = preferencesRepository.getSkipOutroEnabled()

            val shouldShowSkipButton = when (segment.type) {
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
                        showSkipButton = true
                    )
                }
            } else {
                updateUiState {
                    it.copy(
                        currentSegment = null,
                        showSkipButton = false
                    )
                }
            }
        } ?: run {
            updateUiState {
                it.copy(
                    currentSegment = null,
                    showSkipButton = false
                )
            }
        }
    }

    private fun getSkipButtonText(segment: AfinitySegment): String {
        return when (segment.type) {
            AfinitySegmentType.INTRO -> "Skip Intro"
            AfinitySegmentType.OUTRO -> "Skip Outro"
            AfinitySegmentType.RECAP -> "Skip Recap"
            AfinitySegmentType.COMMERCIAL -> "Skip Commercial"
            AfinitySegmentType.PREVIEW -> "Skip Preview"
            else -> "Skip"
        }
    }

    fun onSkipSegment(segment: AfinitySegment) {
        handlePlayerEvent(PlayerEvent.SkipSegment(segment))
    }

    fun onDoubleTapSeek(isForward: Boolean) {
        val seekMs = if (isForward) gestureConfig.doubleTapSeekMs else -gestureConfig.doubleTapSeekMs
        handlePlayerEvent(PlayerEvent.SeekRelative(seekMs))

        updateUiState { it.copy(showSeekIndicator = true, seekDirection = if (isForward) 1 else -1) }

        viewModelScope.launch {
            delay(1000)
            updateUiState { it.copy(showSeekIndicator = false) }
        }
    }

    private var brightnessHideJob: Job? = null

    private var seekHideJob: Job? = null

    fun onSeekGesture(delta: Float) {
        val currentPosition = playerState.value.currentPosition
        val duration = playerState.value.duration

        val maxSeekMs = 120000L
        val seekDelta = (delta * maxSeekMs).toLong()
        val newPosition = (currentPosition + seekDelta).coerceIn(0L, duration)

        handlePlayerEvent(PlayerEvent.Seek(newPosition))

        updateUiState {
            it.copy(
                showSeekIndicator = true,
                seekDirection = if (seekDelta > 0) 1 else -1
            )
        }

        seekHideJob?.cancel()
        seekHideJob = viewModelScope.launch {
            delay(1000)
            updateUiState { it.copy(showSeekIndicator = false) }
        }
    }

    fun onScreenBrightnessGesture(delta: Float) {
        val currentBrightness = _uiState.value.brightnessLevel
        val newBrightness = (currentBrightness + delta * gestureConfig.brightnessStepSize)
            .coerceIn(0.0f, 1.0f)

        updateUiState {
            it.copy(
                showBrightnessIndicator = true,
                brightnessLevel = newBrightness
            )
        }

        brightnessHideJob?.cancel()
        brightnessHideJob = viewModelScope.launch {
            delay(2000)
            updateUiState { it.copy(showBrightnessIndicator = false) }
        }
    }

    private var volumeHideJob: Job? = null

    fun onVolumeGesture(delta: Float) {
        val currentVolume = volumeManager.getCurrentVolume()

        val volumeChange = (delta * gestureConfig.volumeStepSize).toInt()
        val newVolume = (currentVolume + volumeChange).coerceIn(0, 100)

        volumeManager.setVolume(newVolume)

        Timber.d("Volume gesture: delta=$delta, current=$currentVolume, new=$newVolume")

        updateUiState {
            it.copy(
                showVolumeIndicator = true,
                volumeLevel = newVolume
            )
        }

        volumeHideJob?.cancel()
        volumeHideJob = viewModelScope.launch {
            delay(2000)
            updateUiState { it.copy(showVolumeIndicator = false) }
        }
    }

    fun onSingleTap() {
        if (playerState.value.isControlsLocked) {
            showControls()
        } else {
            if (!_uiState.value.showControls) {
                showControls()
            } else {
                hideControls()
            }
        }
    }

    fun onPlayPauseClick() {
        val isPlaying = playerState.value.isPlaying
        if (isPlaying) {
            handlePlayerEvent(PlayerEvent.Pause)
            showControls()
        } else {
            handlePlayerEvent(PlayerEvent.Play)
            showControls()
        }
    }

    fun onSeekBarDrag(positionMs: Long) {
        handlePlayerEvent(PlayerEvent.Seek(positionMs))
    }

    fun onBackPressed() {
        Timber.d("🔙 Back pressed - delegating to stop event")
        handlePlayerEvent(PlayerEvent.Stop)
    }

    fun onFullscreenToggle() {
        handlePlayerEvent(PlayerEvent.ToggleFullscreen)
    }

    fun showControls() {
        Timber.d("showControls() called")
        updateUiState { it.copy(showControls = true, showPlayButton = !playerState.value.isControlsLocked) }
        startControlsAutoHide()
    }

    fun hideControls() {
        Timber.d("hideControls() called")
        updateUiState { it.copy(showControls = false, showPlayButton = false) }
        controlsHideJob?.cancel()
    }

    private fun toggleControls() {
        val shouldShow = !_uiState.value.showControls
        if (shouldShow) {
            showControls()
        } else {
            hideControls()
        }
    }

    private fun startControlsAutoHide() {
        controlsHideJob?.cancel()
        Timber.d("Starting auto-hide timer, isPlaying: ${playerState.value.isPlaying}")
        controlsHideJob = viewModelScope.launch {
            delay(3000)
            Timber.d("Auto-hide timer expired, isPlaying: ${playerState.value.isPlaying}, showControls: ${_uiState.value.showControls}")
            if (playerState.value.isPlaying && _uiState.value.showControls) {
                Timber.d("Hiding controls now")
                hideControls()
            } else {
                Timber.d("NOT hiding controls - isPlaying: ${playerState.value.isPlaying}, showControls: ${_uiState.value.showControls}")
            }
        }
    }

    private fun updateUiState(update: (PlayerUiState) -> PlayerUiState) {
        _uiState.value = update(_uiState.value)
    }

    fun onResume() {
        playerRepository.onResume()
    }

    fun onPause() {
        playerRepository.onPause()
    }

    fun onAudioTrackSelect(streamIndex: Int?) {
        viewModelScope.launch {
            try {
                playerRepository.selectAudioTrack(streamIndex ?: 0)
            } catch (e: Exception) {
                Timber.e(e, "Failed to select audio track: $streamIndex")
            }
        }
    }

    fun onSubtitleTrackSelect(streamIndex: Int?) {
        viewModelScope.launch {
            try {
                playerRepository.selectSubtitleTrack(streamIndex)
            } catch (e: Exception) {
                Timber.e(e, "Failed to select subtitle track: $streamIndex")
            }
        }
    }

    fun onPlaybackSpeedChange(speed: Float) {
        viewModelScope.launch {
            try {
                handlePlayerEvent(PlayerEvent.SetPlaybackSpeed(speed))
            } catch (e: Exception) {
                Timber.e(e, "Failed to set playback speed: $speed")
            }
        }
    }

    fun onLockToggle() {
        viewModelScope.launch {
            try {
                playerRepository.toggleControlsLock()
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle controls lock")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        controlsHideJob?.cancel()
        brightnessHideJob?.cancel()
        volumeHideJob?.cancel()
        seekHideJob?.cancel()
        playerRepository.onDestroy()
        segmentCheckingJob?.cancel()
    }

    private fun loadTrickplayData() {
        val currentItem = playerState.value.currentItem ?: return

        val trickplayInfo = when (currentItem) {
            is com.makd.afinity.data.models.media.AfinityEpisode -> {
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
                        val maxTileIndex = kotlin.math.ceil(info.thumbnailCount.toDouble() / thumbnailsPerTile).toInt()

                        val individualThumbnails = mutableListOf<ImageBitmap>()

                        Timber.d("Loading trickplay: ${info.thumbnailCount} thumbnails from $maxTileIndex tiles (${info.tileWidth}x${info.tileHeight} per tile)")

                        for (tileIndex in 0..maxTileIndex) {
                            val imageData = mediaRepository.getTrickplayData(
                                currentItem.id,
                                info.width,
                                tileIndex
                            )

                            if (imageData != null) {
                                val tileBitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size)

                                for (offsetY in 0 until (info.height * info.tileHeight) step info.height) {
                                    for (offsetX in 0 until (info.width * info.tileWidth) step info.width) {
                                        try {
                                            val thumbnail = Bitmap.createBitmap(tileBitmap, offsetX, offsetY, info.width, info.height)
                                            individualThumbnails.add(thumbnail.asImageBitmap())

                                            if (individualThumbnails.size >= info.thumbnailCount) break
                                        } catch (e: Exception) {
                                            Timber.w("Failed to crop thumbnail at offset ($offsetX, $offsetY)")
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
                            currentTrickplay = Trickplay(
                                interval = info.interval,
                                images = individualThumbnails
                            )
                            Timber.d("Extracted ${individualThumbnails.size} individual thumbnails for ${currentItem.name}")
                        } else {
                            Timber.d("No trickplay thumbnails could be extracted for ${currentItem.name}")
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load trickplay data")
                }
            }
        }
    }

    fun onGestureSeekPreview(positionMs: Long) {
        onSeekBarPreview(positionMs, true)
    }

    fun onSeekBarPreview(position: Long, isActive: Boolean) {
        Timber.d("onSeekBarPreview called: position=$position, isActive=$isActive")

        if (!isActive) {
            Timber.d("Preview inactive - hiding")
            updateUiState { it.copy(showTrickplayPreview = false) }
            return
        }

        val trickplay = currentTrickplay
        if (trickplay == null || trickplay.images.isEmpty()) {
            Timber.d("No trickplay data available - trickplay=${trickplay}, images=${trickplay?.images?.size}")
            updateUiState { it.copy(showTrickplayPreview = false) }
            return
        }

        val duration = playerState.value.duration
        if (duration <= 0) {
            Timber.d("Duration is 0 - cannot show preview")
            return
        }

        try {
            val thumbnailIndex = (position / trickplay.interval).toInt()
                .coerceIn(0, trickplay.images.size - 1)

            val positionFloat = position.toFloat() / duration

            Timber.d("Showing trickplay preview: thumbnailIndex=$thumbnailIndex, position=$positionFloat, interval=${trickplay.interval}")

            updateUiState {
                it.copy(
                    showTrickplayPreview = true,
                    trickplayPreviewImage = trickplay.images[thumbnailIndex],
                    trickplayPreviewPosition = positionFloat
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error showing trickplay preview")
            updateUiState { it.copy(showTrickplayPreview = false) }
        }
    }

    fun onSeekBackward() {
        handlePlayerEvent(PlayerEvent.SeekRelative(-10000L))
        Timber.d("Seeking backward 10 seconds")
    }

    fun onSeekForward() {
        handlePlayerEvent(PlayerEvent.SeekRelative(30000L))
        Timber.d("Seeking forward 30 seconds")
    }

    private fun handlePlaybackCompleted(completedItem: AfinityItem) {
        viewModelScope.launch {
            try {
                val autoPlayEnabled = preferencesRepository.getAutoPlay()
                if (!autoPlayEnabled) {
                    Timber.d("Autoplay is disabled")
                    return@launch
                }

                val nextItem = playlistManager.next()
                if (nextItem != null) {
                    Timber.d("Found next item for autoplay: ${nextItem.name}")
                    onAutoplayNextEpisode?.invoke(nextItem)
                } else {
                    Timber.d("No next item found, autoplay complete")
                }

            } catch (e: Exception) {
                Timber.e(e, "Failed to handle autoplay after playback completion")
            }
        }
    }

    fun setAutoplayCallback(callback: (AfinityItem) -> Unit) {
        onAutoplayNextEpisode = callback
    }

    fun initializePlaylist(startingItem: AfinityItem) {
        viewModelScope.launch {
            try {
                Timber.d("PlayerViewModel: Initializing playlist for ${startingItem.name}")
                val success = playlistManager.initializePlaylist(startingItem)
                Timber.d("PlayerViewModel: Playlist initialization ${if (success) "succeeded" else "failed"}")
            } catch (e: Exception) {
                Timber.e(e, "PlayerViewModel: Failed to initialize playlist")
            }
        }
    }

    fun onNextEpisode() {
        viewModelScope.launch {
            try {
                val nextItem = playlistManager.next()
                if (nextItem != null) {
                    Timber.d("Manual next episode: ${nextItem.name}")
                    onAutoplayNextEpisode?.invoke(nextItem)
                } else {
                    Timber.d("No next episode available")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to go to next episode")
            }
        }
    }

    fun onPreviousEpisode() {
        viewModelScope.launch {
            try {
                val previousItem = playlistManager.previous()
                if (previousItem != null) {
                    Timber.d("Manual previous episode: ${previousItem.name}")
                    onAutoplayNextEpisode?.invoke(previousItem)
                } else {
                    Timber.d("No previous episode available")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to go to previous episode")
            }
        }
    }
}

data class PlayerUiState(
    val showControls: Boolean = false,
    val showPlayButton: Boolean = true,
    val showBuffering: Boolean = false,
    val showError: Boolean = false,
    val errorMessage: String? = null,
    val showSeekIndicator: Boolean = false,
    val seekDirection: Int = 0,
    val showBrightnessIndicator: Boolean = false,
    val brightnessLevel: Float = 0.5f,
    val showVolumeIndicator: Boolean = false,
    val volumeLevel: Int = 100,
    val screenBrightness: Float = -1f,
    val showTrickplayPreview: Boolean = false,
    val trickplayPreviewImage: androidx.compose.ui.graphics.ImageBitmap? = null,
    val trickplayPreviewPosition: Float = 0f,
    val currentSegment: AfinitySegment? = null,
    val skipButtonText: String = "",
    val showSkipButton: Boolean = false
)