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
import com.makd.afinity.data.models.download.DownloadPriority
import com.makd.afinity.data.models.download.DownloadState
import com.makd.afinity.data.models.download.DownloadTask
import kotlin.math.ceil
import org.jellyfin.sdk.model.api.MediaStreamType
import com.makd.afinity.data.models.download.QueuedDownloadItem
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.media.AfinitySource
import com.makd.afinity.data.models.media.AfinitySourceType
import com.makd.afinity.data.models.media.AfinityTrickplayInfo
import com.makd.afinity.data.network.ConnectionType
import com.makd.afinity.data.network.NetworkMonitor
import com.makd.afinity.data.network.NetworkState
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.JellyfinRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.segments.SegmentsRepository
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

@Singleton
class MediaDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sourceDao: SourceDao,
    private val preferencesRepository: PreferencesRepository,
    private val apiClient: ApiClient,
    private val networkMonitor: NetworkMonitor,
    private val jellyfinRepository: JellyfinRepository,
    private val databaseRepository: DatabaseRepository,
    private val segmentsRepository: SegmentsRepository
) {
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private val _downloadStates = MutableStateFlow<Map<UUID, DownloadState>>(emptyMap())
    val downloadStates: StateFlow<Map<UUID, DownloadState>> = _downloadStates.asStateFlow()

    private val activeTasks = mutableMapOf<Long, DownloadTask>()
    private val progressJobs = mutableMapOf<Long, Job>()
    private var networkMonitorJob: Job? = null

    private val downloadQueue = mutableListOf<QueuedDownloadItem>()
    private var maxConcurrentDownloads = 3
    private val queueLock = Any()

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            Timber.d("Download complete broadcast received for ID: $downloadId")

            if (downloadId in activeTasks) {
                handleDownloadComplete(downloadId)
            }
        }
    }

    fun addToQueue(
        itemId: UUID,
        itemName: String,
        sourceId: String,
        downloadUrl: String,
        itemType: DownloadItemType,
        item: AfinityItem,
        priority: DownloadPriority = DownloadPriority.NORMAL
    ) {
        synchronized(queueLock) {
            if (downloadQueue.any { it.itemId == itemId }) {
                Timber.w("Item already in download queue: $itemName")
                return
            }

            if (activeTasks.values.any { it.itemId == itemId }) {
                Timber.w("Item already downloading: $itemName")
                return
            }

            val queueItem = QueuedDownloadItem(
                itemId = itemId,
                itemName = itemName,
                sourceId = sourceId,
                downloadUrl = downloadUrl,
                itemType = itemType,
                priority = priority,
                item = item
            )

            downloadQueue.add(queueItem)
            Timber.d("Added to queue: $itemName (priority: ${priority.name})")

            updateDownloadState(itemId, DownloadState.Queued(itemId, itemName))

            coroutineScope.launch(Dispatchers.IO) {
                processQueue()
            }
        }
    }

    private suspend fun processQueue() {
        synchronized(queueLock) {
            if (activeTasks.size >= maxConcurrentDownloads) {
                Timber.d("Max concurrent downloads reached (${activeTasks.size}/$maxConcurrentDownloads)")
                return
            }

            val sortedQueue = downloadQueue.sortedWith(
                compareByDescending<QueuedDownloadItem> { it.priority.value }
                    .thenBy { it.addedAt }
            )

            val itemsToStart = sortedQueue.take(maxConcurrentDownloads - activeTasks.size)

            itemsToStart.forEach { queueItem ->
                downloadQueue.remove(queueItem)

                coroutineScope.launch(Dispatchers.IO) {
                    val baseUrl = apiClient.baseUrl ?: return@launch
                    downloadItem(
                        itemId = queueItem.itemId,
                        itemName = queueItem.itemName,
                        sourceId = queueItem.sourceId,
                        downloadUrl = queueItem.downloadUrl,
                        itemType = queueItem.itemType,
                        baseUrl = baseUrl,
                        item = queueItem.item
                    )
                }
            }
        }
    }

    fun getDownloadQueue(): List<QueuedDownloadItem> {
        synchronized(queueLock) {
            return downloadQueue.toList()
        }
    }

    fun removeFromQueue(itemId: UUID) {
        synchronized(queueLock) {
            downloadQueue.removeAll { it.itemId == itemId }
            updateDownloadState(itemId, DownloadState.Idle)
            Timber.d("Removed from queue: $itemId")
        }
    }

    fun changePriority(itemId: UUID, newPriority: DownloadPriority) {
        synchronized(queueLock) {
            val item = downloadQueue.find { it.itemId == itemId } ?: return
            downloadQueue.remove(item)
            downloadQueue.add(item.copy(priority = newPriority))
            Timber.d("Changed priority for ${item.itemName} to ${newPriority.name}")

            coroutineScope.launch(Dispatchers.IO) {
                processQueue()
            }
        }
    }

    fun setMaxConcurrentDownloads(max: Int) {
        maxConcurrentDownloads = max.coerceIn(1, 10)
        Timber.d("Max concurrent downloads set to: $maxConcurrentDownloads")

        coroutineScope.launch(Dispatchers.IO) {
            processQueue()
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
        startNetworkObservation()
    }

    private fun startNetworkObservation() {
        networkMonitorJob = coroutineScope.launch {
            networkMonitor.observeNetworkState().collect { networkState ->
                logNetworkState(networkState)
            }
        }
    }

    private suspend fun logNetworkState(networkState: NetworkState) {
        val wifiOnly = preferencesRepository.getDownloadOverWifiOnly()

        when {
            !wifiOnly -> {
                Timber.d("Network changed to ${networkState.connectionType}, WiFi-only disabled, downloads continue")
            }
            networkState.connectionType == ConnectionType.WIFI ||
                    networkState.connectionType == ConnectionType.ETHERNET -> {
                Timber.d("WiFi/Ethernet connected - DownloadManager will auto-resume paused downloads")
            }
            networkState.connectionType == ConnectionType.CELLULAR -> {
                Timber.d("Switched to cellular - DownloadManager will auto-pause WiFi-only downloads")
            }
            networkState.connectionType == ConnectionType.NONE -> {
                Timber.d("Network disconnected - downloads paused")
            }
        }
    }

    private fun stopNetworkObservation() {
        networkMonitorJob?.cancel()
        networkMonitorJob = null
    }

    suspend fun downloadItem(
        itemId: UUID,
        itemName: String,
        sourceId: String,
        downloadUrl: String,
        itemType: DownloadItemType,
        baseUrl: String,
        item: AfinityItem
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
                itemType = itemType,
                item = item
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

    private suspend fun downloadTrickplayData(
        itemId: UUID,
        sourceId: String,
        trickplayManifest: Map<String, Map<Int, AfinityTrickplayInfo>>?
    ) {
        try {
            if (trickplayManifest == null || trickplayManifest.isEmpty()) {
                Timber.d("No trickplay data available for item: $itemId")
                return
            }

            val trickplayInfo = trickplayManifest[sourceId]?.values?.firstOrNull()
            if (trickplayInfo == null) {
                Timber.d("No trickplay info found for source: $sourceId")
                return
            }

            val maxIndex = ceil(
                trickplayInfo.thumbnailCount.toDouble() /
                        (trickplayInfo.tileWidth * trickplayInfo.tileHeight)
            ).toInt()

            Timber.d("Downloading $maxIndex trickplay tiles for item $itemId")

            val byteArrays = mutableListOf<ByteArray>()
            for (i in 0 until maxIndex) {
                jellyfinRepository.getTrickplayTileImage(itemId, trickplayInfo.width, i)?.let { byteArray ->
                    byteArrays.add(byteArray)
                    Timber.d("Downloaded trickplay tile $i of $maxIndex")
                }
            }

            saveTrickplayData(itemId, sourceId, trickplayInfo, byteArrays)
            Timber.d("Successfully downloaded and saved ${byteArrays.size} trickplay tiles")

        } catch (e: Exception) {
            Timber.e(e, "Failed to download trickplay data for item: $itemId")
        }
    }

    private suspend fun saveTrickplayData(
        itemId: UUID,
        sourceId: String,
        trickplayInfo: AfinityTrickplayInfo,
        byteArrays: List<ByteArray>
    ) {
        try {
            val basePath = "trickplay/$itemId/$sourceId"
            val trickplayDir = File(context.filesDir, basePath)
            if (!trickplayDir.exists()) {
                trickplayDir.mkdirs()
            }

            databaseRepository.insertTrickplayInfo(trickplayInfo, sourceId)

            for ((index, byteArray) in byteArrays.withIndex()) {
                val file = File(trickplayDir, index.toString())
                file.writeBytes(byteArray)
            }

            Timber.d("Saved ${byteArrays.size} trickplay tiles to $basePath")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save trickplay data")
        }
    }

    private suspend fun downloadExternalSubtitles(
        itemId: UUID,
        source: AfinitySource,
        storageDir: File
    ) {
        try {
            val externalStreams = source.mediaStreams.filter {
                it.isExternal && it.type == org.jellyfin.sdk.model.api.MediaStreamType.SUBTITLE
            }

            if (externalStreams.isEmpty()) {
                Timber.d("No external subtitles found for item: $itemId")
                return
            }

            Timber.d("Downloading ${externalStreams.size} external subtitle files")

            for (mediaStream in externalStreams) {
                val subtitleUrl = mediaStream.path ?: continue
                val streamId = UUID.randomUUID()
                val fileName = "${itemId}_${source.id}_${streamId}_subtitle"

                val apiKey = apiClient.accessToken ?: ""
                val finalUrl = if (subtitleUrl.contains("?")) {
                    "$subtitleUrl&api_key=$apiKey"
                } else {
                    "$subtitleUrl?api_key=$apiKey"
                }

                val request = DownloadManager.Request(Uri.parse(finalUrl))
                    .setTitle("Subtitle: ${mediaStream.title}")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)
                    .setDestinationInExternalFilesDir(
                        context,
                        Environment.DIRECTORY_DOWNLOADS + "/media",
                        fileName
                    )
                    .setAllowedOverMetered(!preferencesRepository.getDownloadOverWifiOnly())
                    .setAllowedOverRoaming(false)

                val downloadId = downloadManager.enqueue(request)

                val subtitlePath = File(storageDir, fileName).absolutePath
                databaseRepository.insertMediaStream(
                    mediaStream.copy(path = subtitlePath),
                    source.id
                )

                Timber.d("Started download of subtitle: ${mediaStream.title}")
            }

        } catch (e: Exception) {
            Timber.e(e, "Failed to download external subtitles for item: $itemId")
        }
    }

    private suspend fun downloadSegments(itemId: UUID) {
        try {
            val segments = segmentsRepository.getSegments(itemId)

            Timber.d("Fetched ${segments.size} segments for item: $itemId (already cached by SegmentsRepository)")

        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch segments for item: $itemId")
        }
    }

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

    fun getDownloadState(itemId: UUID): DownloadState {
        return _downloadStates.value[itemId] ?: DownloadState.Idle
    }

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

                                try {
                                    val serverId = null
                                    when (val downloadedItem = task.item) {
                                        is AfinityMovie -> {
                                            Timber.d("Saving movie metadata: ${downloadedItem.name}")
                                            databaseRepository.insertMovie(downloadedItem, serverId)
                                        }
                                        is AfinityEpisode -> {
                                            Timber.d("Saving episode metadata: ${downloadedItem.name}")
                                            databaseRepository.insertEpisode(downloadedItem, serverId)

                                            try {
                                                val seasons = jellyfinRepository.getSeasons(downloadedItem.seriesId)
                                                val season = seasons.firstOrNull { it.id == downloadedItem.seasonId }
                                                season?.let {
                                                    Timber.d("Saving season metadata: ${it.name}")
                                                    databaseRepository.insertSeason(it)
                                                }

                                                val show = jellyfinRepository.getItemById(downloadedItem.seriesId)
                                                if (show is AfinityShow) {
                                                    Timber.d("Saving show metadata: ${show.name}")
                                                    databaseRepository.insertShow(show, serverId)
                                                }
                                            } catch (e: Exception) {
                                                Timber.w(e, "Failed to save season/show metadata")
                                            }
                                        }
                                        else -> {
                                            Timber.d("Item type ${downloadedItem::class.simpleName} - no metadata save needed")
                                        }
                                    }
                                    Timber.d("Item metadata saved for offline access")
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to save item metadata")
                                }

                                Timber.d("Starting download of additional content for ${task.itemName}")

                                try {
                                    downloadSegments(task.itemId)

                                    val trickplayManifest = jellyfinRepository.getTrickplayManifest(task.itemId)
                                    downloadTrickplayData(task.itemId, task.sourceId, trickplayManifest)

                                    val sources = databaseRepository.getSourcesForItem(task.itemId)
                                    val localSource = sources.firstOrNull { it.id == task.sourceId }
                                    if (localSource != null) {
                                        downloadExternalSubtitles(task.itemId, localSource, file.parentFile ?: getDownloadDirectory())
                                    }

                                    Timber.d("Completed download of all additional content for ${task.itemName}")
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to download additional content, but main file is saved")
                                }

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
        coroutineScope.launch(Dispatchers.IO) {
            processQueue()
        }
    }

    private fun updateDownloadState(itemId: UUID, state: DownloadState) {
        val currentStates = _downloadStates.value.toMutableMap()
        currentStates[itemId] = state
        _downloadStates.value = currentStates
    }

    private fun getDownloadDirectory(): File {
        return File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            "media"
        )
    }

    private fun getFileExtension(url: String): String {
        return when {
            url.contains(".mkv", ignoreCase = true) -> "mkv"
            url.contains(".mp4", ignoreCase = true) -> "mp4"
            url.contains(".avi", ignoreCase = true) -> "avi"
            else -> "mp4"
        }
    }

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

    suspend fun deleteDownload(itemId: UUID) {
        try {
            val localSources = sourceDao.getSourcesForItem(itemId)
                .filter { it.type == AfinitySourceType.LOCAL }

            if (localSources.isEmpty()) {
                Timber.w("No downloaded files found for item: $itemId")
                return
            }

            var deletedCount = 0

            localSources.forEach { source ->
                val file = File(source.path)

                if (file.exists()) {
                    if (file.delete()) {
                        deletedCount++
                        Timber.d("Deleted file: ${file.absolutePath}")
                    } else {
                        Timber.w("Failed to delete file: ${file.absolutePath}")
                    }
                }

                sourceDao.deleteSource(source)
            }

            updateDownloadState(itemId, DownloadState.Idle)

            Timber.d("Deleted $deletedCount downloaded files for item: $itemId")

        } catch (e: Exception) {
            Timber.e(e, "Failed to delete download for item: $itemId")
        }
    }

    fun cleanup() {
        try {
            context.unregisterReceiver(downloadReceiver)
        } catch (e: Exception) {
            Timber.e(e, "Error unregistering download receiver")
        }

        stopNetworkObservation()
        progressJobs.values.forEach { it.cancel() }
        progressJobs.clear()
        activeTasks.clear()
    }

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