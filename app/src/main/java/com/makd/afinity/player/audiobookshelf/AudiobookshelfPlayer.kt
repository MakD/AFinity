package com.makd.afinity.player.audiobookshelf

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.makd.afinity.data.models.audiobookshelf.PlaybackSession
import com.makd.afinity.data.repository.AudiobookshelfRepository
import com.makd.afinity.data.repository.SecurePreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class AudiobookshelfPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playbackManager: AudiobookshelfPlaybackManager,
    private val securePreferencesRepository: SecurePreferencesRepository,
    private val audiobookshelfRepository: AudiobookshelfRepository
) {
    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var sleepTimerJob: Job? = null

    private suspend fun getConnectedController(): MediaController? {
        if (mediaController != null) return mediaController

        val sessionToken = SessionToken(
            context,
            ComponentName(context, AudiobookshelfPlayerService::class.java)
        )

        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future

        return try {
            mediaController = future.await()
            Timber.d("MediaController connected to Service")
            mediaController
        } catch (e: Exception) {
            Timber.e(e, "Failed to connect MediaController")
            null
        }
    }

    fun loadSession(session: PlaybackSession, baseUrl: String) {
        scope.launch {
            val controller = getConnectedController() ?: return@launch

            val token = securePreferencesRepository.getCachedAudiobookshelfToken()
            playbackManager.setSession(session, baseUrl, token)

            val audioTracks = session.audioTracks
            if (audioTracks.isNullOrEmpty()) {
                Timber.e("No audio tracks found in session")
                return@launch
            }

            val mediaItems = audioTracks.map { track ->
                val url = if (track.contentUrl?.startsWith("http") == true) track.contentUrl
                else "$baseUrl${track.contentUrl}"

                val artUrl = if (baseUrl.isNotEmpty()) {
                    "$baseUrl/api/items/${session.libraryItemId}/cover?token=$token"
                } else session.coverPath

                val metadata = MediaMetadata.Builder()
                    .setTitle(session.displayTitle)
                    .setArtist(session.displayAuthor)
                    .setArtworkUri(Uri.parse(artUrl))
                    .build()

                MediaItem.Builder()
                    .setUri(url)
                    .setMediaId(track.index.toString())
                    .setMediaMetadata(metadata)
                    .build()
            }

            Timber.d("Sending ${mediaItems.size} items to player")

            controller.stop()
            controller.clearMediaItems()
            controller.setMediaItems(mediaItems)
            controller.prepare()

            if (session.currentTime > 0) {
                seekToPosition(session.currentTime)
            }
            controller.play()
        }
    }

    fun play() = mediaController?.play()

    fun pause() = mediaController?.pause()

    fun isPlaying(): Boolean = mediaController?.isPlaying == true

    fun setPlaybackSpeed(speed: Float) {
        mediaController?.setPlaybackSpeed(speed)
        playbackManager.updatePlaybackSpeed(speed)
    }

    fun seekToPosition(positionSeconds: Double) {
        val controller = mediaController ?: return
        val audioTracks = playbackManager.currentSession.value?.audioTracks ?: return
        var accumulatedDuration = 0.0

        for ((index, track) in audioTracks.withIndex()) {
            if (positionSeconds < accumulatedDuration + track.duration) {
                val positionInTrack = positionSeconds - accumulatedDuration
                controller.seekTo(index, (positionInTrack * 1000).toLong())
                break
            }
            accumulatedDuration += track.duration
        }
    }

    fun seekToChapter(chapterIndex: Int) {
        val chapters = playbackManager.playbackState.value.chapters
        if (chapterIndex in chapters.indices) {
            seekToPosition(chapters[chapterIndex].start)
        }
    }

    fun skipForward(seconds: Int = 30) {
        val current = playbackManager.playbackState.value.currentTime
        seekToPosition(current + seconds)
    }

    fun skipBackward(seconds: Int = 30) {
        val current = playbackManager.playbackState.value.currentTime
        seekToPosition((current - seconds).coerceAtLeast(0.0))
    }

    fun setSleepTimer(durationMinutes: Int) {
        cancelSleepTimer()
        if (durationMinutes <= 0) {
            playbackManager.setSleepTimer(null)
            return
        }
        val endTime = System.currentTimeMillis() + (durationMinutes * 60 * 1000L)
        playbackManager.setSleepTimer(endTime)

        sleepTimerJob = scope.launch {
            delay(durationMinutes * 60 * 1000L)
            pause()
            playbackManager.setSleepTimer(null)
            Timber.d("Sleep timer triggered")
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        playbackManager.setSleepTimer(null)
    }

    fun closeSession() {
        cancelSleepTimer()
        mediaController?.stop()
        mediaController?.clearMediaItems()

        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
        controllerFuture = null

        playbackManager.clearSession()
        Timber.d("Session closed")
    }

    fun release() {
        closeSession()
    }
}

private suspend fun <T> ListenableFuture<T>.await(): T {
    return suspendCancellableCoroutine { continuation ->
        addListener(
            {
                try {
                    continuation.resume(get())
                } catch (e: Exception) {
                    if (isCancelled) {
                        continuation.cancel(e)
                    } else {
                        continuation.resumeWithException(e)
                    }
                }
            },
            MoreExecutors.directExecutor()
        )

        continuation.invokeOnCancellation {
            cancel(false)
        }
    }
}