package com.makd.afinity.data.download

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import com.makd.afinity.data.database.dao.SourceDao
import com.makd.afinity.data.database.entities.AfinitySourceDto
import com.makd.afinity.data.models.download.DownloadItemType
import com.makd.afinity.data.models.download.DownloadState
import com.makd.afinity.data.models.download.DownloadTask
import com.makd.afinity.data.models.media.AfinitySourceType
import com.makd.afinity.data.repository.PreferencesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import timber.log.Timber
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages media downloads using Android's DownloadManager
 *
 * This class handles:
 * - Initiating downloads for movies and episodes
 * - Tracking download progress
 * - Handling download completion
 * - Storing download metadata in the database
 */
@Singleton
class MediaDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sourceDao: SourceDao,
    private val preferencesRepository: PreferencesRepository,
    private val apiClient: ApiClient
) {
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private val _downloadStates = MutableStateFlow<Map<UUID, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<UUID, DownloadState>> = _downloadStates.asStateFlow()

    private val activeTasks = mutableMapOf<Long, DownloadTask>()
    private val progressJobs = mutableMapOf<Long, Job>()

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            Timber.d("Download complete broadcast received for ID: $downloadId")

            if (downloadId in activeTasks) {
                handleDownloadComplete(downloadId)
            }
        }
    }

    init {
        context.registerReceiver(
            downloadReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
            Context.RECEIVER_NOT_EXPORTED
        )
        Timber.d("MediaDownloadManager initialized")

        coroutineScope.launch(Dispatchers.IO) {
            restoreDownloadStates()
        }
    }

    /**
     * Download a media item
     *
     * @param itemId The UUID of the item to download
     * @param itemName The name of the item
     * @param sourceId The source ID from Jellyfin
     * @param downloadUrl The URL to download from
     * @param itemType The type of item (MOVIE, EPISODE, VIDEO)
     * @return The download ID from DownloadManager, or null if download failed to start
     */
    suspend fun downloadItem(
        itemId: UUID,
        itemName: String,
        sourceId: String,
        downloadUrl: String,
        itemType: DownloadItemType,
        baseUrl: String
    ): Long? {
        try {
            val existingSource = sourceDao.getSourcesForItem(itemId)
                .find { it.type == AfinitySourceType.LOCAL }

            if (existingSource != null) {
                Timber.w("Item $itemId already downloaded or downloading")
                return null
            }

            val downloadDir = getDownloadDirectory()
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }

            val streamUrl = if (downloadUrl.isNotBlank()) {
                downloadUrl
            } else {
                "$baseUrl/Videos/$itemId/stream?static=true&mediaSourceId=$sourceId"
            }

            val apiKey = apiClient.accessToken ?: ""
            val finalDownloadUrl = if (streamUrl.contains("?")) {
                "$streamUrl&api_key=$apiKey"
            } else {
                "$streamUrl?api_key=$apiKey"
            }

            val fileName = "${itemId}_${sourceId}"

            Timber.d("Download URL: $finalDownloadUrl (with auth)")
            Timber.d("Saving to: ${File(downloadDir, fileName).absolutePath}")

            val request = DownloadManager.Request(Uri.parse(finalDownloadUrl))
                .setTitle(itemName)
                .setDescription("Downloading $itemName")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(
                    context,
                    Environment.DIRECTORY_DOWNLOADS + "/media",
                    fileName
                )
                .setAllowedOverMetered(!preferencesRepository.getDownloadOverWifiOnly())
                .setAllowedOverRoaming(false)

            val downloadId = downloadManager.enqueue(request)

            val task = DownloadTask(
                itemId = itemId,
                itemName = itemName,
                sourceId = sourceId,
                downloadUrl = finalDownloadUrl,
                downloadId = downloadId,
                fileName = fileName,
                itemType = itemType
            )

            activeTasks[downloadId] = task

            updateDownloadState(itemId, DownloadState.Queued(itemId, itemName))

            trackDownloadProgress(downloadId, task)

            Timber.d("Started download for $itemName with ID: $downloadId")

            return downloadId

        } catch (e: Exception) {
            Timber.e(e, "Failed to start download for item: $itemId")
            updateDownloadState(itemId, DownloadState.Failed(itemId, itemName, e.message ?: "Unknown error"))
            return null
        }
    }

    /**
     * Cancel an active download
     */
    fun cancelDownload(itemId: UUID) {
        val task = activeTasks.values.find { it.itemId == itemId } ?: return

        downloadManager.remove(task.downloadId)
        activeTasks.remove(task.downloadId)
        progressJobs[task.downloadId]?.cancel()
        progressJobs.remove(task.downloadId)

        updateDownloadState(itemId, DownloadState.Cancelled(itemId, task.itemName))

        val file = File(getDownloadDirectory(), task.fileName)
        if (file.exists()) {
            file.delete()
        }

        Timber.d("Cancelled download for: ${task.itemName}")
    }

    /**
     * Get download state for a specific item
     */
    fun getDownloadState(itemId: UUID): DownloadState {
        return _downloadStates.value[itemId] ?: DownloadState.Idle
    }

    /**
     * Track download progress
     */
    private fun trackDownloadProgress(downloadId: Long, task: DownloadTask) {
        progressJobs[downloadId]?.cancel()

        progressJobs[downloadId] = coroutineScope.launch {
            while (isActive && downloadId in activeTasks) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)

                if (cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val status = cursor.getInt(statusIndex)

                    val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val totalBytesIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val bytesDownloaded = cursor.getLong(bytesDownloadedIndex)
                    val totalBytes = cursor.getLong(totalBytesIndex)

                    when (status) {
                        DownloadManager.STATUS_RUNNING -> {
                            if (totalBytes > 0) {
                                val progress = ((bytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 99)
                                updateDownloadState(
                                    task.itemId,
                                    DownloadState.Downloading(
                                        task.itemId,
                                        task.itemName,
                                        progress,
                                        bytesDownloaded,
                                        totalBytes
                                    )
                                )
                                Timber.d("Download progress for ${task.itemName}: $progress%")
                            }
                        }
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            Timber.d("Download successful for ${task.itemName}")
                            cursor.close()
                            delay(1000)
                            if (downloadId in activeTasks) {
                                Timber.w("Broadcast receiver didn't fire, handling completion manually")
                                handleDownloadComplete(downloadId)
                            }
                            return@launch
                        }
                        DownloadManager.STATUS_FAILED -> {
                            val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                            val reason = cursor.getInt(reasonIndex)
                            val errorMessage = getDownloadErrorMessage(reason)
                            Timber.e("Download failed for ${task.itemName}: $errorMessage")
                            updateDownloadState(
                                task.itemId,
                                DownloadState.Failed(task.itemId, task.itemName, errorMessage)
                            )
                            cursor.close()
                            return@launch
                        }
                    }
                } else {
                    cursor.close()
                    return@launch
                }
                cursor.close()
                delay(500)
            }
        }
    }

    /**
     * Handle download completion
     */
    private fun handleDownloadComplete(downloadId: Long) {
        Timber.d("Handling download completion for ID: $downloadId")

        val task = activeTasks[downloadId] ?: return
        progressJobs[downloadId]?.cancel()

        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)

        if (cursor.moveToFirst()) {
            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val status = cursor.getInt(statusIndex)

            when (status) {
                DownloadManager.STATUS_SUCCESSFUL -> {
                    val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                    val uriString = cursor.getString(uriIndex)

                    val file = if (uriString != null) {
                        if (uriString.startsWith("file://")) {
                            File(Uri.parse(uriString).path ?: "")
                        } else {
                            File(uriString)
                        }
                    } else {
                        null
                    }

                    if (file?.exists() == true) {
                        Timber.d("Downloaded file: ${file.absolutePath}")
                        coroutineScope.launch {
                            try {
                                val sourceDto = AfinitySourceDto(
                                    id = task.sourceId,
                                    itemId = task.itemId,
                                    name = task.itemName,
                                    type = AfinitySourceType.LOCAL,
                                    path = file.absolutePath,
                                    downloadId = downloadId
                                )

                                sourceDao.insertSource(sourceDto)

                                updateDownloadState(
                                    task.itemId,
                                    DownloadState.Completed(task.itemId, task.itemName, file)
                                )

                                Timber.d("Download completed and saved to database: ${file.absolutePath}")
                            } catch (e: Exception) {
                                Timber.e(e, "Failed to save download to database")
                                updateDownloadState(
                                    task.itemId,
                                    DownloadState.Failed(task.itemId, task.itemName, "Failed to save to database")
                                )
                            }
                        }
                    } else {
                        Timber.e("Downloaded file not found: ${file?.absolutePath ?: "null"}")
                        updateDownloadState(
                            task.itemId,
                            DownloadState.Failed(task.itemId, task.itemName, "Downloaded file not found")
                        )
                    }
                }
                DownloadManager.STATUS_FAILED -> {
                    val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                    val reason = cursor.getInt(reasonIndex)
                    val errorMessage = getDownloadErrorMessage(reason)

                    updateDownloadState(
                        task.itemId,
                        DownloadState.Failed(task.itemId, task.itemName, errorMessage)
                    )
                    Timber.e("Download failed: $errorMessage")
                }
            }
        }

        cursor.close()
        activeTasks.remove(downloadId)
        progressJobs.remove(downloadId)
    }

    /**
     * Update download state for an item
     */
    private fun updateDownloadState(itemId: UUID, state: DownloadState) {
        val currentStates = _downloadStates.value.toMutableMap()
        currentStates[itemId] = state
        _downloadStates.value = currentStates
    }

    /**
     * Get the download directory
     */
    private fun getDownloadDirectory(): File {
        return File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "media"
        )
    }

    /**
     * Get file extension from URL
     */
    private fun getFileExtension(url: String): String {
        return when {
            url.contains(".mkv", ignoreCase = true) -> "mkv"
            url.contains(".mp4", ignoreCase = true) -> "mp4"
            url.contains(".avi", ignoreCase = true) -> "avi"
            else -> "mp4"
        }
    }

    /**
     * Get human-readable error message from DownloadManager error code
     */
    private fun getDownloadErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            DownloadManager.ERROR_CANNOT_RESUME -> "Download cannot resume"
            DownloadManager.ERROR_DEVICE_NOT_FOUND -> "No external storage device found"
            DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "File already exists"
            DownloadManager.ERROR_FILE_ERROR -> "Storage issue"
            DownloadManager.ERROR_HTTP_DATA_ERROR -> "HTTP data error"
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Insufficient storage space"
            DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Too many redirects"
            DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Unhandled HTTP response"
            DownloadManager.ERROR_UNKNOWN -> "Unknown error"
            else -> "Download failed with code: $errorCode"
        }
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        try {
            context.unregisterReceiver(downloadReceiver)
        } catch (e: Exception) {
            Timber.e(e, "Error unregistering download receiver")
        }

        progressJobs.values.forEach { it.cancel() }
        progressJobs.clear()
        activeTasks.clear()
    }

    /**
     * Restore download states from database on app startup
     */
    suspend fun restoreDownloadStates() {
        try {
            val localSources = sourceDao.getLocalSources()

            Timber.d("Restoring ${localSources.size} download states from database")

            val states = mutableMapOf<UUID, DownloadState>()

            localSources.forEach { source ->
                val file = File(source.path)

                if (file.exists()) {
                    states[source.itemId] = DownloadState.Completed(
                        itemId = source.itemId,
                        itemName = source.name,
                        file = file
                    )
                    Timber.d("Restored completed download: ${source.name}")
                } else {
                    Timber.w("Download file missing: ${source.path}, removing from database")
                    sourceDao.deleteSource(source)
                }
            }

            _downloadStates.value = states

            Timber.d("Successfully restored ${states.size} download states")

        } catch (e: Exception) {
            Timber.e(e, "Failed to restore download states")
        }
    }
}