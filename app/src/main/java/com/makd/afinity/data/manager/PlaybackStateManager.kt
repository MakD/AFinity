package com.makd.afinity.data.manager

import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.playback.PlaybackRepository
import com.makd.afinity.data.sync.UserDataSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackStateManager
@Inject
constructor(
    private val mediaRepository: MediaRepository,
    private val appDataRepository: AppDataRepository,
    private val playbackRepository: PlaybackRepository,
    private val syncScheduler: UserDataSyncScheduler,
    private val mediaChangeManager: MediaChangeManager,
    private val mediaRefreshBus: MediaRefreshBus,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _playbackEvents = MutableSharedFlow<PlaybackEvent>(replay = 1)
    val playbackEvents = _playbackEvents.asSharedFlow()

    @Volatile private var currentItemId: UUID? = null
    @Volatile private var currentSessionId: String? = null
    @Volatile private var lastKnownPosition: Long = 0L
    @Volatile private var lastKnownMediaSourceId: String? = null

    fun trackCurrentItem(itemId: UUID) {
        currentItemId = itemId
    }

    fun trackPlaybackSession(sessionId: String, itemId: UUID, mediaSourceId: String) {
        currentSessionId = sessionId
        currentItemId = itemId
        lastKnownMediaSourceId = mediaSourceId
    }

    fun updatePlaybackPosition(positionMs: Long) {
        lastKnownPosition = positionMs
    }

    fun notifyPlaybackStopped(itemId: UUID, positionMs: Long) {
        val capturedSessionId = currentSessionId
        val capturedMediaSourceId = lastKnownMediaSourceId
        scope.launch {
            withContext(NonCancellable) {
                val positionTicks = positionMs * 10000
                _playbackEvents.emit(PlaybackEvent.Stopped(itemId, positionTicks))

                try {
                    updatePlaybackPosition(positionMs)
                    reportPlaybackStop(
                        itemId,
                        capturedSessionId,
                        capturedMediaSourceId,
                        positionTicks,
                    )
                    handlePlaybackStopped(itemId)
                } catch (e: Exception) {
                    Timber.e(e, "Error in notifyPlaybackStopped")
                }
            }
        }
    }

    private suspend fun reportPlaybackStop(
        itemId: UUID,
        sessionId: String?,
        mediaSourceId: String?,
        positionTicks: Long,
    ) {
        try {
            if (sessionId != null && !mediaSourceId.isNullOrEmpty()) {
                val success =
                    playbackRepository.reportPlaybackStop(
                        itemId = itemId,
                        sessionId = sessionId,
                        positionTicks = positionTicks,
                        mediaSourceId = mediaSourceId,
                    )

                if (success) {
                    Timber.d(
                        "Playback stop reported to Jellyfin: item=$itemId, positionTicks=$positionTicks"
                    )
                } else {
                    Timber.w(
                        "Failed to report playback stop, progress saved locally. Scheduling sync..."
                    )
                    syncScheduler.scheduleSyncNow()
                }
            } else {
                Timber.w(
                    "Cannot report playback stop - missing data: item=$itemId, session=$sessionId, source=$mediaSourceId"
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to report playback stop to Jellyfin")
            syncScheduler.scheduleSyncNow()
        }
    }

    private suspend fun handlePlaybackStopped(itemId: UUID) {
        try {
            val refreshedItem =
                mediaRepository.refreshItemUserData(itemId, FieldSets.REFRESH_USER_DATA)

            if (refreshedItem != null) {
                val seriesId = (refreshedItem as? AfinityEpisode)?.seriesId
                val seasonId = (refreshedItem as? AfinityEpisode)?.seasonId
                appDataRepository.updateItemInCaches(refreshedItem)
                mediaChangeManager.publishKnownChange(
                    updatedItem = refreshedItem,
                    knownSeriesId = seriesId,
                    knownSeasonId = seasonId,
                    source = MediaChangeSource.PLAYBACK,
                )
                mediaRefreshBus.emit(RefreshTrigger.USER_DATA_CHANGED)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to handle playback stopped")
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
}
