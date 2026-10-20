package com.makd.afinity.cast

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.makd.afinity.MainActivity
import com.makd.afinity.data.manager.PendingNavigationManager
import com.makd.afinity.navigation.Destination
import com.makd.afinity.player.audiobookshelf.AudiobookshelfPlaybackManager
import com.makd.afinity.ui.player.PlayerLauncher
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class CastRemoteRouterActivity : ComponentActivity() {

    @Inject lateinit var castManager: CastManager
    @Inject lateinit var pendingNavigationManager: PendingNavigationManager
    @Inject lateinit var audiobookshelfPlaybackManager: AudiobookshelfPlaybackManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openRemote()
        finish()
    }

    @OptIn(UnstableApi::class)
    private fun openRemote() {
        val state = castManager.castState.value
        val itemId = state.currentItemId
        val mediaSourceId = state.mediaSourceId
        when {
            state.isMusicCasting -> openMainAt(Destination.MUSIC_PLAYER_ROUTE)
            state.isAbsCasting -> {
                val absState = audiobookshelfPlaybackManager.playbackState.value
                val absItemId = absState.itemId
                if (absItemId != null) {
                    openMainAt(
                        Destination.createAudiobookshelfPlayerRoute(absItemId, absState.episodeId)
                    )
                } else {
                    openApp()
                }
            }
            state.isConnected && itemId != null && mediaSourceId != null ->
                PlayerLauncher.launchCastRemote(
                    context = this,
                    itemId = itemId,
                    mediaSourceId = mediaSourceId,
                    audioStreamIndex = state.audioStreamIndex,
                    subtitleStreamIndex = state.subtitleStreamIndex,
                    positionMs = state.currentPosition,
                )
            else -> openApp()
        }
    }

    private fun openMainAt(route: String) {
        pendingNavigationManager.navigateTo(route)
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        )
    }

    private fun openApp() {
        packageManager.getLaunchIntentForPackage(packageName)?.let { startActivity(it) }
    }
}
