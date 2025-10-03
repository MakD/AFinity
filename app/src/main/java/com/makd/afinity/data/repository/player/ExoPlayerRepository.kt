package com.makd.afinity.data.repository.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.player.PlayerError
import com.makd.afinity.data.models.player.PlayerState
import com.makd.afinity.data.repository.playback.PlaybackRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jellyfin.sdk.model.api.MediaStreamType
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@androidx.media3.common.util.UnstableApi
class ExoPlayerRepository constructor(
    @ApplicationContext private val context: Context,
    private val playbackRepository: PlaybackRepository,
    private val apiClient: org.jellyfin.sdk.api.client.ApiClient
) : PlayerRepository, Player.Listener {

    private val _playerState = MutableStateFlow(PlayerState())
    override val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var playbackReportingJob: Job? = null

    private var player: ExoPlayer? = null
    private val trackSelector = DefaultTrackSelector(context)

    private var onPlaybackCompleted: ((AfinityItem) -> Unit)? = null
    private var currentItem: AfinityItem? = null

    init {
        initializePlayer()
    }

    private fun initializePlayer() {
        try {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(C.USAGE_MEDIA)
                .build()

            trackSelector.setParameters(
                trackSelector.buildUponParameters()
                    .setTunnelingEnabled(true)
            )

            val renderersFactory = DefaultRenderersFactory(context)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

            player = ExoPlayer.Builder(context, renderersFactory)
                .setAudioAttributes(audioAttributes, true)
                .setTrackSelector(trackSelector)
                .setSeekBackIncrementMs(10000)
                .setSeekForwardIncrementMs(10000)
                .setPauseAtEndOfMediaItems(true)
                .build()
                .apply {
                    addListener(this@ExoPlayerRepository)
                }

            Timber.d("ExoPlayer initialized successfully")
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize ExoPlayer")
            updateState {
                it.copy(
                    error = PlayerError(
                        code = -1,
                        message = "Failed to initialize player: ${e.message}",
                        cause = e
                    )
                )
            }
        }
    }

    override suspend fun loadMedia(
        item: AfinityItem,
        mediaSourceId: String,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
        startPositionMs: Long
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            currentItem = item

            updateState {
                it.copy(
                    isLoading = true,
                    error = null,
                    currentItem = item,
                    mediaSourceId = mediaSourceId,
                    audioStreamIndex = audioStreamIndex,
                    subtitleStreamIndex = subtitleStreamIndex
                )
            }

            val sessionId = UUID.randomUUID().toString()
            updateState { it.copy(sessionId = sessionId) }

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
                updateState {
                    it.copy(
                        isLoading = false,
                        error = PlayerError(
                            code = -8,
                            message = "Stream URL is empty",
                            cause = null
                        )
                    )
                }
                return@withContext false
            }

            Timber.d("Stream URL: $streamUrl")

            val mediaSources = playbackRepository.getMediaSources(item.id)
            val mediaSource = mediaSources.firstOrNull { it.id == mediaSourceId }

            val externalSubtitles = mediaSource?.mediaStreams
                ?.filter { stream ->
                    stream.isExternal &&
                            stream.type == MediaStreamType.SUBTITLE &&
                            !stream.deliveryUrl.isNullOrBlank()
                }
                ?.map { stream ->
                    val subtitleUrl = apiClient.baseUrl + stream.deliveryUrl
                    val mimeType = when (stream.codec?.lowercase()) {
                        "subrip" -> androidx.media3.common.MimeTypes.APPLICATION_SUBRIP
                        "webvtt" -> androidx.media3.common.MimeTypes.APPLICATION_SUBRIP
                        "ass", "ssa" -> androidx.media3.common.MimeTypes.TEXT_SSA
                        else -> androidx.media3.common.MimeTypes.TEXT_UNKNOWN
                    }

                    MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(subtitleUrl))
                        .setLabel(stream.title ?: stream.language ?: "Unknown")
                        .setMimeType(mimeType)
                        .setLanguage(stream.language)
                        .setSelectionFlags(if (stream.isDefault) C.SELECTION_FLAG_DEFAULT else 0)
                        .build()
                } ?: emptyList()

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
                player?.apply {
                    setMediaItem(mediaItem)
                    seekTo(startPositionMs)
                    prepare()
                    playWhenReady = true
                }
            }

            updateState {
                it.copy(
                    isLoading = false,
                    isPlaying = true,
                    isPaused = false
                )
            }

            startPlaybackReporting()

            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to load media")
            updateState {
                it.copy(
                    isLoading = false,
                    error = PlayerError(
                        code = -3,
                        message = "Failed to load media: ${e.message}",
                        cause = e
                    )
                )
            }
            false
        }
    }

    override suspend fun play(): Unit = withContext(Dispatchers.Main) {
        player?.play()
    }

    override suspend fun pause(): Unit = withContext(Dispatchers.Main) {
        player?.pause()
    }

    override suspend fun stop() = withContext(Dispatchers.Main) {
        player?.stop()
        stopPlaybackReporting()
    }

    override suspend fun seekTo(positionMs: Long): Unit = withContext(Dispatchers.Main) {
        player?.seekTo(positionMs)
    }

    override suspend fun seekRelative(deltaMs: Long): Unit = withContext(Dispatchers.Main) {
        val currentPos = player?.currentPosition ?: 0
        player?.seekTo(currentPos + deltaMs)
    }

    override suspend fun setVolume(volume: Int) = withContext(Dispatchers.Main) {
        player?.volume = volume / 100f
    }

    override suspend fun setBrightness(brightness: Float) {
        // Brightness handled at UI level
    }

    override suspend fun setPlaybackSpeed(speed: Float) = withContext(Dispatchers.Main) {
        player?.setPlaybackSpeed(speed)
        updateState { it.copy(playbackSpeed = speed) }
    }

    override suspend fun selectAudioTrack(index: Int) = withContext(Dispatchers.Main) {
        try {
            val tracks = player?.currentTracks ?: return@withContext
            val audioGroups = tracks.groups.filter {
                it.type == C.TRACK_TYPE_AUDIO && it.isSupported
            }

            if (index >= 0 && index < audioGroups.size) {
                val trackGroup = audioGroups[index].mediaTrackGroup
                player?.trackSelectionParameters = player?.trackSelectionParameters
                    ?.buildUpon()
                    ?.setOverrideForType(TrackSelectionOverride(trackGroup, 0))
                    ?.build() ?: return@withContext

                updateState { it.copy(audioStreamIndex = index) }
                Timber.d("Selected audio track: $index")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to select audio track: $index")
        }
    }

    override suspend fun selectSubtitleTrack(index: Int?) = withContext(Dispatchers.Main) {
        try {
            if (index == null) {
                player?.trackSelectionParameters = player?.trackSelectionParameters
                    ?.buildUpon()
                    ?.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    ?.build() ?: return@withContext

                updateState { it.copy(subtitleStreamIndex = null) }
                Timber.d("Disabled subtitles")
            } else {
                val tracks = player?.currentTracks ?: return@withContext
                val subtitleGroups = tracks.groups.filter {
                    it.type == C.TRACK_TYPE_TEXT && it.isSupported
                }

                if (index >= 0 && index < subtitleGroups.size) {
                    val trackGroup = subtitleGroups[index].mediaTrackGroup
                    player?.trackSelectionParameters = player?.trackSelectionParameters
                        ?.buildUpon()
                        ?.setOverrideForType(TrackSelectionOverride(trackGroup, 0))
                        ?.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        ?.build() ?: return@withContext

                    updateState { it.copy(subtitleStreamIndex = index) }
                    Timber.d("Selected subtitle track: $index")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to select subtitle track: $index")
        }
    }

    override suspend fun toggleFullscreen() {
        updateState { it.copy(isFullscreen = !it.isFullscreen) }
    }

    override suspend fun toggleControlsLock() {
        updateState { it.copy(isControlsLocked = !it.isControlsLocked) }
    }

    override suspend fun reportPlaybackStart() {
        val state = _playerState.value
        val item = state.currentItem ?: return
        val sessionId = state.sessionId ?: return
        val mediaSourceId = state.mediaSourceId ?: return

        try {
            playbackRepository.reportPlaybackStart(
                itemId = item.id,
                sessionId = sessionId,
                mediaSourceId = mediaSourceId,
                audioStreamIndex = state.audioStreamIndex,
                subtitleStreamIndex = state.subtitleStreamIndex,
                playMethod = "DirectPlay",
                canSeek = true
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to report playback start")
        }
    }

    override suspend fun reportPlaybackProgress() {
        val state = _playerState.value
        val item = state.currentItem ?: return
        val sessionId = state.sessionId ?: return
        val position = player?.currentPosition ?: 0
        val isPaused = player?.playWhenReady == false

        try {
            playbackRepository.reportPlaybackProgress(
                itemId = item.id,
                sessionId = sessionId,
                positionTicks = position * 10000,
                isPaused = isPaused
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to report playback progress")
        }
    }

    override suspend fun reportPlaybackStop() {
        val state = _playerState.value
        val item = state.currentItem ?: return
        val sessionId = state.sessionId ?: return
        val mediaSourceId = state.mediaSourceId ?: return
        val position = player?.currentPosition ?: 0

        try {
            playbackRepository.reportPlaybackStop(
                itemId = item.id,
                sessionId = sessionId,
                positionTicks = position * 10000,
                mediaSourceId = mediaSourceId
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to report playback stop")
        }
    }

    override fun setOnPlaybackStoppedCallback(callback: () -> Unit) {
        // Not needed for ExoPlayer
    }

    fun setOnPlaybackCompleted(callback: (AfinityItem) -> Unit) {
        onPlaybackCompleted = callback
    }

    override fun onResume() {
        player?.play()
    }

    override fun onPause() {
        player?.pause()
    }

    override fun onDestroy() {
        stopPlaybackReporting()
        player?.removeListener(this)
        player?.release()
        player = null
        scope.cancel()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_IDLE -> {
                Timber.d("ExoPlayer: STATE_IDLE")
                updateState { it.copy(isPlaying = false, isBuffering = false) }
            }
            Player.STATE_BUFFERING -> {
                Timber.d("ExoPlayer: STATE_BUFFERING")
                updateState { it.copy(isBuffering = true) }
            }
            Player.STATE_READY -> {
                Timber.d("ExoPlayer: STATE_READY")
                updateState { it.copy(isBuffering = false, isLoading = false) }
            }
            Player.STATE_ENDED -> {
                Timber.d("ExoPlayer: STATE_ENDED")
                updateState { it.copy(isPlaying = false, isPaused = false) }

                currentItem?.let { item ->
                    onPlaybackCompleted?.invoke(item)
                }
            }
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        updateState {
            it.copy(
                isPlaying = isPlaying,
                isPaused = !isPlaying && player?.playbackState != Player.STATE_ENDED
            )
        }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
            scope.launch {
                reportPlaybackStop()
            }
        }
    }

    private fun updateState(update: (PlayerState) -> PlayerState) {
        _playerState.value = update(_playerState.value)
    }

    private fun startPlaybackReporting() {
        stopPlaybackReporting()

        scope.launch {
            reportPlaybackStart()

            playbackReportingJob = launch {
                while (isActive) {
                    delay(10000)
                    reportPlaybackProgress()

                    val position = player?.currentPosition ?: 0
                    val duration = player?.duration ?: 0
                    updateState {
                        it.copy(
                            currentPosition = position,
                            duration = duration
                        )
                    }
                }
            }
        }
    }

    private fun stopPlaybackReporting() {
        playbackReportingJob?.cancel()
        playbackReportingJob = null

        scope.launch {
            reportPlaybackStop()
        }
    }

    fun getPlayer(): Player? = player
}