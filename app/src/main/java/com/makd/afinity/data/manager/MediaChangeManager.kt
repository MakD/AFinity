package com.makd.afinity.data.manager

import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.watchlist.WatchlistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.UserItemDataDto
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(FlowPreview::class)
@Singleton
class MediaChangeManager
@Inject
constructor(
    private val mediaRepository: MediaRepository,
    private val appDataRepository: AppDataRepository,
    private val databaseRepository: DatabaseRepository,
    private val sessionManager: SessionManager,
    private val watchlistRepository: WatchlistRepository,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _mediaChanges = MutableSharedFlow<MediaChangeEvent>(extraBufferCapacity = 64)
    val mediaChanges = _mediaChanges.asSharedFlow()

    private val _libraryContentChanges =
        MutableSharedFlow<LibraryContentChangeEvent>(extraBufferCapacity = 16)
    val libraryContentChanges = _libraryContentChanges.asSharedFlow()

    private val liveSectionRefreshTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    init {
        scope.launch {
            liveSectionRefreshTrigger.debounce(1000L).collect {
                Timber.d("WebSocket transfer finished. Syncing Next Up & Continue Watching...")
                appDataRepository.refreshLiveSections()
            }
        }
    }

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
        applyUserDataChangesBatch(listOf(userData))
    }

    suspend fun applyUserDataChangesBatch(userDataList: List<UserItemDataDto>) {
        if (userDataList.isEmpty()) return

        val currentSession = sessionManager.currentSession.value ?: return
        val userId = currentSession.userId
        val serverId = currentSession.serverId
        var requiresLiveSectionRefresh = false

        userDataList.forEach { userData ->
            val itemId = userData.itemId ?: return@forEach
            try {
                databaseRepository.patchUserDataLocally(itemId, userId, serverId, userData)
            } catch (e: Exception) {
                Timber.e(e, "Failed to patch local DB for $itemId")
            }

            _mediaChanges.emit(
                MediaChangeEvent(
                    itemId = itemId,
                    source = MediaChangeSource.WEBSOCKET,
                    userData = userData,
                )
            )

            if (userData.played || (userData.playbackPositionTicks ?: 0L) > 0L) {
                requiresLiveSectionRefresh = true
            }
        }

        if (requiresLiveSectionRefresh) {
            liveSectionRefreshTrigger.tryEmit(Unit)
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
            liveSectionRefreshTrigger.tryEmit(Unit)
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
