package com.makd.afinity.player.audiobookshelf

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.makd.afinity.data.repository.AudiobookshelfRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudiobookshelfProgressSyncer
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val audiobookshelfRepository: AudiobookshelfRepository,
    private val playbackManager: AudiobookshelfPlaybackManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncJob: Job? = null
    private var lastSyncTime: Double = 0.0
    private var totalTimeListened: Double = 0.0
    private var sessionStartTime: Long = 0L
    private var currentPlaylistEpisodeId: String? = null

    companion object {
        private const val WIFI_SYNC_INTERVAL_MS = 15_000L
        private const val CELLULAR_SYNC_INTERVAL_MS = 60_000L
    }

    fun startSyncing() {
        stopSyncing()

        sessionStartTime = System.currentTimeMillis()
        lastSyncTime = playbackManager.playbackState.value.currentTime
        totalTimeListened = 0.0
        currentPlaylistEpisodeId = playbackManager.playbackState.value.episodeId

        syncJob =
            scope.launch {
                while (true) {
                    val interval = getSyncInterval()
                    delay(interval)

                    syncProgress()
                }
            }

        Timber.d("Progress syncer started")
    }

    fun stopSyncing() {
        syncJob?.cancel()
        syncJob = null
        Timber.d("Progress syncer stopped")
    }

    suspend fun syncNow() {
        syncProgress()
    }

    private suspend fun syncProgress() {
        val state = playbackManager.playbackState.value
        val sessionId = state.sessionId ?: return

        if (state.isPodcastPlaylist) {
            syncPlaylistProgress(state, sessionId)
        } else {
            syncStandardProgress(state, sessionId)
        }
    }

    private suspend fun syncStandardProgress(state: AudiobookshelfPlaybackState, sessionId: String) {
        val currentTime = state.currentTime
        val timeListenedSinceLastSync = (currentTime - lastSyncTime).coerceAtLeast(0.0)
        totalTimeListened += timeListenedSinceLastSync
        lastSyncTime = currentTime

        try {
            val result =
                audiobookshelfRepository.syncPlaybackSession(
                    sessionId = sessionId,
                    timeListened = timeListenedSinceLastSync,
                    currentTime = currentTime,
                    duration = state.duration,
                )

            result.fold(
                onSuccess = { Timber.d("Progress synced: ${currentTime}s / ${state.duration}s") },
                onFailure = { error -> Timber.w(error, "Failed to sync progress, will retry") },
            )
        } catch (e: Exception) {
            Timber.e(e, "Exception syncing progress")
        }
    }

    private suspend fun syncPlaylistProgress(
        state: AudiobookshelfPlaybackState,
        sessionId: String,
    ) {
        val currentChapter = state.currentChapter ?: return
        val chapterIndex = state.chapters.indexOf(currentChapter)
        val episodeId =
            state.playlistEpisodeIds.getOrNull(chapterIndex) ?: return

        val episodeCurrentTime = (state.currentTime - currentChapter.start).coerceAtLeast(0.0)
        val episodeDuration = (currentChapter.end - currentChapter.start).coerceAtLeast(0.0)

        if (episodeId != currentPlaylistEpisodeId) {
            handleEpisodeTransition(state, sessionId, episodeId)
            return
        }

        val timeListenedSinceLastSync =
            (state.currentTime - lastSyncTime).coerceAtLeast(0.0)
        lastSyncTime = state.currentTime

        try {
            val result =
                audiobookshelfRepository.syncPlaybackSession(
                    sessionId = state.sessionId ?: return,
                    timeListened = timeListenedSinceLastSync,
                    currentTime = episodeCurrentTime,
                    duration = episodeDuration,
                )

            result.fold(
                onSuccess = {
                    Timber.d(
                        "Playlist progress synced: episode=$episodeId, " +
                            "${episodeCurrentTime}s / ${episodeDuration}s"
                    )
                },
                onFailure = { error -> Timber.w(error, "Failed to sync playlist progress") },
            )
        } catch (e: Exception) {
            Timber.e(e, "Exception syncing playlist progress")
        }
    }

    private suspend fun handleEpisodeTransition(
        state: AudiobookshelfPlaybackState,
        oldSessionId: String,
        newEpisodeId: String,
    ) {
        val itemId = state.itemId ?: return

        Timber.d(
            "Playlist episode transition: ${currentPlaylistEpisodeId} -> $newEpisodeId"
        )

        val prevEpisodeId = currentPlaylistEpisodeId
        if (prevEpisodeId != null) {
            val prevChapter = state.chapters.find { chapter ->
                val idx = state.chapters.indexOf(chapter)
                state.playlistEpisodeIds.getOrNull(idx) == prevEpisodeId
            }
            if (prevChapter != null) {
                val prevDuration = (prevChapter.end - prevChapter.start).coerceAtLeast(0.0)
                try {
                    audiobookshelfRepository.closePlaybackSession(
                        sessionId = oldSessionId,
                        currentTime = prevDuration,
                        timeListened = prevDuration,
                        duration = prevDuration,
                    )
                    Timber.d("Closed session for episode: $prevEpisodeId")
                } catch (e: Exception) {
                    Timber.e(e, "Failed to close previous episode session")
                }
            }
        }

        try {
            val result = audiobookshelfRepository.startPlaybackSession(itemId, newEpisodeId)
            result.fold(
                onSuccess = { newSession ->
                    currentPlaylistEpisodeId = newEpisodeId
                    playbackManager.updateSessionInfo(newSession.id, newEpisodeId)
                    lastSyncTime = state.currentTime
                    totalTimeListened = 0.0
                    Timber.d("Started new session for episode: $newEpisodeId (${newSession.id})")
                },
                onFailure = { error ->
                    Timber.e(error, "Failed to start session for new episode: $newEpisodeId")
                },
            )
        } catch (e: Exception) {
            Timber.e(e, "Exception starting new episode session")
        }
    }

    private fun getSyncInterval(): Long {
        return if (isOnWifi()) {
            WIFI_SYNC_INTERVAL_MS
        } else {
            CELLULAR_SYNC_INTERVAL_MS
        }
    }

    private fun isOnWifi(): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }
}
