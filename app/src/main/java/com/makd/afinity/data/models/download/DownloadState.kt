package com.makd.afinity.data.models.download

import com.makd.afinity.data.models.media.AfinityItem
import java.io.File
import java.util.UUID

/**
 * Represents the current state of a download operation
 */
sealed class DownloadState {
    /**
     * No download in progress
     */
    data object Idle : DownloadState()

    /**
     * Download is queued but not started yet
     */
    data class Queued(
        val itemId: UUID,
        val itemName: String
    ) : DownloadState()

    /**
     * Download is currently in progress
     * @param itemId The ID of the item being downloaded
     * @param itemName The name of the item
     * @param progress Download progress (0-100)
     * @param bytesDownloaded Bytes downloaded so far
     * @param totalBytes Total bytes to download
     */
    data class Downloading(
        val itemId: UUID,
        val itemName: String,
        val progress: Int,
        val bytesDownloaded: Long,
        val totalBytes: Long
    ) : DownloadState()

    /**
     * Download completed successfully
     * @param itemId The ID of the downloaded item
     * @param itemName The name of the item
     * @param file The downloaded file
     */
    data class Completed(
        val itemId: UUID,
        val itemName: String,
        val file: File
    ) : DownloadState()

    /**
     * Download failed
     * @param itemId The ID of the item that failed
     * @param itemName The name of the item
     * @param error Error message
     */
    data class Failed(
        val itemId: UUID,
        val itemName: String,
        val error: String
    ) : DownloadState()

    /**
     * Download was cancelled by user
     */
    data class Cancelled(
        val itemId: UUID,
        val itemName: String
    ) : DownloadState()
}

/**
 * Information about a download task
 */
data class DownloadTask(
    val itemId: UUID,
    val itemName: String,
    val sourceId: String,
    val downloadUrl: String,
    val downloadId: Long,
    val fileName: String,
    val itemType: DownloadItemType,
    val item: AfinityItem
)

/**
 * Type of item being downloaded
 */
enum class DownloadItemType {
    MOVIE,
    EPISODE,
    VIDEO
}

/**
 * Download queue item with priority
 */
data class QueuedDownloadItem(
    val itemId: UUID,
    val itemName: String,
    val sourceId: String,
    val downloadUrl: String,
    val itemType: DownloadItemType,
    val item: AfinityItem,
    val priority: DownloadPriority = DownloadPriority.NORMAL,
    val addedAt: Long = System.currentTimeMillis()
)

/**
 * Priority levels for downloads
 */
enum class DownloadPriority(val value: Int) {
    LOW(0),
    NORMAL(1),
    HIGH(2),
    URGENT(3);

    companion object {
        fun fromValue(value: Int): DownloadPriority {
            return entries.find { it.value == value } ?: NORMAL
        }
    }
}

/**
 * Download progress information
 */
data class DownloadProgress(
    val downloadId: Long,
    val itemId: UUID,
    val progress: Int,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val state: DownloadState
)