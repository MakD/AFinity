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
class AudiobookshelfProgressSyncer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val audiobookshelfRepository: AudiobookshelfRepository,
    private val playbackManager: AudiobookshelfPlaybackManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var syncJob: Job? = null
    private var lastSyncTime: Double = 0.0
    private var totalTimeListened: Double = 0.0
    private var sessionStartTime: Long = 0L

    companion object {
        private const val WIFI_SYNC_INTERVAL_MS = 15_000L
        private const val CELLULAR_SYNC_INTERVAL_MS = 60_000L
    }

    fun startSyncing() {
        stopSyncing()

        sessionStartTime = System.currentTimeMillis()
        lastSyncTime = playbackManager.playbackState.value.currentTime
        totalTimeListened = 0.0

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

        val currentTime = state.currentTime
        val timeListenedSinceLastSync = (currentTime - lastSyncTime).coerceAtLeast(0.0)
        totalTimeListened += timeListenedSinceLastSync
        lastSyncTime = currentTime

        try {
            val result = audiobookshelfRepository.syncPlaybackSession(
                sessionId = sessionId,
                timeListened = timeListenedSinceLastSync,
                currentTime = currentTime,
                duration = state.duration
            )

            result.fold(
                onSuccess = {
                    Timber.d("Progress synced: ${currentTime}s / ${state.duration}s")
                },
                onFailure = { error ->
                    Timber.w(error, "Failed to sync progress, will retry")
                }
            )
        } catch (e: Exception) {
            Timber.e(e, "Exception syncing progress")
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
