package com.makd.afinity.widget

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.makd.afinity.cast.CastManager
import com.makd.afinity.player.AudioService
import com.makd.afinity.player.audiobookshelf.AudiobookshelfPlaybackManager
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.ExecutionException
import javax.inject.Inject
import timber.log.Timber

@AndroidEntryPoint
class AudioWidgetActionReceiver : BroadcastReceiver() {

    @Inject lateinit var castManager: CastManager
    @Inject lateinit var absPlaybackManager: AudiobookshelfPlaybackManager

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (handleCast(action)) return
        val pending = goAsync()
        val absItemArgs =
            Bundle().apply {
                putString(
                    AudioService.ARG_ITEM_ID,
                    intent.getStringExtra(AudioWidgetIntents.EXTRA_ABS_ITEM_ID),
                )
                putString(
                    AudioService.ARG_EPISODE_ID,
                    intent.getStringExtra(AudioWidgetIntents.EXTRA_ABS_EPISODE_ID),
                )
            }
        WidgetMediaController.run(context.applicationContext, pending::finish) {
            perform(it, action, absItemArgs)
        }
    }

    private fun handleCast(action: String): Boolean {
        val cast = castManager.castState.value
        if (!cast.isMusicCasting && !cast.isAbsCasting) return false
        when (action) {
            AudioWidgetIntents.ACTION_PLAY_PAUSE ->
                if (cast.isPlaying) castManager.pause() else castManager.play()
            AudioWidgetIntents.ACTION_PREVIOUS ->
                if (cast.isMusicCasting) castManager.skipMusicTrack(forward = false)
            AudioWidgetIntents.ACTION_NEXT ->
                if (cast.isMusicCasting) castManager.skipMusicTrack(forward = true)
            AudioWidgetIntents.ACTION_REWIND ->
                if (cast.isAbsCasting) {
                    castManager.seekAbsGlobalPosition(
                        (cast.currentPosition - CAST_SEEK_STEP_MS).coerceAtLeast(0L)
                    )
                }
            AudioWidgetIntents.ACTION_FORWARD ->
                if (cast.isAbsCasting) {
                    val durationMs =
                        (absPlaybackManager.playbackState.value.duration * 1000).toLong()
                    val target = cast.currentPosition + CAST_SEEK_STEP_MS
                    castManager.seekAbsGlobalPosition(
                        if (durationMs > 0) target.coerceAtMost(durationMs) else target
                    )
                }
        }
        return true
    }

    @OptIn(UnstableApi::class)
    private fun perform(controller: MediaController, action: String, absItemArgs: Bundle) {
        when (action) {
            AudioWidgetIntents.ACTION_RESUME_MUSIC ->
                sendCommand(controller, AudioService.COMMAND_RESUME_MUSIC)
            AudioWidgetIntents.ACTION_PLAY_ABS_ITEM ->
                sendCommand(controller, AudioService.COMMAND_PLAY_ABS_ITEM, absItemArgs)
            AudioWidgetIntents.ACTION_PLAY_PAUSE -> Util.handlePlayPauseButtonAction(controller)
            AudioWidgetIntents.ACTION_PREVIOUS -> controller.seekToPrevious()
            AudioWidgetIntents.ACTION_NEXT -> controller.seekToNext()
            AudioWidgetIntents.ACTION_REWIND -> sendCommand(controller, AudioService.COMMAND_REWIND)
            AudioWidgetIntents.ACTION_FORWARD ->
                sendCommand(controller, AudioService.COMMAND_FORWARD)
            AudioWidgetIntents.ACTION_SHUFFLE_ALL ->
                sendCommand(controller, AudioService.COMMAND_SHUFFLE_ALL)
        }
    }

    private fun sendCommand(
        controller: MediaController,
        command: String,
        args: Bundle = Bundle.EMPTY,
    ) {
        controller.sendCustomCommand(SessionCommand(command, Bundle.EMPTY), args)
    }

    private companion object {
        const val CAST_SEEK_STEP_MS = 30_000L
    }
}

private object WidgetMediaController {
    private const val IDLE_RELEASE_MS = 60_000L

    private val handler = Handler(Looper.getMainLooper())
    private var future: ListenableFuture<MediaController>? = null
    private val releaseRunnable = Runnable { release() }

    fun run(context: Context, onDone: () -> Unit, block: (MediaController) -> Unit) {
        val current = future
        if (current != null && current.isDone && current.connectedOrNull()?.isConnected != true) {
            release()
        }
        val active =
            future
                ?: MediaController.Builder(
                        context,
                        SessionToken(context, ComponentName(context, AudioService::class.java)),
                    )
                    .buildAsync()
                    .also { future = it }
        active.addListener(
            {
                try {
                    active.connectedOrNull()?.let(block) ?: release()
                } finally {
                    onDone()
                    handler.removeCallbacks(releaseRunnable)
                    handler.postDelayed(releaseRunnable, IDLE_RELEASE_MS)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }

    private fun ListenableFuture<MediaController>.connectedOrNull(): MediaController? =
        try {
            get()
        } catch (e: ExecutionException) {
            Timber.w(e, "Widget: could not connect to the audio service")
            null
        } catch (e: java.util.concurrent.CancellationException) {
            null
        } catch (e: InterruptedException) {
            null
        }

    private fun release() {
        future?.let { MediaController.releaseFuture(it) }
        future = null
    }
}
