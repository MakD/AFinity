package com.makd.afinity.player.audiobookshelf

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import com.makd.afinity.data.repository.AudiobookshelfRepository
import com.makd.afinity.data.repository.audiobookshelf.AbsProgressSyncScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

@Singleton
class AudiobookshelfProgressSyncer
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val audiobookshelfRepository: AudiobookshelfRepository,
    private val playbackManager: AudiobookshelfPlaybackManager,
    private val absSyncScheduler: AbsProgressSyncScheduler,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncJob: Job? = null
    private var currentPlaylistEpisodeId: String? = null
    private var lastEpisodeTime: Double? = null

    private val episodeMutex = Mutex()
    private val pendingTransitions = AtomicInteger(0)

    private val listenLock = Any()
    private var playingSinceMs: Long? = null
    private var pendingListenedMs: Long = 0L
    private var accountedSessionId: String? = null

    companion object {
        private const val WIFI_SYNC_INTERVAL_MS = 15_000L
        private const val CELLULAR_SYNC_INTERVAL_MS = 60_000L
        private const val FINISHED_RATIO = 0.99
    }

    fun onSessionLoaded(episodeId: String?) {
        currentPlaylistEpisodeId = episodeId
        lastEpisodeTime = null
    }

    fun startSyncing() {
        stopSyncing()

        val state = playbackManager.playbackState.value
        synchronized(listenLock) {
            if (state.sessionId != accountedSessionId) {
                pendingListenedMs = 0L
                accountedSessionId = state.sessionId
            }
            playingSinceMs = SystemClock.elapsedRealtime()
        }
        if (currentPlaylistEpisodeId == null) currentPlaylistEpisodeId = state.episodeId

        syncJob = scope.launch {
            while (true) {
                val interval = getSyncInterval()
                delay(interval)

                syncProgress()
            }
        }

        Timber.d("Progress syncer started")
    }

    fun stopSyncing() {
        synchronized(listenLock) {
            accrueListenedLocked()
            playingSinceMs = null
        }
        if (syncJob != null) {
            syncJob?.cancel()
            syncJob = null
            Timber.d("Progress syncer stopped")
        }
    }

    fun takeListenedSeconds(): Double =
        synchronized(listenLock) {
            accrueListenedLocked()
            val seconds = pendingListenedMs / 1000.0
            pendingListenedMs = 0L
            seconds
        }

    private fun returnListenedSeconds(seconds: Double) {
        synchronized(listenLock) { pendingListenedMs += (seconds * 1000).toLong() }
    }

    private fun accrueListenedLocked() {
        val since = playingSinceMs ?: return
        val now = SystemClock.elapsedRealtime()
        pendingListenedMs += now - since
        playingSinceMs = now
    }

    suspend fun syncNow() {
        syncProgress()
    }

    fun onPlaylistEpisodeChanged(
        previousEpisodeId: String,
        previousTime: Double,
        previousDuration: Double,
        newEpisodeId: String,
    ) {
        pendingTransitions.incrementAndGet()
        scope.launch {
            try {
                episodeMutex.withLock {
                    switchPlaylistEpisode(
                        previousEpisodeId,
                        previousTime,
                        previousDuration,
                        newEpisodeId,
                    )
                }
            } finally {
                pendingTransitions.decrementAndGet()
            }
        }
    }

    private suspend fun syncProgress() {
        episodeMutex.withLock {
            val state = playbackManager.playbackState.value
            val sessionId = state.sessionId ?: return

            if (state.isPodcastPlaylist) {
                syncPlaylistProgress(state, sessionId)
            } else {
                syncStandardProgress(state, sessionId)
            }
        }
    }

    private suspend fun syncStandardProgress(
        state: AudiobookshelfPlaybackState,
        sessionId: String,
    ) {
        val currentTime = state.currentTime

        if (sessionId.startsWith("local_")) {
            val itemId = state.itemId ?: return
            val (serverId, userId) = audiobookshelfRepository.currentActiveContext ?: return
            val listened = takeListenedSeconds()
            Timber.d(
                "ProgressSync[audiobook]: saving offline progress itemId=$itemId episodeId=${state.episodeId} currentTime=$currentTime duration=${state.duration} listened=$listened"
            )
            audiobookshelfRepository
                .updateProgress(
                    itemId = itemId,
                    episodeId = state.episodeId,
                    currentTime = currentTime,
                    duration = state.duration,
                    isFinished =
                        state.duration > 0 && currentTime / state.duration >= FINISHED_RATIO,
                    timeListened = listened,
                )
                .onFailure { returnListenedSeconds(listened) }
            absSyncScheduler.scheduleSync(serverId, userId)
            Timber.d("ProgressSync[audiobook]: offline progress saved and sync scheduled")
            return
        }

        val listened = takeListenedSeconds()
        try {
            val result =
                audiobookshelfRepository.syncPlaybackSession(
                    sessionId = sessionId,
                    timeListened = listened,
                    currentTime = currentTime,
                    duration = state.duration,
                )

            result.fold(
                onSuccess = { Timber.d("Progress synced: ${currentTime}s / ${state.duration}s") },
                onFailure = { error ->
                    returnListenedSeconds(listened)
                    Timber.w(error, "Failed to sync progress, will retry")
                },
            )
        } catch (e: CancellationException) {
            returnListenedSeconds(listened)
            throw e
        } catch (e: Exception) {
            returnListenedSeconds(listened)
            Timber.e(e, "Exception syncing progress")
        }
    }

    private suspend fun syncPlaylistProgress(
        state: AudiobookshelfPlaybackState,
        sessionId: String,
    ) {
        val currentChapter = state.currentChapter ?: return
        val chapterIndex = state.chapters.indexOf(currentChapter)
        val episodeId = state.playlistEpisodeIds.getOrNull(chapterIndex) ?: return

        val episodeCurrentTime = (state.currentTime - currentChapter.start).coerceAtLeast(0.0)
        val episodeDuration = (currentChapter.end - currentChapter.start).coerceAtLeast(0.0)

        if (episodeId != currentPlaylistEpisodeId) {
            if (pendingTransitions.get() > 0) return
            val previousEpisodeId = currentPlaylistEpisodeId
            val previousDuration =
                previousEpisodeId
                    ?.let { state.playlistEpisodeIds.indexOf(it) }
                    ?.let { state.chapters.getOrNull(it) }
                    ?.let { (it.end - it.start).coerceAtLeast(0.0) } ?: 0.0
            Timber.w("Playlist episode change missed by the player, recovering at last position")
            switchPlaylistEpisode(
                previousEpisodeId,
                lastEpisodeTime ?: 0.0,
                previousDuration,
                episodeId,
            )
            return
        }

        lastEpisodeTime = episodeCurrentTime

        if (sessionId.startsWith("local_")) {
            val itemId = state.itemId ?: return
            val (serverId, userId) = audiobookshelfRepository.currentActiveContext ?: return
            val listened = takeListenedSeconds()
            Timber.d(
                "ProgressSync[podcast]: saving offline progress itemId=$itemId episodeId=$episodeId currentTime=$episodeCurrentTime duration=$episodeDuration listened=$listened"
            )
            audiobookshelfRepository
                .updateProgress(
                    itemId = itemId,
                    episodeId = episodeId,
                    currentTime = episodeCurrentTime,
                    duration = episodeDuration,
                    isFinished =
                        episodeDuration > 0 &&
                            episodeCurrentTime / episodeDuration >= FINISHED_RATIO,
                    timeListened = listened,
                )
                .onFailure { returnListenedSeconds(listened) }
            absSyncScheduler.scheduleSync(serverId, userId)
            Timber.d("ProgressSync[podcast]: offline progress saved and sync scheduled")
            return
        }

        val listened = takeListenedSeconds()
        try {
            val result =
                audiobookshelfRepository.syncPlaybackSession(
                    sessionId = sessionId,
                    timeListened = listened,
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
                onFailure = { error ->
                    returnListenedSeconds(listened)
                    Timber.w(error, "Failed to sync playlist progress")
                },
            )
        } catch (e: CancellationException) {
            returnListenedSeconds(listened)
            throw e
        } catch (e: Exception) {
            returnListenedSeconds(listened)
            Timber.e(e, "Exception syncing playlist progress")
        }
    }

    private suspend fun switchPlaylistEpisode(
        previousEpisodeId: String?,
        previousTime: Double,
        previousDuration: Double,
        newEpisodeId: String,
    ) {
        if (currentPlaylistEpisodeId == newEpisodeId) return
        val state = playbackManager.playbackState.value
        val itemId = state.itemId ?: return

        Timber.d(
            "Playlist episode change: $previousEpisodeId -> $newEpisodeId at ${previousTime}s / ${previousDuration}s"
        )

        val sessionId = state.sessionId
        if (
            previousEpisodeId != null &&
                previousEpisodeId == currentPlaylistEpisodeId &&
                sessionId != null
        ) {
            closeEpisodeSession(
                sessionId,
                itemId,
                previousEpisodeId,
                previousTime,
                previousDuration,
            )
        }

        currentPlaylistEpisodeId = newEpisodeId
        lastEpisodeTime = null

        val startedSessionId =
            try {
                audiobookshelfRepository
                    .startPlaybackSession(itemId, newEpisodeId)
                    .onFailure { Timber.e(it, "Failed to start session for episode $newEpisodeId") }
                    .getOrNull()
                    ?.id
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Exception starting session for episode $newEpisodeId")
                null
            }
        val newSessionId = startedSessionId ?: "local_${itemId}_$newEpisodeId"

        synchronized(listenLock) { accountedSessionId = newSessionId }
        playbackManager.updateSessionInfo(newSessionId, newEpisodeId)
        Timber.d("Now tracking episode $newEpisodeId with session $newSessionId")
    }

    private suspend fun closeEpisodeSession(
        sessionId: String,
        itemId: String,
        episodeId: String,
        currentTime: Double,
        duration: Double,
    ) {
        val listened = takeListenedSeconds()
        try {
            if (sessionId.startsWith("local_")) {
                val (serverId, userId) = audiobookshelfRepository.currentActiveContext ?: return
                audiobookshelfRepository.updateProgress(
                    itemId = itemId,
                    episodeId = episodeId,
                    currentTime = currentTime,
                    duration = duration,
                    isFinished = duration > 0 && currentTime / duration >= FINISHED_RATIO,
                    timeListened = listened,
                )
                absSyncScheduler.scheduleSync(serverId, userId)
            } else {
                audiobookshelfRepository.closePlaybackSession(
                    sessionId = sessionId,
                    currentTime = currentTime,
                    timeListened = listened,
                    duration = duration,
                )
            }
            Timber.d("Closed episode $episodeId at ${currentTime}s / ${duration}s")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to close session for episode $episodeId")
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
