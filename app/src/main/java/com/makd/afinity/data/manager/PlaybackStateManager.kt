package com.makd.afinity.data.manager

import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.player.PlayerRepository
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
    private val playerRepository: PlayerRepository,
    private val mediaRepository: MediaRepository
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var currentItemId: UUID? = null

    fun initialize() {
        playerRepository.setOnPlaybackStoppedCallback {
            handlePlaybackStopped()
        }
        Timber.d("PlaybackStateManager initialized")
    }

    fun trackCurrentItem(itemId: UUID) {
        currentItemId = itemId
        Timber.d("Now tracking playback for item: $itemId")
    }

    private var onItemUpdatedCallback: ((UUID) -> Unit)? = null

    fun setOnItemUpdatedCallback(callback: (UUID) -> Unit) {
        onItemUpdatedCallback = callback
    }

    private fun handlePlaybackStopped() {
        scope.launch {
            try {
                Timber.d("=== PLAYBACK STOPPED - REFRESHING ITEM DATA ===")

                currentItemId?.let { itemId ->
                    val refreshedItem = mediaRepository.refreshItemUserData(itemId, FieldSets.REFRESH_USER_DATA)

                    if (refreshedItem != null) {
                        Timber.d("Successfully updated UserData for played item: ${refreshedItem.name}")
                        onItemUpdatedCallback?.invoke(itemId)
                    } else {
                        Timber.w("Failed to refresh item data for: $itemId")
                    }
                }

                Timber.d("=== PLAYBACK STOPPED HANDLING COMPLETE ===")
            } catch (e: Exception) {
                Timber.e(e, "Failed to handle playback stopped")
            }
        }
    }
}