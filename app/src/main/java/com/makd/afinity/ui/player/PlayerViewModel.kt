package com.makd.afinity.ui.player

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
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
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.playback.PlaybackRepository
import com.makd.afinity.data.repository.segments.SegmentsRepository
import com.makd.afinity.player.mpv.MPVPlayer
import com.makd.afinity.ui.player.utils.VolumeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.MediaStreamType
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@androidx.media3.common.util.UnstableApi
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val application: Application,
    private val playbackRepository: PlaybackRepository,
    private val playbackStateManager: PlaybackStateManager,
    private val mediaRepository: MediaRepository,
    private val segmentsRepository: SegmentsRepository,
    private val preferencesRepository: PreferencesRepository,
    private val playlistManager: PlaylistManager,
    private val apiClient: ApiClient
) : ViewModel(), Player.Listener {

    lateinit var player: Player
        private set

    private var hasStoppedPlayback = false
    private var currentSessionId: String? = null
    private val volumeManager: VolumeManager by lazy { VolumeManager(context) }

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    val gestureConfig = GestureConfig()

    private var controlsHideJob: Job? = null
    private var currentMediaSegments: List<AfinitySegment> = emptyList()
    private var segmentCheckingJob: Job? = null
    private var currentItem: AfinityItem? = null
    private var currentTrickplay: Trickplay? = null

    var onAutoplayNextEpisode: ((AfinityItem) -> Unit)? = null
    val playlistState = playlistManager.playlistState

    init {
        initializePlayer()
        startPositionUpdateLoop()
    }

    private fun startPositionUpdateLoop() {
        viewModelScope.launch {
            while (true) {
                delay(100)
                if (player.isPlaying) {
                    updatePlayerState()
                }
            }
        }
    }

    private fun initializePlayer() {
        val useExoPlayer = kotlinx.coroutines.runBlocking {
            preferencesRepository.useExoPlayer.first()
        }

        player = if (useExoPlayer) {
            createExoPlayer()
        } else {
            createMPVPlayer()
        }

        player.addListener(this@PlayerViewModel)
        Timber.d("Player initialized: ${player.javaClass.simpleName}")
    }

    private fun createExoPlayer(): ExoPlayer {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val trackSelector = DefaultTrackSelector(context)
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .setTunnelingEnabled(true)
        )

        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        return ExoPlayer.Builder(context, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setTrackSelector(trackSelector)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .setPauseAtEndOfMediaItems(true)
            .build()
    }

    private fun createMPVPlayer(): MPVPlayer {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .setUsage(C.USAGE_MEDIA)
            .build()

        return MPVPlayer.Builder(application)
            .setAudioAttributes(audioAttributes, true)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .setPauseAtEndOfMediaItems(true)
            .setVideoOutput("gpu")
            .setAudioOutput("audiotrack")
            .setHwDec("mediacodec")
            .build()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        updatePlayerState()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        updatePlayerState()
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
            viewModelScope.launch {
                currentItem?.let { item ->
                    onAutoplayNextEpisode?.invoke(item)
                }
            }
        }
        updatePlayerState()
    }

    private fun updatePlayerState() {
        val position = player.currentPosition.coerceAtLeast(0)
        val duration = player.duration.coerceAtLeast(0)
        _uiState.value = _uiState.value.copy(
            isPlaying = player.isPlaying,
            isPaused = !player.isPlaying && player.playbackState == Player.STATE_READY,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            currentPosition = position,
            duration = duration
        )
    }

    fun handlePlayerEvent(event: PlayerEvent) {
        viewModelScope.launch {
            when (event) {
                is PlayerEvent.Play -> player.play()
                is PlayerEvent.Pause -> player.pause()
                is PlayerEvent.Seek -> player.seekTo(event.positionMs)
                is PlayerEvent.SeekRelative -> {
                    val newPos = (player.currentPosition + event.deltaMs).coerceIn(0, player.duration)
                    player.seekTo(newPos)
                }
                is PlayerEvent.SetVolume -> volumeManager.setVolume(event.volume)
                is PlayerEvent.SetBrightness -> { /* Handle at UI level */ }
                is PlayerEvent.SetPlaybackSpeed -> player.setPlaybackSpeed(event.speed)
                is PlayerEvent.SwitchToTrack -> switchToTrack(event.trackType, event.index)
                is PlayerEvent.ToggleControls -> toggleControls()
                is PlayerEvent.ToggleFullscreen -> { /* Handled at UI level */ }
                is PlayerEvent.LoadMedia -> loadMedia(
                    event.item,
                    event.mediaSourceId,
                    event.audioStreamIndex,
                    event.subtitleStreamIndex,
                    event.startPositionMs
                )
                is PlayerEvent.SkipSegment -> skipSegment()
                is PlayerEvent.Stop -> player.stop()
            }
        }
    }

    private suspend fun loadMedia(
        item: AfinityItem,
        mediaSourceId: String,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
        startPositionMs: Long
    ) = withContext(Dispatchers.IO) {
        try {
            currentItem = item
            updateUiState {
                it.copy(
                    currentItem = item,
                    audioStreamIndex = audioStreamIndex,
                    subtitleStreamIndex = subtitleStreamIndex
                )
            }
            currentSessionId = UUID.randomUUID().toString()

            playbackStateManager.trackCurrentItem(item.id)

            val streamUrl = playbackRepository.getStreamUrl(
                itemId = item.id,
                mediaSourceId = mediaSourceId,
                audioStreamIndex = audioStreamIndex,
                subtitleStreamIndex = null,
                videoStreamIndex = null,
                maxStreamingBitrate = null,
                startTimeTicks = null
            )

            if (streamUrl.isNullOrBlank()) {
                Timber.e("Stream URL is null or empty")
                return@withContext
            }

            val mediaSource = item.sources.firstOrNull { it.id == mediaSourceId }
            val externalSubtitles = mediaSource?.mediaStreams
                ?.filter { stream ->
                    stream.isExternal &&
                            stream.type == MediaStreamType.SUBTITLE &&
                            !stream.path.isNullOrBlank()
                }
                ?.map { stream ->
                    val subtitleUrl = if (stream.path?.startsWith("http") == true) {
                        stream.path!!
                    } else {
                        val format = when (stream.codec?.lowercase()) {
                            "subrip" -> "srt"
                            "ass" -> "ass"
                            "ssa" -> "ass"
                            "webvtt" -> "vtt"
                            "vobsub" -> "sub"
                            else -> "srt"
                        }
                        "${apiClient.baseUrl}/Videos/${item.id}/${mediaSourceId}/Subtitles/${stream.index}/Stream.${format}?api_key=${apiClient.accessToken}"
                    }

                    val mimeType = when (stream.codec?.lowercase()) {
                        "subrip" -> MimeTypes.APPLICATION_SUBRIP
                        "webvtt" -> MimeTypes.APPLICATION_SUBRIP
                        "ass", "ssa" -> MimeTypes.TEXT_SSA
                        else -> MimeTypes.TEXT_UNKNOWN
                    }

                    MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subtitleUrl))
                        .setLabel(stream.title?.ifBlank { stream.language } ?: "Unknown")
                        .setMimeType(mimeType)
                        .setLanguage(stream.language)
                        .build()
                } ?: emptyList()

            Timber.d("Built ${externalSubtitles.size} external subtitle configurations")

            val mediaItem = MediaItem.Builder()
                .setMediaId(item.id.toString())
                .setUri(streamUrl)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(item.name)
                        .build()
                )
                .setSubtitleConfigurations(externalSubtitles)
                .build()

            withContext(Dispatchers.Main) {
                player.setMediaItems(listOf(mediaItem), 0, startPositionMs)
                player.prepare()
                player.play()
            }

            reportPlaybackStart(item)
            loadSegments(item.id)

        } catch (e: Exception) {
            Timber.e(e, "Failed to load media")
        }
    }

    private fun getMimeType(codec: String): String {
        return when (codec.lowercase()) {
            "subrip", "srt" -> MimeTypes.APPLICATION_SUBRIP
            "webvtt", "vtt" -> MimeTypes.APPLICATION_SUBRIP
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            else -> MimeTypes.TEXT_UNKNOWN
        }
    }

    fun switchToTrack(trackType: @C.TrackType Int, index: Int) {
        if (index == -1) {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(trackType)
                .setTrackTypeDisabled(trackType, true)
                .build()
        } else {
            val tracksGroups = player.currentTracks.groups.filter {
                it.type == trackType && it.isSupported
            }

            if (index < tracksGroups.size) {
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .setOverrideForType(
                        TrackSelectionOverride(tracksGroups[index].mediaTrackGroup, 0)
                    )
                    .setTrackTypeDisabled(trackType, false)
                    .build()
            }
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
                    canSeek = true
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to report playback start")
        }
    }

    private suspend fun loadSegments(itemId: UUID) {
        try {
            currentMediaSegments = segmentsRepository.getSegments(itemId)
            startSegmentChecking()
        } catch (e: Exception) {
            Timber.e(e, "Failed to load segments")
            currentMediaSegments = emptyList()
        }
    }

    private fun startSegmentChecking() {
        segmentCheckingJob?.cancel()
        segmentCheckingJob = viewModelScope.launch {
            while (true) {
                delay(500)
                val currentPos = player.currentPosition
                currentMediaSegments.find { segment ->
                    currentPos >= segment.startTicks / 10000 &&
                            currentPos <= segment.endTicks / 10000
                }?.let { segment ->
                    updateUiState { it.copy(currentSegment = segment) }
                } ?: updateUiState { it.copy(currentSegment = null) }
            }
        }
    }

    fun skipSegment() {
        _uiState.value.currentSegment?.let { segment ->
            player.seekTo(segment.endTicks / 10000)
        }
    }

    fun initializePlaylist(item: AfinityItem) {
        viewModelScope.launch {
            playlistManager.initializePlaylist(item)
        }
    }

    fun setAutoplayCallback(callback: (AfinityItem) -> Unit) {
        onAutoplayNextEpisode = callback
    }

    private fun toggleControls() {
        updateUiState {
            it.copy(showControls = !it.showControls)
        }
        if (_uiState.value.showControls) {
            scheduleControlsHide()
        }
    }

    private fun scheduleControlsHide() {
        controlsHideJob?.cancel()
        controlsHideJob = viewModelScope.launch {
            delay(5000)
            updateUiState { it.copy(showControls = false) }
        }
    }

    fun onSingleTap() {
        handlePlayerEvent(PlayerEvent.ToggleControls)
    }

    fun onDoubleTapSeek(isForward: Boolean) {
        val delta = if (isForward) 10000L else -10000L
        handlePlayerEvent(PlayerEvent.SeekRelative(delta))
    }

    fun onPlayPauseClick() {
        if (player.isPlaying) {
            handlePlayerEvent(PlayerEvent.Pause)
        } else {
            handlePlayerEvent(PlayerEvent.Play)
        }
    }

    fun onSeekBarDrag(position: Long) {
        handlePlayerEvent(PlayerEvent.Seek(position))
    }

    fun onFullscreenToggle() {
        handlePlayerEvent(PlayerEvent.ToggleFullscreen)
    }

    fun onAudioTrackSelect(index: Int) {
        switchToTrack(C.TRACK_TYPE_AUDIO, index)
        updateUiState { it.copy(audioStreamIndex = if (index == -1) null else index) }
        Timber.d("Selected audio track: $index")
    }

    fun onSubtitleTrackSelect(index: Int) {
        switchToTrack(C.TRACK_TYPE_TEXT, index)
        updateUiState { it.copy(subtitleStreamIndex = if (index == -1) null else index) }
        Timber.d("Selected subtitle track: $index")
    }

    fun onPlaybackSpeedChange(speed: Float) {
        handlePlayerEvent(PlayerEvent.SetPlaybackSpeed(speed))
    }

    fun onLockToggle() {
        updateUiState { it.copy(isControlsLocked = !it.isControlsLocked) }
    }

    fun onSkipSegment() {
        skipSegment()
    }

    fun onSeekBackward() {
        handlePlayerEvent(PlayerEvent.SeekRelative(-10000L))
    }

    fun onSeekForward() {
        handlePlayerEvent(PlayerEvent.SeekRelative(10000L))
    }

    fun onNextEpisode() {
        viewModelScope.launch {
            playlistManager.getNextItem()?.let { nextItem ->
                handlePlayerEvent(
                    PlayerEvent.LoadMedia(
                        item = nextItem,
                        mediaSourceId = nextItem.sources.firstOrNull()?.id ?: "",
                        audioStreamIndex = null,
                        subtitleStreamIndex = null,
                        startPositionMs = 0L
                    )
                )
                initializePlaylist(nextItem)
            }
        }
    }

    fun onPreviousEpisode() {
        viewModelScope.launch {
            playlistManager.getPreviousItem()?.let { prevItem ->
                handlePlayerEvent(
                    PlayerEvent.LoadMedia(
                        item = prevItem,
                        mediaSourceId = prevItem.sources.firstOrNull()?.id ?: "",
                        audioStreamIndex = null,
                        subtitleStreamIndex = null,
                        startPositionMs = 0L
                    )
                )
                initializePlaylist(prevItem)
            }
        }
    }

    fun onVolumeGesture(delta: Float) {
        volumeManager.adjustVolume(delta.toInt())
    }

    fun onScreenBrightnessGesture(delta: Float) {
    }

    fun onSeekGesture(delta: Long) {
        val newPos = (player.currentPosition + delta).coerceIn(0, player.duration)
        player.seekTo(newPos)
    }

    fun onSeekBarPreview(position: Long, isActive: Boolean) {
        updateUiState {
            it.copy(
                isSeekPreviewActive = isActive,
                seekPreviewPosition = position
            )
        }
    }

    fun onResume() {
        player.play()
    }

    fun onPause() {
        player.pause()
    }

    private fun updateUiState(update: (PlayerUiState) -> PlayerUiState) {
        _uiState.value = update(_uiState.value)
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            try {
                currentItem?.let { item ->
                    currentSessionId?.let { sessionId ->
                        playbackRepository.reportPlaybackStop(
                            itemId = item.id,
                            sessionId = sessionId,
                            positionTicks = player.currentPosition * 10000,
                            mediaSourceId = item.sources.firstOrNull()?.id ?: ""
                        )
                    }
                }
                playbackStateManager.notifyPlaybackStopped()
            } catch (e: Exception) {
                Timber.e(e, "Failed to report playback stop")
            }
        }

        segmentCheckingJob?.cancel()
        controlsHideJob?.cancel()
        player.removeListener(this)
        player.release()
    }
    data class PlayerUiState(
        val isPlaying: Boolean = false,
        val isPaused: Boolean = false,
        val isBuffering: Boolean = false,
        val isLoading: Boolean = false,
        val currentPosition: Long = 0L,
        val duration: Long = 0L,
        val showControls: Boolean = true,
        val isFullscreen: Boolean = false,
        val isControlsLocked: Boolean = false,
        val currentSegment: AfinitySegment? = null,
        val isSeekPreviewActive: Boolean = false,
        val seekPreviewPosition: Long = 0L,
        val playbackSpeed: Float = 1f,
        val audioStreamIndex: Int? = null,
        val subtitleStreamIndex: Int? = null,
        val showPlayButton: Boolean = false,
        val showBuffering: Boolean = false,
        val showError: Boolean = false,
        val errorMessage: String? = null,
        val brightnessLevel: Float = 0.5f,
        val showTrickplayPreview: Boolean = false,
        val trickplayPreviewImage: androidx.compose.ui.graphics.ImageBitmap? = null,
        val trickplayPreviewPosition: Long = 0L,
        val currentItem: AfinityItem? = null,
        val showSkipButton: Boolean = false,
        val skipButtonText: String = "Skip",
        val showSeekIndicator: Boolean = false,
        val seekDirection: Int = 0,
        val showBrightnessIndicator: Boolean = false,
        val showVolumeIndicator: Boolean = false,
        val volumeLevel: Int = 50
    )
}