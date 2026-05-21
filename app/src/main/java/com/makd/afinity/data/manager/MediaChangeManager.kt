package com.makd.afinity.data.manager

import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.watchlist.WatchlistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.UserItemDataDto
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaChangeManager
@Inject
constructor(
    private val mediaRepository: MediaRepository,
    private val appDataRepository: AppDataRepository,
    private val watchlistRepository: WatchlistRepository,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _mediaChanges = MutableSharedFlow<MediaChangeEvent>(extraBufferCapacity = 64)
    val mediaChanges = _mediaChanges.asSharedFlow()

    private val _libraryContentChanges =
        MutableSharedFlow<LibraryContentChangeEvent>(extraBufferCapacity = 16)
    val libraryContentChanges = _libraryContentChanges.asSharedFlow()

    fun notifyLibraryContentChanged(reason: String) {
        scope.launch { _libraryContentChanges.emit(LibraryContentChangeEvent(reason)) }
    }

    fun notifyItemChanged(
        itemId: UUID,
        seriesId: UUID? = null,
        seasonId: UUID? = null,
        source: MediaChangeSource = MediaChangeSource.MANUAL,
    ) {
        scope.launch {
            refreshAndPublish(
                itemId = itemId,
                knownSeriesId = seriesId,
                knownSeasonId = seasonId,
                source = source,
            )
        }
    }

    suspend fun applyUserDataChange(userData: UserItemDataDto) {
        val updatedItem =
            refreshAndPublish(
                itemId = userData.itemId,
                source = MediaChangeSource.WEBSOCKET,
                userData = userData,
            )

        if (updatedItem != null) {
            appDataRepository.updateFavoriteStatus(updatedItem, userData.isFavorite)
            userData.likes?.let { isLiked ->
                appDataRepository.updateWatchlistStatus(updatedItem, isLiked)
                watchlistRepository.refreshWatchlistCount()
            }
        }

        if (userData.played || userData.playbackPositionTicks > 0L) {
            appDataRepository.refreshLiveSections()
        }
    }

    suspend fun applyUserDataChangesBatch(userDataList: List<UserItemDataDto>) {
        val itemIds = userDataList.mapNotNull { it.itemId }
        if (itemIds.isEmpty()) return

        try {
            val updatedItems = mediaRepository.getItemsByIds(itemIds)

            var requiresLiveSectionRefresh = false
            updatedItems.forEach { updatedItem ->
                val correspondingUserData = userDataList.find { it.itemId == updatedItem.id }
                appDataRepository.updateItemInCaches(updatedItem)
                if (
                    correspondingUserData?.played == true ||
                        (correspondingUserData?.playbackPositionTicks ?: 0L) > 0L
                ) {
                    requiresLiveSectionRefresh = true
                }
                _mediaChanges.emit(
                    MediaChangeEvent(
                        itemId = updatedItem.id,
                        updatedItem = updatedItem,
                        source = MediaChangeSource.WEBSOCKET,
                        userData = correspondingUserData,
                    )
                )
            }
            if (requiresLiveSectionRefresh) {
                Timber.d("Batch processed. Refreshing live sections once.")
                appDataRepository.refreshLiveSections()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to process batch user data changes")
        }
    }

    suspend fun refreshAndPublish(
        itemId: UUID,
        knownSeriesId: UUID? = null,
        knownSeasonId: UUID? = null,
        source: MediaChangeSource = MediaChangeSource.MANUAL,
        userData: UserItemDataDto? = null,
    ): AfinityItem? {
        return try {
            val updatedItem =
                mediaRepository.refreshItemUserData(itemId, FieldSets.REFRESH_USER_DATA)
            val parentItem = resolveParentItem(updatedItem, knownSeriesId)

            updatedItem?.let { appDataRepository.updateItemInCaches(it) }
            parentItem?.let { appDataRepository.updateItemInCaches(it) }
            updatedItem?.let { refreshDerivedState(it) }

            val resolvedSeriesId =
                when (updatedItem) {
                    is AfinityEpisode -> updatedItem.seriesId
                    is AfinitySeason -> updatedItem.seriesId
                    else -> knownSeriesId
                }

            val resolvedSeasonId =
                when (updatedItem) {
                    is AfinityEpisode -> updatedItem.seasonId
                    else -> knownSeasonId
                }

            _mediaChanges.emit(
                MediaChangeEvent(
                    itemId = itemId,
                    updatedItem = updatedItem,
                    parentItem = parentItem,
                    seriesId = resolvedSeriesId,
                    seasonId = resolvedSeasonId,
                    source = source,
                    userData = userData,
                )
            )

            updatedItem
        } catch (e: Exception) {
            Timber.e(e, "Failed to publish media change for $itemId")
            _mediaChanges.emit(
                MediaChangeEvent(
                    itemId = itemId,
                    seriesId = knownSeriesId,
                    seasonId = knownSeasonId,
                    source = source,
                    userData = userData,
                )
            )
            null
        }
    }

    suspend fun publishKnownChange(
        updatedItem: AfinityItem,
        knownSeriesId: UUID? = null,
        knownSeasonId: UUID? = null,
        source: MediaChangeSource = MediaChangeSource.MANUAL,
        userData: UserItemDataDto? = null,
        favoriteStatus: Boolean? = null,
        watchlistStatus: Boolean? = null,
    ) {
        try {
            val parentItem = resolveParentItem(updatedItem, knownSeriesId)

            appDataRepository.updateItemInCaches(updatedItem)
            parentItem?.let { appDataRepository.updateItemInCaches(it) }
            favoriteStatus?.let { appDataRepository.updateFavoriteStatus(updatedItem, it) }
            watchlistStatus?.let {
                appDataRepository.updateWatchlistStatus(updatedItem, it)
                watchlistRepository.refreshWatchlistCount()
            }
            refreshDerivedState(updatedItem)

            val resolvedSeriesId =
                when (updatedItem) {
                    is AfinityEpisode -> updatedItem.seriesId
                    is AfinitySeason -> updatedItem.seriesId
                    else -> knownSeriesId
                }

            val resolvedSeasonId =
                when (updatedItem) {
                    is AfinityEpisode -> updatedItem.seasonId
                    else -> knownSeasonId
                }

            _mediaChanges.emit(
                MediaChangeEvent(
                    itemId = updatedItem.id,
                    updatedItem = updatedItem,
                    parentItem = parentItem,
                    seriesId = resolvedSeriesId,
                    seasonId = resolvedSeasonId,
                    source = source,
                    userData = userData,
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to publish known media change for ${updatedItem.id}")
        }
    }

    private suspend fun refreshDerivedState(updatedItem: AfinityItem) {
        if (updatedItem is AfinityEpisode) {
            appDataRepository.refreshLiveSections()
        }
    }

    private suspend fun resolveParentItem(
        updatedItem: AfinityItem?,
        knownSeriesId: UUID?,
    ): AfinityItem? {
        val parentId =
            when (updatedItem) {
                is AfinityEpisode -> updatedItem.seriesId
                is AfinitySeason -> updatedItem.seriesId
                else -> knownSeriesId
            }

        if (parentId == null || parentId == updatedItem?.id) return null
        return mediaRepository.getItemById(parentId)
    }
}

data class MediaChangeEvent(
    val itemId: UUID,
    val updatedItem: AfinityItem? = null,
    val parentItem: AfinityItem? = null,
    val seriesId: UUID? = null,
    val seasonId: UUID? = null,
    val source: MediaChangeSource,
    val userData: UserItemDataDto? = null,
)

enum class MediaChangeSource {
    MANUAL,
    PLAYBACK,
    WEBSOCKET,
}

data class LibraryContentChangeEvent(val reason: String)
