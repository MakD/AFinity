package com.makd.afinity.ui.item.components.shared

import android.view.LayoutInflater
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.makd.afinity.R

@UnstableApi
@Composable
fun TrailerPlayer(
    trailerUrl: String,
    isMuted: Boolean,
    onVideoReady: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    val exoPlayer = remember {
        ExoPlayer.Builder(context)
            .build()
            .apply {
                volume = 0f
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = true
            }
    }

    val playerView = remember {
        (LayoutInflater.from(context).inflate(R.layout.view_trailer_player, null) as PlayerView)
            .apply { player = exoPlayer }
    }

    LaunchedEffect(trailerUrl) {
        exoPlayer.setMediaItem(MediaItem.fromUri(trailerUrl))
        exoPlayer.prepare()
    }

    LaunchedEffect(isMuted) {
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) onVideoReady()
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            playerView.player = null
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = { playerView },
        modifier = modifier,
    )
}