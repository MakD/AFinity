package com.makd.afinity.data.repository.download

import com.makd.afinity.data.download.MediaDownloadManager
import com.makd.afinity.data.models.download.DownloadItemType
import com.makd.afinity.data.models.download.DownloadState
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinitySource
import com.makd.afinity.data.models.media.AfinitySourceType
import com.makd.afinity.data.models.media.AfinityVideo
import com.makd.afinity.data.repository.JellyfinRepository
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository interface for download operations
 */
interface DownloadRepository {
    val downloadStates: StateFlow<Map<UUID, DownloadState>>

    suspend fun downloadItem(item: AfinityItem, source: AfinitySource): Long?
    fun cancelDownload(itemId: UUID)
    suspend fun deleteDownload(itemId: UUID)
    fun getDownloadState(itemId: UUID): DownloadState
    fun isDownloaded(itemId: UUID): Boolean
    fun isDownloading(itemId: UUID): Boolean
}

/**
 * Implementation of DownloadRepository
 */
@Singleton
class DownloadRepositoryImpl @Inject constructor(
    private val mediaDownloadManager: MediaDownloadManager,
    private val jellyfinRepository: JellyfinRepository
) : DownloadRepository {

    override val downloadStates: StateFlow<Map<UUID, DownloadState>>
        get() = mediaDownloadManager.downloadStates

    /**
     * Download a media item
     *
     * @param item The item to download (Movie, Episode, or Video)
     * @param source The source to download from
     * @return The download ID, or null if download failed to start
     */
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

    /**
     * Cancel an active download
     */
    override fun cancelDownload(itemId: UUID) {
        mediaDownloadManager.cancelDownload(itemId)
    }

    /**
     * Delete a downloaded item
     */
    override suspend fun deleteDownload(itemId: UUID) {
        mediaDownloadManager.deleteDownload(itemId)
    }

    /**
     * Get the current download state for an item
     */
    override fun getDownloadState(itemId: UUID): DownloadState {
        return mediaDownloadManager.getDownloadState(itemId)
    }

    /**
     * Check if an item is fully downloaded
     */
    override fun isDownloaded(itemId: UUID): Boolean {
        val state = getDownloadState(itemId)
        return state is DownloadState.Completed
    }

    /**
     * Check if an item is currently downloading
     */
    override fun isDownloading(itemId: UUID): Boolean {
        val state = getDownloadState(itemId)
        return state is DownloadState.Downloading || state is DownloadState.Queued
    }
}