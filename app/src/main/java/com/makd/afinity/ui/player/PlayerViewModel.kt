package com.makd.afinity.ui.player

import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.display.DisplayManager
import android.media.MediaCodecList
import android.net.Uri
import android.provider.Settings
import android.view.Display
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
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheKeyFactory
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.extractor.DefaultExtractorsFactory
import com.makd.afinity.BuildConfig
import com.makd.afinity.R
import com.makd.afinity.cast.CastEvent
import com.makd.afinity.cast.CastManager
import com.makd.afinity.data.manager.OfflineModeManager
import com.makd.afinity.data.manager.PlaybackStateManager
import com.makd.afinity.data.manager.RemoteMessage
import com.makd.afinity.data.manager.RemoteMessageManager
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.livetv.AfinityChannel
import com.makd.afinity.data.models.livetv.ChannelType
import com.makd.afinity.data.models.livetv.LiveTvPlaybackInfo
import com.makd.afinity.data.models.mdblist.MdbListRating
import com.makd.afinity.data.models.media.AfinityChapter
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMediaStream
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinitySegment
import com.makd.afinity.data.models.media.AfinitySegmentType
import com.makd.afinity.data.models.media.AfinitySource
import com.makd.afinity.data.models.media.AfinitySourceType
import com.makd.afinity.data.models.media.AfinitySources
import com.makd.afinity.data.models.media.AfinityTrickplayInfo
import com.makd.afinity.data.models.player.AssRenderMode
import com.makd.afinity.data.models.player.GestureConfig
import com.makd.afinity.data.models.player.MpvHdrOutput
import com.makd.afinity.data.models.player.PlaybackStats
import com.makd.afinity.data.models.player.PlayerEvent
import com.makd.afinity.data.models.player.SkipMode
import com.makd.afinity.data.models.player.StreamDecision
import com.makd.afinity.data.models.player.SubtitleOutlineStyle
import com.makd.afinity.data.models.player.SubtitlePreferences
import com.makd.afinity.data.models.player.VideoQuality
import com.makd.afinity.data.models.player.VideoZoomMode
import com.makd.afinity.data.models.server.ConnectionType
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.download.JellyfinDownloadRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.metadata.ItemRatingsLoader
import com.makd.afinity.data.repository.playback.PlaybackRepository
import com.makd.afinity.data.repository.playback.TranscodingUrl
import com.makd.afinity.data.repository.segments.SegmentsRepository
import com.makd.afinity.data.websocket.JellyfinWebSocketManager
import com.makd.afinity.player.audiobookshelf.AudiobookshelfPlayer
import com.makd.afinity.player.common.TrackMapping
import com.makd.afinity.player.common.TrackSelection
import com.makd.afinity.player.mpv.MPVPlayer
import com.makd.afinity.ui.player.utils.VolumeManager
import com.makd.afinity.util.NetworkConnectivityMonitor
import com.makd.afinity.util.formatFileSize
import com.makd.afinity.util.redactUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.AssHandlerConfig
import io.github.peerless2012.ass.media.kt.withAssMkvSupport
import io.github.peerless2012.ass.media.kt.withAssSupport
import io.github.peerless2012.ass.media.parser.AssSubtitleParserFactory
import io.github.peerless2012.ass.media.type.AssRenderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.GeneralCommandType
import org.jellyfin.sdk.model.api.MediaStreamProtocol
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaystateCommand
import org.jellyfin.sdk.model.api.SubtitleDeliveryMethod
import org.jellyfin.sdk.model.api.SubtitlePlaybackMode
import org.jellyfin.sdk.model.api.TranscodeReason
import org.jellyfin.sdk.model.api.TranscodingInfo
import timber.log.Timber
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

private const val MAX_PENDING_TRACK_ATTEMPTS = 10
private const val TICKS_PER_MILLISECOND = 10_000L
private const val REMOTE_SEEK_STEP_MS = 30_000L
private const val REMOTE_VOLUME_STEP = 10

