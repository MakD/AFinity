package com.makd.afinity.player.audiobookshelf

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.makd.afinity.MainActivity
import com.makd.afinity.R
import com.makd.afinity.util.NetworkConnectivityMonitor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class AudiobookshelfPlayerService : MediaSessionService() {

    @Inject
    lateinit var audiobookshelfPlayer: AudiobookshelfPlayer

    @Inject
    lateinit var playbackManager: AudiobookshelfPlaybackManager

    @Inject
    lateinit var networkConnectivityMonitor: NetworkConnectivityMonitor

    private var mediaSession: MediaSession? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        audiobookshelfPlayer.initialize()

        val player = audiobookshelfPlayer.exoPlayer
            ?: throw IllegalStateException("Player must be initialized")

        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()

        val notificationProvider = DefaultMediaNotificationProvider(this)
        notificationProvider.setSmallIcon(R.drawable.ic_headphones_filled)
        setMediaNotificationProvider(notificationProvider)

        serviceScope.launch {
            networkConnectivityMonitor.isNetworkAvailable.collect { isConnected ->
                Timber.d("Service Network Monitor: Connected = $isConnected")
            }
        }

        Timber.d("AudiobookshelfPlayerService created")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            release()
        }
        mediaSession = null

        Timber.d("AudiobookshelfPlayerService destroyed")
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
