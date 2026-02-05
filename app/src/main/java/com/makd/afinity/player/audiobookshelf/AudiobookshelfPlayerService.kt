package com.makd.afinity.player.audiobookshelf

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.makd.afinity.MainActivity
import com.makd.afinity.R
import com.makd.afinity.data.repository.SecurePreferencesRepository
import com.makd.afinity.util.NetworkConnectivityMonitor
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

@AndroidEntryPoint
class AudiobookshelfPlayerService : MediaSessionService() {

    @Inject
    lateinit var playbackManager: AudiobookshelfPlaybackManager
    @Inject
    lateinit var progressSyncer: AudiobookshelfProgressSyncer
    @Inject
    lateinit var securePreferencesRepository: SecurePreferencesRepository
    @Inject
    lateinit var networkConnectivityMonitor: NetworkConnectivityMonitor

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var positionUpdateJob: Job? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val token = securePreferencesRepository.getCachedAudiobookshelfToken()
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(buildMap {
                if (token != null) put("Authorization", "Bearer $token")
            })

        exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()

        exoPlayer?.addListener(object : Player.Listener {
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

                    else -> {}
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Timber.e(error, "Player error")
                playbackManager.updatePlayingState(false)
            }
        })

        val sessionIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, sessionIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, exoPlayer!!)
            .setSessionActivity(pendingIntent)
            .setCallback(CustomMediaSessionCallback())
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId("afinity_audiobook_playback")
                .setChannelName(R.string.playback_channel_name)
                .build().apply {
                    setSmallIcon(R.drawable.ic_launcher_monochrome)
                }
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    @UnstableApi
    private inner class CustomMediaSessionCallback : MediaSession.Callback {
        @UnstableApi
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session).build()
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            Timber.d("System requested playback resumption")
            return Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(
                    emptyList(),
                    0,
                    0L
                )
            )
        }
    }

    private fun startPositionUpdates() {
        stopPositionUpdates()
        positionUpdateJob = serviceScope.launch {
            while (isActive) {
                val player = exoPlayer ?: break

                val currentMediaItemIndex = player.currentMediaItemIndex
                val audioTracks = playbackManager.playbackState.value.audioTracks
                var totalPosition = 0.0

                for (i in 0 until currentMediaItemIndex) {
                    totalPosition += audioTracks.getOrNull(i)?.duration ?: 0.0
                }
                totalPosition += player.currentPosition / 1000.0

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