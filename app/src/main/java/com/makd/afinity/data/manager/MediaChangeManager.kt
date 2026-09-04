package com.makd.afinity.data.manager

import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.UserDataPatch
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.store.ItemStore
import com.makd.afinity.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.UserItemDataDto
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class MediaChangeManager
@Inject
constructor(
    private val mediaRepository: MediaRepository,
    private val databaseRepository: DatabaseRepository,
    private val sessionManager: SessionManager,
    private val mediaRefreshBus: MediaRefreshBus,
    private val itemStore: ItemStore,
    @ApplicationScope private val scope: CoroutineScope,
) {

    private val _batches = MutableSharedFlow<MediaChangeBatch>(extraBufferCapacity = 16)
    val batches = _batches.asSharedFlow()

    val mediaChanges: Flow<MediaChangeEvent> = _batches.flatMapConcat { it.changes.asFlow() }

    private val _libraryContentChanges =
        MutableSharedFlow<LibraryContentChangeEvent>(extraBufferCapacity = 16)
    val libraryContentChanges = _libraryContentChanges.asSharedFlow()

    private val _libraryMetadataChanges =
        MutableSharedFlow<LibraryContentChangeEvent>(extraBufferCapacity = 16)
    val libraryMetadataChanges = _libraryMetadataChanges.asSharedFlow()

    private val _itemsRemoved = MutableSharedFlow<List<String>>(extraBufferCapacity = 16)
    val itemsRemoved = _itemsRemoved.asSharedFlow()

    private val _itemsAdded = MutableSharedFlow<List<String>>(extraBufferCapacity = 16)
    val itemsAdded = _itemsAdded.asSharedFlow()

    fun notifyItemsRemoved(itemIds: List<String>) {
        if (itemIds.isEmpty()) return
        scope.launch { _itemsRemoved.emit(itemIds) }
    }

    fun notifyItemsAdded(itemIds: List<String>) {
        if (itemIds.isEmpty()) return
        scope.launch { _itemsAdded.emit(itemIds) }
    }

    fun notifyLibraryContentChanged(reason: String) {
        scope.launch { _libraryContentChanges.emit(LibraryContentChangeEvent(reason)) }
    }

    fun notifyLibraryMetadataChanged(reason: String) {
        scope.launch { _libraryMetadataChanges.emit(LibraryContentChangeEvent(reason)) }
    }

    fun notifyItemChanged(
        itemId: UUID,
        seriesId: UUID? = null,
        seasonId: UUID? = null,
        source: MediaChangeSource = MediaChangeSource.MANUAL,
        patch: UserDataPatch? = null,
    ) {
        scope.launch {
            refreshAndPublish(
                itemId = itemId,
                knownSeriesId = seriesId,
                knownSeasonId = seasonId,
                source = source,
                patch = patch,
            )
        }
    }

    suspend fun applyUserDataChange(userData: UserItemDataDto) {
        applyUserDataChangesBatch(listOf(userData))
    }

    private suspend fun emitBatch(batch: MediaChangeBatch) {
        itemStore.put(
            batch.changes.flatMap {
                listOfNotNull(it.updatedItem, it.parentItem, it.seasonItem)
            }
        )
        _batches.emit(batch)
    }

    private suspend fun emitSingle(change: MediaChangeEvent) {
        emitBatch(MediaChangeBatch(listOf(change), change.source))
    }

    suspend fun publishContentChanges(ids: List<UUID>) {
        if (ids.isEmpty() || ids.size > CONTENT_CHANGE_PATCH_LIMIT) return

        val items =
            try {
                mediaRepository.getItemsByIds(ids, FieldSets.ITEM_DETAIL)
            } catch (e: Exception) {
                Timber.e(e, "Failed to resolve content changes for ${ids.size} items")
                return
            }
        if (items.isEmpty()) return

        val changes =
            items.map { item ->
                MediaChangeEvent(
                    itemId = item.id,
                    updatedItem = item,
                    seriesId =
                        (item as? AfinityEpisode)?.seriesId ?: (item as? AfinitySeason)?.seriesId,
                    seasonId = (item as? AfinityEpisode)?.seasonId,
                    source = MediaChangeSource.WEBSOCKET,
                )
            }
        emitBatch(MediaChangeBatch(changes, MediaChangeSource.WEBSOCKET))
    }

    companion object {
        const val CONTENT_CHANGE_PATCH_LIMIT = 10
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

        userDataByItemId.forEach { (itemId, data) -> itemStore.applyUserData(itemId, data) }

        val itemIds = userDataByItemId.keys.toList()
        val resolvedById =
            try {
                val items =
                    if (itemIds.size == 1) {
                        listOfNotNull(mediaRepository.getItemById(itemIds.first()))
                    } else {
                        mediaRepository.getItemsByIds(itemIds, FieldSets.MEDIA_ITEM_CARDS)
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

        val resolvedParents = mutableMapOf<UUID, AfinityItem?>()
        val changes = mutableListOf<MediaChangeEvent>()

        for (itemId in itemIds) {
            if (itemId in parentIdsCarriedByChildren) continue

            val item = resolvedById[itemId]
            val resolvedSeriesId =
                (item as? AfinityEpisode)?.seriesId ?: (item as? AfinitySeason)?.seriesId
            val resolvedSeasonId = (item as? AfinityEpisode)?.seasonId
            val parentItem = resolvedSeriesId?.let { seriesId ->
                resolvedById[seriesId]
                    ?: resolvedParents.getOrPut(seriesId) { resolveParentItem(item, seriesId) }
            }
            val seasonItem = resolvedSeasonId?.let { resolvedById[it] }

            changes.add(
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

        if (changes.isNotEmpty()) {
            emitBatch(MediaChangeBatch(changes, MediaChangeSource.WEBSOCKET))
        }
    }

    suspend fun refreshAndPublish(
        itemId: UUID,
        knownSeriesId: UUID? = null,
        knownSeasonId: UUID? = null,
        source: MediaChangeSource = MediaChangeSource.MANUAL,
        userData: UserItemDataDto? = null,
        patch: UserDataPatch? = null,
    ): AfinityItem? {
        return try {
            val storeOwner = patch?.let { itemStore.get(itemId) }
            if (patch != null && storeOwner != null) {
                itemStore.applyPatch(itemId, patch)
            }
            val updatedItem =
                if (storeOwner != null) {
                    itemStore.get(itemId) as? AfinityItem
                } else {
                    mediaRepository.refreshItemUserData(itemId, FieldSets.REFRESH_USER_DATA)
                }
            val parentItem = resolveParentItem(updatedItem, knownSeriesId)
            val seasonItem = resolveSeasonItem(updatedItem, knownSeasonId)

            mediaRefreshBus.emit(RefreshTrigger.USER_DATA_CHANGED)

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

            emitSingle(
                MediaChangeEvent(
                    itemId = itemId,
                    updatedItem = updatedItem,
                    parentItem = parentItem,
                    seasonItem = seasonItem,
                    seriesId = resolvedSeriesId,
                    seasonId = resolvedSeasonId,
                    source = source,
                    userData = userData,
                    patch = patch,
                )
            )

            updatedItem
        } catch (e: Exception) {
            Timber.e(e, "Failed to publish media change for $itemId")
            emitSingle(
                MediaChangeEvent(
                    itemId = itemId,
                    seriesId = knownSeriesId,
                    seasonId = knownSeasonId,
                    source = source,
                    userData = userData,
                    patch = patch,
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

            emitSingle(
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

data class MediaChangeBatch(
    val changes: List<MediaChangeEvent>,
    val source: MediaChangeSource,
) {
    val itemIds: Set<UUID> =
        buildSet {
            changes.forEach { change ->
                add(change.itemId)
                change.seriesId?.let { add(it) }
                change.seasonId?.let { add(it) }
            }
        }

    fun affects(vararg ids: UUID?): Boolean = ids.any { it != null && it in itemIds }

    fun affectsAny(ids: Collection<UUID>): Boolean = ids.any { it in itemIds }
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
    val patch: UserDataPatch? = null,
)

enum class MediaChangeSource {
    MANUAL,
    PLAYBACK,
    WEBSOCKET,
}

data class LibraryContentChangeEvent(val reason: String)
