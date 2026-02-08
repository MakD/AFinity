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
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.audiobookshelf.PlaybackSession
import com.makd.afinity.data.models.audiobookshelf.PodcastEpisode
import com.makd.afinity.data.repository.AudiobookshelfRepository
import com.makd.afinity.data.repository.SecurePreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber

@Singleton
class AudiobookshelfPlayer
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val playbackManager: AudiobookshelfPlaybackManager,
    private val securePreferencesRepository: SecurePreferencesRepository,
    private val audiobookshelfRepository: AudiobookshelfRepository,
    private val sessionManager: SessionManager,
) {
    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var sleepTimerJob: Job? = null

    init {
        scope.launch {
            var previousSessionKey: String? = null
            sessionManager.currentSession.collect { session ->
                val newKey = session?.let { "${it.serverId}/${it.userId}" }
                if (previousSessionKey != null && newKey != previousSessionKey) {
                    if (playbackManager.playbackState.value.sessionId != null) {
                        Timber.d("Jellyfin session changed, closing Audiobookshelf playback")
                        closeSession()
                    }
                }
                previousSessionKey = newKey
            }
        }

        scope.launch {
            var wasAuthenticated = false
            audiobookshelfRepository.isAuthenticated.collect { isAuthenticated ->
                if (wasAuthenticated && !isAuthenticated) {
                    if (playbackManager.playbackState.value.sessionId != null) {
                        Timber.d("Audiobookshelf auth lost, closing playback")
                        closeSession()
                    }
                }
                wasAuthenticated = isAuthenticated
            }
        }
    }

    private suspend fun getConnectedController(): MediaController? {
        if (mediaController != null) return mediaController

        val sessionToken =
            SessionToken(context, ComponentName(context, AudiobookshelfPlayerService::class.java))

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

    fun loadSession(
        session: PlaybackSession,
        baseUrl: String,
        startPosition: Double? = null,
        episodeSort: String? = null,
    ) {
        scope.launch {
            val controller = getConnectedController() ?: return@launch

            val token = securePreferencesRepository.getCachedAudiobookshelfToken()
            val isPodcastWithoutEpisode =
                session.audioTracks.isNullOrEmpty() && session.mediaType == "podcast"
            val unsortedEpisodes = session.libraryItem?.media?.episodes ?: emptyList()
            val episodes =
                if (isPodcastWithoutEpisode && episodeSort != null) {
                    sortEpisodes(unsortedEpisodes, episodeSort)
                } else {
                    unsortedEpisodes
                }

            val audioTracks =
                if (isPodcastWithoutEpisode) {
                    val allTracks =
                        episodes.mapNotNull { episode ->
                            episode.audioTrack?.let { track -> track.copy(title = episode.title) }
                        }
                    if (allTracks.isNotEmpty()) {
                        Timber.d("Loading ${allTracks.size} episodes for podcast playlist")
                        allTracks
                    } else {
                        Timber.e("No audio tracks found in any episodes")
                        return@launch
                    }
                } else if (session.audioTracks.isNullOrEmpty()) {
                    Timber.e("No audio tracks found in session. Session data: $session")
                    return@launch
                } else {
                    session.audioTracks
                }

            val enhancedSession =
                if (isPodcastWithoutEpisode && episodes.isNotEmpty()) {
                    var accumulatedTime = 0.0
                    val episodeChapters =
                        episodes.mapNotNull { episode ->
                            episode.audioTrack?.let {
                                val chapter =
                                    com.makd.afinity.data.models.audiobookshelf.BookChapter(
                                        id = episodes.indexOf(episode),
                                        start = accumulatedTime,
                                        end = accumulatedTime + (episode.duration ?: 0.0),
                                        title = episode.title,
                                    )
                                accumulatedTime += episode.duration ?: 0.0
                                chapter
                            }
                        }
                    val totalDuration = episodes.sumOf { it.duration ?: 0.0 }

                    session.copy(
                        audioTracks = audioTracks,
                        displayTitle =
                            session.mediaMetadata?.title ?: session.displayTitle ?: "Podcast",
                        displayAuthor = session.mediaMetadata?.authorName ?: session.displayAuthor,
                        duration = totalDuration,
                        chapters = episodeChapters,
                    )
                } else {
                    session
                }

            playbackManager.setSession(enhancedSession, baseUrl, token)

            val mediaItems =
                audioTracks.mapIndexed { index, track ->
                    val url =
                        if (track.contentUrl?.startsWith("http") == true) track.contentUrl
                        else "$baseUrl${track.contentUrl}"

                    val artUrl =
                        if (baseUrl.isNotEmpty()) {
                            "$baseUrl/api/items/${enhancedSession.libraryItemId}/cover?token=$token"
                        } else enhancedSession.coverPath

                    val itemTitle =
                        if (isPodcastWithoutEpisode && track.title != null) {
                            track.title
                        } else {
                            enhancedSession.displayTitle
                                ?: enhancedSession.mediaMetadata?.title
                                ?: "Unknown"
                        }

                    val metadata =
                        MediaMetadata.Builder()
                            .setTitle(itemTitle)
                            .setArtist(
                                enhancedSession.displayAuthor
                                    ?: enhancedSession.mediaMetadata?.authorName
                                    ?: ""
                            )
                            .setArtworkUri(Uri.parse(artUrl))
                            .build()

                    MediaItem.Builder()
                        .setUri(url)
                        .setMediaId(index.toString())
                        .setMediaMetadata(metadata)
                        .build()
                }

            Timber.d("Sending ${mediaItems.size} items to player")

            controller.stop()
            controller.clearMediaItems()
            controller.setMediaItems(mediaItems)
            controller.prepare()

            val seekPosition = startPosition ?: session.currentTime
            if (seekPosition > 0) {
                seekToPosition(seekPosition)
            }
            controller.play()
        }
    }

    private fun sortEpisodes(
        episodes: List<PodcastEpisode>,
        sortParam: String,
    ): List<PodcastEpisode> {
        val parts = sortParam.split("_")
        val ascending = parts.lastOrNull() == "asc"
        val sortType = parts.dropLast(1).joinToString("_")

        val cmp = Comparator<String> { a, b ->
            val pattern = Regex("(\\d+|\\D+)")
            val aParts = pattern.findAll(a).map { it.value }.toList()
            val bParts = pattern.findAll(b).map { it.value }.toList()
            for (i in 0 until minOf(aParts.size, bParts.size)) {
                val ap = aParts[i]
                val bp = bParts[i]
                val aNum = ap.toBigIntegerOrNull()
                val bNum = bp.toBigIntegerOrNull()
                val result =
                    if (aNum != null && bNum != null) aNum.compareTo(bNum)
                    else ap.compareTo(bp, ignoreCase = true)
                if (result != 0) return@Comparator result
            }
            aParts.size - bParts.size
        }

        val sorted =
            when (sortType) {
                "pub_date" ->
                    episodes.sortedBy { it.publishedAt ?: 0L }
                "title" ->
                    episodes.sortedWith(
                        compareBy<PodcastEpisode, String>(cmp) { it.title }
                    )
                "season" ->
                    episodes.sortedWith(
                        compareBy<PodcastEpisode, String>(cmp) {
                            it.season ?: ""
                        }.thenBy(cmp) { it.episode ?: "" }
                    )
                "episode" ->
                    episodes.sortedWith(
                        compareBy<PodcastEpisode, String>(cmp) { it.episode ?: "" }
                    )
                "filename" ->
                    episodes.sortedWith(
                        compareBy<PodcastEpisode, String>(cmp) {
                            it.audioFile?.metadata?.filename ?: ""
                        }
                    )
                else -> episodes
            }

        return if (ascending) sorted else sorted.reversed()
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

        sleepTimerJob =
            scope.launch {
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
        val state = playbackManager.playbackState.value
        val sessionId = state.sessionId
        if (sessionId != null) {
            scope.launch {
                try {
                    val result =
                        audiobookshelfRepository.closePlaybackSession(
                            sessionId = sessionId,
                            currentTime = state.currentTime,
                            timeListened = state.currentTime,
                            duration = state.duration,
                        )
                    if (result.isSuccess) {
                        Timber.d("Session closed on server: $sessionId")
                    } else {
                        Timber.e(
                            "Failed to close session on server: ${result.exceptionOrNull()?.message}"
                        )
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error closing session on server")
                }
            }
        }

        mediaController?.stop()
        mediaController?.clearMediaItems()

        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
        controllerFuture = null

        playbackManager.clearSession()
        Timber.d("Session closed locally")
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
            MoreExecutors.directExecutor(),
        )

        continuation.invokeOnCancellation { cancel(false) }
    }
}
