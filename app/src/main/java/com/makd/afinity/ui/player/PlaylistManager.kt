package com.makd.afinity.ui.player

import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.PlaylistEntry
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.media.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jellyfin.sdk.model.api.ItemFields
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class PlaylistState(
    val queue: List<AfinityItem> = emptyList(),
    val currentIndex: Int = -1,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val currentItem: AfinityItem? = null,
    val contentStartIndex: Int = 0,
    val collectionName: String? = null,
)

@Singleton
class PlaylistManager @Inject constructor(private val mediaRepository: MediaRepository) {
    private val _playlistState = MutableStateFlow(PlaylistState())
    val playlistState: StateFlow<PlaylistState> = _playlistState.asStateFlow()

    private var currentQueue: MutableList<AfinityItem> = mutableListOf()
    private var currentIndex: Int = -1
    private var currentSeriesId: UUID? = null
    private var isJellyfinPlaylistQueue: Boolean = false
    private var contentStartIndex: Int = 0
    private var isJumpOnlyQueue: Boolean = false
    private var collectionName: String? = null

    suspend fun initializePlaylist(
        startingItem: AfinityItem,
        seasonId: UUID? = null,
        startPositionMs: Long = 0L,
        playlistId: UUID? = null,
    ): Boolean {
        if (playlistId != null) {
            return initializeJellyfinPlaylistQueue(startingItem, playlistId)
        }
        if (
            startingItem is AfinityEpisode &&
                currentSeriesId == startingItem.seriesId &&
                currentQueue.isNotEmpty()
        ) {
            val existingIndex = currentQueue.indexOfFirst { it.id == startingItem.id }
            if (existingIndex != -1) {
                currentIndex = existingIndex
                updatePlaylistState()
                return true
            }
        }

        return try {
            val intros =
                try {
                    if (startPositionMs == 0L) {
                        mediaRepository.getIntros(startingItem.id)
                    } else {
                        Timber.d("Resuming media at ${startPositionMs}ms, skipping intros")
                        emptyList()
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to fetch intros")
                    emptyList()
                }

            val result =
                when (startingItem) {
                    is AfinityEpisode -> {
                        currentSeriesId = startingItem.seriesId
                        isJellyfinPlaylistQueue = false
                        initializeEpisodeQueue(startingItem, seasonId, intros)
                    }

                    else -> {
                        currentSeriesId = null
                        isJellyfinPlaylistQueue = false
                        initializeSingleItemQueue(startingItem, intros)
                    }
                }
            result
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize playlist")
            false
        }
    }

    private suspend fun initializeJellyfinPlaylistQueue(
        startingItem: AfinityItem,
        playlistId: UUID,
    ): Boolean {
        return try {
            currentSeriesId = null
            isJellyfinPlaylistQueue = true
            val videoItems =
                mediaRepository
                    .getPlaylistEntries(playlistId, fields = FieldSets.PLAYABLE_EPISODE)
                    .entries
                    .filterIsInstance<PlaylistEntry.Video>()
                    .map { it.item }

            if (videoItems.isEmpty()) {
                return initializeSingleItemQueue(startingItem, emptyList())
            }

            val startIndex = videoItems.indexOfFirst { it.id == startingItem.id }.coerceAtLeast(0)
            setQueue(videoItems, startIndex)
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize playlist queue for $playlistId")
            initializeSingleItemQueue(startingItem, emptyList())
        }
    }

    private suspend fun initializeEpisodeQueue(
        startingEpisode: AfinityEpisode,
        seasonId: UUID? = null,
        intros: List<AfinityItem> = emptyList(),
    ): Boolean {
        return try {
            if (seasonId != null) {
                Timber.d("Loading episodes for season $seasonId only")
                val episodes =
                    mediaRepository.getEpisodes(seasonId, startingEpisode.seriesId).filter {
                        !it.missing
                    }

                if (episodes.isEmpty()) {
                    val fallbackQueue = intros.toMutableList().apply { add(startingEpisode) }
                    setQueue(fallbackQueue, 0, contentStart = intros.size)
                    return true
                }

                val orderedEpisodes = episodes.toMutableList()
                var startIndex = orderedEpisodes.indexOfFirst { it.id == startingEpisode.id }

                if (startIndex == -1) {
                    orderedEpisodes.add(0, startingEpisode)
                    startIndex = 0
                } else {
                    orderedEpisodes[startIndex] = startingEpisode
                }

                val finalQueue = orderedEpisodes.map { it as AfinityItem }.toMutableList()
                if (intros.isNotEmpty()) {
                    finalQueue.addAll(startIndex, intros)
                }

                setQueue(finalQueue, startIndex, contentStart = startIndex + intros.size)
                return true
            }

            Timber.d("Loading episodes for entire series")
            val allEpisodes =
                mediaRepository
                    .getSeriesEpisodes(startingEpisode.seriesId)
                    .filter { !it.missing }
                    .toMutableList()

            if (allEpisodes.isEmpty()) {
                val fallbackQueue = intros.toMutableList().apply { add(startingEpisode) }
                setQueue(fallbackQueue, 0, contentStart = intros.size)
                return true
            }

            var startIndex = allEpisodes.indexOfFirst { it.id == startingEpisode.id }

            if (startIndex == -1) {
                allEpisodes.add(0, startingEpisode)
                startIndex = 0
            } else {
                allEpisodes[startIndex] = startingEpisode
            }

            val finalQueue = allEpisodes.map { it as AfinityItem }.toMutableList()
            var actualContentStart = startIndex
            if (intros.isNotEmpty()) {
                if (!finalQueue.any { intro -> intros.any { it.id == intro.id } }) {
                    finalQueue.addAll(startIndex, intros)
                    actualContentStart = startIndex + intros.size
                }
            }

            setQueue(finalQueue, startIndex, contentStart = actualContentStart)
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize episode queue")
            val fallbackQueue = intros.toMutableList().apply { add(startingEpisode) }
            setQueue(fallbackQueue, 0, contentStart = intros.size)
            true
        }
    }

    suspend fun enrichWithCollectionQueue(item: AfinityItem): Boolean {
        val skipReason =
            when {
                item !is AfinityMovie -> "not a movie (${item.javaClass.simpleName})"
                isJellyfinPlaylistQueue -> "jellyfin playlist queue"
                currentSeriesId != null -> "series queue"
                currentQueue.size - contentStartIndex > 1 -> "content queue already has siblings"
                getCurrentItem()?.id != item.id ->
                    "current item is ${getCurrentItem()?.name} not ${item.name}"
                else -> null
            }
        if (skipReason != null) {
            Timber.d("Collection queue skipped for ${item.name}: $skipReason")
            return false
        }

        return try {
            val boxSets = mediaRepository.getBoxSetsContaining(item.id, FieldSets.MEDIA_ITEM_CARDS)
            val boxSet =
                boxSets.filter { (it.itemCount ?: 0) > 1 }.minByOrNull { it.itemCount ?: Int.MAX_VALUE }
            if (boxSet == null) {
                Timber.d(
                    "Collection queue: no usable boxset for ${item.name} " +
                        "(${boxSets.size} found: ${boxSets.joinToString { "${it.name}=${it.itemCount}" }})"
                )
                return false
            }

            val siblings =
                mediaRepository.getMovies(
                    parentId = boxSet.id,
                    sortBy = SortBy.RELEASE_DATE,
                    fields = FieldSets.PLAYABLE_EPISODE + ItemFields.OVERVIEW,
                )

            val startIndex = siblings.indexOfFirst { it.id == item.id }
            if (siblings.size <= 1 || startIndex == -1) {
                Timber.d(
                    "Collection queue: \"${boxSet.name}\" returned ${siblings.size} movies, " +
                        "index of ${item.name} = $startIndex"
                )
                return false
            }
            if (getCurrentItem()?.id != item.id) return false

            setQueue(siblings, startIndex)
            isJumpOnlyQueue = true
            collectionName = boxSet.name
            updatePlaylistState()
            Timber.d(
                "Collection queue: ${siblings.size} items from \"${boxSet.name}\", " +
                    "current at $startIndex (jump only)"
            )
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to build collection queue for ${item.name}")
            false
        }
    }

    private fun initializeSingleItemQueue(item: AfinityItem, intros: List<AfinityItem>): Boolean {
        Timber.d("Initializing single item queue for: ${item.name} with ${intros.size} intros")

        val queue = mutableListOf<AfinityItem>()
        queue.addAll(intros)
        queue.add(item)

        setQueue(queue, 0, contentStart = intros.size)
        return true
    }

    fun setQueue(queue: List<AfinityItem>, startIndex: Int = 0, contentStart: Int = 0) {
        currentQueue.clear()
        currentQueue.addAll(queue)
        currentIndex = startIndex.coerceIn(0, queue.size - 1)
        contentStartIndex = contentStart.coerceIn(0, queue.size)
        isJumpOnlyQueue = false
        collectionName = null

        updatePlaylistState()

        Timber.d(
            "Queue set with ${queue.size} items, starting at index $currentIndex, content starts at $contentStartIndex"
        )
    }

    fun next(): AfinityItem? {
        if (!hasNext()) {
            Timber.d("No next item available (current: $currentIndex, size: ${currentQueue.size})")
            return null
        }

        currentIndex++
        updatePlaylistState()

        val nextItem = currentQueue.getOrNull(currentIndex)
        Timber.d("Moved to next item: ${nextItem?.name} (index: $currentIndex)")
        return nextItem
    }

    fun previous(): AfinityItem? {
        if (!hasPrevious()) {
            Timber.d("No previous item available (current: $currentIndex)")
            return null
        }

        currentIndex--
        updatePlaylistState()

        val previousItem = currentQueue.getOrNull(currentIndex)
        Timber.d("Moved to previous item: ${previousItem?.name} (index: $currentIndex)")
        return previousItem
    }

    fun hasNext(): Boolean {
        return currentIndex >= 0 && currentIndex < currentQueue.size - 1
    }

    fun canAutoAdvance(): Boolean {
        if (!hasNext()) return false
        return !(isJumpOnlyQueue && currentIndex >= contentStartIndex)
    }

    fun hasPrevious(): Boolean {
        return currentIndex > 0 && currentQueue.isNotEmpty()
    }

    fun getCurrentItem(): AfinityItem? {
        return currentQueue.getOrNull(currentIndex)
    }

    fun getNextItem(): AfinityItem? {
        return if (hasNext()) currentQueue.getOrNull(currentIndex + 1) else null
    }

    fun getPreviousItem(): AfinityItem? {
        return if (hasPrevious()) currentQueue.getOrNull(currentIndex - 1) else null
    }

    fun jumpToItem(itemId: UUID): AfinityItem? {
        val targetIndex = currentQueue.indexOfFirst { it.id == itemId }
        if (targetIndex == -1) {
            Timber.w("Item with ID $itemId not found in queue")
            return null
        }

        currentIndex = targetIndex
        updatePlaylistState()

        val targetItem = currentQueue.getOrNull(currentIndex)
        Timber.d("Jumped to item: ${targetItem?.name} (index: $currentIndex)")
        return targetItem
    }

    fun insertAfterCurrent(items: List<AfinityItem>) {
        if (items.isEmpty()) return
        val insertIndex = currentIndex + 1
        currentQueue.addAll(insertIndex, items)
        updatePlaylistState()
        Timber.d("Inserted ${items.size} additional parts at index $insertIndex")
    }

    fun markCurrentItemAsPlayed() {
        val item = currentQueue.getOrNull(currentIndex) ?: return
        currentQueue[currentIndex] =
            when (item) {
                is AfinityEpisode -> item.copy(played = true, playbackPositionTicks = 0)
                is AfinityMovie -> item.copy(played = true, playbackPositionTicks = 0)
                else -> return
            }
    }

    fun clearQueue() {
        currentQueue.clear()
        currentIndex = -1
        currentSeriesId = null
        isJellyfinPlaylistQueue = false
        contentStartIndex = 0
        isJumpOnlyQueue = false
        collectionName = null
        updatePlaylistState()
        Timber.d("Queue cleared")
    }

    fun isEmpty(): Boolean {
        return currentQueue.isEmpty()
    }

    fun shuffleQueue() {
        if (currentQueue.size <= 1) {
            Timber.d("Queue has ${currentQueue.size} items, no shuffle needed")
            return
        }

        if (isJellyfinPlaylistQueue) {
            currentQueue.shuffle()
            currentIndex = 0
            contentStartIndex = 0
            updatePlaylistState()
            Timber.d("Playlist queue pure shuffled (${currentQueue.size} items)")
            return
        }

        val resumableIndex = currentQueue.indexOfFirst { item ->
            item.playbackPositionTicks > 0 && item.playbackPositionTicks < item.runtimeTicks
        }

        val unwatchedIndex =
            if (resumableIndex == -1) {
                currentQueue.indexOfFirst { item ->
                    !item.played && item.playbackPositionTicks == 0L
                }
            } else {
                -1
            }

        val priorityIndex = if (resumableIndex != -1) resumableIndex else unwatchedIndex

        if (priorityIndex != -1) {
            val priorityEpisode = currentQueue[priorityIndex]
            val remainingEpisodes =
                currentQueue.toMutableList().apply {
                    removeAt(priorityIndex)
                    shuffle()
                }

            currentQueue.clear()
            currentQueue.add(priorityEpisode)
            currentQueue.addAll(remainingEpisodes)
            currentIndex = 0
            contentStartIndex = 0

            Timber.d(
                "Queue shuffled with priority episode: ${priorityEpisode.name} (${currentQueue.size} total items)"
            )
        } else {
            currentQueue.shuffle()
            currentIndex = 0
            contentStartIndex = 0

            Timber.d("Queue pure shuffled (${currentQueue.size} items)")
        }

        updatePlaylistState()
    }

    private fun updatePlaylistState() {
        _playlistState.value =
            PlaylistState(
                queue = currentQueue.toList(),
                currentIndex = currentIndex,
                hasNext = hasNext(),
                hasPrevious = hasPrevious(),
                currentItem = getCurrentItem(),
                contentStartIndex = contentStartIndex,
                collectionName = collectionName,
            )

        Timber.d(
            "Playlist state updated: index=$currentIndex, hasNext=${hasNext()}, hasPrevious=${hasPrevious()}"
        )
    }
}
