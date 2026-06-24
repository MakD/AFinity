package com.makd.afinity.player

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.MediaCodecList
import android.os.Bundle
import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionError
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.makd.afinity.MainActivity
import com.makd.afinity.R
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.music.RepeatMode
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.SecurePreferencesRepository
import com.makd.afinity.player.audiobookshelf.AudiobookshelfEqualizerManager
import com.makd.afinity.player.audiobookshelf.AudiobookshelfPlaybackManager
import com.makd.afinity.player.audiobookshelf.AudiobookshelfProgressSyncer
import com.makd.afinity.player.audiobookshelf.AudiobookshelfSkipSilenceManager
import com.makd.afinity.player.music.MusicPlaybackManager
import com.makd.afinity.player.music.MusicProgressReporter
import com.makd.afinity.player.music.MusicQueueManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import javax.inject.Inject
import kotlin.math.pow

@UnstableApi
@OptIn(UnstableApi::class)
@AndroidEntryPoint
class AudioService : MediaSessionService() {

    enum class ActiveEngine {
        NONE,
        ABS,
        MUSIC,
    }

    companion object {
        const val ACTION_ENGINE_ABS = "com.makd.afinity.player.ACTION_ENGINE_ABS"
        const val ACTION_ENGINE_MUSIC = "com.makd.afinity.player.ACTION_ENGINE_MUSIC"
        const val ACTION_STOP = "com.makd.afinity.player.ACTION_STOP"
        const val ACTION_PAUSE_FOR_CAST = "com.makd.afinity.player.ACTION_PAUSE_FOR_CAST"

        private const val CHANNEL_ID = "afinity_audio_playback"
    }