@UnstableApi
@HiltViewModel
class PlayerViewModel
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val application: Application,
    private val exoCache: SimpleCache,
    private val playbackRepository: PlaybackRepository,
    private val playbackStateManager: PlaybackStateManager,
    val castManager: CastManager,
    private val mediaRepository: MediaRepository,
    private val itemRatingsLoader: ItemRatingsLoader,
    private val segmentsRepository: SegmentsRepository,
    private val preferencesRepository: PreferencesRepository,
    private val playlistManager: PlaylistManager,
    private val downloadRepository: JellyfinDownloadRepository,
    private val appDataRepository: AppDataRepository,
    private val apiClient: ApiClient,
    private val audiobookshelfPlayer: AudiobookshelfPlayer,
    private val musicPlaybackManager: com.makd.afinity.player.music.MusicPlaybackManager,
    private val offlineModeManager: OfflineModeManager,
    private val sessionManager: SessionManager,
    private val jellyfinWebSocketManager: JellyfinWebSocketManager,
    private val remoteMessageManager: RemoteMessageManager,
    private val networkConnectivityMonitor: NetworkConnectivityMonitor,
) : ViewModel(), Player.Listener {

    lateinit var player: Player
        private set

    private var assHandler: AssHandler? = null

    val assOverlayHandler: AssHandler?
        get() = assHandler?.takeIf { it.renderType == AssRenderType.OVERLAY_OPEN_GL }

    val isAssActive: Boolean
        get() = assHandler != null

    private var videoMediaSession: androidx.media3.session.MediaSession? = null

    var syncPlayInterceptor: SyncPlayInterceptor? = null

    private var hasStoppedPlayback = false
    private var currentSessionId: String? = null
    private var currentLivePlaybackInfo: LiveTvPlaybackInfo? = null
    private var currentStreamDecision: StreamDecision? = null
    private var sessionVideoQuality: VideoQuality? = null
    private var forceTranscodeFallback = false
    private var sessionAllowTranscoding: Boolean? = null
    private var currentTranscodingInfo: TranscodingInfo? = null
    private var httpDataSourceFactory: DefaultHttpDataSource.Factory? = null
    private val volumeManager: VolumeManager by lazy { VolumeManager(context) }

    private var isRemoteMuted: Boolean = false
    private var volumeBeforeRemoteMute: Int = 100

    private val _closePlayerEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val closePlayerEvent: SharedFlow<Unit> = _closePlayerEvent.asSharedFlow()

    private val _liveStreamFailedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val liveStreamFailedEvent: SharedFlow<Unit> = _liveStreamFailedEvent.asSharedFlow()

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    val playlistState: StateFlow<PlaylistState> = playlistManager.playlistState

    val gestureConfig = GestureConfig()

    private var exoVideoDecoder: String = "Unknown"
    private var exoDecoderName: String = ""
    private var exoDroppedFrames = 0
    private var exoNetworkBitrate = 0L

    private var controlsHideJob: Job? = null
    private var statsPollingJob: Job? = null
    private var speedBeforeLongPress: Float? = null
    private var chapterSkipGestureEnabled: Boolean = false
    private var currentMediaSegments: List<AfinitySegment> = emptyList()
    private var segmentCheckingJob: Job? = null
    private var skipButtonHideJob: Job? = null
    private var lastShownSegmentKey: String? = null

    private var progressReportingJob: Job? = null
    private var pendingMainItemOptions: MainItemPlaybackOptions? = null
    private var pendingAudioStreamIndex: Int? = null
    private var pendingSubtitleStreamIndex: Int? = null
    private var sideLoadedSubtitleUris: Map<Int, String> = emptyMap()
    private var pendingTrackResolveAttempts = 0
    private var currentItem: AfinityItem? = null
    val currentPlayingItemId: UUID?
        get() = currentItem?.id

    private var currentTrickplayInfo: AfinityTrickplayInfo? = null
    private var currentTrickplayItemId: UUID? = null
    private val trickplayTileCache =
        object : LinkedHashMap<Int, Bitmap>(4, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Bitmap>?) = size > 3
        }
    private var trickplayFetchJob: Job? = null
    private var currentZoomMode: VideoZoomMode = VideoZoomMode.FIT
    private var isVideoPortrait: Boolean = false
    private var isOrientationOverridden: Boolean = false
    private var suppressNextControlShow = false

    var enterPictureInPicture: (() -> Unit)? = null
    var updatePipParams: (() -> Unit)? = null
    private val screenAspectRatio: Float by lazy {
        val metrics = context.resources.displayMetrics
        metrics.widthPixels.toFloat() / metrics.heightPixels.toFloat()
    }

    private val SKIP_BUTTON_TIMEOUT_MS = 8000L

    init {
        viewModelScope.launch {
            Timber.d("PlayerViewModel: starting async player init")
            initializePlayer()
            Timber.d("PlayerViewModel: player ready, starting observers and loops")
            updateUiState { it.copy(isPlayerReady = true) }
            launch {
                appDataRepository.sessionCleared.collect {
                    Timber.d("Session cleared, stopping playback")
                    stopPlayback()
                    player.stop()
                    player.clearMediaItems()
                    updateUiState { PlayerUiState() }
                }
            }
            launch { observeRemotePlaystateCommands() }
            launch { observeRemoteGeneralCommands() }
            launch {
                remoteMessageManager.message.collect { message ->
                    updateUiState { it.copy(remoteMessage = message) }
                }
            }
            startPositionUpdateLoop()
            startProgressReporting()
            initializeVideoZoomMode()
            initializeLogoAutoHide()
            initializePauseScreen()
            initializeChapterSkipGesture()
            initializeBackgroundPlay()
            observeCastState()
            observeServerUrlChanges()
        }
    }

    private fun observeServerUrlChanges() {
        viewModelScope.launch {
            var previousKey: String? = null
            var previousUrl: String? = null
            sessionManager.currentSession.collect { session ->
                val newKey = session?.let { "${it.serverId}_${it.userId}" }
                val newUrl = session?.serverUrl
                if (
                    newKey != null &&
                        newKey == previousKey &&
                        previousUrl != null &&
                        newUrl != previousUrl
                ) {
                    migrateStreamToNewUrl()
                }
                previousKey = newKey
                previousUrl = newUrl
            }
        }
    }

    private suspend fun migrateStreamToNewUrl() {
        val item = currentItem ?: return
        val state = _uiState.value
        if (state.isLiveChannel || castManager.isCasting) return
        val sourceId = state.currentMediaSourceId ?: return
        val source = item.sources.firstOrNull { it.id == sourceId } ?: return
        if (source.type == AfinitySourceType.LOCAL) return

        val jfAudioIndex = state.audioStreamIndex
        val jfSubIndex = state.subtitleStreamIndex

        val position = player.currentPosition
        Timber.d("Server URL changed mid-playback, migrating stream at ${position}ms")
        suppressNextControlShow = true
        updateUiState { it.copy(isLoading = true) }
        loadMedia(
            item = item,
            mediaSourceId = sourceId,
            audioStreamIndex = jfAudioIndex,
            subtitleStreamIndex = jfSubIndex,
            startPositionMs = position,
        )
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

    private fun initializePauseScreen() {
        viewModelScope.launch {
            val pauseScreenEnabled = preferencesRepository.getPauseScreenEnabled()
            val pauseScreenDelaySeconds = preferencesRepository.getPauseScreenDelaySeconds()
            updateUiState {
                it.copy(
                    pauseScreenEnabled = pauseScreenEnabled,
                    pauseScreenDelaySeconds = pauseScreenDelaySeconds,
                )
            }
        }
    }

    private fun initializeChapterSkipGesture() {
        viewModelScope.launch {
            chapterSkipGestureEnabled = preferencesRepository.getChapterSkipGesture()
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
                        Timber.d("Cast disconnected, last position: ${event.lastPositionMs}ms")
                        if (event.lastPositionMs > 0) {
                            player.seekTo(event.lastPositionMs)
                        }
                        updateUiState { it.copy(isCasting = false) }
                        startProgressReporting()
                    }
                    is CastEvent.PlaybackStarted -> {
                        Timber.d("Cast playback started")
                    }
                    is CastEvent.PlaybackError -> {
                        Timber.e("Cast error: ${event.message}")
                        updateUiState { it.copy(isCasting = false) }
                    }
                    is CastEvent.MusicCastDisconnected -> {}
                    is CastEvent.AbsCastDisconnected -> {}
                }
            }
        }
    }

    fun startCasting(startPositionOverride: Long? = null) {
        val item = currentItem ?: return
        val mediaSourceId = item.sources.firstOrNull()?.id ?: return
        val serverBaseUrl = apiClient.baseUrl ?: return
        val startPositionMs = startPositionOverride ?: player.currentPosition
        Timber.d("startCasting: captured position=${startPositionMs}ms for ${item.name}")

        progressReportingJob?.cancel()
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
                startPositionMs = startPositionMs,
                maxBitrate = maxBitrate,
                enableHevc = enableHevc,
            )
        }
    }

    fun stopCasting() {
        castManager.disconnect()
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

    private var positionUpdateJob: Job? = null

    private fun startPositionUpdateLoop() {
        if (positionUpdateJob?.isActive == true) return
        positionUpdateJob = viewModelScope.launch {
            var lastPreloadedTileIndex = -1

            while (true) {
                delay(100)
                val isMpvLive = player is MPVPlayer && _uiState.value.isLiveChannel
                if (!player.isPlaying && !isMpvLive) {
                    updatePlayerState()
                    break
                }
                if (!_uiState.value.isSeeking) {
                    updatePlayerState()

                    val info = currentTrickplayInfo
                    val itemId = currentTrickplayItemId
                    if (info != null && itemId != null && info.interval > 0) {
                        val position = player.currentPosition
                        val thumbnailsPerTile = info.tileWidth * info.tileHeight
                        val globalIndex =
                            (position / info.interval).toInt().coerceIn(0, info.thumbnailCount - 1)
                        val currentTileIndex = globalIndex / thumbnailsPerTile

                        if (currentTileIndex != lastPreloadedTileIndex) {
                            lastPreloadedTileIndex = currentTileIndex

                            if (!trickplayTileCache.containsKey(currentTileIndex)) {
                                viewModelScope.launch(Dispatchers.IO) {
                                    try {
                                        val imageData =
                                            mediaRepository.getTrickplayData(
                                                itemId,
                                                info.width,
                                                currentTileIndex,
                                            )
                                        if (imageData != null) {
                                            val tileBitmap =
                                                BitmapFactory.decodeByteArray(
                                                    imageData,
                                                    0,
                                                    imageData.size,
                                                )
                                            if (tileBitmap != null) {
                                                trickplayTileCache[currentTileIndex] = tileBitmap
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Timber.e(e, "Failed to pre-fetch trickplay tile")
                                    }
                                }
                            }
                        }
                    }
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
                    if (_uiState.value.isPlayingIntro) {
                        return@let
                    }
                    currentSessionId?.let { sessionId ->
                        try {
                            val positionTicks = player.currentPosition * 10000
                            val isPaused = !player.isPlaying
                            playbackStateManager.updatePlaybackPosition(player.currentPosition)
                            val sourceId =
                                _uiState.value.currentMediaSourceId
                                    ?: item.sources.firstOrNull()?.id
                                    ?: ""
                            val source =
                                item.sources.firstOrNull { it.id == sourceId }
                                    ?: item.sources.firstOrNull()
                            val jfAudioIndex = _uiState.value.audioStreamIndex
                            val jfSubIndex =
                                _uiState.value.subtitleStreamIndex ?: TrackSelection.NO_SUBTITLE

                            val livePlaybackInfo = currentLivePlaybackInfo
                            playbackRepository.reportPlaybackProgress(
                                itemId = item.id,
                                sessionId = sessionId,
                                positionTicks = positionTicks,
                                isPaused = isPaused,
                                audioStreamIndex = jfAudioIndex,
                                subtitleStreamIndex = jfSubIndex,
                                playMethod =
                                    livePlaybackInfo?.playMethod
                                        ?: (currentStreamDecision?.playMethod
                                                ?: PlayMethod.DIRECT_PLAY)
                                            .serialName,
                                liveStreamId = livePlaybackInfo?.liveStreamId,
                                repeatMode = "RepeatNone",
                            )
                            Timber.d(
                                "Reported progress: ${player.currentPosition}ms, paused: $isPaused, audio: $jfAudioIndex, sub: $jfSubIndex"
                            )
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to report periodic progress")
                        }
                    }
                }
            }
        }
    }

    private suspend fun initializePlayer() {
        Timber.d("PlayerViewModel: reading useExoPlayer preference")
        val useExoPlayer = preferencesRepository.useExoPlayer.first()
        Timber.d("PlayerViewModel: useExoPlayer=$useExoPlayer, creating player")

        player =
            if (useExoPlayer) {
                createExoPlayer()
            } else {
                createMPVPlayer()
            }

        player.addListener(this@PlayerViewModel)
        videoMediaSession =
            androidx.media3.session.MediaSession.Builder(context, player)
                .setId("afinity_video")
                .build()
        Timber.d("Player initialized: ${player.javaClass.simpleName}")
    }

    private suspend fun createExoPlayer(): ExoPlayer {
        val audioAttributes =
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(C.USAGE_MEDIA)
                .build()

        val preferredAudioLang = resolvePreferredAudioLanguage()

        val trackSelector = DefaultTrackSelector(context)
        trackSelector.setParameters(
            trackSelector.buildUponParameters().setTunnelingEnabled(true).apply {
                if (preferredAudioLang.isNotEmpty()) {
                    setPreferredAudioLanguage(preferredAudioLang)
                }
                setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            }
        )

        val bufferSizeMb = preferencesRepository.getBufferSizeMb()
        val safeExoRamBufferMb = bufferSizeMb.coerceAtMost(128)

        val loadControl =
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    DefaultLoadControl.DEFAULT_MIN_BUFFER_MS,
                    Int.MAX_VALUE,
                    500,
                    1500,
                )
                .setTargetBufferBytes(safeExoRamBufferMb * 1024 * 1024)
                .setPrioritizeTimeOverSizeThresholds(false)
                .build()

        val userAgent = "AFinity/${BuildConfig.VERSION_NAME} (Android; ExoPlayer)"
        val bandwidthMeter = DefaultBandwidthMeter.getSingletonInstance(context)
        val upstreamFactory =
            DefaultHttpDataSource.Factory()
                .setUserAgent(userAgent)
                .setAllowCrossProtocolRedirects(true)
                .setTransferListener(bandwidthMeter)
        httpDataSourceFactory = upstreamFactory
        refreshStreamAuthHeader()

        val cacheKeyFactory = CacheKeyFactory { dataSpec -> streamCacheKey(dataSpec.uri) }

        val cacheDataSourceFactory =
            CacheDataSource.Factory()
                .setCache(exoCache)
                .setUpstreamDataSourceFactory(upstreamFactory)
                .setCacheKeyFactory(cacheKeyFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        val dataSourceFactory = DefaultDataSource.Factory(context, cacheDataSourceFactory)

        val assRenderMode = preferencesRepository.getAssRenderMode()
        val handler =
            when (assRenderMode) {
                AssRenderMode.OFF -> null
                AssRenderMode.CUES -> AssHandler(AssRenderType.CUES)
                AssRenderMode.OVERLAY ->
                    AssHandler(
                        AssRenderType.OVERLAY_OPEN_GL,
                        AssHandlerConfig(maxRenderPixels = 1920 * 1080),
                    )
            }
        assHandler = handler

        val baseRenderersFactory =
            DefaultRenderersFactory(context)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)

        val mediaSourceFactory: DefaultMediaSourceFactory
        val renderersFactory: RenderersFactory
        if (handler != null) {
            val assParserFactory = AssSubtitleParserFactory(handler)
            mediaSourceFactory =
                DefaultMediaSourceFactory(
                        dataSourceFactory,
                        DefaultExtractorsFactory().withAssMkvSupport(assParserFactory, handler),
                    )
                    .setSubtitleParserFactory(assParserFactory)
            renderersFactory = baseRenderersFactory.withAssSupport(handler)
        } else {
            mediaSourceFactory =
                DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)
            renderersFactory = baseRenderersFactory
        }

        return ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setBandwidthMeter(bandwidthMeter)
            .setAudioAttributes(audioAttributes, true)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .setPauseAtEndOfMediaItems(true)
            .build()
            .apply {
                handler?.init(this)
                addAnalyticsListener(
                    object : AnalyticsListener {
                        override fun onVideoDecoderInitialized(
                            eventTime: AnalyticsListener.EventTime,
                            decoderName: String,
                            initializedTimestampMs: Long,
                            initializationDurationMs: Long,
                        ) {
                            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
                            val codecInfo = codecList.codecInfos.find { it.name == decoderName }

                            exoVideoDecoder =
                                if (codecInfo?.isHardwareAccelerated == true) "H/W Dec"
                                else "S/W Dec"
                            exoDecoderName = decoderName
                        }

                        override fun onDroppedVideoFrames(
                            eventTime: AnalyticsListener.EventTime,
                            droppedFrames: Int,
                            elapsedMs: Long,
                        ) {
                            exoDroppedFrames += droppedFrames
                        }

                        override fun onBandwidthEstimate(
                            eventTime: AnalyticsListener.EventTime,
                            totalLoadTimeMs: Int,
                            totalBytesLoaded: Long,
                            bitrateEstimate: Long,
                        ) {
                            exoNetworkBitrate = bitrateEstimate
                        }
                    }
                )
            }
    }

    var mpvVideoOutputValue: String = "gpu-next"
        private set

    private suspend fun createMPVPlayer(): MPVPlayer {
        val audioAttributes =
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(C.USAGE_MEDIA)
                .build()

        val hwDec = preferencesRepository.getMpvHwDec()
        val vo = preferencesRepository.getMpvVideoOutput()
        val ao = preferencesRepository.getMpvAudioOutput()
        val subs = preferencesRepository.getSubtitlePreferences()
        val audioLang = resolvePreferredAudioLanguage()
        val subtitleLang = resolvePreferredSubtitleLanguage()
        val (mpvHwDec, mpvVideoOutput, mpvAudioOutput, subtitlePrefs, preferredAudioLang) =
            MpvPrefsSnapshot(hwDec.value, vo.value, ao.value, subs, audioLang)
        val preferredSubtitleLang = subtitleLang.ifBlank { audioLang }

        mpvVideoOutputValue = mpvVideoOutput

        val bufferSizeMb = preferencesRepository.getBufferSizeMb()
        val gpuApi = preferencesRepository.getMpvGpuApi()
        val hdrOutput = preferencesRepository.getMpvHdrOutput()
        val toneMapping = preferencesRepository.getMpvToneMapping()
        val hdrPeakDetection = preferencesRepository.getMpvHdrPeakDetection()

        val initialOptions = buildList {
            add("sub-ass-override" to "no")
            add("sub-ass-force-margins" to "yes")
            add("sub-use-margins" to "yes")
            add("sub-color" to SubtitlePreferences.colorToMpvHex(subtitlePrefs.textColor))
            add("sub-font-size" to subtitlePrefs.toMpvFontSize().toString())
            add("sub-bold" to if (subtitlePrefs.bold) "yes" else "no")
            add("sub-italic" to if (subtitlePrefs.italic) "yes" else "no")

            when (subtitlePrefs.outlineStyle) {
                SubtitleOutlineStyle.OUTLINE -> {
                    add("sub-border-style" to "outline-and-shadow")
                    add("sub-border-size" to subtitlePrefs.outlineSize.toString())
                    add(
                        "sub-border-color" to
                            SubtitlePreferences.colorToMpvHex(subtitlePrefs.outlineColor)
                    )
                    add("sub-shadow-offset" to "0")
                    add("sub-shadow-color" to "#00000000")
                }

                SubtitleOutlineStyle.DROP_SHADOW -> {
                    add("sub-border-style" to "outline-and-shadow")
                    add("sub-border-size" to "0")
                    add("sub-border-color" to "#00000000")
                    add("sub-shadow-offset" to subtitlePrefs.outlineSize.toString())
                    add(
                        "sub-shadow-color" to
                            SubtitlePreferences.colorToMpvHex(subtitlePrefs.outlineColor)
                    )
                }

                SubtitleOutlineStyle.BACKGROUND_BOX -> {
                    add("sub-border-style" to "background-box")
                    add(
                        "sub-back-color" to
                            SubtitlePreferences.colorToMpvHex(subtitlePrefs.backgroundColor)
                    )
                    add("sub-spacing" to "0.5")
                }

                else -> {
                    add("sub-border-style" to "outline-and-shadow")
                    add("sub-border-size" to "0")
                    add("sub-border-color" to "#00000000")
                    add("sub-shadow-offset" to "0")
                    add("sub-shadow-color" to "#00000000")
                }
            }

            add("sub-align-y" to subtitlePrefs.verticalPosition.mpvValue)
            add("sub-align-x" to subtitlePrefs.horizontalAlignment.mpvValue)

            TrackSelection.languageAliases(preferredAudioLang)
                .takeIf { it.isNotEmpty() }
                ?.let { add("alang" to it.joinToString(",")) }
            TrackSelection.languageAliases(preferredSubtitleLang)
                .takeIf { it.isNotEmpty() }
                ?.let { add("slang" to it.joinToString(",")) }
            add("subs-with-matching-audio" to "yes")
            add("subs-fallback" to "no")
        }

        val mpvPlayer =
            MPVPlayer.Builder(application)
                .setAudioAttributes(audioAttributes, true)
                .setSeekBackIncrementMs(10000)
                .setSeekForwardIncrementMs(10000)
                .setPauseAtEndOfMediaItems(true)
                .setVideoOutput(mpvVideoOutput)
                .setAudioOutput(mpvAudioOutput)
                .setHwDec(mpvHwDec)
                .setBufferSizeMb(bufferSizeMb)
                .setGpuApi(gpuApi.value)
                .setHdrPassthrough(hdrOutput == MpvHdrOutput.AUTO)
                .setToneMapping(toneMapping.value)
                .setHdrPeakDetection(hdrPeakDetection)
                .setInitialOptions(initialOptions)
                .build()

        return mpvPlayer
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        updatePlayerState()
    }

    override fun onPlayerError(error: PlaybackException) {
        Timber.e(error, "Player error")
        if (currentLivePlaybackInfo?.playMethod == PlayMethod.DIRECT_PLAY.serialName) {
            _liveStreamFailedEvent.tryEmit(Unit)
            return
        }
        if (retryWithTranscodeFallback(error)) return
    }

    private fun retryWithTranscodeFallback(error: PlaybackException): Boolean {
        if (forceTranscodeFallback) return false
        if (_uiState.value.isLiveChannel) return false
        if (currentStreamDecision !is StreamDecision.DirectPlay) return false

        val state = _uiState.value
        val item = currentItem ?: return false
        val sourceId = state.currentMediaSourceId ?: return false
        val source = item.sources.firstOrNull { it.id == sourceId }
        if (source?.type == AfinitySourceType.LOCAL) return false
        if (!isDecodeFailure(error)) return false

        Timber.w(
            "Direct play failed (${error.errorCodeName}); re-negotiating with transcoding forced"
        )
        forceTranscodeFallback = true
        val resumePosition = player.currentPosition.coerceAtLeast(0L)
        viewModelScope.launch {
            loadMedia(
                item = item,
                mediaSourceId = sourceId,
                audioStreamIndex = state.audioStreamIndex,
                subtitleStreamIndex = state.subtitleStreamIndex,
                startPositionMs = resumePosition,
            )
        }
        return true
    }

    private fun isDecodeFailure(error: PlaybackException): Boolean =
        error.errorCode in
            setOf(
                PlaybackException.ERROR_CODE_DECODING_FAILED,
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
                PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
            )

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            updateUiState { it.copy(isLoading = false) }
            if (_uiState.value.showControls) {
                startControlsAutoHide()
            }
            startPositionUpdateLoop()
        }
        updatePlayerState()
        updatePipParams?.invoke()
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        updatePlayerState()
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
            reportCurrentItemStopped(isEnded = true)
            playlistManager.markCurrentItemAsPlayed()
            viewModelScope.launch {
                val isIntro = _uiState.value.isPlayingIntro
                val autoPlay = preferencesRepository.getAutoPlay()
                val nextItem =
                    if ((isIntro || autoPlay) && playlistManager.canAutoAdvance()) {
                        playlistManager.next()
                    } else null
                when {
                    nextItem != null -> {
                        Timber.d("Episode ended, auto-advancing to: ${nextItem.name}")
                        playQueueItem(nextItem)
                    }
                    !_uiState.value.isLiveChannel -> {
                        Timber.d("Playback ended with no next item, closing player")
                        _closePlayerEvent.tryEmit(Unit)
                    }
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
        val isPausedState = !isActuallyPlaying && playbackState == Player.STATE_READY
        val playWhenReady = player.playWhenReady

        lastKnownPosition = position
        lastKnownDuration = duration

        val bufferedPosition = player.bufferedPosition.coerceAtLeast(0)

        _uiState.value =
            _uiState.value.copy(
                isPlaying = isActuallyPlaying,
                isPaused = isPausedState,
                isBuffering = isBuffering,
                playWhenReady = playWhenReady,
                currentPosition = position,
                bufferedPosition = bufferedPosition,
                duration = duration,
                showPlayButton =
                    if (isBuffering && playWhenReady) false else _uiState.value.showPlayButton,
            )
    }

    private suspend fun observeRemotePlaystateCommands() {
        jellyfinWebSocketManager.remotePlaystateCommands.collect { request ->
            if (!::player.isInitialized) return@collect
            when (request.command) {
                PlaystateCommand.PAUSE -> handlePlayerEvent(PlayerEvent.Pause)
                PlaystateCommand.UNPAUSE -> handlePlayerEvent(PlayerEvent.Play)
                PlaystateCommand.PLAY_PAUSE ->
                    handlePlayerEvent(if (player.isPlaying) PlayerEvent.Pause else PlayerEvent.Play)
                PlaystateCommand.STOP -> handlePlayerEvent(PlayerEvent.Stop)
                PlaystateCommand.SEEK ->
                    request.seekPositionTicks?.let {
                        handlePlayerEvent(PlayerEvent.Seek(it / TICKS_PER_MILLISECOND))
                    }
                PlaystateCommand.REWIND ->
                    handlePlayerEvent(PlayerEvent.SeekRelative(-REMOTE_SEEK_STEP_MS))
                PlaystateCommand.FAST_FORWARD ->
                    handlePlayerEvent(PlayerEvent.SeekRelative(REMOTE_SEEK_STEP_MS))
                PlaystateCommand.NEXT_TRACK -> onNextEpisode()
                PlaystateCommand.PREVIOUS_TRACK -> onPreviousEpisode()
            }
        }
    }

    private suspend fun observeRemoteGeneralCommands() {
        jellyfinWebSocketManager.remoteGeneralCommands.collect { command ->
            if (!::player.isInitialized) return@collect
            when (command.name) {
                GeneralCommandType.SET_VOLUME ->
                    command.arguments["Volume"]?.toIntOrNull()?.let {
                        handlePlayerEvent(PlayerEvent.SetVolume(it.coerceIn(0, 100)))
                    }
                GeneralCommandType.VOLUME_UP ->
                    handlePlayerEvent(
                        PlayerEvent.SetVolume(
                            (volumeManager.getCurrentVolume() + REMOTE_VOLUME_STEP).coerceIn(0, 100)
                        )
                    )
                GeneralCommandType.VOLUME_DOWN ->
                    handlePlayerEvent(
                        PlayerEvent.SetVolume(
                            (volumeManager.getCurrentVolume() - REMOTE_VOLUME_STEP).coerceIn(0, 100)
                        )
                    )
                GeneralCommandType.MUTE -> applyRemoteMute(true)
                GeneralCommandType.UNMUTE -> applyRemoteMute(false)
                GeneralCommandType.TOGGLE_MUTE -> applyRemoteMute(!isRemoteMuted)
                GeneralCommandType.SET_AUDIO_STREAM_INDEX ->
                    command.arguments["Index"]?.toIntOrNull()?.let {
                        handlePlayerEvent(PlayerEvent.SwitchToTrack(C.TRACK_TYPE_AUDIO, it))
                    }
                GeneralCommandType.SET_SUBTITLE_STREAM_INDEX ->
                    command.arguments["Index"]?.toIntOrNull()?.let {
                        handlePlayerEvent(PlayerEvent.SwitchToTrack(C.TRACK_TYPE_TEXT, it))
                    }
                GeneralCommandType.DISPLAY_MESSAGE -> Unit
                else -> Timber.d("Ignoring unsupported remote command ${command.name}")
            }
        }
    }

    private fun applyRemoteMute(muted: Boolean) {
        if (muted) {
            volumeBeforeRemoteMute = volumeManager.getCurrentVolume()
            isRemoteMuted = true
            handlePlayerEvent(PlayerEvent.SetVolume(0))
        } else {
            isRemoteMuted = false
            handlePlayerEvent(PlayerEvent.SetVolume(volumeBeforeRemoteMute.coerceIn(1, 100)))
        }
    }

    fun handlePlayerEvent(event: PlayerEvent) {
        viewModelScope.launch {
            if (syncPlayInterceptor?.handle(event) == true) return@launch
            when (event) {
                is PlayerEvent.Play -> player.play()
                is PlayerEvent.Pause -> player.pause()
                is PlayerEvent.Seek -> player.seekTo(event.positionMs)
                is PlayerEvent.SeekRelative -> {
                    showControls()
                    val duration = player.duration
                    val target = player.currentPosition.coerceAtLeast(0) + event.deltaMs
                    val newPos =
                        if (duration > 0) target.coerceIn(0, duration) else target.coerceAtLeast(0)
                    player.seekTo(newPos)
                }

                is PlayerEvent.SetVolume -> {
                    volumeManager.setVolume(event.volume)

                    updateUiState {
                        it.copy(showVolumeIndicator = true, volumeLevel = event.volume)
                    }

                    volumeHideJob?.cancel()
                    volumeHideJob = viewModelScope.launch {
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

                is PlayerEvent.SwitchToTrack -> {
                    switchToTrack(event.trackType, event.index, userInitiated = true)
                    if (event.trackType == C.TRACK_TYPE_TEXT) {
                        updateUiState { it.copy(subtitleUserSelected = true) }
                    } else if (event.trackType == C.TRACK_TYPE_AUDIO && event.index >= 0) {
                        autoUpdateSubtitleForAudio(event.index)
                    }
                }
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
                    loadLiveChannel(
                        event.channelId,
                        event.channelName,
                        event.streamUrl,
                        event.playbackInfo,
                    )
                }

                is PlayerEvent.SkipSegment -> {
                    skipButtonHideJob?.cancel()

                    updateUiState { it.copy(currentSegment = null, showSkipButton = false) }

                    if (event.segment.type == AfinitySegmentType.OUTRO) {
                        viewModelScope.launch {
                            val nextItem =
                                if (playlistManager.canAutoAdvance()) {
                                    playlistManager.getNextItem()
                                } else null
                            if (nextItem != null) {
                                val originalVolume = getInternalVolume()
                                val steps = 10
                                val stepDelay = 150L / steps

                                for (i in steps downTo 0) {
                                    val progress = i.toFloat() / steps
                                    setInternalVolume(originalVolume * progress)
                                    delay(stepDelay)
                                }

                                playlistManager.markCurrentItemAsPlayed()
                                onNextEpisode()
                                setInternalVolume(originalVolume)
                            } else {
                                executeSegmentSeek(event.segment.endTicks)
                            }
                        }
                    } else {
                        executeSegmentSeek(event.segment.endTicks)
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
                    val seekable = _uiState.value.duration > 0
                    val finalPos = if (seekable) event.positionMs else player.currentPosition
                    if (seekable) player.seekTo(finalPos)
                    updateUiState {
                        it.copy(
                            isSeeking = false,
                            showTrickplayPreview = false,
                            trickplayPreviewImage = null,
                            trickplayPreviewPosition = 0L,
                            currentPosition = finalPos.coerceAtLeast(0),
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

                is PlayerEvent.ToggleVersionPicker -> {
                    updateUiState { it.copy(showVersionPicker = !it.showVersionPicker) }
                }

                is PlayerEvent.SwitchVersion -> {
                    val item = currentItem ?: return@launch
                    if (event.mediaSourceId == _uiState.value.currentMediaSourceId) {
                        updateUiState { it.copy(showVersionPicker = false) }
                        return@launch
                    }
                    val resumePosition = player.currentPosition
                    updateUiState { it.copy(isLoading = true, showVersionPicker = false) }
                    loadMedia(
                        item = item,
                        mediaSourceId = event.mediaSourceId,
                        audioStreamIndex = null,
                        subtitleStreamIndex = null,
                        startPositionMs = resumePosition,
                    )
                }

                is PlayerEvent.TogglePlaybackStats -> {
                    val willShow = !_uiState.value.showPlaybackStats
                    updateUiState { it.copy(showPlaybackStats = willShow) }
                    if (willShow) {
                        startStatsPolling()
                    } else {
                        statsPollingJob?.cancel()
                    }
                }

                is PlayerEvent.SelectVideoQuality -> {
                    if (event.quality == _uiState.value.videoQuality) return@launch
                    sessionVideoQuality = event.quality
                    forceTranscodeFallback = false
                    reloadAtCurrentPosition()
                }

                is PlayerEvent.PlayAnywayWithTranscoding -> {
                    val item = currentItem ?: return@launch
                    val sourceId = _uiState.value.currentMediaSourceId ?: return@launch
                    sessionAllowTranscoding = true
                    updateUiState {
                        it.copy(
                            showError = false,
                            errorMessage = null,
                            canPlayAnywayWithTranscoding = false,
                            isLoading = true,
                        )
                    }
                    loadMedia(
                        item = item,
                        mediaSourceId = sourceId,
                        audioStreamIndex = _uiState.value.audioStreamIndex,
                        subtitleStreamIndex = _uiState.value.subtitleStreamIndex,
                        startPositionMs = 0L,
                    )
                }

                is PlayerEvent.RenegotiateTracks -> {
                    reloadAtCurrentPosition(
                        audioStreamIndex = event.audioStreamIndex,
                        subtitleStreamIndex = event.subtitleStreamIndex,
                    )
                }
            }
        }
    }

    fun executeScheduledPlay() {
        player.play()
    }

    fun executeScheduledPause() {
        player.pause()
    }

    fun executeScheduledSeek(positionMs: Long) {
        player.seekTo(positionMs)
    }

    private fun startStatsPolling() {
        statsPollingJob?.cancel()
        statsPollingJob = viewModelScope.launch {
            var tick = 0
            while (true) {
                if (_uiState.value.showPlaybackStats) {
                    if (_uiState.value.playMethod == PlayMethod.TRANSCODE) {
                        if (tick % SERVER_STATS_INTERVAL_TICKS == 0) {
                            currentTranscodingInfo = playbackRepository.getTranscodingInfo()
                        }
                    } else {
                        currentTranscodingInfo = null
                    }
                    updateUiState { it.copy(playbackStats = gatherPlaybackStats()) }
                    tick++
                } else {
                    tick = 0
                    currentTranscodingInfo = null
                }
                delay(1000L)
            }
        }
    }

    private fun gatherPlaybackStats(): PlaybackStats {
        val state = _uiState.value
        val currentSource =
            state.currentItem?.sources?.firstOrNull { it.id == state.currentMediaSourceId }
        val isLocal = currentSource?.type == AfinitySourceType.LOCAL
        val videoStream =
            currentSource?.mediaStreams?.firstOrNull { it.type == MediaStreamType.VIDEO }
        val videoRange =
            videoStream?.videoDoViTitle
                ?: videoStream?.videoRangeType?.name?.replace('_', ' ')
                ?: ""
        val container =
            currentSource
                ?.takeIf { it.size > 0 }
                ?.let { "${it.container?.uppercase() ?: "?"} • ${formatSize(it.size)}" } ?: ""
        val playMethod = currentPlayMethod()
        val transcode = transcodeStatsFields()
        val connection = connectionLabel()
        val subtitleTrack = subtitleTrackLabel(currentSource)
        val displayRefresh = displayRefreshLabel()

        return when (val currentPlayer = player) {
            is ExoPlayer -> {
                val videoFormat = currentPlayer.videoFormat
                val audioFormat = currentPlayer.audioFormat

                val bufferSeconds =
                    ((currentPlayer.bufferedPosition - currentPlayer.currentPosition) / 1000L)
                        .coerceAtLeast(0)
                val bitrateMbps = (videoFormat?.bitrate ?: 0) / 1_000_000f
                val fps = videoFormat?.frameRate?.takeIf { it > 0f }
                val frameRate =
                    when {
                        fps != null && displayRefresh.isNotBlank() ->
                            String.format(Locale.US, "%.3f fps @ %s", fps, displayRefresh)
                        fps != null -> String.format(Locale.US, "%.3f fps", fps)
                        else -> ""
                    }

                val durationMs = currentPlayer.duration
                val videoBitrate =
                    when {
                        bitrateMbps > 0 -> String.format(Locale.US, "%.1f Mbps", bitrateMbps)
                        currentSource != null && currentSource.size > 0 && durationMs > 0 ->
                            context.getString(
                                R.string.playback_stats_value_bitrate_container_fmt,
                                String.format(
                                    Locale.US,
                                    "%.1f",
                                    currentSource.size * 8f / (durationMs / 1000f) / 1_000_000f,
                                ),
                            )
                        else -> "Unknown"
                    }

                val networkSpeed =
                    if (!isLocal && exoNetworkBitrate > 0)
                        String.format(Locale.US, "%.1f Mbps", exoNetworkBitrate / 1_000_000f)
                    else ""

                val cached =
                    if (!isLocal && currentSource != null && currentSource.size > 0) {
                        val uri = currentPlayer.currentMediaItem?.localConfiguration?.uri
                        val cachedBytes =
                            if (uri != null)
                                exoCache.getCachedBytes(streamCacheKey(uri), 0, currentSource.size)
                            else 0L
                        if (cachedBytes > 0)
                            "${formatSize(cachedBytes)} / ${formatSize(currentSource.size)}"
                        else ""
                    } else ""

                val colorInfo =
                    videoFormat
                        ?.colorInfo
                        ?.let { ci ->
                            listOfNotNull(
                                    when (ci.colorSpace) {
                                        C.COLOR_SPACE_BT709 -> "BT.709"
                                        C.COLOR_SPACE_BT601 -> "BT.601"
                                        C.COLOR_SPACE_BT2020 -> "BT.2020"
                                        else -> null
                                    },
                                    when (ci.colorTransfer) {
                                        C.COLOR_TRANSFER_SDR -> "SDR"
                                        C.COLOR_TRANSFER_ST2084 -> "PQ"
                                        C.COLOR_TRANSFER_HLG -> "HLG"
                                        else -> null
                                    },
                                )
                                .joinToString(" • ")
                        }
                        ?.takeIf { it.isNotBlank() } ?: ""

                val transfer = videoFormat?.colorInfo?.colorTransfer
                val isSourceHdr =
                    transfer == C.COLOR_TRANSFER_ST2084 || transfer == C.COLOR_TRANSFER_HLG
                val exoVideoRange =
                    effectiveVideoRange(
                        videoRange,
                        isSourceHdr,
                        if (isSourceHdr) isDisplayHdr() else null,
                    )

                PlaybackStats(
                    playerType = "ExoPlayer",
                    playMethod = playMethod,
                    connection = connection,
                    decoderName = exoDecoderName,
                    networkSpeed = networkSpeed,
                    cached = cached,
                    videoRange = exoVideoRange,
                    colorInfo = colorInfo,
                    frameRate = frameRate,
                    container = container,
                    subtitleTrack = subtitleTrack,
                    audioBitrate =
                        audioFormat?.bitrate?.takeIf { it > 0 }?.let { "${it / 1000} kbps" } ?: "",
                    videoResolution = "${videoFormat?.width ?: 0}x${videoFormat?.height ?: 0}",
                    videoCodec = PlaybackStats.friendlyCodecName(videoFormat?.sampleMimeType),
                    audioCodec = PlaybackStats.friendlyCodecName(audioFormat?.sampleMimeType),
                    audioChannels = audioFormat?.channelCount ?: 0,
                    audioSampleRate = audioFormat?.sampleRate ?: 0,
                    droppedFrames = exoDroppedFrames,
                    hwDec = exoVideoDecoder,
                    bufferHealth =
                        context.resources.getQuantityString(
                            R.plurals.playback_stats_value_seconds_fmt,
                            bufferSeconds.toInt(),
                            bufferSeconds,
                        ),
                    videoBitrate = videoBitrate,
                    transcodeOutput = transcode.output,
                    transcodeVideo = transcode.video,
                    transcodeAudio = transcode.audio,
                    transcodeSpeed = transcode.speed,
                    transcodeHardware = transcode.hardware,
                    transcodeReasons = transcode.reasons,
                )
            }
            is MPVPlayer -> {
                val mpv = currentPlayer.mpv

                val bufferSeconds = mpv.getPropertyInt("demuxer-cache-duration") ?: 0
                val bitrateBps = mpv.getPropertyInt("video-bitrate") ?: 0
                val bitrateMbps = bitrateBps / 1_000_000f
                val fps =
                    (mpv.getPropertyDouble("estimated-vf-fps")
                            ?: mpv.getPropertyDouble("container-fps"))
                        ?.takeIf { it > 0.0 }
                val frameRate =
                    when {
                        fps != null && displayRefresh.isNotBlank() ->
                            String.format(Locale.US, "%.3f fps @ %s", fps, displayRefresh)
                        fps != null -> String.format(Locale.US, "%.3f fps", fps)
                        else -> ""
                    }

                val hwdecCurrent = mpv.getPropertyString("hwdec-current")
                val decoderName =
                    if (
                        hwdecCurrent.isNullOrBlank() ||
                            hwdecCurrent == "no" ||
                            hwdecCurrent == "none"
                    )
                        "ffmpeg (software)"
                    else hwdecCurrent

                val networkSpeed =
                    if (!isLocal) {
                        mpv.getPropertyInt("cache-speed")
                            ?.takeIf { it > 0 }
                            ?.let { String.format(Locale.US, "%.1f Mbps", it * 8f / 1_000_000f) }
                            ?: ""
                    } else ""

                val colorInfo =
                    listOfNotNull(
                            mpv.getPropertyString("video-params/pixelformat"),
                            mpv.getPropertyString("video-params/colormatrix"),
                            mpv.getPropertyString("video-params/gamma"),
                        )
                        .joinToString(" • ")

                val sourceGamma = mpv.getPropertyString("video-params/gamma")
                val targetGamma = mpv.getPropertyString("video-target-params/gamma")
                val isSourceHdr = sourceGamma == "pq" || sourceGamma == "hlg"
                val mpvVideoRange =
                    effectiveVideoRange(
                        videoRange,
                        isSourceHdr,
                        targetGamma?.let { it == "pq" || it == "hlg" },
                    )

                PlaybackStats(
                    playerType = "MPV",
                    playMethod = playMethod,
                    videoOutput = mpvVideoOutputValue,
                    connection = connection,
                    decoderName = decoderName,
                    avSync =
                        mpv.getPropertyDouble("avsync")?.let {
                            String.format(Locale.US, "%+.3f s", it)
                        } ?: "",
                    networkSpeed = networkSpeed,
                    videoRange = mpvVideoRange,
                    colorInfo = colorInfo,
                    frameRate = frameRate,
                    container = container,
                    subtitleTrack = subtitleTrack,
                    audioBitrate =
                        mpv.getPropertyInt("audio-bitrate")
                            ?.takeIf { it > 0 }
                            ?.let { "${it / 1000} kbps" } ?: "",
                    videoResolution =
                        "${mpv.getPropertyInt("width") ?: 0}x${mpv.getPropertyInt("height") ?: 0}",
                    videoCodec = mpv.getPropertyString("video-codec")?.uppercase() ?: "UNKNOWN",
                    audioCodec = mpv.getPropertyString("audio-codec")?.uppercase() ?: "UNKNOWN",
                    audioChannels = mpv.getPropertyInt("audio-params/channel-count") ?: 0,
                    audioSampleRate = mpv.getPropertyInt("audio-params/samplerate") ?: 0,
                    droppedFrames = mpv.getPropertyInt("frame-drop-count") ?: 0,
                    hwDec =
                        if ((hwdecCurrent ?: "no").contains("mediacodec")) "H/W Dec" else "S/W Dec",
                    bufferHealth =
                        context.resources.getQuantityString(
                            R.plurals.playback_stats_value_seconds_fmt,
                            bufferSeconds.toInt(),
                            bufferSeconds,
                        ),
                    videoBitrate =
                        if (bitrateMbps > 0) String.format(Locale.US, "%.1f Mbps", bitrateMbps)
                        else "Unknown",
                    transcodeOutput = transcode.output,
                    transcodeVideo = transcode.video,
                    transcodeAudio = transcode.audio,
                    transcodeSpeed = transcode.speed,
                    transcodeHardware = transcode.hardware,
                    transcodeReasons = transcode.reasons,
                )
            }
            else -> PlaybackStats()
        }
    }

    private data class TranscodeStatsFields(
        val output: String = "",
        val video: String = "",
        val audio: String = "",
        val speed: String = "",
        val hardware: String = "",
        val reasons: List<String> = emptyList(),
    )

    private fun transcodeStatsFields(): TranscodeStatsFields {
        if (_uiState.value.playMethod != PlayMethod.TRANSCODE) return TranscodeStatsFields()
        val info = currentTranscodingInfo ?: return TranscodeStatsFields()
        val width = info.width ?: 0
        val height = info.height ?: 0
        val output = buildString {
            if (width > 0 && height > 0) append("${width}x${height}")
            info.bitrate?.takeIf { it > 0 }?.let {
                if (isNotEmpty()) append(" • ")
                append(String.format(Locale.US, "%.1f Mbps", it / 1_000_000.0))
            }
        }
        val contentFps = (player as? ExoPlayer)?.videoFormat?.frameRate?.takeIf { it > 0f }
        val speed =
            info.framerate?.takeIf { it > 0f }?.let { encoderFps ->
                if (contentFps != null && contentFps > 0) {
                    String.format(Locale.US, "%.1fx realtime", encoderFps / contentFps)
                } else {
                    String.format(Locale.US, "%.0f fps", encoderFps)
                }
            } ?: ""
        val directLabel = context.getString(R.string.playback_stats_value_stream_copy)
        return TranscodeStatsFields(
            output = output,
            video =
                if (info.isVideoDirect) directLabel
                else info.videoCodec?.uppercase().orEmpty(),
            audio =
                if (info.isAudioDirect) directLabel
                else
                    listOfNotNull(
                            info.audioCodec?.uppercase(),
                            info.audioChannels?.takeIf { it > 0 }?.let { "${it}ch" },
                        )
                        .joinToString(" "),
            speed = speed,
            hardware = info.hardwareAccelerationType?.serialName.orEmpty(),
            reasons = info.transcodeReasons.mapNotNull { transcodeReasonLabel(it) }.distinct(),
        )
    }

    private fun streamCacheKey(uri: Uri): String {
        if (isTranscodedStream(uri)) return uri.toString()
        val mediaSourceId = uri.getQueryParameter("mediaSourceId")
        return if (mediaSourceId != null) {
            buildString {
                append(uri.path)
                append('|')
                append(mediaSourceId)
                uri.getQueryParameter("tag")?.let {
                    append('|')
                    append(it)
                }
            }
        } else {
            uri.toString()
        }
    }

    private fun refreshStreamAuthHeader() {
        val factory = httpDataSourceFactory ?: return
        val token =
            sessionManager.getCurrentApiClient()?.accessToken?.takeIf { it.isNotBlank() }
                ?: apiClient.accessToken?.takeIf { it.isNotBlank() }
        if (token == null) {
            Timber.w("No access token available for stream requests")
            return
        }
        factory.setDefaultRequestProperties(
            mapOf("Authorization" to "MediaBrowser Token=\"$token\"")
        )
    }

    private fun isTranscodedStream(uri: Uri): Boolean {
        val path = uri.path?.lowercase() ?: return false
        return path.endsWith(".m3u8") || path.contains("/hls1/") || path.contains("/hls/")
    }

    private fun connectionLabel(): String =
        when (offlineModeManager.connectionType.value) {
            ConnectionType.LOCAL -> "LAN"
            ConnectionType.TAILSCALE -> "Tailscale"
            ConnectionType.VPN -> "VPN"
            ConnectionType.REMOTE -> "WAN"
            ConnectionType.OFFLINE -> "Offline"
        }

    private fun subtitleTrackLabel(currentSource: AfinitySource?): String {
        val index = _uiState.value.subtitleStreamIndex
        if (index == null || index < 0) return context.getString(R.string.track_none)
        val stream =
            currentSource
                ?.mediaStreams
                ?.filter { it.type == MediaStreamType.SUBTITLE }
                ?.getOrNull(index) ?: return ""
        return buildString {
            append(stream.language.ifEmpty { stream.title }.uppercase())
            if (stream.codec.isNotBlank()) append(" • ${stream.codec.uppercase()}")
            if (stream.isExternal) append(" • EXT")
        }
    }

    private fun hdrPlaybackSuffix(isSourceHdr: Boolean, isOutputHdr: Boolean?): String =
        when {
            !isSourceHdr || isOutputHdr == null -> ""
            isOutputHdr -> context.getString(R.string.playback_stats_value_hdr_output)
            else -> context.getString(R.string.playback_stats_value_hdr_tonemapped)
        }

    private fun effectiveVideoRange(
        metadataRange: String,
        isSourceHdr: Boolean,
        isOutputHdr: Boolean?,
    ): String {
        val base = metadataRange.ifBlank { if (isSourceHdr) "HDR" else "" }
        val suffix = hdrPlaybackSuffix(isSourceHdr, isOutputHdr)
        return when {
            base.isBlank() -> suffix
            suffix.isBlank() -> base
            else -> "$base • $suffix"
        }
    }

    private fun isDisplayHdr(): Boolean? =
        try {
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.isHdr
        } catch (e: Exception) {
            null
        }

    private fun displayRefreshLabel(): String =
        try {
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            val rate = displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate ?: 0f
            if (rate > 0f) String.format(Locale.US, "%.0f Hz", rate) else ""
        } catch (e: Exception) {
            ""
        }

    private fun formatSize(bytes: Long): String = formatFileSize(context, bytes)

    private fun currentPlayMethod(): String {
        val state = _uiState.value
        val isLocal =
            state.currentItem?.sources?.firstOrNull { it.id == state.currentMediaSourceId }?.type ==
                AfinitySourceType.LOCAL
        return when {
            state.isLiveChannel -> context.getString(R.string.playback_stats_value_live)
            isLocal -> context.getString(R.string.playback_stats_value_direct_play_local)
            state.playMethod == PlayMethod.TRANSCODE ->
                context.getString(R.string.playback_stats_value_transcoding)
            state.playMethod == PlayMethod.DIRECT_PLAY ->
                context.getString(R.string.playback_stats_value_direct_play)
            else -> context.getString(R.string.playback_stats_value_direct_streaming)
        }
    }

    private suspend fun diagnoseDirectPlayFailure(
        itemId: UUID,
        mediaSourceId: String,
    ): List<TranscodeReason> {
        val probe =
            playbackRepository.getPlaybackInfo(
                itemId = itemId,
                quality = resolveVideoQuality(),
                mediaSourceId = mediaSourceId,
                allowTranscoding = true,
            ) ?: return emptyList()
        val source =
            probe.mediaSources?.firstOrNull { it.id == mediaSourceId }
                ?: probe.mediaSources?.firstOrNull()
        val url = source?.transcodingUrl ?: return emptyList()
        return TranscodingUrl.transcodeReasons(url)
    }

    private fun directPlayBlockedMessage(reasons: List<TranscodeReason>): String {
        val detail =
            reasons
                .mapNotNull { transcodeReasonLabel(it) }
                .distinct()
                .takeIf { it.isNotEmpty() }
                ?.joinToString(", ")
        return if (detail == null) {
            context.getString(R.string.error_direct_play_blocked)
        } else {
            context.getString(R.string.error_direct_play_blocked_fmt, detail)
        }
    }

    private fun transcodeReasonLabel(reason: TranscodeReason): String? {
        val resId =
            when (reason) {
                TranscodeReason.AUDIO_CODEC_NOT_SUPPORTED ->
                    R.string.player_transcode_reason_audio_codec
                TranscodeReason.VIDEO_CODEC_NOT_SUPPORTED ->
                    R.string.player_transcode_reason_video_codec
                TranscodeReason.CONTAINER_NOT_SUPPORTED ->
                    R.string.player_transcode_reason_container
                TranscodeReason.SUBTITLE_CODEC_NOT_SUPPORTED ->
                    R.string.player_transcode_reason_subtitle_codec
                TranscodeReason.CONTAINER_BITRATE_EXCEEDS_LIMIT ->
                    R.string.player_transcode_reason_bitrate_limit
                TranscodeReason.VIDEO_RESOLUTION_NOT_SUPPORTED ->
                    R.string.player_transcode_reason_resolution
                TranscodeReason.VIDEO_RANGE_TYPE_NOT_SUPPORTED ->
                    R.string.player_transcode_reason_video_range
                TranscodeReason.VIDEO_PROFILE_NOT_SUPPORTED ->
                    R.string.player_transcode_reason_video_profile
                TranscodeReason.VIDEO_LEVEL_NOT_SUPPORTED ->
                    R.string.player_transcode_reason_video_level
                TranscodeReason.AUDIO_CHANNELS_NOT_SUPPORTED ->
                    R.string.player_transcode_reason_audio_channels
                TranscodeReason.VIDEO_BIT_DEPTH_NOT_SUPPORTED ->
                    R.string.player_transcode_reason_bit_depth
                TranscodeReason.VIDEO_FRAMERATE_NOT_SUPPORTED ->
                    R.string.player_transcode_reason_framerate
                else -> null
            }
        return resId?.let { context.getString(it) }
    }

    private suspend fun reloadAtCurrentPosition(
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
    ) {
        val state = _uiState.value
        val item = currentItem ?: return
        val sourceId = state.currentMediaSourceId ?: return
        val resumePosition = player.currentPosition.coerceAtLeast(0L)
        updateUiState { it.copy(isLoading = true) }
        reportCurrentItemStopped(isEnded = false)
        loadMedia(
            item = item,
            mediaSourceId = sourceId,
            audioStreamIndex = audioStreamIndex ?: state.audioStreamIndex,
            subtitleStreamIndex = subtitleStreamIndex ?: state.subtitleStreamIndex,
            startPositionMs = resumePosition,
        )
    }

    private fun serverSubtitleStreamIndex(
        selectedIndex: Int?,
        subtitleStreams: List<AfinityMediaStream>,
        clientRendered: Set<Int>,
    ): Int {
        if (selectedIndex == null || selectedIndex == TrackSelection.NO_SUBTITLE) {
            return TrackSelection.NO_SUBTITLE
        }
        if (selectedIndex in clientRendered) return TrackSelection.NO_SUBTITLE
        val stream =
            subtitleStreams.firstOrNull { it.index == selectedIndex }
                ?: return TrackSelection.NO_SUBTITLE
        return if (stream.isExternal) TrackSelection.NO_SUBTITLE else selectedIndex
    }

    private suspend fun resolveVideoQuality(): VideoQuality {
        sessionVideoQuality?.let {
            return it
        }
        val bitrate =
            if (networkConnectivityMonitor.isOnWifi()) {
                preferencesRepository.getVideoQualityWifi()
            } else {
                preferencesRepository.getVideoQualityCellular()
            }
        return VideoQuality.fromBitrate(bitrate)
    }

    private fun applyZoomMode(mode: VideoZoomMode, saveAsCurrent: Boolean = true) {
        if (player is MPVPlayer) {
            applyMPVZoomMode(mode)
        }

        if (saveAsCurrent) {
            currentZoomMode = mode
        }
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

    private fun stopAudiobookshelfIfPlaying() {
        if (audiobookshelfPlayer.isPlaying()) {
            Timber.d("Stopping Audiobookshelf playback before starting Jellyfin playback")
            audiobookshelfPlayer.pause()
            audiobookshelfPlayer.closeSession()
        }
        if (musicPlaybackManager.state.value.currentTrack != null) {
            Timber.d("Stopping Music playback before starting Jellyfin playback")
            context.startService(
                android.content
                    .Intent(context, com.makd.afinity.player.AudioService::class.java)
                    .setAction(com.makd.afinity.player.AudioService.ACTION_STOP)
            )
        }
    }

    private fun subtitleMimeType(codecOrExtension: String): String? =
        when (codecOrExtension.lowercase()) {
            "subrip",
            "srt" -> MimeTypes.APPLICATION_SUBRIP
            "vtt",
            "webvtt" -> MimeTypes.TEXT_VTT
            "ass",
            "ssa" -> MimeTypes.TEXT_SSA
            "ttml" -> MimeTypes.APPLICATION_TTML
            else -> null
        }

    private fun subtitleExtension(codec: String): String =
        when (codec.lowercase()) {
            "vtt",
            "webvtt" -> "vtt"
            "ass" -> "ass"
            "ssa" -> "ssa"
            "ttml" -> "ttml"
            else -> "srt"
        }

    private suspend fun loadMedia(
        item: AfinityItem,
        mediaSourceId: String,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
        startPositionMs: Long,
    ) {
        val shouldShowControls = !suppressNextControlShow
        suppressNextControlShow = false
        stopAudiobookshelfIfPlaying()
        try {
            val previousSourceId = _uiState.value.currentMediaSourceId
            val previousSource = currentItem?.sources?.firstOrNull { it.id == previousSourceId }

            val fullItem: AfinityItem = item

            if (currentItem?.id != fullItem.id) {
                forceTranscodeFallback = false
                sessionAllowTranscoding = null
            }
            currentItem = fullItem

            val chapters = fullItem.chapters
            updateUiState {
                it.copy(chapters = chapters, baseUrl = apiClient.baseUrl ?: it.baseUrl)
            }

            val finalMediaSourceId =
                if (mediaSourceId.isBlank() && fullItem.sources.isNotEmpty()) {
                    findBestMatchingSource(previousSource, fullItem.sources)?.id ?: ""
                } else {
                    mediaSourceId
                }

            val mediaSource =
                fullItem.sources.firstOrNull {
                    if (finalMediaSourceId.isBlank()) true else it.id == finalMediaSourceId
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

            refreshStreamAuthHeader()

            val quality = resolveVideoQuality()
            val allowTranscoding =
                sessionAllowTranscoding ?: !preferencesRepository.getNeverTranscode()
            val playbackInfo =
                playbackRepository.getPlaybackInfo(
                    itemId = fullItem.id,
                    quality = quality,
                    mediaSourceId = actualMediaSourceId,
                    enableDirectPlay = !forceTranscodeFallback,
                    allowTranscoding = allowTranscoding,
                )
            val negotiatedSource =
                playbackInfo?.mediaSources?.firstOrNull { it.id == actualMediaSourceId }
                    ?: playbackInfo?.mediaSources?.firstOrNull()

            val negotiatedSubtitleDelivery =
                negotiatedSource
                    ?.mediaStreams
                    .orEmpty()
                    .filter { it.type == MediaStreamType.SUBTITLE }
                    .mapNotNull { stream ->
                        stream.deliveryMethod?.let { stream.index to it }
                    }
                    .toMap()

            val clientRenderedSubtitles =
                negotiatedSubtitleDelivery
                    .filterValues { it == SubtitleDeliveryMethod.EXTERNAL }
                    .keys
                    .toSet()

            val serverBurnedSubtitle =
                negotiatedSubtitleDelivery
                    .entries
                    .firstOrNull { it.value == SubtitleDeliveryMethod.ENCODE }
                    ?.key

            val serverSavedAudioIndex = negotiatedSource?.defaultAudioStreamIndex
            val serverSavedSubtitleIndex = negotiatedSource?.defaultSubtitleStreamIndex
            val audioStreams = mediaSource.mediaStreams.filter { it.type == MediaStreamType.AUDIO }
            val subtitleStreams =
                mediaSource.mediaStreams.filter { it.type == MediaStreamType.SUBTITLE }

            val isResuming = startPositionMs > 0

            val trackSelection =
                TrackSelection.select(
                    audioStreams = audioStreams,
                    subtitleStreams = subtitleStreams,
                    subtitleMode = resolveSubtitlePlaybackMode(),
                    preferredAudioLanguage = resolvePreferredAudioLanguage(),
                    preferredSubtitleLanguage = resolvePreferredSubtitleLanguage(),
                    preferHearingImpaired = preferencesRepository.getPreferSdhSubtitles(),
                    requestedAudioStreamIndex =
                        audioStreamIndex ?: serverSavedAudioIndex.takeIf { isResuming },
                    requestedSubtitleStreamIndex =
                        subtitleStreamIndex ?: serverSavedSubtitleIndex.takeIf { isResuming },
                    serverDefaultAudioStreamIndex = serverSavedAudioIndex,
                    serverDefaultSubtitleStreamIndex = serverSavedSubtitleIndex,
                    playDefaultAudioTrack = resolvePlayDefaultAudioTrack(),
                )

            val audioPosition = trackSelection.audioPosition
            val subtitlePosition = trackSelection.subtitlePosition

            val targetAudioStreamIndex = audioPosition?.let { audioStreams.getOrNull(it)?.index }
            val targetSubtitleStreamIndex =
                if (subtitlePosition == TrackSelection.NO_SUBTITLE) TrackSelection.NO_SUBTITLE
                else subtitleStreams.getOrNull(subtitlePosition)?.index

            pendingAudioStreamIndex = targetAudioStreamIndex
            pendingSubtitleStreamIndex = targetSubtitleStreamIndex
            pendingTrackResolveAttempts = 0

            updateUiState {
                it.copy(
                    currentItem = fullItem,
                    audioStreamIndex = targetAudioStreamIndex,
                    subtitleStreamIndex = targetSubtitleStreamIndex,
                    subtitleUserSelected = false,
                    availableSources = fullItem.sources,
                    currentMediaSourceId = actualMediaSourceId,
                    videoQuality = quality,
                    availableQualities =
                        if (mediaSource.type == AfinitySourceType.LOCAL) {
                            emptyList()
                        } else {
                            VideoQuality.optionsFor(
                                sourceBitrate = mediaSource.bitrate?.toInt(),
                                sourceWidth = videoStream?.width,
                            )
                        },
                    mdbRatings = emptyList(),
                    omdbAwards = null,
                )
            }
            loadItemRatings(fullItem)
            currentSessionId = playbackInfo?.playSessionId ?: UUID.randomUUID().toString()
            currentLivePlaybackInfo = null
            Timber.d(
                "Playback session ID: $currentSessionId (from server: ${playbackInfo?.playSessionId != null})"
            )
            hasStoppedPlayback = false
            exoDroppedFrames = 0
            playbackStateManager.trackPlaybackSession(
                sessionId = currentSessionId!!,
                itemId = fullItem.id,
                mediaSourceId = actualMediaSourceId,
            )
            playbackStateManager.trackCurrentItem(fullItem.id)
            coroutineScope {
                val useLocalSource = mediaSource.type == AfinitySourceType.LOCAL
                val streamDecision =
                    when {
                        useLocalSource ->
                            mediaSource.path?.let { StreamDecision.DirectPlay("file://$it") }
                        negotiatedSource != null ->
                            playbackRepository.resolveStream(
                                itemId = fullItem.id,
                                source = negotiatedSource,
                                audioStreamIndex = targetAudioStreamIndex,
                                subtitleStreamIndex =
                                    serverSubtitleStreamIndex(
                                        targetSubtitleStreamIndex,
                                        subtitleStreams,
                                        clientRenderedSubtitles,
                                    ),
                                playSessionId = currentSessionId,
                            )
                        else ->
                            playbackRepository
                                .getStreamUrl(
                                    itemId = fullItem.id,
                                    mediaSourceId = actualMediaSourceId,
                                    audioStreamIndex = targetAudioStreamIndex,
                                    subtitleStreamIndex = targetSubtitleStreamIndex,
                                    playSessionId = currentSessionId,
                                )
                                ?.let { StreamDecision.DirectPlay(it) }
                    }
                currentStreamDecision = streamDecision
                currentTranscodingInfo = null
                updateUiState {
                    it.copy(
                        playMethod = streamDecision?.playMethod,
                        transcodeReasons = streamDecision?.transcodeReasons.orEmpty(),
                        burnedInSubtitleIndex =
                            serverBurnedSubtitle ?: streamDecision?.burnedInSubtitleIndex,
                        clientRenderedSubtitles =
                            if (streamDecision is StreamDecision.DirectPlay) emptySet()
                            else clientRenderedSubtitles,
                    )
                }
                val streamUrl = streamDecision?.url

                if (streamUrl.isNullOrBlank()) {
                    val blockedByPreference = !allowTranscoding && !useLocalSource
                    if (blockedByPreference) {
                        val reasons = diagnoseDirectPlayFailure(fullItem.id, actualMediaSourceId)
                        Timber.w("Direct play not possible and transcoding is disabled: $reasons")
                        updateUiState {
                            it.copy(
                                isLoading = false,
                                showError = true,
                                errorMessage = directPlayBlockedMessage(reasons),
                                canPlayAnywayWithTranscoding = true,
                            )
                        }
                    } else {
                        Timber.e("Stream URL is null or empty")
                        updateUiState {
                            it.copy(
                                isLoading = false,
                                showError = true,
                                errorMessage = context.getString(R.string.error_load_stream),
                            )
                        }
                    }
                    return@coroutineScope
                }

                viewModelScope.launch(Dispatchers.IO) { loadSegments(fullItem.id) }
                viewModelScope.launch(Dispatchers.IO) { loadTrickplayData() }
                if (!useLocalSource)
                    viewModelScope.launch(Dispatchers.IO) { reportPlaybackStart(fullItem) }

                val externalSubtitles =
                    if (useLocalSource) {
                        val itemDir = downloadRepository.getItemDownloadDirectory(fullItem.id)
                        val subtitlesDir = java.io.File(itemDir, "subtitles")
                        if (subtitlesDir.exists()) {
                            val files = subtitlesDir.listFiles()
                            files?.mapIndexedNotNull { subtitleIndex, subtitleFile ->
                                try {
                                    val mimeType =
                                        subtitleMimeType(subtitleFile.extension)
                                            ?: MimeTypes.TEXT_UNKNOWN

                                    val nameParts = subtitleFile.nameWithoutExtension.split("_")
                                    val streamIndex =
                                        nameParts.getOrNull(1)?.toIntOrNull() ?: subtitleIndex
                                    val rawCode = nameParts.firstOrNull()
                                    val language =
                                        rawCode.toLocalizedLanguageName()
                                            ?: context.getString(R.string.track_unknown)

                                    MediaItem.SubtitleConfiguration.Builder(
                                            "file://${subtitleFile.absolutePath}".toUri()
                                        )
                                        .setId(TrackMapping.sideLoadedId(streamIndex))
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
                        val containerDropsSubtitles =
                            streamDecision !is StreamDecision.DirectPlay

                        val negotiatedDeliveryUrls =
                            negotiatedSource
                                ?.mediaStreams
                                .orEmpty()
                                .mapNotNull { stream ->
                                    stream.deliveryUrl
                                        ?.takeIf { it.isNotBlank() }
                                        ?.let { stream.index to it }
                                }
                                .toMap()

                        mediaSource.mediaStreams
                            .filter { stream ->
                                stream.type == MediaStreamType.SUBTITLE &&
                                    (stream.isExternal ||
                                        (containerDropsSubtitles &&
                                            stream.index in clientRenderedSubtitles))
                            }
                            .mapNotNull { stream ->
                                try {
                                    val extension = subtitleExtension(stream.codec)
                                    val mimeType =
                                        subtitleMimeType(stream.codec)
                                            ?: MimeTypes.APPLICATION_SUBRIP
                                    val subtitleUrl =
                                        negotiatedDeliveryUrls[stream.index]?.let {
                                            "${apiClient.baseUrl}$it"
                                        }
                                            ?: "${apiClient.baseUrl}/Videos/${fullItem.id}/${actualMediaSourceId}/Subtitles/${stream.index}/Stream.$extension"
                                    val langCode = stream.language ?: "eng"
                                    val localizedLang = langCode.toLocalizedLanguageName()
                                    val finalLabel =
                                        if (
                                            !stream.displayTitle.isNullOrBlank() &&
                                                !stream.displayTitle.equals(
                                                    langCode,
                                                    ignoreCase = true,
                                                )
                                        ) {
                                            if (
                                                localizedLang != null &&
                                                    !stream.displayTitle.contains(
                                                        localizedLang,
                                                        ignoreCase = true,
                                                    )
                                            ) {
                                                "$localizedLang - ${stream.displayTitle}"
                                            } else {
                                                stream.displayTitle
                                            }
                                        } else {
                                            localizedLang
                                        }
                                            ?: context.getString(
                                                R.string.track_number_fmt,
                                                stream.index,
                                            )

                                    MediaItem.SubtitleConfiguration.Builder(subtitleUrl.toUri())
                                        .setId(TrackMapping.sideLoadedId(stream.index))
                                        .setLabel(finalLabel)
                                        .setMimeType(mimeType)
                                        .setLanguage(stream.language ?: "eng")
                                        .build()
                                } catch (_: Exception) {
                                    null
                                }
                            }
                    }

                sideLoadedSubtitleUris =
                    externalSubtitles
                        .mapNotNull { config ->
                            config.id?.toIntOrNull()?.let { id ->
                                (id - TrackMapping.EXTERNAL_SUBTITLE_ID_BASE) to
                                    config.uri.toString()
                            }
                        }
                        .toMap()
                updateUiState { it.copy(sideLoadedSubtitleUris = sideLoadedSubtitleUris) }

                val mediaItem =
                    MediaItem.Builder()
                        .setMediaId(fullItem.id.toString())
                        .setUri(streamUrl)
                        .apply {
                            if (streamDecision?.protocol == MediaStreamProtocol.HLS) {
                                setMimeType(MimeTypes.APPLICATION_M3U8)
                            }
                        }
                        .setMediaMetadata(MediaMetadata.Builder().setTitle(fullItem.name).build())
                        .setSubtitleConfigurations(externalSubtitles)
                        .build()

                withContext(Dispatchers.Main) {
                    player.setMediaItems(listOf(mediaItem), 0, startPositionMs)
                    player.prepare()
                    if (castManager.isCasting) {
                        startCasting(startPositionOverride = startPositionMs)
                    } else {
                        player.play()
                    }
                    if (shouldShowControls) {
                        showControls()
                    }
                }

                if (fullItem is AfinityMovie || fullItem is AfinityEpisode) {
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            Timber.d(
                                "[MultiPart] Checking additional parts for '${fullItem.name}' id=${fullItem.id}"
                            )
                            val parts = mediaRepository.getAdditionalParts(fullItem.id)
                            Timber.d(
                                "[MultiPart] getAdditionalParts returned ${parts.size} item(s): ${parts.map { "'${it.name}' (${it.id})" }}"
                            )
                            if (parts.isNotEmpty()) {
                                playlistManager.insertAfterCurrent(parts)
                                Timber.d(
                                    "[MultiPart] Inserted ${parts.size} parts into queue after '${fullItem.name}'"
                                )
                            }
                        } catch (e: Exception) {
                            Timber.e(
                                e,
                                "[MultiPart] Exception fetching additional parts for: ${fullItem.id}",
                            )
                        }
                    }
                }
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

    private suspend fun loadLiveChannel(
        channelId: UUID,
        channelName: String,
        streamUrl: String,
        playbackInfo: LiveTvPlaybackInfo,
    ) {
        stopAudiobookshelfIfPlaying()
        mpvLiveAutoHideTriggered = false
        currentLivePlaybackInfo = playbackInfo
        currentSessionId = playbackInfo.playSessionId

        try {
            Timber.d("Loading live channel: $channelName ($channelId)")
            Timber.d("Stream URL: ${redactUrl(streamUrl)}")
            val userAgent = "AFinity/${BuildConfig.VERSION_NAME} (Android; ExoPlayer)"

            if (player is MPVPlayer) {
                val mpv = player as MPVPlayer
                mpv.setOption("user-agent", userAgent)
                mpv.setOption("http-header-fields", "allow-cross-protocol-redirects: true")
                mpv.setOption("profile", "low-latency")
                mpv.setOption("cache", "yes")
                mpv.setOption("cache-secs", "2")
                mpv.setOption("demuxer-max-bytes", "32M")
                mpv.setOption("demuxer-max-back-bytes", "16M")
                mpv.setOption("demuxer-lavf-analyzeduration", "0.5")
                mpv.setOption("demuxer-lavf-probesize", "32768")

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
                    DefaultHttpDataSource.Factory()
                        .setUserAgent(userAgent)
                        .setAllowCrossProtocolRedirects(true)
                        .setKeepPostFor302Redirects(true)
                        .setConnectTimeoutMs(15000)
                        .setReadTimeoutMs(15000)

                val mediaItem =
                    MediaItem.Builder()
                        .setMediaId(channelId.toString())
                        .setUri(streamUrl)
                        .apply {
                            if (playbackInfo.isHls) {
                                setMimeType(MimeTypes.APPLICATION_M3U8)
                            }
                        }
                        .setMediaMetadata(MediaMetadata.Builder().setTitle(channelName).build())
                        .setLiveConfiguration(
                            MediaItem.LiveConfiguration.Builder()
                                .setTargetOffsetMs(3000)
                                .setMaxPlaybackSpeed(1.02f)
                                .build()
                        )
                        .build()

                withContext(Dispatchers.Main) {
                    if (playbackInfo.isHls) {
                        val hlsMediaSource =
                            HlsMediaSource.Factory(dataSourceFactory)
                                .setAllowChunklessPreparation(true)
                                .createMediaSource(mediaItem)
                        (player as ExoPlayer).setMediaSource(hlsMediaSource)
                    } else {
                        val progressiveSource =
                            ProgressiveMediaSource.Factory(dataSourceFactory)
                                .createMediaSource(mediaItem)
                        (player as ExoPlayer).setMediaSource(progressiveSource)
                    }
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
            hasStoppedPlayback = false
            exoDroppedFrames = 0
            playbackStateManager.trackPlaybackSession(
                sessionId = playbackInfo.playSessionId,
                itemId = channelId,
                mediaSourceId = playbackInfo.mediaSourceId,
                liveStreamId = playbackInfo.liveStreamId,
            )
            playbackStateManager.trackCurrentItem(channelId)
            startPositionUpdateLoop()
            viewModelScope.launch(Dispatchers.IO) {
                playbackRepository.reportPlaybackStart(
                    itemId = channelId,
                    sessionId = playbackInfo.playSessionId,
                    mediaSourceId = playbackInfo.mediaSourceId,
                    playMethod = playbackInfo.playMethod,
                    liveStreamId = playbackInfo.liveStreamId,
                    canSeek = false,
                )
            }

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
        val item = uiState.value.currentItem ?: return
        currentTrickplayInfo =
            when (item) {
                is AfinityEpisode -> item.trickplayInfo?.values?.firstOrNull()
                is AfinityMovie -> item.trickplayInfo?.values?.firstOrNull()
                else ->
                    if (item is AfinitySources) item.trickplayInfo?.values?.firstOrNull() else null
            }
        currentTrickplayItemId = item.id
        trickplayTileCache.clear()
        trickplayFetchJob?.cancel()
    }

    private fun loadItemRatings(item: AfinityItem) {
        viewModelScope.launch {
            val ratings = runCatching { itemRatingsLoader.load(item) }.getOrNull() ?: return@launch
            if (currentItem?.id != item.id) return@launch
            updateUiState {
                it.copy(mdbRatings = ratings.mdbRatings, omdbAwards = ratings.omdbAwards)
            }
        }
    }

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        super.onVideoSizeChanged(videoSize)
        if (videoSize.width > 0 && videoSize.height > 0) {
            isVideoPortrait = videoSize.height > videoSize.width
            val aspectRatio = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
            Timber.d(
                "Video size changed: ${videoSize.width}x${videoSize.height}, isPortrait=$isVideoPortrait"
            )
            updateUiState {
                it.copy(
                    videoAspectRatio = aspectRatio,
                    outputVideoWidth = videoSize.width,
                    outputVideoHeight = videoSize.height,
                )
            }
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
        applyPendingTrackSelections()
        if (pendingAudioStreamIndex == null && pendingSubtitleStreamIndex == null) {
            updateCurrentTrackSelections()
        }
    }

    private fun supportedTrackGroups(trackType: @C.TrackType Int): List<Tracks.Group> =
        player.currentTracks.groups.filter { it.type == trackType && it.isSupported }

    private fun currentMediaStreams(type: MediaStreamType): List<AfinityMediaStream> {
        val state = _uiState.value
        val item = state.currentItem ?: return emptyList()
        val source =
            item.sources.firstOrNull { it.id == state.currentMediaSourceId }
                ?: item.sources.firstOrNull()
        return source?.mediaStreams?.filter { it.type == type }.orEmpty()
    }

    private fun orderedAudioGroups(): List<Tracks.Group> =
        player.currentTracks.groups
            .filter { it.type == C.TRACK_TYPE_AUDIO }
            .sortedBy { TrackMapping.audioSortKey(it.mediaTrackGroup.getFormat(0).id) }

    private fun textGroups(): List<Tracks.Group> =
        player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }

    private fun audioGroupForStreamIndex(streamIndex: Int): Tracks.Group? {
        val ordinal =
            TrackMapping.embeddedOrdinal(currentMediaStreams(MediaStreamType.AUDIO), streamIndex)
        return ordinal?.let { orderedAudioGroups().getOrNull(it) }?.takeIf { it.isSupported }
    }

    private fun subtitleGroupForStreamIndex(streamIndex: Int): Tracks.Group? {
        val sideLoadedId = TrackMapping.sideLoadedId(streamIndex)
        val sideLoadedUri = sideLoadedSubtitleUris[streamIndex]
        val groups = textGroups()
        groups
            .firstOrNull { group ->
                val format = group.mediaTrackGroup.getFormat(0)
                format.id == sideLoadedId ||
                    (sideLoadedUri != null && format.customData == sideLoadedUri)
            }
            ?.let {
                return it.takeIf { group -> group.isSupported }
            }
        val ordinal =
            TrackMapping.embeddedOrdinal(currentMediaStreams(MediaStreamType.SUBTITLE), streamIndex)
        return ordinal?.let { groups.getOrNull(it) }?.takeIf { it.isSupported }
    }

    private fun streamIndexForAudioGroup(group: Tracks.Group): Int? {
        val ordinal = orderedAudioGroups().indexOf(group).takeIf { it >= 0 } ?: return null
        return TrackMapping.streamIndexAtEmbeddedOrdinal(
            currentMediaStreams(MediaStreamType.AUDIO),
            ordinal,
        )
    }

    private fun streamIndexForSubtitleGroup(group: Tracks.Group): Int? {
        val format = group.mediaTrackGroup.getFormat(0)
        TrackMapping.streamIndexFromSideLoadedId(format.id)?.let {
            return it
        }
        TrackMapping.streamIndexFromExternalUri(
                format.customData as? String,
                sideLoadedSubtitleUris,
            )
            ?.let {
                return it
            }
        val ordinal = textGroups().indexOf(group).takeIf { it >= 0 } ?: return null
        return TrackMapping.streamIndexAtEmbeddedOrdinal(
            currentMediaStreams(MediaStreamType.SUBTITLE),
            ordinal,
        )
    }

    private fun applyPendingTrackSelections() {
        val audioPending = pendingAudioStreamIndex
        val subtitlePending = pendingSubtitleStreamIndex
        if (audioPending == null && subtitlePending == null) return

        val audioGroup = audioPending?.let { audioGroupForStreamIndex(it) }
        val disableSubtitles = subtitlePending == TrackSelection.NO_SUBTITLE
        val subtitleGroup =
            subtitlePending?.takeIf { it >= 0 }?.let { subtitleGroupForStreamIndex(it) }

        if (audioGroup == null && subtitleGroup == null && !disableSubtitles) {
            Timber.d(
                "Deferring track selection, tracks not ready (audio=$audioPending, subtitle=$subtitlePending)"
            )
            abandonUnresolvedPendingTracks()
            return
        }

        player.trackSelectionParameters =
            player.trackSelectionParameters
                .buildUpon()
                .apply {
                    if (audioGroup != null) {
                        setOverrideForType(TrackSelectionOverride(audioGroup.mediaTrackGroup, 0))
                        setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                    }
                    if (subtitleGroup != null) {
                        setOverrideForType(TrackSelectionOverride(subtitleGroup.mediaTrackGroup, 0))
                        setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        setIgnoredTextSelectionFlags(0)
                    } else if (disableSubtitles) {
                        clearOverridesOfType(C.TRACK_TYPE_TEXT)
                        setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        setIgnoredTextSelectionFlags(
                            C.SELECTION_FLAG_FORCED or C.SELECTION_FLAG_DEFAULT
                        )
                    }
                }
                .build()

        if (audioGroup != null) pendingAudioStreamIndex = null
        if (subtitleGroup != null || disableSubtitles) pendingSubtitleStreamIndex = null

        abandonUnresolvedPendingTracks()
    }

    private fun abandonUnresolvedPendingTracks() {
        if (pendingAudioStreamIndex == null && pendingSubtitleStreamIndex == null) {
            pendingTrackResolveAttempts = 0
            return
        }
        pendingTrackResolveAttempts++
        if (pendingTrackResolveAttempts < MAX_PENDING_TRACK_ATTEMPTS) return

        Timber.w(
            "Giving up on track selection after $pendingTrackResolveAttempts attempts (audio=$pendingAudioStreamIndex, subtitle=$pendingSubtitleStreamIndex)"
        )
        pendingAudioStreamIndex = null
        pendingSubtitleStreamIndex = null
        pendingTrackResolveAttempts = 0
        updateCurrentTrackSelections()
    }

    fun switchToTrack(
        trackType: @C.TrackType Int,
        index: Int,
        userInitiated: Boolean = false,
    ) {
        if (trackType == C.TRACK_TYPE_AUDIO) pendingAudioStreamIndex = null
        if (trackType == C.TRACK_TYPE_TEXT) pendingSubtitleStreamIndex = null

        if (index == -1) {
            player.trackSelectionParameters =
                player.trackSelectionParameters
                    .buildUpon()
                    .clearOverridesOfType(trackType)
                    .setTrackTypeDisabled(trackType, true)
                    .apply {
                        if (trackType == C.TRACK_TYPE_TEXT) {
                            setIgnoredTextSelectionFlags(
                                C.SELECTION_FLAG_FORCED or C.SELECTION_FLAG_DEFAULT
                            )
                        }
                    }
                    .build()
        } else {
            val group =
                when (trackType) {
                    C.TRACK_TYPE_AUDIO -> audioGroupForStreamIndex(index)
                    C.TRACK_TYPE_TEXT -> subtitleGroupForStreamIndex(index)
                    else -> supportedTrackGroups(trackType).getOrNull(index)
                }

            if (group == null) {
                Timber.w("Stream index $index not available for track type $trackType")
                updateCurrentTrackSelections()
                return
            }

            val language =
                group.getTrackFormat(0).language?.takeIf { it.isNotBlank() && it != "und" }

            player.trackSelectionParameters =
                player.trackSelectionParameters
                    .buildUpon()
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
                    .setTrackTypeDisabled(trackType, false)
                    .apply {
                        if (trackType == C.TRACK_TYPE_TEXT) {
                            setIgnoredTextSelectionFlags(0)
                        }
                        if (userInitiated && language != null) {
                            when (trackType) {
                                C.TRACK_TYPE_AUDIO -> setPreferredAudioLanguage(language)
                                C.TRACK_TYPE_TEXT -> setPreferredTextLanguage(language)
                            }
                        }
                    }
                    .build()
        }
        updateCurrentTrackSelections()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        super.onMediaItemTransition(mediaItem, reason)

        player.trackSelectionParameters =
            player.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .build()

        if (pendingAudioStreamIndex == null) {
            updateUiState { it.copy(audioStreamIndex = null) }
        }
        if (pendingSubtitleStreamIndex == null) {
            updateUiState { it.copy(subtitleStreamIndex = null) }
        }
    }

    private suspend fun resolveSubtitlePlaybackMode(): SubtitlePlaybackMode =
        TrackSelection.resolveSubtitleMode(
            override = preferencesRepository.getSubtitleModeOverride(),
            serverMode = sessionManager.currentSession.value?.userConfiguration?.subtitleMode,
        )

    private fun resolvePlayDefaultAudioTrack(): Boolean =
        sessionManager.currentSession.value?.userConfiguration?.playDefaultAudioTrack == true

    private suspend fun resolvePreferredAudioLanguage(): String {
        val local = preferencesRepository.getPreferredAudioLanguage()
        if (local != TrackSelection.FOLLOW_SERVER_LANGUAGE) return local
        return sessionManager.currentSession.value
            ?.userConfiguration
            ?.audioLanguagePreference
            .orEmpty()
    }

    private suspend fun resolvePreferredSubtitleLanguage(): String {
        val local = preferencesRepository.getPreferredSubtitleLanguage()
        if (local != TrackSelection.FOLLOW_SERVER_LANGUAGE) return local
        return sessionManager.currentSession.value
            ?.userConfiguration
            ?.subtitleLanguagePreference
            .orEmpty()
    }

    private fun autoUpdateSubtitleForAudio(audioStreamIndex: Int) {
        if (_uiState.value.subtitleUserSelected) return

        val sourceId = _uiState.value.currentMediaSourceId ?: return
        val source =
            currentItem?.sources?.find { it.id == sourceId }
                ?: currentItem?.sources?.firstOrNull()
                ?: return
        val audioStreams = source.mediaStreams.filter { it.type == MediaStreamType.AUDIO }
        val subtitleStreams = source.mediaStreams.filter { it.type == MediaStreamType.SUBTITLE }
        if (audioStreams.none { it.index == audioStreamIndex }) return

        viewModelScope.launch {
            val selection =
                TrackSelection.select(
                    audioStreams = audioStreams,
                    subtitleStreams = subtitleStreams,
                    subtitleMode = resolveSubtitlePlaybackMode(),
                    preferredAudioLanguage = resolvePreferredAudioLanguage(),
                    preferredSubtitleLanguage = resolvePreferredSubtitleLanguage(),
                    preferHearingImpaired = preferencesRepository.getPreferSdhSubtitles(),
                    requestedAudioStreamIndex = audioStreamIndex,
                )
            val targetSubtitleStreamIndex =
                if (selection.subtitlePosition == TrackSelection.NO_SUBTITLE) {
                    TrackSelection.NO_SUBTITLE
                } else {
                    subtitleStreams.getOrNull(selection.subtitlePosition)?.index
                        ?: TrackSelection.NO_SUBTITLE
                }
            switchToTrack(C.TRACK_TYPE_TEXT, targetSubtitleStreamIndex)
        }
    }

    private fun updateCurrentTrackSelections() {
        val currentAudioStreamIndex =
            orderedAudioGroups().firstOrNull { it.isSelected }?.let { streamIndexForAudioGroup(it) }

        val selectedTextGroup = textGroups().firstOrNull { it.isSelected }
        val currentSubtitleStreamIndex =
            if (selectedTextGroup == null) TrackSelection.NO_SUBTITLE
            else streamIndexForSubtitleGroup(selectedTextGroup)

        updateUiState {
            it.copy(
                audioStreamIndex = currentAudioStreamIndex,
                subtitleStreamIndex = currentSubtitleStreamIndex,
            )
        }
    }

    private suspend fun reportPlaybackStart(item: AfinityItem) {
        if (_uiState.value.isPlayingIntro) {
            Timber.d("Skipping playback start report for Intro: ${item.name}")
            return
        }
        try {
            currentSessionId?.let { sessionId ->
                val sourceId =
                    _uiState.value.currentMediaSourceId ?: item.sources.firstOrNull()?.id ?: ""
                val source =
                    item.sources.firstOrNull { it.id == sourceId } ?: item.sources.firstOrNull()
                val jfAudioIndex = _uiState.value.audioStreamIndex
                val jfSubIndex = _uiState.value.subtitleStreamIndex ?: TrackSelection.NO_SUBTITLE

                playbackRepository.reportPlaybackStart(
                    itemId = item.id,
                    sessionId = sessionId,
                    mediaSourceId = sourceId,
                    audioStreamIndex = jfAudioIndex,
                    subtitleStreamIndex = jfSubIndex,
                    playMethod =
                        (currentStreamDecision?.playMethod ?: PlayMethod.DIRECT_PLAY).serialName,
                    canSeek = true,
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to report playback start")
        }
    }

    private suspend fun loadSegments(itemId: UUID) {
        segmentCheckingJob?.cancel()
        currentMediaSegments = emptyList()
        updateUiState { it.copy(currentSegment = null, showSkipButton = false) }

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
            val durationMs = segment.endTicks - segment.startTicks
            val skipMode =
                when (segment.type) {
                    AfinitySegmentType.INTRO -> preferencesRepository.getSkipIntroMode()
                    AfinitySegmentType.OUTRO -> preferencesRepository.getSkipOutroMode()
                    else -> SkipMode.DISABLED
                }

            if (skipMode == SkipMode.DISABLED) {
                updateUiState { it.copy(currentSegment = null, showSkipButton = false) }
                return@let
            }

            if (durationMs >= 3000L) {
                val segmentKey = "${segment.type}-${segment.startTicks}"
                val alreadyShown = lastShownSegmentKey == segmentKey

                if (!alreadyShown) {
                    lastShownSegmentKey = segmentKey

                    val skipButtonText = getSkipButtonText(segment, skipMode)

                    updateUiState {
                        it.copy(
                            currentSegment = segment,
                            skipButtonText = skipButtonText,
                            showSkipButton = true,
                        )
                    }

                    skipButtonHideJob?.cancel()
                    skipButtonHideJob = viewModelScope.launch {
                        delay(SKIP_BUTTON_TIMEOUT_MS)

                        if (skipMode == SkipMode.AUTO_SKIP) {
                            handlePlayerEvent(PlayerEvent.SkipSegment(segment))
                        } else {
                            updateUiState { it.copy(showSkipButton = false) }
                        }
                    }
                }
            }
        }
            ?: run {
                lastShownSegmentKey = null
                skipButtonHideJob?.cancel()
                updateUiState { it.copy(currentSegment = null, showSkipButton = false) }
            }
    }

    private fun getSkipButtonText(segment: AfinitySegment, skipMode: SkipMode): String {
        val baseText =
            when (segment.type) {
                AfinitySegmentType.INTRO -> context.getString(R.string.skip_intro)
                AfinitySegmentType.OUTRO -> context.getString(R.string.skip_outro)
                AfinitySegmentType.RECAP -> context.getString(R.string.skip_recap)
                AfinitySegmentType.PREVIEW -> context.getString(R.string.skip_preview)
                AfinitySegmentType.COMMERCIAL -> context.getString(R.string.skip_commercial)
                else -> context.getString(R.string.skip_generic)
            }

        return if (skipMode == SkipMode.AUTO_SKIP) {
            val cleanText = baseText.replace(Regex("(?i)^skip\\s+"), "")
            "Skipping $cleanText..."
        } else {
            baseText
        }
    }

    fun initializePlaylistAndPlay(
        item: AfinityItem,
        mediaSourceId: String,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
        startPositionMs: Long,
        seasonId: UUID? = null,
        shuffle: Boolean = false,
        playlistId: UUID? = null,
    ) {

        val targetSource =
            item.sources.firstOrNull { it.id == mediaSourceId } ?: item.sources.firstOrNull()
        val videoStream =
            targetSource?.mediaStreams?.firstOrNull { it.type == MediaStreamType.VIDEO }

        if (videoStream != null) {
            val w = videoStream.width
            val h = videoStream.height
            if (w != null && h != null && w > 0 && h > 0) {
                isVideoPortrait = h > w
                if (!isOrientationOverridden) {
                    updateUiState {
                        it.copy(resolvedOrientation = computeOrientation(isVideoPortrait))
                    }
                }
            }
        } else {
            if (!isOrientationOverridden) {
                isVideoPortrait = false
                updateUiState {
                    it.copy(resolvedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
                }
            }
        }
        viewModelScope.launch {
            playlistManager.initializePlaylist(item, seasonId, startPositionMs, playlistId)
            if (shuffle) {
                playlistManager.shuffleQueue()
            }
            val firstItem = playlistManager.getCurrentItem() ?: item
            val queueState = playlistManager.playlistState.value
            val isOnIntro = queueState.contentStartIndex > queueState.currentIndex

            if (firstItem.id != item.id && !isOnIntro) {
                pendingMainItemOptions = null
                updateUiState { it.copy(isPlayingIntro = false) }
                Timber.d("Queue reordered, playing queue head: ${firstItem.name}")
                suppressNextControlShow = false
                handlePlayerEvent(
                    PlayerEvent.LoadMedia(
                        item = firstItem,
                        mediaSourceId = firstItem.sources.firstOrNull()?.id ?: "",
                        audioStreamIndex = null,
                        subtitleStreamIndex = null,
                        startPositionMs =
                            if (firstItem.playbackPositionTicks > 0)
                                firstItem.playbackPositionTicks / 10000
                            else 0L,
                    )
                )
            } else if (firstItem.id != item.id) {
                pendingMainItemOptions =
                    MainItemPlaybackOptions(
                        itemId = item.id,
                        mediaSourceId = mediaSourceId,
                        audioStreamIndex = audioStreamIndex,
                        subtitleStreamIndex = subtitleStreamIndex,
                        startPositionMs = startPositionMs,
                    )

                updateUiState { it.copy(isPlayingIntro = true) }
                applyZoomMode(VideoZoomMode.ZOOM, saveAsCurrent = false)

                Timber.d("Intro found ${firstItem.name}")
                suppressNextControlShow = true
                handlePlayerEvent(
                    PlayerEvent.LoadMedia(
                        item = firstItem,
                        mediaSourceId = firstItem.sources.firstOrNull()?.id ?: "",
                        audioStreamIndex = null,
                        subtitleStreamIndex = null,
                        startPositionMs = 0L,
                    )
                )
            } else {
                pendingMainItemOptions = null
                updateUiState { it.copy(isPlayingIntro = false) }
                Timber.d("No intros or resuming, playing item: ${item.name}")
                suppressNextControlShow = false
                handlePlayerEvent(
                    PlayerEvent.LoadMedia(
                        item = item,
                        mediaSourceId = mediaSourceId,
                        audioStreamIndex = audioStreamIndex,
                        subtitleStreamIndex = subtitleStreamIndex,
                        startPositionMs = startPositionMs,
                    )
                )
            }

            launch { playlistManager.enrichWithCollectionQueue(item) }
        }
    }

    fun clearPlaylist() {
        playlistManager.clearQueue()
    }

    /**
     * Given a list of [candidates] (sources of the next episode), returns the one that best matches
     * [reference] (the source currently playing). Matching priority:
     * 1. Exact name match (case-insensitive) — most reliable when Merge Versions plugin is used, as
     *    it propagates the folder/file name as the source name (e.g. "1080p BluRay").
     * 2. Resolution (height) match — catches cases where names differ slightly.
     * 3. First candidate — default fallback.
     */
    fun findBestMatchingSource(
        reference: AfinitySource?,
        candidates: List<AfinitySource>,
    ): AfinitySource? {
        if (candidates.isEmpty()) return null
        if (reference == null) return candidates.first()

        // 1. Name match
        val refName = reference.name.trim().lowercase()
        if (refName.isNotBlank() && refName != "default") {
            candidates
                .firstOrNull { it.name.trim().lowercase() == refName }
                ?.let {
                    return it
                }
        }

        // 2. Resolution match (height)
        val refHeight = reference.height
        if (refHeight != null && refHeight > 0) {
            candidates
                .firstOrNull { it.height == refHeight }
                ?.let {
                    return it
                }
        }

        // 3. Fallback
        return candidates.first()
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

    fun onLongPress(xFraction: Float) {
        if (mpvPlayerOrNull()?.sendLongPressKeypress() == true) return

        val chapters = _uiState.value.chapters
        if (chapterSkipGestureEnabled && chapters.isNotEmpty()) {
            when {
                xFraction < 0.25f -> {
                    seekToPreviousChapter()
                    return
                }
                xFraction > 0.75f -> {
                    seekToNextChapter()
                    return
                }
            }
        }
        onLongPressStart()
    }

    fun onLongPressStart() {
        val currentSpeed = _uiState.value.playbackSpeed
        if (currentSpeed < 2.0f) {
            speedBeforeLongPress = currentSpeed
            handlePlayerEvent(PlayerEvent.SetPlaybackSpeed(2.0f))
            updateUiState { it.copy(isSpeedingUp = true) }
        }
    }

    private fun seekToPreviousChapter() {
        val chapters = _uiState.value.chapters
        if (chapters.isEmpty()) return
        val currentPos = player.currentPosition
        val currentChapterIndex = chapters.indexOfLast { it.startPosition <= currentPos }
        if (currentChapterIndex == -1) return
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
    }

    private fun seekToNextChapter() {
        val chapters = _uiState.value.chapters
        if (chapters.isEmpty()) return
        val currentPos = player.currentPosition
        val nextChapter = chapters.find { it.startPosition > currentPos + 1000 }
        if (nextChapter != null) {
            handlePlayerEvent(PlayerEvent.Seek(nextChapter.startPosition))
        } else {
            onNextEpisode()
        }
    }

    fun onLongPressEnd() {
        speedBeforeLongPress?.let { savedSpeed ->
            handlePlayerEvent(PlayerEvent.SetPlaybackSpeed(savedSpeed))
            speedBeforeLongPress = null
            updateUiState { it.copy(isSpeedingUp = false) }
        }
    }

    fun onDoubleTapSeek(isForward: Boolean, xFraction: Float) {
        if (sendMpvGestureKeypress(xFraction)) return
        if (_uiState.value.isLiveChannel) return

        val delta = if (isForward) 10000L else -10000L
        handlePlayerEvent(PlayerEvent.SeekRelative(delta))
    }

    private fun mpvPlayerOrNull(): MPVPlayer? =
        (if (::player.isInitialized) player else null) as? MPVPlayer

    private fun sendMpvGestureKeypress(xFraction: Float): Boolean {
        val mpvPlayer = mpvPlayerOrNull() ?: return false

        val zone =
            when {
                xFraction < 1f / 3f -> -1
                xFraction > 2f / 3f -> 1
                else -> 0
            }
        return mpvPlayer.sendGestureKeypress(zone)
    }

    private fun onLockToggle() {
        updateUiState { it.copy(isControlsLocked = !it.isControlsLocked) }
    }

    fun onNextEpisode() {
        reportCurrentItemStopped()
        viewModelScope.launch {
            playlistManager.getNextItem()?.let { nextItem ->
                playQueueItem(nextItem)
                playlistManager.next()
            }
        }
    }

    fun onPreviousEpisode() {
        reportCurrentItemStopped()
        viewModelScope.launch {
            playlistManager.getPreviousItem()?.let { prevItem ->
                playQueueItem(prevItem)
                playlistManager.previous()
            }
        }
    }

    fun jumpToEpisode(episodeId: UUID) {
        reportCurrentItemStopped()
        viewModelScope.launch {
            playlistManager.jumpToItem(episodeId)?.let { targetItem -> playQueueItem(targetItem) }
        }
    }

    private suspend fun playQueueItem(item: AfinityItem) {
        val fullItem =
            if (item.sources.isEmpty()) {
                withContext(Dispatchers.IO) { mediaRepository.getItemById(item.id) } ?: item
            } else {
                item
            }

        suppressNextControlShow = true
        if (pendingMainItemOptions?.itemId == fullItem.id) {
            val options = pendingMainItemOptions!!
            pendingMainItemOptions = null
            updateUiState {
                it.copy(isPlayingIntro = false, showControls = false, showPlayButton = false)
            }
            controlsHideJob?.cancel()
            applyZoomMode(currentZoomMode, saveAsCurrent = true)

            Timber.d("Intro finished, restoring settings: ${fullItem.name}")
            handlePlayerEvent(
                PlayerEvent.LoadMedia(
                    item = fullItem,
                    mediaSourceId = options.mediaSourceId,
                    audioStreamIndex = options.audioStreamIndex,
                    subtitleStreamIndex = options.subtitleStreamIndex,
                    startPositionMs = options.startPositionMs,
                )
            )
            viewModelScope.launch { playlistManager.enrichWithCollectionQueue(fullItem) }
            return
        }

        val isStillIntro = pendingMainItemOptions != null
        updateUiState { it.copy(isPlayingIntro = isStillIntro) }

        val currentSourceId = _uiState.value.currentMediaSourceId
        val currentSource =
            _uiState.value.currentItem?.sources?.firstOrNull { it.id == currentSourceId }

        val bestMatch =
            findBestMatchingSource(reference = currentSource, candidates = fullItem.sources)
        val mediaSourceId = bestMatch?.id ?: fullItem.sources.firstOrNull()?.id ?: ""

        handlePlayerEvent(
            PlayerEvent.LoadMedia(
                item = fullItem,
                mediaSourceId = mediaSourceId,
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
        brightnessHideJob = viewModelScope.launch {
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

    private fun getInternalVolume(): Float {
        return when (val currentPlayer = player) {
            is ExoPlayer -> currentPlayer.volume
            is MPVPlayer -> {
                val mpvVol = currentPlayer.mpv.getPropertyDouble("volume") ?: 100.0
                (mpvVol / 100.0).toFloat()
            }
            else -> 1f
        }
    }

    private fun setInternalVolume(volume: Float) {
        val clampedVolume = volume.coerceIn(0f, 1f)
        when (val currentPlayer = player) {
            is ExoPlayer -> currentPlayer.volume = clampedVolume
            is MPVPlayer -> {
                currentPlayer.mpv.setPropertyDouble("volume", (clampedVolume * 100.0))
            }
        }
    }

    private fun executeSegmentSeek(targetPositionMs: Long) {
        viewModelScope.launch {
            val originalVolume = getInternalVolume()
            val fadeDurationMs = 150L
            val steps = 10
            val stepDelay = fadeDurationMs / steps

            for (i in steps downTo 0) {
                val progress = i.toFloat() / steps
                setInternalVolume(originalVolume * progress)
                delay(stepDelay)
            }

            player.seekTo(targetPositionMs)

            delay(50)

            for (i in 1..steps) {
                val progress = i.toFloat() / steps
                setInternalVolume(originalVolume * progress)
                delay(stepDelay)
            }

            setInternalVolume(originalVolume)
        }
    }

    private fun onSeekBarPreview(position: Long, isActive: Boolean) {
        if (!isActive) {
            trickplayFetchJob?.cancel()
            updateUiState {
                it.copy(
                    showTrickplayPreview = false,
                    trickplayPreviewImage = null,
                    trickplayPreviewPosition = 0L,
                )
            }
            return
        }

        val info = currentTrickplayInfo ?: return
        val itemId = currentTrickplayItemId ?: return
        if (info.interval <= 0 || info.thumbnailCount <= 0) return

        val thumbnailsPerTile = info.tileWidth * info.tileHeight
        val globalIndex = (position / info.interval).toInt().coerceIn(0, info.thumbnailCount - 1)
        val tileIndex = globalIndex / thumbnailsPerTile
        val offsetInTile = globalIndex % thumbnailsPerTile
        val offsetX = (offsetInTile % info.tileWidth) * info.width
        val offsetY = (offsetInTile / info.tileWidth) * info.height

        fun cropAndShow(tileBitmap: Bitmap) {
            try {
                val thumbnail =
                    Bitmap.createBitmap(tileBitmap, offsetX, offsetY, info.width, info.height)
                updateUiState {
                    it.copy(
                        showTrickplayPreview = true,
                        trickplayPreviewImage = thumbnail.asImageBitmap(),
                        trickplayPreviewPosition = position,
                    )
                }
            } catch (e: Exception) {
                Timber.w(
                    e,
                    "Failed to crop trickplay thumbnail at tile=$tileIndex offset=($offsetX,$offsetY)",
                )
            }
        }

        val cached = trickplayTileCache[tileIndex]
        if (cached != null) {
            cropAndShow(cached)
            return
        }

        trickplayFetchJob?.cancel()
        trickplayFetchJob =
            viewModelScope.launch(Dispatchers.IO) {
                val imageData =
                    mediaRepository.getTrickplayData(itemId, info.width, tileIndex) ?: return@launch
                val tileBitmap =
                    BitmapFactory.decodeByteArray(imageData, 0, imageData.size) ?: return@launch
                withContext(Dispatchers.Main) {
                    trickplayTileCache[tileIndex] = tileBitmap
                    cropAndShow(tileBitmap)
                }
            }
    }

    private var playStatus = false

    fun onResume() {
        if (!_uiState.value.isCasting && playStatus) {
            player.play()
        }
    }

    fun onPause() {
        if (!_uiState.value.isCasting) {
            playStatus = player.isPlaying
            player.pause()
        }
        onLongPressEnd()
    }

    fun stopPlayback() {
        if (hasStoppedPlayback) return
        if (!::player.isInitialized) return
        hasStoppedPlayback = true
        progressReportingJob?.cancel()

        val finalPosition =
            if (_uiState.value.isCasting) {
                castManager.castState.value.currentPosition
            } else {
                player.currentPosition
            }
        val item = currentItem

        if (item != null) {
            if (!_uiState.value.isPlayingIntro) {
                playbackStateManager.notifyPlaybackStopped(
                    itemId = item.id,
                    positionMs = finalPosition,
                    runtimeTicks = currentItemRuntimeTicks(item),
                )
            }
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
        controlsHideJob = viewModelScope.launch {
            delay(3000)
            if (uiState.value.isPlaying && uiState.value.showControls && !uiState.value.isSeeking) {
                hideControls()
            } else if (uiState.value.isSeeking) {
                startControlsAutoHide()
            }
        }
    }

    private fun reportCurrentItemStopped(isEnded: Boolean = false) {
        val item = currentItem ?: return
        if (_uiState.value.isPlayingIntro) return

        val position =
            if (isEnded && player.duration > 0) {
                player.duration
            } else if (_uiState.value.isCasting) {
                castManager.castState.value.currentPosition
            } else {
                player.currentPosition
            }

        playbackStateManager.notifyPlaybackStopped(
            itemId = item.id,
            positionMs = position,
            runtimeTicks = currentItemRuntimeTicks(item),
            isEnded = isEnded && !_uiState.value.isLiveChannel,
        )
    }

    private fun currentItemRuntimeTicks(item: AfinityItem): Long {
        if (_uiState.value.isLiveChannel) return 0L
        val playerDuration = if (::player.isInitialized) player.duration else 0L
        return if (playerDuration > 0) playerDuration * 10000 else item.runtimeTicks
    }

    override fun onCleared() {
        super.onCleared()

        clearPlaylist()
        stopPlayback()

        progressReportingJob?.cancel()
        segmentCheckingJob?.cancel()
        skipButtonHideJob?.cancel()
        controlsHideJob?.cancel()
        statsPollingJob?.cancel()
        videoMediaSession?.release()
        videoMediaSession = null
        if (::player.isInitialized) {
            player.removeListener(this)
            player.release()
        }
        assHandler?.release()
        assHandler = null
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
        val isPlayerReady: Boolean = false,
        val isPlaying: Boolean = false,
        val isPaused: Boolean = false,
        val isBuffering: Boolean = false,
        val playWhenReady: Boolean = false,
        val isLoading: Boolean = false,
        val currentPosition: Long = 0L,
        val bufferedPosition: Long = 0L,
        val duration: Long = 0L,
        val showControls: Boolean = false,
        val isFullscreen: Boolean = false,
        val isControlsLocked: Boolean = false,
        val currentSegment: AfinitySegment? = null,
        val isSeekPreviewActive: Boolean = false,
        val seekPreviewPosition: Long = 0L,
        val playbackSpeed: Float = 1f,
        val sideLoadedSubtitleUris: Map<Int, String> = emptyMap(),
        val audioStreamIndex: Int? = null,
        val subtitleStreamIndex: Int? = null,
        val subtitleUserSelected: Boolean = false,
        val playMethod: PlayMethod? = null,
        val transcodeReasons: List<TranscodeReason> = emptyList(),
        val burnedInSubtitleIndex: Int? = null,
        val clientRenderedSubtitles: Set<Int> = emptySet(),
        val videoQuality: VideoQuality = VideoQuality.ORIGINAL,
        val availableQualities: List<VideoQuality> = emptyList(),
        val outputVideoWidth: Int = 0,
        val outputVideoHeight: Int = 0,
        val showPlayButton: Boolean = true,
        val showBuffering: Boolean = false,
        val showError: Boolean = false,
        val canPlayAnywayWithTranscoding: Boolean = false,
        val errorMessage: String? = null,
        val brightnessLevel: Float = -1.0f,
        val showTrickplayPreview: Boolean = false,
        val trickplayPreviewImage: ImageBitmap? = null,
        val trickplayPreviewPosition: Long = 0L,
        val chapters: List<AfinityChapter> = emptyList(),
        val baseUrl: String = "",
        val currentItem: AfinityItem? = null,
        val showSkipButton: Boolean = false,
        val skipButtonText: String = "Skip",
        val showSeekIndicator: Boolean = false,
        val seekDirection: Int = 0,
        val showBrightnessIndicator: Boolean = false,
        val showVolumeIndicator: Boolean = false,
        val volumeLevel: Int = 50,
        val remoteMessage: RemoteMessage? = null,
        val isSeeking: Boolean = false,
        val seekPosition: Long = 0L,
        val dragStartPosition: Long = 0L,
        val showRemainingTime: Boolean = false,
        val isInPictureInPictureMode: Boolean = false,
        val pipBackgroundPlay: Boolean = true,
        val isControlsVisible: Boolean = true,
        val videoZoomMode: VideoZoomMode = VideoZoomMode.FIT,
        val videoAspectRatio: Float = 0f,
        val mdbRatings: List<MdbListRating> = emptyList(),
        val omdbAwards: String? = null,
        val resolvedOrientation: Int = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
        val logoAutoHide: Boolean = false,
        val pauseScreenEnabled: Boolean = false,
        val pauseScreenDelaySeconds: Int = 0,
        val isLiveChannel: Boolean = false,
        val isCasting: Boolean = false,
        val showCastChooser: Boolean = false,
        val isSpeedingUp: Boolean = false,
        // Version picker
        val availableSources: List<AfinitySource> = emptyList(),
        val currentMediaSourceId: String? = null,
        val showVersionPicker: Boolean = false,
        val isPlayingIntro: Boolean = false,
        val showPlaybackStats: Boolean = false,
        val playbackStats: PlaybackStats = PlaybackStats(),
    )

    data class MainItemPlaybackOptions(
        val itemId: UUID,
        val mediaSourceId: String,
        val audioStreamIndex: Int?,
        val subtitleStreamIndex: Int?,
        val startPositionMs: Long,
    )

    private companion object {
        const val SERVER_STATS_INTERVAL_TICKS = 5
    }
}

private data class MpvPrefsSnapshot(
    val hwDec: String,
    val videoOutput: String,
    val audioOutput: String,
    val subtitlePrefs: SubtitlePreferences,
    val preferredAudioLanguage: String,
)
