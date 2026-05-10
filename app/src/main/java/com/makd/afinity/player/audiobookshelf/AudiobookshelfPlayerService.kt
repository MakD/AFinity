package com.makd.afinity.player.audiobookshelf

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
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
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.makd.afinity.MainActivity
import com.makd.afinity.R
import com.makd.afinity.data.repository.SecurePreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@UnstableApi
@AndroidEntryPoint
class AudiobookshelfPlayerService : MediaSessionService() {

    @Inject lateinit var playbackManager: AudiobookshelfPlaybackManager
    @Inject lateinit var progressSyncer: AudiobookshelfProgressSyncer
    @Inject lateinit var securePreferencesRepository: SecurePreferencesRepository
    @Inject lateinit var equalizerManager: AudiobookshelfEqualizerManager
    @Inject lateinit var skipSilenceManager: AudiobookshelfSkipSilenceManager

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var positionUpdateJob: Job? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val token = securePreferencesRepository.getCachedAudiobookshelfToken()
        val httpDataSourceFactory =
            DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(15_000)
                .setDefaultRequestProperties(
                    buildMap { if (token != null) put("Authorization", "Bearer $token") }
                )
        val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)
        val retryPolicy =
            object : DefaultLoadErrorHandlingPolicy() {
                override fun getRetryDelayMsFor(
                    loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo
                ): Long = minOf(1_000L shl loadErrorInfo.errorCount.coerceAtMost(5), 30_000L)

                override fun getMinimumLoadableRetryCount(dataType: Int) = Int.MAX_VALUE
            }

        val loadControl =
            DefaultLoadControl.Builder().setBufferDurationsMs(15_000, 60_000, 2_500, 5_000).build()
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
                        val eac3JocSafeSelector =
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
                            eac3JocSafeSelector,
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
        exoPlayer =
            ExoPlayer.Builder(this, renderersFactory)
                .setAudioAttributes(AudioAttributes.DEFAULT, true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .setLoadControl(loadControl)
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(dataSourceFactory)
                        .setLoadErrorHandlingPolicy(retryPolicy)
                )
                .build()

        serviceScope.launch {
            skipSilenceManager.isEnabled.collect { isEnabled ->
                exoPlayer?.skipSilenceEnabled = isEnabled
            }
        }

        exoPlayer?.addListener(
            object : Player.Listener {
                override fun onAudioSessionIdChanged(audioSessionId: Int) {
                    equalizerManager.attachToSession(audioSessionId)
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    playbackManager.updatePlayingState(isPlaying)
                    if (isPlaying) {
                        startPositionUpdates()
                        progressSyncer.startSyncing()
                    } else {
                        stopPositionUpdates()
                        serviceScope.launch { progressSyncer.syncNow() }
                        progressSyncer.stopSyncing()
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_BUFFERING -> playbackManager.updateBufferingState(true)
                        Player.STATE_READY -> playbackManager.updateBufferingState(false)
                        Player.STATE_ENDED -> {
                            playbackManager.updatePlayingState(false)
                            stopPositionUpdates()
                            serviceScope.launch { progressSyncer.syncNow() }
                        }

                        Player.STATE_IDLE -> {
                            playbackManager.updateBufferingState(false)
                        }
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    Timber.e(error, "Player error")
                    playbackManager.updatePlayingState(false)
                    playbackManager.updateBufferingState(false)
                }
            }
        )

        val sessionIntent =
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                sessionIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        mediaSession =
            MediaSession.Builder(this, exoPlayer!!)
                .setSessionActivity(pendingIntent)
                .setCallback(CustomMediaSessionCallback())
                .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId("afinity_audiobook_playback")
                .setChannelName(R.string.playback_channel_name)
                .build()
                .apply { setSmallIcon(R.drawable.ic_launcher_monochrome) }
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    @UnstableApi
    private class CustomMediaSessionCallback : MediaSession.Callback {
        private val REWIND_COMMAND = SessionCommand("action_rewind", Bundle.EMPTY)
        private val FORWARD_COMMAND = SessionCommand("action_forward", Bundle.EMPTY)

        @UnstableApi
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {

            val sessionCommands =
                MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                    .add(REWIND_COMMAND)
                    .add(FORWARD_COMMAND)
                    .build()

            val rewindButton =
                CommandButton.Builder(CommandButton.ICON_REWIND)
                    .setSessionCommand(REWIND_COMMAND)
                    .setDisplayName("Rewind")
                    .setEnabled(true)
                    .build()

            val forwardButton =
                CommandButton.Builder(CommandButton.ICON_FAST_FORWARD)
                    .setSessionCommand(FORWARD_COMMAND)
                    .setDisplayName("Forward")
                    .setEnabled(true)
                    .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setCustomLayout(ImmutableList.of(rewindButton, forwardButton))
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            val player = session.player

            when (customCommand.customAction) {
                "action_rewind" -> {
                    player.seekTo(maxOf(0, player.currentPosition - 15000))
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
                "action_forward" -> {
                    val duration = player.duration
                    val nextPos =
                        if (duration != C.TIME_UNSET) {
                            minOf(duration, player.currentPosition + 30000)
                        } else {
                            player.currentPosition + 30000
                        }
                    player.seekTo(nextPos)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            Timber.d("System requested playback resumption")
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L)
            )
        }
    }

    private fun startPositionUpdates() {
        stopPositionUpdates()
        positionUpdateJob = serviceScope.launch {
            while (isActive) {
                val player = exoPlayer ?: break
                val state = playbackManager.playbackState.value
                val idx = player.currentMediaItemIndex
                val positionInItemSeconds = player.currentPosition / 1000.0

                val totalPosition = if (state.isChapterBasedPlayback) {
                    val chapter = state.chapters.getOrNull(idx)
                    if (chapter != null) chapter.start + positionInItemSeconds else positionInItemSeconds
                } else {
                    var accumulated = 0.0
                    for (i in 0 until idx) accumulated += state.audioTracks.getOrNull(i)?.duration ?: 0.0
                    accumulated + positionInItemSeconds
                }

                playbackManager.updatePosition(totalPosition)
                delay(1000)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    override fun onDestroy() {
        equalizerManager.releaseEqualizer()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        stopPositionUpdates()
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
