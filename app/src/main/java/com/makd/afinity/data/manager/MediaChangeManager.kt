package com.makd.afinity.data.manager

import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
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
    private val databaseRepository: DatabaseRepository,
    private val sessionManager: SessionManager,
    private val mediaRefreshBus: MediaRefreshBus,
    @ApplicationScope private val scope: CoroutineScope,
) {

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
        applyUserDataChangesBatch(listOf(userData))
    }

    suspend fun applyUserDataChangesBatch(userDataList: List<UserItemDataDto>) {
        if (userDataList.isEmpty()) return

        val currentSession = sessionManager.currentSession.value ?: return
        val userId = currentSession.userId
        val serverId = currentSession.serverId

        val userDataByItemId = linkedMapOf<UUID, UserItemDataDto>()
        userDataList.forEach { userData ->
            val itemId = userData.itemId ?: return@forEach
            userDataByItemId[itemId] = userData
            try {
                databaseRepository.patchUserDataLocally(itemId, userId, serverId, userData)
            } catch (e: Exception) {
                Timber.e(e, "Failed to patch local DB for $itemId")
            }
        }
        mediaRefreshBus.emit(RefreshTrigger.USER_DATA_CHANGED)
        if (userDataByItemId.isEmpty()) return

        val itemIds = userDataByItemId.keys.toList()
        val resolvedById =
            try {
                val items =
                    if (itemIds.size == 1) {
                        listOfNotNull(mediaRepository.getItemById(itemIds.first()))
                    } else {
                        mediaRepository.getItemsByIds(itemIds, FieldSets.ITEM_DETAIL)
                    }
                items.associateBy { it.id }
            } catch (e: Exception) {
                Timber.e(e, "Failed to resolve items for user data changes")
                emptyMap()
            }

        val parentIdsCarriedByChildren =
            resolvedById.values
                .mapNotNull { item ->
                    when (item) {
                        is AfinityEpisode -> item.seriesId
                        is AfinitySeason -> item.seriesId
                        else -> null
                    }
                }
                .filter { it in userDataByItemId }
                .toSet()

        for (itemId in itemIds) {
            if (itemId in parentIdsCarriedByChildren) continue

            val item = resolvedById[itemId]
            val resolvedSeriesId =
                (item as? AfinityEpisode)?.seriesId ?: (item as? AfinitySeason)?.seriesId
            val resolvedSeasonId = (item as? AfinityEpisode)?.seasonId
            val parentItem =
                resolvedSeriesId?.let { seriesId ->
                    resolvedById[seriesId] ?: resolveParentItem(item, seriesId)
                }
            val seasonItem = resolvedSeasonId?.let { resolvedById[it] }

            _mediaChanges.emit(
                MediaChangeEvent(
                    itemId = itemId,
                    updatedItem = item,
                    parentItem = parentItem,
                    seasonItem = seasonItem,
                    seriesId = resolvedSeriesId,
                    seasonId = resolvedSeasonId,
                    source = MediaChangeSource.WEBSOCKET,
                    userData = userDataByItemId[itemId],
                )
            )
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
            val seasonItem = resolveSeasonItem(updatedItem, knownSeasonId)

            mediaRefreshBus.emit(RefreshTrigger.USER_DATA_CHANGED)
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
                    seasonItem = seasonItem,
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
    ) {
        try {
            val parentItem = resolveParentItem(updatedItem, knownSeriesId)

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

    private fun refreshDerivedState(updatedItem: AfinityItem) {
        if (updatedItem is AfinityEpisode) {
            mediaRefreshBus.emit(RefreshTrigger.USER_DATA_CHANGED)
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

    private suspend fun resolveSeasonItem(
        updatedItem: AfinityItem?,
        knownSeasonId: UUID?,
    ): AfinityItem? {
        val seasonId =
            when (updatedItem) {
                is AfinityEpisode -> updatedItem.seasonId
                else -> knownSeasonId
            }
        if (seasonId == null || seasonId == updatedItem?.id) return null
        return try {
            mediaRepository.getItemById(seasonId)
        } catch (e: Exception) {
            Timber.w(e, "Could not resolve season item for $seasonId")
            null
        }
    }
}

data class MediaChangeEvent(
    val itemId: UUID,
    val updatedItem: AfinityItem? = null,
    val parentItem: AfinityItem? = null,
    val seasonItem: AfinityItem? = null,
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
