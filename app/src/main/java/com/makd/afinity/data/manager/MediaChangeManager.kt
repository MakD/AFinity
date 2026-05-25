package com.makd.afinity.data.manager

import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.media.MediaRepository
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
    private val databaseRepository: DatabaseRepository,
    private val sessionManager: SessionManager,
    private val mediaRefreshBus: MediaRefreshBus,
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
        applyUserDataChangesBatch(listOf(userData))
    }

    suspend fun applyUserDataChangesBatch(userDataList: List<UserItemDataDto>) {
        if (userDataList.isEmpty()) return

        val currentSession = sessionManager.currentSession.value ?: return
        val userId = currentSession.userId
        val serverId = currentSession.serverId

        userDataList.forEach { userData ->
            val itemId = userData.itemId ?: return@forEach
            try {
                databaseRepository.patchUserDataLocally(itemId, userId, serverId, userData)
            } catch (e: Exception) {
                Timber.e(e, "Failed to patch local DB for $itemId")
            }
        }
        mediaRefreshBus.emit(RefreshTrigger.USER_DATA_CHANGED)

        if (userDataList.size == 1) {
            val userData = userDataList.first()
            val itemId = userData.itemId ?: return
            val cachedItem =
                try {
                    mediaRepository.getItemById(itemId)
                } catch (_: Exception) {
                    null
                }
            val resolvedSeriesId =
                (cachedItem as? AfinityEpisode)?.seriesId
                    ?: (cachedItem as? AfinitySeason)?.seriesId
            val resolvedSeasonId = (cachedItem as? AfinityEpisode)?.seasonId
            _mediaChanges.emit(
                MediaChangeEvent(
                    itemId = itemId,
                    updatedItem = null,
                    seriesId = resolvedSeriesId,
                    seasonId = resolvedSeasonId,
                    source = MediaChangeSource.WEBSOCKET,
                    userData = userData,
                    isBatchEvent = false,
                )
            )
            return
        }

        val firstItemId = userDataList.first().itemId ?: return
        val firstItem =
            try {
                mediaRepository.getItemById(firstItemId)
            } catch (_: Exception) {
                null
            }
        val resolvedSeriesId =
            (firstItem as? AfinityEpisode)?.seriesId ?: (firstItem as? AfinitySeason)?.seriesId
        val resolvedSeasonId =
            when (firstItem) {
                is AfinityEpisode -> firstItem.seasonId
                is AfinitySeason -> firstItem.id
                else -> null
            }

        _mediaChanges.emit(
            MediaChangeEvent(
                itemId = firstItemId,
                seriesId = resolvedSeriesId,
                seasonId = resolvedSeasonId,
                source = MediaChangeSource.WEBSOCKET,
                isBatchEvent = true,
            )
        )
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
    val isBatchEvent: Boolean = false,
)

enum class MediaChangeSource {
    MANUAL,
    PLAYBACK,
    WEBSOCKET,
}

data class LibraryContentChangeEvent(val reason: String)
