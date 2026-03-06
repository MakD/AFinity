package com.makd.afinity.ui.item.delegates

import com.makd.afinity.data.manager.PlaybackStateManager
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.repository.userdata.UserDataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

class ItemUserDataDelegate
@Inject
constructor(
    private val userDataRepository: UserDataRepository,
    private val playbackStateManager: PlaybackStateManager,
) {
    fun toggleFavorite(
        scope: CoroutineScope,
        item: AfinityItem,
        updateOptimisticUI: () -> Unit,
        revertUI: () -> Unit,
    ) {
        updateOptimisticUI()
        scope.launch {
            try {
                val success =
                    if (item.favorite) {
                        userDataRepository.removeFromFavorites(item.id)
                    } else {
                        userDataRepository.addToFavorites(item.id)
                    }

                if (!success) {
                    Timber.w("Failed to toggle favorite status, reverted UI")
                    revertUI()
                } else {
                    playbackStateManager.notifyItemChanged(
                        item.id,
                        (item as? AfinityEpisode)?.seriesId,
                        (item as? AfinityEpisode)?.seasonId,
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error toggling favorite status")
                revertUI()
            }
        }
    }

    fun toggleWatchlist(
        scope: CoroutineScope,
        item: AfinityItem,
        updateOptimisticUI: () -> Unit,
        revertUI: () -> Unit,
    ) {
        updateOptimisticUI()
        scope.launch {
            try {
                val success = userDataRepository.setLike(itemId = item.id, isLiked = !item.liked)

                if (!success) {
                    Timber.w("Failed to toggle like status, reverted UI")
                    revertUI()
                } else {
                    playbackStateManager.notifyItemChanged(
                        item.id,
                        (item as? AfinityEpisode)?.seriesId,
                        (item as? AfinityEpisode)?.seasonId,
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error toggling like status")
                revertUI()
            }
        }
    }

    fun toggleEpisodeFavorite(
        scope: CoroutineScope,
        episode: AfinityEpisode,
        onSuccess: () -> Unit,
    ) {
        scope.launch {
            try {
                val success =
                    if (episode.favorite) {
                        userDataRepository.removeFromFavorites(episode.id)
                    } else {
                        userDataRepository.addToFavorites(episode.id)
                    }
                if (success) {
                    onSuccess()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error toggling episode favorite")
            }
        }
    }
}
