package com.makd.afinity.ui.player

import android.app.Application
import android.content.Context
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
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import com.makd.afinity.data.manager.PlaybackStateManager
import com.makd.afinity.data.models.media.AfinityChapter
import com.makd.afinity.data.models.media.AfinityEpisode
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
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.download.JellyfinDownloadRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.playback.PlaybackRepository
import com.makd.afinity.data.repository.segments.SegmentsRepository
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
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val application: Application,
    private val playbackRepository: PlaybackRepository,
    private val playbackStateManager: PlaybackStateManager,
    private val mediaRepository: MediaRepository,
    private val segmentsRepository: SegmentsRepository,
    private val preferencesRepository: PreferencesRepository,
    private val playlistManager: PlaylistManager,
    private val downloadRepository: JellyfinDownloadRepository,
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

    private var progressReportingJob: Job? = null
    private var currentItem: AfinityItem? = null
    private var currentTrickplay: Trickplay? = null

    private var playerView: PlayerView? = null
    private var currentZoomMode: VideoZoomMode = VideoZoomMode.FIT

    var onAutoplayNextEpisode: ((AfinityItem) -> Unit)? = null
    var enterPictureInPicture: (() -> Unit)? = null
    private val screenAspectRatio: Float by lazy {
        val metrics = context.resources.displayMetrics
        metrics.widthPixels.toFloat() / metrics.heightPixels.toFloat()
    }
    val playlistState = playlistManager.playlistState

    init {
        initializePlayer()
        startPositionUpdateLoop()
        startProgressReporting()
        initializeVideoZoomMode()
        initializeLogoAutoHide()
    }

    private fun initializeBrightness() {
        val systemBrightness = getSystemBrightness()
        _uiState.value = _uiState.value.copy(brightnessLevel = systemBrightness)
    }

    private fun initializeVideoZoomMode() {
        viewModelScope.launch {
            updateUiState { it.copy(videoZoomMode = currentZoomMode) }
        }
    }

    private fun initializeLogoAutoHide() {
        viewModelScope.launch {
            val logoAutoHide = preferencesRepository.getLogoAutoHide()
            updateUiState { it.copy(logoAutoHide = logoAutoHide) }
        }
    }

    private fun getSystemBrightness(): Float {
        return try {
            val brightnessMode = Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE
            )

            if (brightnessMode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) {
                Timber.d("System brightness is in automatic mode. Returning default brightness.")
                0.5f
            } else {
                val brightness = Settings.System.getInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS
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
                if (player.isPlaying && !_uiState.value.isSeeking) {
                    updatePlayerState()
                }
            }
        }
    }

    private fun startProgressReporting() {
        progressReportingJob?.cancel()
        progressReportingJob = viewModelScope.launch {
            while (true) {
                delay(5000L)
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
                                playMethod = "DirectPlay"
                            )
                            Timber.d("Reported progress: ${player.currentPosition}ms, paused: $isPaused")
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to report periodic progress")
                        }
                    }
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

        val mpvPlayer = MPVPlayer.Builder(application)
            .setAudioAttributes(audioAttributes, true)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .setPauseAtEndOfMediaItems(true)
            .setVideoOutput("gpu")
            .setAudioOutput("audiotrack")
            .setHwDec("mediacodec")
            .build()

        val subtitlePrefs = kotlinx.coroutines.runBlocking {
            preferencesRepository.getSubtitlePreferences()
        }

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
                    SubtitlePreferences.colorToMpvHex(subtitlePrefs.outlineColor)
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
                    SubtitlePreferences.colorToMpvHex(subtitlePrefs.outlineColor)
                )
            }

            SubtitleOutlineStyle.BACKGROUND_BOX -> {
                mpvPlayer.setOption("sub-border-style", "background-box")
                mpvPlayer.setOption(
                    "sub-back-color",
                    SubtitlePreferences.colorToMpvHex(subtitlePrefs.backgroundColor)
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

        return mpvPlayer
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        updatePlayerState()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            updateUiState { it.copy(isLoading = false) }
        }
        updatePlayerState()
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
                    showControls()
                    val newPos =
                        (player.currentPosition + event.deltaMs).coerceIn(0, player.duration)
                    player.seekTo(newPos)
                }

                is PlayerEvent.SetVolume -> volumeManager.setVolume(event.volume)
                is PlayerEvent.SetBrightness -> { /* Handle at UI level */
                }

                is PlayerEvent.SetPlaybackSpeed -> {
                    player.setPlaybackSpeed(event.speed)
                    updateUiState { it.copy(playbackSpeed = event.speed) }
                }

                is PlayerEvent.SwitchToTrack -> switchToTrack(event.trackType, event.index)
                is PlayerEvent.ToggleControls -> toggleControls()
                is PlayerEvent.ToggleLock -> onLockToggle()
                is PlayerEvent.ToggleFullscreen -> { /* Handled at UI level */
                }

                is PlayerEvent.EnterPictureInPicture -> enterPictureInPicture()
                is PlayerEvent.LoadMedia -> {
                    updateUiState { it.copy(isLoading = true) }
                    loadMedia(
                        event.item,
                        event.mediaSourceId,
                        event.audioStreamIndex,
                        event.subtitleStreamIndex,
                        event.startPositionMs
                    )
                }

                is PlayerEvent.SkipSegment -> {
                    handlePlayerEvent(PlayerEvent.Seek(event.segment.endTicks))
                    updateUiState { it.copy(currentSegment = null, showSkipButton = false) }
                }

                is PlayerEvent.Stop -> player.stop()
                is PlayerEvent.OnSeekBarDragStart -> {
                    updateUiState {
                        it.copy(
                            isSeeking = true,
                            dragStartPosition = uiState.value.currentPosition
                        )
                    }
                    onSeekBarPreview(uiState.value.currentPosition, true)
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
                            dragStartPosition = 0L
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

    fun setPlayerView(view: PlayerView) {
        playerView = view
        applyZoomMode(currentZoomMode)
    }

    private suspend fun loadMedia(
        item: AfinityItem,
        mediaSourceId: String,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
        startPositionMs: Long
    ) {
        try {
            currentItem = item
            val chapters = item.chapters
            updateUiState { it.copy(chapters = chapters) }

            val audioStreams = item.sources.firstOrNull()?.mediaStreams?.filter {
                it.type == MediaStreamType.AUDIO
            }
            val audioPosition = if (audioStreamIndex != null && audioStreams != null) {
                audioStreams.indexOfFirst { it.index == audioStreamIndex }
                    .takeIf { it >= 0 }
            } else {
                null
            }

            updateUiState {
                it.copy(
                    currentItem = item,
                    audioStreamIndex = audioPosition,
                    subtitleStreamIndex = subtitleStreamIndex
                )
            }
            currentSessionId = UUID.randomUUID().toString()

            playbackStateManager.trackPlaybackSession(
                sessionId = currentSessionId!!,
                itemId = item.id,
                mediaSourceId = mediaSourceId
            )
            playbackStateManager.trackCurrentItem(item.id)

            coroutineScope {
                val mediaSource = item.sources.firstOrNull { it.id == mediaSourceId }
                val useLocalSource =
                    mediaSource?.type == AfinitySourceType.LOCAL

                val streamUrlDeferred = async(Dispatchers.IO) {
                    if (useLocalSource) {
                        mediaSource?.path?.let { "file://$it" }
                    } else {
                        playbackRepository.getStreamUrl(
                            itemId = item.id,
                            mediaSourceId = mediaSourceId,
                            audioStreamIndex = audioStreamIndex,
                            subtitleStreamIndex = null,
                            videoStreamIndex = null,
                            maxStreamingBitrate = null,
                            startTimeTicks = null
                        )
                    }
                }

                val segmentsJob = launch(Dispatchers.IO) { loadSegments(item.id) }
                val trickplayJob = launch(Dispatchers.IO) { loadTrickplayData() }
                val reportStartJob = launch(Dispatchers.IO) {
                    if (!useLocalSource) {
                        reportPlaybackStart(item)
                    }
                }

                val streamUrl = streamUrlDeferred.await()
                if (streamUrl.isNullOrBlank()) {
                    Timber.e("Stream URL is null or empty")
                    updateUiState {
                        it.copy(
                            isLoading = false,
                            showError = true,
                            errorMessage = "Failed to load stream."
                        )
                    }
                    return@coroutineScope
                }
                val externalSubtitles = if (useLocalSource) {
                    val itemDir = downloadRepository.getItemDownloadDirectory(item.id)
                    val subtitlesDir = java.io.File(itemDir, "subtitles")
                    Timber.d("PlayerViewModel: Looking for local subtitles in: ${subtitlesDir.absolutePath}")
                    Timber.d("PlayerViewModel: Subtitles directory exists: ${subtitlesDir.exists()}")
                    if (subtitlesDir.exists()) {
                        val files = subtitlesDir.listFiles()
                        Timber.d("PlayerViewModel: Found ${files?.size ?: 0} subtitle files")
                        files?.forEach { file ->
                            Timber.d("PlayerViewModel: Subtitle file: ${file.name}")
                        }
                        files?.mapNotNull { subtitleFile ->
                            try {
                                val extension = subtitleFile.extension
                                val mimeType = when (extension.lowercase()) {
                                    "srt" -> MimeTypes.APPLICATION_SUBRIP
                                    "vtt" -> MimeTypes.TEXT_VTT
                                    "ass", "ssa" -> MimeTypes.TEXT_SSA
                                    else -> MimeTypes.TEXT_UNKNOWN
                                }

                                val language =
                                    subtitleFile.nameWithoutExtension.split("_").firstOrNull()
                                        ?: "unknown"

                                val subtitleConfig =
                                    MediaItem.SubtitleConfiguration.Builder("file://${subtitleFile.absolutePath}".toUri())
                                        .setLabel(language)
                                        .setMimeType(mimeType)
                                        .setLanguage(language)
                                        .build()
                                Timber.d("PlayerViewModel: Built subtitle config - Label: $language, MimeType: $mimeType, URI: file://${subtitleFile.absolutePath}")
                                subtitleConfig
                            } catch (e: Exception) {
                                Timber.e(
                                    e,
                                    "Failed to build subtitle config for local file: ${subtitleFile.name}"
                                )
                                null
                            }
                        } ?: emptyList()
                    } else {
                        Timber.w("PlayerViewModel: Subtitles directory does not exist")
                        emptyList()
                    }
                } else {
                    mediaSource?.mediaStreams
                        ?.filter { stream ->
                            stream.type == MediaStreamType.SUBTITLE && stream.isExternal
                        }
                        ?.mapNotNull { stream ->
                            try {
                                val subtitleUrl =
                                    "${apiClient.baseUrl}/Videos/${item.id}/${mediaSourceId}/Subtitles/${stream.index}/Stream.srt"
                                MediaItem.SubtitleConfiguration.Builder(
                                    subtitleUrl.toUri()
                                )
                                    .setLabel(
                                        stream.displayTitle ?: stream.language
                                        ?: "Track ${stream.index}"
                                    )
                                    .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                                    .setLanguage(stream.language ?: "eng")
                                    .build()
                            } catch (e: Exception) {
                                Timber.e(
                                    e,
                                    "Failed to build subtitle config for stream ${stream.index}"
                                )
                                null
                            }
                        } ?: emptyList()
                }

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
                    errorMessage = "An unexpected error occurred."
                )
            }
        }
        updateCurrentTrackSelections()
    }

    private fun getMimeType(codec: String): String {
        return when (codec.lowercase()) {
            "subrip", "srt" -> MimeTypes.APPLICATION_SUBRIP
            "webvtt", "vtt" -> MimeTypes.APPLICATION_SUBRIP
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            else -> MimeTypes.TEXT_UNKNOWN
        }
    }

    private fun loadTrickplayData() {
        val currentItem = uiState.value.currentItem ?: return

        val trickplayInfo = when (currentItem) {
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
                            kotlin.math.ceil(info.thumbnailCount.toDouble() / thumbnailsPerTile)
                                .toInt()

                        val individualThumbnails = mutableListOf<ImageBitmap>()

                        for (tileIndex in 0..maxTileIndex) {
                            val imageData = mediaRepository.getTrickplayData(
                                currentItem.id,
                                info.width,
                                tileIndex
                            )
                            if (imageData != null) {
                                val tileBitmap =
                                    BitmapFactory.decodeByteArray(imageData, 0, imageData.size)
                                for (offsetY in 0 until (info.height * info.tileHeight) step info.height) {
                                    for (offsetX in 0 until (info.width * info.tileWidth) step info.width) {
                                        try {
                                            val thumbnail = Bitmap.createBitmap(
                                                tileBitmap,
                                                offsetX,
                                                offsetY,
                                                info.width,
                                                info.height
                                            )
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

    override fun onTracksChanged(tracks: Tracks) {
        super.onTracksChanged(tracks)
        updateCurrentTrackSelections()
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
        updateCurrentTrackSelections()
    }

    private fun updateCurrentTrackSelections() {
        val currentAudioTrackIndex = player.currentTracks.groups
            .filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }
            .indexOfFirst { it.isSelected }
            .takeIf { it != -1 }

        val currentSubtitleTrackIndex = player.currentTracks.groups
            .filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }
            .indexOfFirst { it.isSelected }
            .takeIf { it != -1 }

        updateUiState {
            it.copy(
                audioStreamIndex = currentAudioTrackIndex,
                subtitleStreamIndex = currentSubtitleTrackIndex
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
                    canSeek = true
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to report playback start")
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

        val currentPositionMs = uiState.value.currentPosition

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
            AfinitySegmentType.PREVIEW -> "Skip Preview"
            AfinitySegmentType.COMMERCIAL -> "Skip Commercial"
            else -> "Skip"
        }
    }

    fun onSkipSegment(segment: AfinitySegment) {
        handlePlayerEvent(PlayerEvent.SkipSegment(segment))
    }

    fun initializePlaylist(item: AfinityItem) {
        viewModelScope.launch {
            playlistManager.initializePlaylist(item)
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
    }

    fun onSubtitleTrackSelect(index: Int) {
        switchToTrack(C.TRACK_TYPE_TEXT, index)
    }

    fun onPlaybackSpeedChange(speed: Float) {
        handlePlayerEvent(PlayerEvent.SetPlaybackSpeed(speed))
    }

    private fun onLockToggle() {
        updateUiState { it.copy(isControlsLocked = !it.isControlsLocked) }
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

    private var brightnessHideJob: Job? = null

    fun onScreenBrightnessGesture(delta: Float) {
        var currentLevel = _uiState.value.brightnessLevel

        if (currentLevel < 0f) {
            currentLevel = getSystemBrightness()
        }

        val newBrightness = (currentLevel + delta * gestureConfig.brightnessStepSize)
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

    fun onSeekGestureChange(delta: Float) {
        val duration = uiState.value.duration
        if (duration <= 0) return

        val startPosition = uiState.value.dragStartPosition
        val maxSeekMs = 1200000L
        val seekDelta = (delta * maxSeekMs).toLong()
        val newPosition = (startPosition + seekDelta).coerceIn(0L, duration)

        handlePlayerEvent(PlayerEvent.OnSeekBarValueChange(newPosition))
    }

    fun onSeekBarPreview(position: Long, isActive: Boolean) {
        if (!isActive) {
            updateUiState {
                it.copy(
                    showTrickplayPreview = false,
                    trickplayPreviewImage = null,
                    trickplayPreviewPosition = 0L
                )
            }
            return
        }

        currentTrickplay?.let { trickplay ->
            val index = (position / trickplay.interval).toInt()
                .coerceIn(0, trickplay.images.size - 1)

            updateUiState {
                it.copy(
                    showTrickplayPreview = true,
                    trickplayPreviewImage = trickplay.images[index],
                    trickplayPreviewPosition = position
                )
            }
        }
    }

    fun onResume() {
        player.play()
    }

    fun onPause() {
        player.pause()
    }

    fun stopPlayback() {
        if (hasStoppedPlayback) return
        hasStoppedPlayback = true
        progressReportingJob?.cancel()

        playbackStateManager.updatePlaybackPosition(player.currentPosition)

        playbackStateManager.notifyPlaybackStopped()
    }

    private fun updateUiState(update: (PlayerUiState) -> PlayerUiState) {
        _uiState.value = update(_uiState.value)
    }

    fun showControls() {
        updateUiState {
            it.copy(
                showControls = true,
                showPlayButton = !uiState.value.isControlsLocked
            )
        }
        startControlsAutoHide()
    }

    fun hideControls() {
        updateUiState { it.copy(showControls = false, showPlayButton = false) }
        controlsHideJob?.cancel()
    }


    private fun startControlsAutoHide() {
        controlsHideJob?.cancel()
        controlsHideJob = viewModelScope.launch {
            delay(3000)
            if (uiState.value.isPlaying && uiState.value.showControls && !uiState.value.isSeeking) {
                hideControls()
            } else if (uiState.value.isSeeking) {
                startControlsAutoHide()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()

        stopPlayback()

        progressReportingJob?.cancel()
        segmentCheckingJob?.cancel()
        controlsHideJob?.cancel()
        player.removeListener(this)
        player.release()
    }

    fun onPipModeChanged(isInPictureInPictureMode: Boolean) {
        _uiState.value = _uiState.value.copy(
            isInPictureInPictureMode = isInPictureInPictureMode
        )

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
        val isControlsVisible: Boolean = true,
        val videoZoomMode: VideoZoomMode = VideoZoomMode.FIT,
        val logoAutoHide: Boolean = false
    )
}