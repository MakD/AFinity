package com.makd.afinity.ui.player

import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.repository.JellyfinRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
)

@Singleton
class PlaylistManager @Inject constructor(private val jellyfinRepository: JellyfinRepository) {
    private val _playlistState = MutableStateFlow(PlaylistState())
    val playlistState: StateFlow<PlaylistState> = _playlistState.asStateFlow()

    private var currentQueue: MutableList<AfinityItem> = mutableListOf()
    private var currentIndex: Int = -1
    private var currentSeriesId: UUID? = null

    suspend fun initializePlaylist(
        startingItem: AfinityItem,
        seasonId: UUID? = null,
        startPositionMs: Long = 0L,
    ): Boolean {
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
                        jellyfinRepository.getIntros(startingItem.id)
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
                        initializeEpisodeQueue(startingItem, seasonId, intros)
                    }

                    else -> {
                        currentSeriesId = null
                        initializeSingleItemQueue(startingItem, intros)
                    }
                }
            result
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize playlist")
            false
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
                val episodes = jellyfinRepository.getEpisodes(seasonId, startingEpisode.seriesId)

                if (episodes.isEmpty()) {
                    val fallbackQueue = intros.toMutableList().apply { add(startingEpisode) }
                    setQueue(fallbackQueue, 0)
                    return true
                }

                val sortedEpisodes = episodes.sortedBy { it.indexNumber ?: 0 }.toMutableList()
                var startIndex = sortedEpisodes.indexOfFirst { it.id == startingEpisode.id }

                if (startIndex == -1) {
                    sortedEpisodes.add(0, startingEpisode)
                    startIndex = 0
                } else {
                    sortedEpisodes[startIndex] = startingEpisode
                }

                val finalQueue = sortedEpisodes.map { it as AfinityItem }.toMutableList()
                if (intros.isNotEmpty()) {
                    finalQueue.addAll(startIndex, intros)
                }

                setQueue(finalQueue, startIndex)
                return true
            }

            Timber.d("Loading episodes for entire series")
            val seasons =
                jellyfinRepository.getSeasons(
                    startingEpisode.seriesId,
                    SortBy.NAME,
                    sortDescending = false,
                )

            if (seasons.isEmpty()) {
                val fallbackQueue = intros.toMutableList().apply { add(startingEpisode) }
                setQueue(fallbackQueue, 0)
                return true
            }

            val allEpisodes = mutableListOf<AfinityEpisode>()

            for (season in seasons.sortedBy { it.indexNumber ?: 0 }) {
                try {
                    val episodes =
                        jellyfinRepository.getEpisodes(season.id, startingEpisode.seriesId)

                    if (episodes.isNotEmpty()) {
                        allEpisodes.addAll(episodes.sortedBy { it.indexNumber ?: 0 })
                    }

                    if (allEpisodes.isNotEmpty()) {
                        val tempQueue = allEpisodes.map { it as AfinityItem }.toMutableList()
                        val currentTargetIndex =
                            tempQueue.indexOfFirst { it.id == startingEpisode.id }

                        if (currentTargetIndex != -1 && intros.isNotEmpty()) {
                            tempQueue.addAll(currentTargetIndex, intros)
                            setQueue(tempQueue, currentTargetIndex)
                        } else {
                            setQueue(tempQueue, currentTargetIndex.coerceAtLeast(0))
                        }
                    }
                } catch (_: Exception) {
                    Timber.w("Failed to load episodes for season ${season.indexNumber}")
                }
            }

            if (allEpisodes.isEmpty()) {
                val fallbackQueue = intros.toMutableList().apply { add(startingEpisode) }
                setQueue(fallbackQueue, 0)
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
            if (intros.isNotEmpty()) {
                if (!finalQueue.any { intro -> intros.any { it.id == intro.id } }) {
                    finalQueue.addAll(startIndex, intros)
                }
            }

            setQueue(finalQueue, startIndex)
            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize episode queue")
            val fallbackQueue = intros.toMutableList().apply { add(startingEpisode) }
            setQueue(fallbackQueue, 0)
            true
        }
    }

    private fun initializeSingleItemQueue(item: AfinityItem, intros: List<AfinityItem>): Boolean {
        Timber.d("Initializing single item queue for: ${item.name} with ${intros.size} intros")

        val queue = mutableListOf<AfinityItem>()
        queue.addAll(intros)
        queue.add(item)

        setQueue(queue, 0)
        return true
    }

    fun setQueue(queue: List<AfinityItem>, startIndex: Int = 0) {
        currentQueue.clear()
        currentQueue.addAll(queue)
        currentIndex = startIndex.coerceIn(0, queue.size - 1)

        updatePlaylistState()

        Timber.d("Queue set with ${queue.size} items, starting at index $currentIndex")
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

    fun clearQueue() {
        currentQueue.clear()
        currentIndex = -1
        currentSeriesId = null
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

        val resumableIndex =
            currentQueue.indexOfFirst { item ->
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

            Timber.d(
                "Queue shuffled with priority episode: ${priorityEpisode.name} (${currentQueue.size} total items)"
            )
        } else {
            currentQueue.shuffle()
            currentIndex = 0

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
            )

        Timber.d(
            "Playlist state updated: index=$currentIndex, hasNext=${hasNext()}, hasPrevious=${hasPrevious()}"
        )
    }
}
