package com.makd.afinity.data.manager

import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.playback.PlaybackRepository
import com.makd.afinity.data.sync.UserDataSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackStateManager @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val syncScheduler: UserDataSyncScheduler
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var currentItemId: UUID? = null
    private var currentSessionId: String? = null
    private var lastKnownPosition: Long = 0L
    private var lastKnownMediaSourceId: String? = null
    private var onItemUpdatedCallback: ((UUID) -> Unit)? = null
    private var onPlaybackStoppedCallback: (() -> Unit)? = null

    fun trackCurrentItem(itemId: UUID) {
        currentItemId = itemId
    }

    fun trackPlaybackSession(
        sessionId: String,
        itemId: UUID,
        mediaSourceId: String
    ) {
        currentSessionId = sessionId
        currentItemId = itemId
        lastKnownMediaSourceId = mediaSourceId
    }

    fun updatePlaybackPosition(positionMs: Long) {
        lastKnownPosition = positionMs
    }

    fun setOnItemUpdatedCallback(callback: (UUID) -> Unit) {
        onItemUpdatedCallback = callback
    }

    fun setOnPlaybackStoppedCallback(callback: () -> Unit) {
        onPlaybackStoppedCallback = callback
    }

    fun notifyPlaybackStopped() {
        scope.launch {
            try {
                reportPlaybackStop()

                onPlaybackStoppedCallback?.invoke()
                handlePlaybackStopped()
            } catch (e: Exception) {
                Timber.e(e, "Error in notifyPlaybackStopped")
            }
        }
    }

    private suspend fun reportPlaybackStop() {
        try {
            val itemId = currentItemId
            val sessionId = currentSessionId
            val mediaSourceId = lastKnownMediaSourceId
            val positionTicks = lastKnownPosition * 10000

            if (itemId != null && sessionId != null && !mediaSourceId.isNullOrEmpty()) {
                val success = playbackRepository.reportPlaybackStop(
                    itemId = itemId,
                    sessionId = sessionId,
                    positionTicks = positionTicks,
                    mediaSourceId = mediaSourceId
                )

                if (success) {
                    Timber.d("Playback stop reported to Jellyfin: item=$itemId, position=${lastKnownPosition}ms")
                } else {
                    Timber.w("Failed to report playback stop, progress saved locally. Scheduling sync...")
                    syncScheduler.scheduleSyncNow()
                }
            } else {
                Timber.w("Cannot report playback stop - missing data: item=$itemId, session=$sessionId, source=$mediaSourceId")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to report playback stop to Jellyfin")
            syncScheduler.scheduleSyncNow()
        }
    }

    private fun handlePlaybackStopped() {
        scope.launch {
            try {
                currentItemId?.let { itemId ->
                    val refreshedItem =
                        mediaRepository.refreshItemUserData(itemId, FieldSets.REFRESH_USER_DATA)

                    if (refreshedItem != null) {
                        Timber.d("Successfully updated UserData for played item: ${refreshedItem.name}")
                        onItemUpdatedCallback?.invoke(itemId)
                    } else {
                        Timber.w("Failed to refresh item data for: $itemId")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to handle playback stopped")
            }
        }
    }

    fun clearSession() {
        currentItemId = null
        currentSessionId = null
        lastKnownPosition = 0L
        lastKnownMediaSourceId = null
    }
}