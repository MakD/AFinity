package com.makd.afinity.ui.player

import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.repository.JellyfinRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class PlaylistState(
    val queue: List<AfinityItem> = emptyList(),
    val currentIndex: Int = -1,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val currentItem: AfinityItem? = null
)

@Singleton
class PlaylistManager @Inject constructor(
    private val jellyfinRepository: JellyfinRepository
) {
    private val _playlistState = MutableStateFlow(PlaylistState())
    val playlistState: StateFlow<PlaylistState> = _playlistState.asStateFlow()

    private var currentQueue: MutableList<AfinityItem> = mutableListOf()
    private var currentIndex: Int = -1

    suspend fun initializePlaylist(startingItem: AfinityItem): Boolean {
        Timber.d("Initializing playlist for: ${startingItem.name} (type: ${startingItem::class.simpleName})")

        return try {
            val result = when (startingItem) {
                is AfinityEpisode -> {
                    Timber.d("Initializing episode queue for series: ${startingItem.seriesId}")
                    initializeEpisodeQueue(startingItem)
                }

                is AfinityMovie -> {
                    Timber.d("Initializing single item queue for movie")
                    initializeSingleItemQueue(startingItem)
                }

                else -> {
                    Timber.d("Initializing single item queue for other type: ${startingItem::class.simpleName}")
                    initializeSingleItemQueue(startingItem)
                }
            }

            Timber.d("Playlist initialization result: $result, queue size: ${currentQueue.size}, current index: $currentIndex")
            result
        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize playlist for: ${startingItem.name}")
            false
        }
    }

    private suspend fun initializeEpisodeQueue(startingEpisode: AfinityEpisode): Boolean {
        return try {
            Timber.d("Getting seasons for series: ${startingEpisode.seriesId}")
            val seasons = jellyfinRepository.getSeasons(
                startingEpisode.seriesId,
                SortBy.NAME,
                sortDescending = false
            )

            if (seasons.isEmpty()) {
                Timber.w("No seasons found for series: ${startingEpisode.seriesId}")
                setQueue(listOf(startingEpisode as AfinityItem), 0)
                return true
            }

            Timber.d("Found ${seasons.size} seasons for series")

            val allEpisodes = mutableListOf<AfinityEpisode>()

            for (season in seasons.sortedBy { it.indexNumber ?: 0 }) {
                try {
                    Timber.d("Loading episodes for season ${season.indexNumber} (${season.id})")
                    val episodes =
                        jellyfinRepository.getEpisodes(season.id, startingEpisode.seriesId)
                    Timber.d("Season ${season.indexNumber}: ${episodes.size} episodes")

                    if (episodes.isNotEmpty()) {
                        val episodesWithSources = withContext(Dispatchers.IO) {
                            coroutineScope {
                                episodes.sortedBy { it.indexNumber ?: 0 }.map { episode ->
                                    async {
                                        try {
                                            val fullEpisode =
                                                jellyfinRepository.getItemById(episode.id) as? AfinityEpisode
                                            if (fullEpisode != null && fullEpisode.sources.isNotEmpty()) {
                                                Timber.d("Loaded episode with ${fullEpisode.sources.size} sources: ${fullEpisode.name}")
                                                fullEpisode
                                            } else {
                                                Timber.w("Episode ${episode.name} has no media sources")
                                                episode
                                            }
                                        } catch (e: Exception) {
                                            Timber.w(
                                                e,
                                                "Failed to load full details for episode ${episode.name}"
                                            )
                                            episode
                                        }
                                    }
                                }.map { it.await() }
                            }
                        }
                        allEpisodes.addAll(episodesWithSources)
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to load episodes for season ${season.id}")
                }
            }

            if (allEpisodes.isEmpty()) {
                Timber.w("No episodes found for series, using single episode")
                setQueue(listOf(startingEpisode as AfinityItem), 0)
                return true
            }

            val startingEpisodeWithSources = if (startingEpisode.sources.isEmpty()) {
                try {
                    Timber.d("Starting episode has no sources, loading full details...")
                    jellyfinRepository.getItemById(startingEpisode.id) as? AfinityEpisode
                        ?: startingEpisode
                } catch (e: Exception) {
                    Timber.w(e, "Failed to load sources for starting episode")
                    startingEpisode
                }
            } else {
                startingEpisode
            }

            val startIndex = allEpisodes.indexOfFirst { it.id == startingEpisodeWithSources.id }
            if (startIndex == -1) {
                Timber.w("Starting episode not found in queue, adding it")
                allEpisodes.add(0, startingEpisodeWithSources)
                setQueue(allEpisodes.map { it as AfinityItem }, 0)
            } else {
                allEpisodes[startIndex] = startingEpisodeWithSources
                setQueue(allEpisodes.map { it as AfinityItem }, startIndex)
            }

            Timber.d("Episode queue initialized: ${allEpisodes.size} episodes, starting at index $currentIndex")
            true

        } catch (e: Exception) {
            Timber.e(e, "Failed to initialize episode queue")
            setQueue(listOf(startingEpisode as AfinityItem), 0)
            true
        }
    }

    private suspend fun initializeSingleItemQueue(item: AfinityItem): Boolean {
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

    fun addToQueue(item: AfinityItem) {
        currentQueue.add(item)
        updatePlaylistState()
        Timber.d("Added ${item.name} to queue")
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

    fun getQueueInfo(): String {
        return if (currentQueue.isNotEmpty() && currentIndex >= 0) {
            "${currentIndex + 1} of ${currentQueue.size}"
        } else {
            "Empty queue"
        }
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

    fun getAllItems(): List<AfinityItem> {
        return currentQueue.toList()
    }

    private fun updatePlaylistState() {
        _playlistState.value = PlaylistState(
            queue = currentQueue.toList(),
            currentIndex = currentIndex,
            hasNext = hasNext(),
            hasPrevious = hasPrevious(),
            currentItem = getCurrentItem()
        )

        Timber.d("Playlist state updated: index=$currentIndex, hasNext=${hasNext()}, hasPrevious=${hasPrevious()}")
    }
}