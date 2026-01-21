package com.makd.afinity.data.manager

import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.playback.PlaybackRepository
import com.makd.afinity.data.sync.UserDataSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
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

    private val _playbackEvents = MutableSharedFlow<PlaybackEvent>()
    val playbackEvents = _playbackEvents.asSharedFlow()

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

    fun notifyPlaybackStopped(itemId: UUID, positionMs: Long) {
        scope.launch {
            val positionTicks = positionMs * 10000
            _playbackEvents.emit(PlaybackEvent.Stopped(itemId, positionTicks))

            try {
                updatePlaybackPosition(positionMs)
                reportPlaybackStop()
                handlePlaybackStopped(itemId)
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

    private fun handlePlaybackStopped(itemId: UUID) {
        scope.launch {
            try {
                val refreshedItem = mediaRepository.refreshItemUserData(itemId, FieldSets.REFRESH_USER_DATA)

                if (refreshedItem != null) {
                    _playbackEvents.emit(PlaybackEvent.Synced(itemId))
                    if (refreshedItem is AfinityEpisode && refreshedItem.played) {
                        Timber.d("Episode finished. Refreshing Next Up queue...")
                        mediaRepository.invalidateNextUpCache()
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to handle playback stopped")
            }
        }
    }

    fun notifyItemChanged(itemId: UUID) {
        scope.launch {
            Timber.d("Broadcasting manual item change for: $itemId")
            _playbackEvents.emit(PlaybackEvent.Synced(itemId))
        }
    }

    fun clearSession() {
        currentItemId = null
        currentSessionId = null
        lastKnownPosition = 0L
        lastKnownMediaSourceId = null
    }
}

sealed class PlaybackEvent {
    data class Stopped(val itemId: UUID, val positionTicks: Long) : PlaybackEvent()
    data class Synced(val itemId: UUID) : PlaybackEvent()
}