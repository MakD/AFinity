package com.makd.afinity.ui.player

import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.repository.JellyfinRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

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

    suspend fun initializePlaylist(startingItem: AfinityItem, seasonId: UUID? = null): Boolean {
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
            val result =
                when (startingItem) {
                    is AfinityEpisode -> {
                        currentSeriesId = startingItem.seriesId
                        initializeEpisodeQueue(startingItem, seasonId)
                    }

                    else -> {
                        currentSeriesId = null
                        initializeSingleItemQueue(startingItem)
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
    ): Boolean {
        return try {
            if (seasonId != null) {
                Timber.d("Loading episodes for season $seasonId only")
                val episodes = jellyfinRepository.getEpisodes(seasonId, startingEpisode.seriesId)

                if (episodes.isEmpty()) {
                    setQueue(listOf(startingEpisode), 0)
                    return true
                }

                val sortedEpisodes = episodes.sortedBy { it.indexNumber ?: 0 }.toMutableList()
                val startIndex = sortedEpisodes.indexOfFirst { it.id == startingEpisode.id }

                if (startIndex == -1) {
                    sortedEpisodes.add(0, startingEpisode)
                    setQueue(sortedEpisodes.map { it as AfinityItem }, 0)
                } else {
                    sortedEpisodes[startIndex] = startingEpisode
                    setQueue(sortedEpisodes.map { it as AfinityItem }, startIndex)
                }

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
                setQueue(listOf(startingEpisode), 0)
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
                        setQueue(
                            allEpisodes.map { it as AfinityItem },
                            allEpisodes
                                .indexOfFirst { it.id == startingEpisode.id }
                                .coerceAtLeast(0),
                        )
                    }
                } catch (_: Exception) {
                    Timber.w("Failed to load episodes for season ${season.indexNumber}")
                }
            }

            if (allEpisodes.isEmpty()) {
                setQueue(listOf(startingEpisode), 0)
                return true
            }

            val startIndex = allEpisodes.indexOfFirst { it.id == startingEpisode.id }

            if (startIndex == -1) {
                allEpisodes.add(0, startingEpisode)
                setQueue(allEpisodes.map { it as AfinityItem }, 0)
            } else {
                allEpisodes[startIndex] = startingEpisode
                setQueue(allEpisodes.map { it as AfinityItem }, startIndex)
            }

            true
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize episode queue")
            setQueue(listOf(startingEpisode), 0)
            true
        }
    }

    private fun initializeSingleItemQueue(item: AfinityItem): Boolean {
        Timber.d("Initializing single item queue for: ${item.name}")
        setQueue(listOf(item), 0)
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
