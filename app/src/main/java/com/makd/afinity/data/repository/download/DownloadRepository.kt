package com.makd.afinity.data.repository.download

import com.makd.afinity.data.download.MediaDownloadManager
import com.makd.afinity.data.models.download.DownloadItemType
import com.makd.afinity.data.models.download.DownloadPriority
import com.makd.afinity.data.models.download.DownloadState
import com.makd.afinity.data.models.download.QueuedDownloadItem
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinitySource
import com.makd.afinity.data.models.media.AfinitySourceType
import com.makd.afinity.data.models.media.AfinityVideo
import com.makd.afinity.data.repository.JellyfinRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface DownloadRepository {
    val downloadStates: StateFlow<Map<UUID, DownloadState>>

    suspend fun downloadItem(item: AfinityItem, source: AfinitySource): Long?
    fun cancelDownload(itemId: UUID)
    suspend fun deleteDownload(itemId: UUID)
    fun getDownloadState(itemId: UUID): DownloadState
    fun isDownloaded(itemId: UUID): Boolean
    fun isDownloading(itemId: UUID): Boolean
    fun getDownloadQueue(): List<QueuedDownloadItem>
    fun removeFromQueue(itemId: UUID)
    fun changePriority(itemId: UUID, priority: DownloadPriority)
    fun setMaxConcurrentDownloads(max: Int)
    suspend fun downloadSeason(seasonId: UUID, seriesId: UUID, priority: DownloadPriority = DownloadPriority.NORMAL): Int
}

@Singleton
class DownloadRepositoryImpl @Inject constructor(
    private val mediaDownloadManager: MediaDownloadManager,
    private val jellyfinRepository: JellyfinRepository
) : DownloadRepository {

    override val downloadStates: StateFlow<Map<UUID, DownloadState>>
        get() = mediaDownloadManager.downloadStates

    override suspend fun downloadItem(item: AfinityItem, source: AfinitySource): Long? {
        if (source.type != AfinitySourceType.REMOTE) {
            Timber.w("Cannot download from non-remote source: ${source.type}")
            return null
        }

        val itemType = when (item) {
            is AfinityMovie -> DownloadItemType.MOVIE
            is AfinityEpisode -> DownloadItemType.EPISODE
            is AfinityVideo -> DownloadItemType.VIDEO
            else -> {
                Timber.w("Unsupported item type for download: ${item::class.simpleName}")
                return null
            }
        }

        val baseUrl = jellyfinRepository.getBaseUrl()

        Timber.d("Starting download for ${item.name} (${itemType.name}) from $baseUrl")

        return mediaDownloadManager.downloadItem(
            itemId = item.id,
            itemName = item.name,
            sourceId = source.id,
            downloadUrl = source.path,
            itemType = itemType,
            baseUrl = baseUrl
        )
    }

    override fun cancelDownload(itemId: UUID) {
        mediaDownloadManager.cancelDownload(itemId)
    }

    override suspend fun deleteDownload(itemId: UUID) {
        mediaDownloadManager.deleteDownload(itemId)
    }

    override fun getDownloadState(itemId: UUID): DownloadState {
        return mediaDownloadManager.getDownloadState(itemId)
    }

    override fun isDownloaded(itemId: UUID): Boolean {
        val state = getDownloadState(itemId)
        return state is DownloadState.Completed
    }

    override fun isDownloading(itemId: UUID): Boolean {
        val state = getDownloadState(itemId)
        return state is DownloadState.Downloading || state is DownloadState.Queued
    }

    override fun getDownloadQueue(): List<QueuedDownloadItem> {
        return mediaDownloadManager.getDownloadQueue()
    }

    override fun removeFromQueue(itemId: UUID) {
        mediaDownloadManager.removeFromQueue(itemId)
    }

    override fun changePriority(itemId: UUID, priority: DownloadPriority) {
        mediaDownloadManager.changePriority(itemId, priority)
    }

    override fun setMaxConcurrentDownloads(max: Int) {
        mediaDownloadManager.setMaxConcurrentDownloads(max)
    }

    override suspend fun downloadSeason(
        seasonId: UUID,
        seriesId: UUID,
        priority: DownloadPriority
    ): Int {
        return withContext(Dispatchers.IO) {
            try {
                val episodes = jellyfinRepository.getEpisodes(seasonId, seriesId)
                val baseUrl = jellyfinRepository.getBaseUrl()

                var queuedCount = 0

                episodes.forEach { episode ->
                    if (isDownloaded(episode.id)) {
                        Timber.d("Episode already downloaded: ${episode.name}")
                        return@forEach
                    }

                    val source = episode.sources.firstOrNull { it.type == AfinitySourceType.REMOTE }
                    if (source == null) {
                        Timber.w("No remote source found for episode: ${episode.name}")
                        return@forEach
                    }

                    mediaDownloadManager.addToQueue(
                        itemId = episode.id,
                        itemName = episode.name,
                        sourceId = source.id,
                        downloadUrl = source.path,
                        itemType = DownloadItemType.EPISODE,
                        priority = priority
                    )

                    queuedCount++
                }

                Timber.d("Queued $queuedCount episodes for download")
                queuedCount

            } catch (e: Exception) {
                Timber.e(e, "Failed to download season: $seasonId")
                0
            }
        }
    }
}