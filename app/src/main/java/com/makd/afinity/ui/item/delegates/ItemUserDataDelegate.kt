package com.makd.afinity.ui.item.delegates

import com.makd.afinity.data.manager.MediaChangeManager
import com.makd.afinity.data.manager.MediaChangeSource
import com.makd.afinity.data.manager.MediaRefreshBus
import com.makd.afinity.data.manager.RefreshTrigger
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.userdata.UserDataRepository
import com.makd.afinity.data.repository.watchlist.WatchlistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

class ItemUserDataDelegate
@Inject
constructor(
    private val userDataRepository: UserDataRepository,
    private val mediaChangeManager: MediaChangeManager,
    private val appDataRepository: AppDataRepository,
    private val watchlistRepository: WatchlistRepository,
    private val mediaRefreshBus: MediaRefreshBus,
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
                    val updatedItem = item.withFavorite(!item.favorite)
                    appDataRepository.updateItemInCaches(updatedItem)
                    appDataRepository.updateFavoriteStatus(updatedItem, !item.favorite)
                    mediaChangeManager.publishKnownChange(
                        updatedItem = updatedItem,
                        source = MediaChangeSource.MANUAL,
                    )
                    mediaRefreshBus.emit(RefreshTrigger.USER_DATA_CHANGED)
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
                    val updatedItem = item.withLiked(!item.liked)
                    appDataRepository.updateItemInCaches(updatedItem)
                    appDataRepository.updateWatchlistStatus(updatedItem, !item.liked)
                    watchlistRepository.refreshWatchlistCount()
                    mediaChangeManager.publishKnownChange(
                        updatedItem = updatedItem,
                        source = MediaChangeSource.MANUAL,
                    )
                    mediaRefreshBus.emit(RefreshTrigger.USER_DATA_CHANGED)
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
                    val updatedEpisode = episode.copy(favorite = !episode.favorite)
                    appDataRepository.updateItemInCaches(updatedEpisode)
                    appDataRepository.updateFavoriteStatus(updatedEpisode, !episode.favorite)
                    mediaChangeManager.publishKnownChange(
                        updatedItem = updatedEpisode,
                        source = MediaChangeSource.MANUAL,
                    )
                    onSuccess()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error toggling episode favorite")
            }
        }
    }

    private fun AfinityItem.withFavorite(isFavorite: Boolean): AfinityItem =
        when (this) {
            is com.makd.afinity.data.models.media.AfinityMovie -> copy(favorite = isFavorite)
            is com.makd.afinity.data.models.media.AfinityShow -> copy(favorite = isFavorite)
            is AfinityEpisode -> copy(favorite = isFavorite)
            is com.makd.afinity.data.models.media.AfinitySeason -> copy(favorite = isFavorite)
            is com.makd.afinity.data.models.media.AfinityBoxSet -> copy(favorite = isFavorite)
            else -> this
        }

    private fun AfinityItem.withLiked(isLiked: Boolean): AfinityItem =
        when (this) {
            is com.makd.afinity.data.models.media.AfinityMovie -> copy(liked = isLiked)
            is com.makd.afinity.data.models.media.AfinityShow -> copy(liked = isLiked)
            is AfinityEpisode -> copy(liked = isLiked)
            is com.makd.afinity.data.models.media.AfinitySeason -> copy(liked = isLiked)
            is com.makd.afinity.data.models.media.AfinityBoxSet -> copy(liked = isLiked)
            else -> this
        }
}