    @Inject lateinit var absPlaybackManager: AudiobookshelfPlaybackManager
    @Inject lateinit var absProgressSyncer: AudiobookshelfProgressSyncer
    @Inject lateinit var absEqualizerManager: AudiobookshelfEqualizerManager
    @Inject lateinit var absSkipSilenceManager: AudiobookshelfSkipSilenceManager
    @Inject lateinit var musicPlaybackManager: MusicPlaybackManager
    @Inject lateinit var musicQueueManager: MusicQueueManager
    @Inject lateinit var musicProgressReporter: MusicProgressReporter
    @Inject lateinit var securePreferencesRepository: SecurePreferencesRepository
    @Inject lateinit var preferencesRepository: PreferencesRepository
    @Inject lateinit var sessionManager: SessionManager

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    var activeEngine: ActiveEngine = ActiveEngine.NONE
        private set

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var positionUpdateJob: Job? = null
    private var musicQueueJob: Job? = null
    private var musicRearrangeJob: Job? = null
    private var skipSilenceJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val bufferSizeMb = runBlocking { preferencesRepository.getBufferSizeMb() }
        initializePlayer(bufferSizeMb)
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.playback_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    private fun initializePlayer(bufferSizeMb: Int) {
        val loadControl =
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(15_000, 300_000, 2_500, 5_000)
                .setTargetBufferBytes(bufferSizeMb * 1024 * 1024)
                .build()

        val renderersFactory =
            object : DefaultRenderersFactory(this) {
                    override fun buildAudioRenderers(
                        context: Context,
                        extensionRendererMode: Int,
                        mediaCodecSelector: MediaCodecSelector,
                        enableDecoderFallback: Boolean,
                        audioSink: AudioSink,
                        eventHandler: Handler,
                        eventListener: AudioRendererEventListener,
                        out: ArrayList<Renderer>,
                    ) {
                        val eac3SafeSelector =
                            MediaCodecSelector { mimeType, requiresSecure, requiresTunneling ->
                                val infos =
                                    MediaCodecUtil.getDecoderInfos(
                                        mimeType,
                                        requiresSecure,
                                        requiresTunneling,
                                    )
                                if (
                                    mimeType == MimeTypes.AUDIO_E_AC3_JOC ||
                                        mimeType == MimeTypes.AUDIO_E_AC3
                                )
                                    infos.filter { it.softwareOnly }
                                else infos
                            }
                        super.buildAudioRenderers(
                            context,
                            extensionRendererMode,
                            eac3SafeSelector,
                            enableDecoderFallback,
                            audioSink,
                            eventHandler,
                            eventListener,
                            out,
                        )
                    }
                }
                .setEnableDecoderFallback(true)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        val retryPolicy =
            object : DefaultLoadErrorHandlingPolicy() {
                override fun getRetryDelayMsFor(
                    loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo
                ): Long {
                    val e = loadErrorInfo.exception
                    if (
                        e is HttpDataSource.InvalidResponseCodeException &&
                            e.responseCode in 400..499
                    ) {
                        return C.TIME_UNSET
                    }
                    return minOf(1_000L shl loadErrorInfo.errorCount.coerceAtMost(5), 30_000L)
                }

                override fun getMinimumLoadableRetryCount(dataType: Int) = 3
            }

        exoPlayer =
            ExoPlayer.Builder(this, renderersFactory)
                .setAudioAttributes(AudioAttributes.DEFAULT, true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .setLoadControl(loadControl)
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(DynamicDataSourceFactory())
                        .setLoadErrorHandlingPolicy(retryPolicy)
                )
                .build()
                .apply {
                    addListener(playerListener)
                    addAnalyticsListener(analyticsListener)
                }

        skipSilenceJob = serviceScope.launch {
            absSkipSilenceManager.isEnabled.collect { enabled ->
                exoPlayer?.skipSilenceEnabled = enabled && activeEngine == ActiveEngine.ABS
            }
        }

        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        mediaSession =
            MediaSession.Builder(this, exoPlayer!!)
                .setId("afinity_audio")
                .setSessionActivity(pendingIntent)
                .setCallback(AudioServiceCallback())
                .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.playback_channel_name)
                .build()
                .apply { setSmallIcon(R.drawable.ic_launcher_monochrome) }
        )

        addSession(mediaSession!!)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ENGINE_ABS -> {
                ensurePlayerInitialized()
                switchToAbs()
            }
            ACTION_ENGINE_MUSIC -> {
                ensurePlayerInitialized()
                switchToMusic()
            }
            ACTION_STOP -> tearDown()
            ACTION_PAUSE_FOR_CAST -> pauseForCast()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun ensurePlayerInitialized() {
        if (exoPlayer == null || mediaSession == null) {
            Timber.d("AudioService: re-initializing after tearDown")
            val bufferSizeMb = runBlocking { preferencesRepository.getBufferSizeMb() }
            initializePlayer(bufferSizeMb)
        }
    }

    private fun switchToAbs() {
        if (activeEngine == ActiveEngine.ABS) {

            Timber.d("AudioService: switchToAbs() — already ABS, no-op")
            return
        }
        Timber.d(
            "AudioService: switchToAbs() from engine=$activeEngine — musicTrack=${musicPlaybackManager.state.value.currentTrack?.name}"
        )
        activeEngine = ActiveEngine.ABS
        val posMs = exoPlayer?.currentPosition ?: 0L
        musicProgressReporter.onPlaybackStopped(posMs)
        musicPlaybackManager.clearPlayer()
        musicPlaybackManager.updateTrack(null)
        musicQueueJob?.cancel()
        musicRearrangeJob?.cancel()
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
        mediaSession?.setCustomLayout(absCustomLayout())
    }

    private fun switchToMusic() {
        if (activeEngine == ActiveEngine.MUSIC) return
        Timber.d("AudioService: switching to Music engine")
        activeEngine = ActiveEngine.MUSIC
        absProgressSyncer.stopSyncing()
        absPlaybackManager.clearSession()
        stopPositionUpdates()
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
        musicPlaybackManager.setPlayer(exoPlayer!!)
        startMusicQueueListener()
        startMusicRearrangeListener()
        mediaSession?.setCustomLayout(musicCustomLayout())
    }

    private fun pauseForCast() {
        if (activeEngine != ActiveEngine.MUSIC) return
        Timber.d("AudioService: pausing music for Cast takeover")
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
        musicQueueJob?.cancel()
        musicRearrangeJob?.cancel()
        activeEngine = ActiveEngine.NONE
    }

    private fun tearDown() {
        Timber.d("AudioService: tear down")
        absPlaybackManager.clearSession()
        musicPlaybackManager.clearPlayer()
        musicPlaybackManager.updateTrack(null)
        activeEngine = ActiveEngine.NONE
        mediaSession?.run {
            removeSession(this)
            player.release()
            release()
            mediaSession = null
        }
        exoPlayer = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private inner class DynamicDataSourceFactory : DataSource.Factory {
        override fun createDataSource(): DataSource {
            val headers =
                when (activeEngine) {
                    ActiveEngine.ABS -> {
                        val token = securePreferencesRepository.getCachedAudiobookshelfToken()
                        if (token != null) mapOf("Authorization" to "Bearer $token") else emptyMap()
                    }
                    else -> {
                        val token = sessionManager.getCurrentApiClient()?.accessToken ?: ""
                        mapOf("Authorization" to "MediaBrowser Token=\"$token\"")
                    }
                }
            return DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(15_000)
                .setDefaultRequestProperties(headers)
                .createDataSource()
        }
    }

    private val playerListener =
        object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (activeEngine == ActiveEngine.ABS) {
                    absEqualizerManager.attachToSession(audioSessionId)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                when (activeEngine) {
                    ActiveEngine.ABS -> {
                        absPlaybackManager.updatePlayingState(isPlaying)
                        if (isPlaying) {
                            startPositionUpdates()
                            absProgressSyncer.startSyncing()
                        } else {
                            stopPositionUpdates()
                            serviceScope.launch { absProgressSyncer.syncNow() }
                            absProgressSyncer.stopSyncing()
                        }
                    }
                    ActiveEngine.MUSIC -> {
                        musicPlaybackManager.updatePlayingState(isPlaying)
                        if (isPlaying) {
                            startPositionUpdates()
                            musicProgressReporter.startProgressUpdates(
                                getPositionMs = { musicPlaybackManager.state.value.positionMs },
                                isPaused = { !musicPlaybackManager.state.value.isPlaying },
                            )
                        } else {
                            stopPositionUpdates()
                            musicProgressReporter.stopProgressUpdates()
                        }
                    }
                    ActiveEngine.NONE -> {}
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                when (activeEngine) {
                    ActiveEngine.ABS ->
                        when (state) {
                            Player.STATE_BUFFERING -> absPlaybackManager.updateBufferingState(true)
                            Player.STATE_READY -> absPlaybackManager.updateBufferingState(false)
                            Player.STATE_ENDED -> {
                                absPlaybackManager.updatePlayingState(false)
                                stopPositionUpdates()
                                serviceScope.launch { absProgressSyncer.syncNow() }
                            }
                            Player.STATE_IDLE -> absPlaybackManager.updateBufferingState(false)
                        }
                    ActiveEngine.MUSIC ->
                        when (state) {
                            Player.STATE_BUFFERING ->
                                musicPlaybackManager.updateBufferingState(true)
                            Player.STATE_READY -> musicPlaybackManager.updateBufferingState(false)
                            Player.STATE_ENDED -> {
                                musicPlaybackManager.updatePlayingState(false)
                                stopPositionUpdates()
                                musicProgressReporter.onPlaybackStopped(
                                    exoPlayer?.currentPosition ?: 0L
                                )
                            }
                            Player.STATE_IDLE -> musicPlaybackManager.updateBufferingState(false)
                        }
                    ActiveEngine.NONE -> {}
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (activeEngine != ActiveEngine.MUSIC) return
                val newIndex = exoPlayer?.currentMediaItemIndex ?: 0
                musicQueueManager.onTrackChanged(newIndex)
                val track = musicQueueManager.currentTrack
                musicPlaybackManager.updateTrack(track)
                if (track != null) {
                    applyNormalizationGain(track.normalizationGain)
                    musicProgressReporter.onPlaybackStarted(track.id, 0L)
                }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                if (activeEngine != ActiveEngine.MUSIC) return
                musicPlaybackManager.updateRepeatMode(
                    when (repeatMode) {
                        Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                        Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                        else -> RepeatMode.OFF
                    }
                )
            }

            override fun onPlayerError(error: PlaybackException) {
                when (activeEngine) {
                    ActiveEngine.ABS -> {
                        Timber.e(error, "ABS playback error")
                        absPlaybackManager.updatePlayingState(false)
                        absPlaybackManager.updateBufferingState(false)
                        absPlaybackManager.updatePlayerError(error.message ?: "Playback failed")
                    }
                    ActiveEngine.MUSIC -> {
                        Timber.e(error, "Music playback error")
                        musicPlaybackManager.updateBufferingState(false)
                        val player = exoPlayer ?: return
                        if (player.hasNextMediaItem()) {
                            player.seekToNextMediaItem()
                            player.play()
                        } else {
                            musicPlaybackManager.updatePlayingState(false)
                        }
                    }
                    ActiveEngine.NONE -> {}
                }
            }
        }

    private val analyticsListener =
        object : AnalyticsListener {
            override fun onAudioDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long,
            ) {
                if (activeEngine != ActiveEngine.ABS) return
                val codecInfo =
                    MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.find {
                        it.name == decoderName
                    }
                absPlaybackManager.updateAudioDecoder(
                    decoderName = decoderName,
                    isHardwareAccelerated = codecInfo?.isHardwareAccelerated == true,
                )
            }
        }

    private fun startMusicQueueListener() {
        musicQueueJob?.cancel()
        musicQueueJob = serviceScope.launch {
            musicQueueManager.loadQueueEvents.collectLatest { event ->
                if (System.currentTimeMillis() - event.timestamp < 30_000L) {
                    val player = exoPlayer ?: return@collectLatest
                    player.setMediaItems(event.mediaItems, event.startIndex, event.startPositionMs)
                    player.prepare()
                    player.play()
                }
            }
        }
    }

    private fun startMusicRearrangeListener() {
        musicRearrangeJob?.cancel()
        musicRearrangeJob = serviceScope.launch {
            musicQueueManager.rearrangeQueueEvents.collect { event ->
                val player = exoPlayer ?: return@collect
                val currentIdx = player.currentMediaItemIndex
                if (player.mediaItemCount > currentIdx + 1) {
                    player.removeMediaItems(currentIdx + 1, player.mediaItemCount)
                }
                if (currentIdx > 0) {
                    player.removeMediaItems(0, currentIdx)
                }
                val itemsBefore = event.mediaItems.take(event.currentIndex)
                if (itemsBefore.isNotEmpty()) player.addMediaItems(0, itemsBefore)
                val itemsAfter = event.mediaItems.drop(event.currentIndex + 1)
                if (itemsAfter.isNotEmpty())
                    player.addMediaItems(event.currentIndex + 1, itemsAfter)
            }
        }
    }

    private fun startPositionUpdates() {
        stopPositionUpdates()
        positionUpdateJob = serviceScope.launch {
            while (isActive) {
                val player = exoPlayer ?: break
                when (activeEngine) {
                    ActiveEngine.ABS -> updateAbsPosition(player)
                    ActiveEngine.MUSIC -> {
                        musicPlaybackManager.updatePosition(
                            positionMs = player.currentPosition,
                            bufferedPositionMs = player.bufferedPosition,
                            durationMs = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L,
                        )
                        musicQueueManager.onPositionChanged(player.currentPosition)
                    }
                    ActiveEngine.NONE -> break
                }
                delay(1_000L)
            }
        }
    }

    private fun updateAbsPosition(player: ExoPlayer) {
        val state = absPlaybackManager.playbackState.value
        val idx = player.currentMediaItemIndex
        val posInItemSec = player.currentPosition / 1000.0
        val bufInItemSec = player.bufferedPosition / 1000.0

        val totalPosition =
            if (state.isChapterBasedPlayback) {
                state.chapters.getOrNull(idx)?.let { it.start + posInItemSec } ?: posInItemSec
            } else {
                var acc = 0.0
                for (i in 0 until idx) acc += state.audioTracks.getOrNull(i)?.duration ?: 0.0
                acc + posInItemSec
            }

        val totalBuffered =
            if (state.isChapterBasedPlayback) {
                state.chapters.getOrNull(idx)?.let { it.start + bufInItemSec } ?: bufInItemSec
            } else {
                var acc = 0.0
                for (i in 0 until idx) acc += state.audioTracks.getOrNull(i)?.duration ?: 0.0
                acc + bufInItemSec
            }

        absPlaybackManager.updatePosition(totalPosition, totalBuffered)
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    private fun applyNormalizationGain(gainDb: Float?) {
        val player = exoPlayer ?: return
        player.volume = if (gainDb == null) 1f else (10f.pow(gainDb / 20f)).coerceIn(0f, 1f)
    }

    private fun absCustomLayout() =
        ImmutableList.of(
            CommandButton.Builder(CommandButton.ICON_REWIND)
                .setSessionCommand(SessionCommand("action_rewind", Bundle.EMPTY))
                .setDisplayName("Rewind")
                .setEnabled(true)
                .build(),
            CommandButton.Builder(CommandButton.ICON_FAST_FORWARD)
                .setSessionCommand(SessionCommand("action_forward", Bundle.EMPTY))
                .setDisplayName("Forward")
                .setEnabled(true)
                .build(),
        )

    private fun musicCustomLayout() =
        ImmutableList.of(
            CommandButton.Builder(CommandButton.ICON_SHUFFLE_ON)
                .setSessionCommand(SessionCommand("action_shuffle", Bundle.EMPTY))
                .setDisplayName("Shuffle")
                .setEnabled(true)
                .build(),
            CommandButton.Builder(CommandButton.ICON_REPEAT_ALL)
                .setSessionCommand(SessionCommand("action_repeat", Bundle.EMPTY))
                .setDisplayName("Repeat")
                .setEnabled(true)
                .build(),
        )

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    private inner class AudioServiceCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val allCommands =
                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(SessionCommand("action_rewind", Bundle.EMPTY))
                    .add(SessionCommand("action_forward", Bundle.EMPTY))
                    .add(SessionCommand("action_shuffle", Bundle.EMPTY))
                    .add(SessionCommand("action_repeat", Bundle.EMPTY))
                    .build()
            val layout =
                if (activeEngine == ActiveEngine.MUSIC) musicCustomLayout() else absCustomLayout()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(allCommands)
                .setCustomLayout(layout)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            val player =
                exoPlayer
                    ?: return Futures.immediateFuture(SessionResult(SessionError.ERROR_UNKNOWN))
            when (customCommand.customAction) {
                "action_rewind" -> {
                    player.seekTo(maxOf(0, player.currentPosition - 15_000))
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                "action_forward" -> {
                    val next =
                        if (player.duration != C.TIME_UNSET)
                            minOf(player.duration, player.currentPosition + 30_000)
                        else player.currentPosition + 30_000
                    player.seekTo(next)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                "action_shuffle" -> {
                    musicQueueManager.toggleShuffle(player.currentPosition)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                "action_repeat" -> {
                    player.repeatMode =
                        when (player.repeatMode) {
                            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                            else -> Player.REPEAT_MODE_OFF
                        }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
            Futures.immediateFuture(MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L))
    }

    override fun onDestroy() {
        absEqualizerManager.releaseEqualizer()
        if (activeEngine == ActiveEngine.MUSIC) {
            musicProgressReporter.onPlaybackStopped(exoPlayer?.currentPosition ?: 0L)
        }
        absPlaybackManager.clearSession()
        musicPlaybackManager.clearPlayer()
        musicPlaybackManager.updateTrack(null)
        mediaSession?.run {
            removeSession(this)
            player.release()
            release()
            mediaSession = null
        }
        stopPositionUpdates()
        musicQueueJob?.cancel()
        musicRearrangeJob?.cancel()
        skipSilenceJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }
}
