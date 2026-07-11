package com.makd.afinity.data.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.makd.afinity.R
import com.makd.afinity.data.database.entities.AfinitySourceDto
import com.makd.afinity.data.database.entities.DownloadDto
import com.makd.afinity.data.manager.DownloadNotificationManager
import com.makd.afinity.data.manager.DownloadSemaphoreManager
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.download.DownloadStatus
import com.makd.afinity.data.models.extensions.toAfinityEpisode
import com.makd.afinity.data.models.extensions.toAfinityMovie
import com.makd.afinity.data.models.extensions.toAfinitySeason
import com.makd.afinity.data.models.extensions.toAfinityShow
import com.makd.afinity.data.models.extensions.toAfinityTrack
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMediaStream
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityPersonImage
import com.makd.afinity.data.models.media.AfinitySource
import com.makd.afinity.data.models.media.AfinitySourceType
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.download.JellyfinDownloadRepository
import com.makd.afinity.data.repository.segments.SegmentsRepository
import com.makd.afinity.di.DownloadClient
import com.makd.afinity.util.parseDashlessUuid
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.operations.ItemsApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

@HiltWorker
class MediaDownloadWorker
@AssistedInject
constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val sessionManager: SessionManager,
    private val databaseRepository: DatabaseRepository,
    private val downloadRepository: JellyfinDownloadRepository,
    private val segmentsRepository: SegmentsRepository,
    private val preferencesRepository: PreferencesRepository,
    private val downloadSemaphoreManager: DownloadSemaphoreManager,
    private val downloadNotificationManager: DownloadNotificationManager,
    @param:DownloadClient private val okHttpClient: OkHttpClient,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_ITEM_ID = "item_id"
        const val KEY_SOURCE_ID = "source_id"
        const val KEY_ITEM_NAME = "item_name"
        const val KEY_ITEM_TYPE = "item_type"
        const val KEY_FILE_PATH = "file_path"
        const val PROGRESS_KEY = "progress"
        const val BUFFER_SIZE = 256 * 1024
        const val PROGRESS_UPDATE_INTERVAL_MS = 500L
    }

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val downloadIdString =
                inputData.getString(KEY_DOWNLOAD_ID)
                    ?: return@withContext Result.failure(
                        workDataOf("error" to "Missing download ID")
                    )

            val downloadId =
                try {
                    UUID.fromString(downloadIdString)
                } catch (e: IllegalArgumentException) {
                    return@withContext Result.failure(workDataOf("error" to "Invalid download ID"))
                }

            val itemIdString =
                inputData.getString(KEY_ITEM_ID)
                    ?: return@withContext Result.failure(workDataOf("error" to "Missing item ID"))

            val itemId =
                try {
                    UUID.fromString(itemIdString)
                } catch (e: IllegalArgumentException) {
                    return@withContext Result.failure(workDataOf("error" to "Invalid item ID"))
                }

            val sourceId =
                inputData.getString(KEY_SOURCE_ID)
                    ?: return@withContext Result.failure(workDataOf("error" to "Missing source ID"))

            val itemName = inputData.getString(KEY_ITEM_NAME) ?: "Unknown"
            val itemType = inputData.getString(KEY_ITEM_TYPE) ?: "Unknown"

            ensureNotificationChannel()

            val downloadRecord = databaseRepository.getDownload(downloadId)
            val notifTitle = notificationTitle(downloadRecord, itemName)
            val notifSubText = notificationSubText(downloadRecord)

            try {
                setForeground(createQueuedForegroundInfo(downloadId, notifTitle, notifSubText))
            } catch (e: Exception) {
                Timber.e(e, "Failed to promote to foreground service")
            }

            downloadNotificationManager.postActiveSummary()

            val maxDownloads = preferencesRepository.getMaxDownloads()
            downloadSemaphoreManager.updatePermits(maxDownloads)
            downloadSemaphoreManager.semaphore.withPermit {
                try {
                    setForeground(
                        createForegroundInfo(
                            downloadId,
                            notifTitle,
                            notifSubText,
                            null,
                            null,
                            0,
                            0,
                        )
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Failed to update foreground service to active")
                }

                try {
                    Timber.d("Starting media download for item: $itemName ($itemType)")

                    val download: DownloadDto =
                        databaseRepository.getDownload(downloadId)
                            ?: return@withContext Result.failure(
                                workDataOf("error" to "Download not found")
                            )

                    val apiClient =
                        sessionManager.getOrRestoreApiClient(download.serverId)
                            ?: throw Exception(
                                "Could not restore session for server ${download.serverId}"
                            )

                    databaseRepository.insertDownload(
                        download.copy(
                            status = DownloadStatus.DOWNLOADING,
                            updatedAt = System.currentTimeMillis(),
                        )
                    )

                    val userId = download.userId

                    val baseUrl = apiClient.baseUrl ?: ""

                    val notificationIcon = loadNotificationIcon(download, apiClient.accessToken)

                    val itemsApi = ItemsApi(apiClient)
                    val baseItemDto =
                        try {
                            itemsApi
                                .getItems(
                                    userId = userId,
                                    ids = listOf(itemId),
                                    fields =
                                        listOf(
                                            ItemFields.MEDIA_SOURCES,
                                            ItemFields.MEDIA_STREAMS,
                                            ItemFields.OVERVIEW,
                                            ItemFields.GENRES,
                                            ItemFields.PEOPLE,
                                            ItemFields.TAGLINES,
                                            ItemFields.CHAPTERS,
                                            ItemFields.TRICKPLAY,
                                        ),
                                    enableImages = true,
                                    enableUserData = true,
                                )
                                .content
                                ?.items
                                ?.firstOrNull()
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to fetch item details")
                            null
                        } ?: throw Exception("Item not found")

                    val isAudio = baseItemDto.type == BaseItemKind.AUDIO
                    val audioMediaSource =
                        if (isAudio) {
                            (if (sourceId.isEmpty()) baseItemDto.mediaSources?.firstOrNull()
                            else
                                baseItemDto.mediaSources?.firstOrNull { it.id == sourceId }
                                    ?: baseItemDto.mediaSources?.firstOrNull())
                                ?: throw Exception("No media source for audio item")
                        } else null

                    val item =
                        if (!isAudio) {
                            when (baseItemDto.type) {
                                BaseItemKind.MOVIE -> baseItemDto.toAfinityMovie(baseUrl)
                                BaseItemKind.EPISODE ->
                                    baseItemDto.toAfinityEpisode(baseUrl)
                                        ?: throw Exception("Failed to convert episode")
                                else ->
                                    throw Exception("Unsupported item type: ${baseItemDto.type}")
                            }
                        } else null

                    val source =
                        if (!isAudio) {
                            item!!.sources.find { it.id == sourceId }
                                ?: throw Exception("Source not found")
                        } else null

                    val bigPicture = loadBitmap(backdropUrl(item), apiClient.accessToken, 512)

                    val itemDir = downloadRepository.getItemDownloadDirectory(download)
                    val mediaDir = File(itemDir, "media")

                    if (!mediaDir.exists() && !mediaDir.mkdirs()) {
                        Timber.e("Failed to create download directory at ${mediaDir.absolutePath}")
                        throw Exception(
                            "Failed to create download directory. Check storage permissions."
                        )
                    }

                    val extension =
                        if (isAudio) {
                            audioMediaSource!!.container?.lowercase() ?: "mp3"
                        } else {
                            source!!.container?.lowercase() ?: "mkv"
                        }

                    val outputFile = File(mediaDir, "$sourceId.$extension.download")
                    val finalFile = File(mediaDir, "$sourceId.$extension")

                    // Each sourceId is actually an itemId corresponding to the specific version
                    // that was requested in the download dialog
                    // Unfortunately, because the SDK expects a UUID, but UUID.fromString doesn't
                    // handle strings without dashes, we have
                    // to do some special handling here to get a UUID to pass to getDownloadUrl
                    val sourceUuid =
                        try {
                            parseDashlessUuid(sourceId)
                        } catch (e: IllegalArgumentException) {
                            throw Exception("Invalid source ID")
                        }

                    val downloadUrl = apiClient.libraryApi.getDownloadUrl(itemId = sourceUuid)

                    val existingFileSize = if (outputFile.exists()) outputFile.length() else 0L

                    Timber.d("Downloading from: $downloadUrl")
                    Timber.d("Saving to: ${outputFile.absolutePath}")
                    Timber.d("Resuming from byte: $existingFileSize")

                    val requestBuilder =
                        Request.Builder()
                            .url(downloadUrl)
                            .header(
                                "Authorization",
                                "MediaBrowser Token=\"${apiClient.accessToken ?: ""}\"",
                            )

                    if (existingFileSize > 0) {
                        requestBuilder.header("Range", "bytes=$existingFileSize-")
                    }

                    val request = requestBuilder.build()

                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            if (response.code == 416) {
                                Timber.w(
                                    "File already fully downloaded (416). Proceeding to completion."
                                )
                            } else {
                                throw Exception(
                                    "Download failed: ${response.code} ${response.message}"
                                )
                            }
                        }

                        val remainingBytes = response.body?.contentLength() ?: -1L
                        val totalBytes =
                            if (remainingBytes != -1L) existingFileSize + remainingBytes else -1L

                        val downloadedBytes = AtomicLong(existingFileSize)
                        var stoppedByUser = false

                        response.body?.byteStream()?.use { input ->
                            FileOutputStream(outputFile, true).use { output ->
                                coroutineScope {
                                    val progressJob =
                                        if (totalBytes > 0) {
                                            launch {
                                                while (isActive) {
                                                    delay(PROGRESS_UPDATE_INTERVAL_MS)
                                                    val bytes = downloadedBytes.get()
                                                    val progress =
                                                        bytes.toFloat() / totalBytes.toFloat()
                                                    updateProgress(
                                                        downloadId,
                                                        progress,
                                                        bytes,
                                                        totalBytes,
                                                    )
                                                    setProgressAsync(
                                                        workDataOf(
                                                            PROGRESS_KEY to progress,
                                                            "downloadedBytes" to bytes,
                                                            "totalBytes" to totalBytes,
                                                        )
                                                    )
                                                    downloadNotificationManager.notify(
                                                        downloadId.hashCode(),
                                                        createForegroundInfo(
                                                                downloadId,
                                                                notifTitle,
                                                                notifSubText,
                                                                notificationIcon,
                                                                bigPicture,
                                                                bytes,
                                                                totalBytes,
                                                            )
                                                            .notification,
                                                    )
                                                }
                                            }
                                        } else null

                                    val buffer = ByteArray(BUFFER_SIZE)
                                    var bytes: Int

                                    while (input.read(buffer).also { bytes = it } != -1) {
                                        if (isStopped) {
                                            Timber.d("Download paused/stopped by user")
                                            stoppedByUser = true
                                            break
                                        }

                                        output.write(buffer, 0, bytes)
                                        downloadedBytes.addAndGet(bytes.toLong())
                                    }

                                    progressJob?.cancelAndJoin()
                                }
                            }
                        }

                        if (stoppedByUser) {
                            return@withContext Result.failure(workDataOf("error" to "Paused"))
                        }
                    }

                    if (outputFile.exists()) {
                        outputFile.renameTo(finalFile)
                        Timber.d("Download completed: ${finalFile.absolutePath}")
                    }

                    val updatedDownload =
                        download.copy(
                            status = DownloadStatus.COMPLETED,
                            progress = 1.0f,
                            bytesDownloaded = finalFile.length(),
                            totalBytes = finalFile.length(),
                            filePath = finalFile.absolutePath,
                            updatedAt = System.currentTimeMillis(),
                        )
                    databaseRepository.insertDownload(updatedDownload)

                    ensureItemInDatabase(
                        apiClient,
                        download.serverId,
                        baseItemDto,
                        userId,
                        download.storageVolumeId,
                    )
                    if (isAudio) {
                        databaseRepository.updateMusicTrackLocalFilePath(
                            itemId,
                            download.serverId,
                            userId.toString(),
                            android.net.Uri.fromFile(finalFile).toString(),
                        )
                    } else {
                        if (itemType.uppercase() == "MOVIE") {
                            downloadPersonImages(apiClient, download.serverId, itemId, userId)
                        }
                        downloadSegments(itemId)
                    }
                    val sourceName =
                        if (isAudio) audioMediaSource!!.name ?: itemName else source!!.name
                    val sourceStreams = if (isAudio) emptyList() else source!!.mediaStreams
                    createLocalSource(itemId, sourceId, sourceName, finalFile, sourceStreams)

                    downloadNotificationManager.postCompleted(
                        "done_$downloadId".hashCode(),
                        notifTitle,
                        notifSubText,
                        notificationIcon,
                        finalFile.length(),
                    )

                    Timber.i("Media download completed successfully for: $itemName")

                    return@withContext Result.success(
                        workDataOf(
                            KEY_DOWNLOAD_ID to downloadIdString,
                            KEY_ITEM_ID to itemIdString,
                            KEY_SOURCE_ID to sourceId,
                            KEY_FILE_PATH to finalFile.absolutePath,
                        )
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Media download failed")
                    try {
                        if (!isStopped) {
                            val download = databaseRepository.getDownload(downloadId)
                            if (download != null) {
                                databaseRepository.insertDownload(
                                    download.copy(
                                        status = DownloadStatus.FAILED,
                                        error = e.message ?: "Unknown error",
                                        updatedAt = System.currentTimeMillis(),
                                    )
                                )
                            }
                        }
                    } catch (dbEx: Exception) {
                        Timber.e(dbEx, "Failed to update download status to FAILED")
                    }
                    if (!isStopped) {
                        downloadNotificationManager.postFailed(
                            "done_$downloadId".hashCode(),
                            notifTitle,
                            notifSubText,
                            e.message,
                        )
                    }
                    return@withContext Result.failure(
                        workDataOf("error" to (e.message ?: "Unknown error"))
                    )
                } finally {
                    downloadNotificationManager.cleanupActiveSummary(downloadId.hashCode())
                }
            }
        }

    private suspend fun updateProgress(
        downloadId: UUID,
        progress: Float,
        downloadedBytes: Long,
        totalBytes: Long,
    ) {
        try {
            databaseRepository.updateDownloadProgress(
                downloadId,
                progress,
                downloadedBytes,
                totalBytes,
                System.currentTimeMillis(),
            )
        } catch (e: Exception) {
            Timber.w(e, "Failed to update download progress")
        }
    }

    private suspend fun ensureItemInDatabase(
        apiClient: ApiClient,
        serverId: String,
        baseItemDto: BaseItemDto,
        userId: UUID,
        volumeId: String,
    ) {
        try {
            Timber.d("Ensuring item ${baseItemDto.id} is saved to database")
            val baseUrl = apiClient.baseUrl ?: ""
            val itemsApi = ItemsApi(apiClient)

            when (baseItemDto.type) {
                BaseItemKind.MOVIE -> {
                    val movie = baseItemDto.toAfinityMovie(baseUrl)
                    databaseRepository.insertMovie(movie, serverId)
                }

                BaseItemKind.EPISODE -> {
                    val episode = baseItemDto.toAfinityEpisode(baseUrl) ?: return
                    val seriesId = episode.seriesId
                    val seasonId = episode.seasonId

                    coroutineScope {
                        val seriesDeferred =
                            seriesId
                                ?.takeIf { databaseRepository.getShow(it, userId) == null }
                                ?.let { id ->
                                    async {
                                        try {
                                            itemsApi
                                                .getItems(
                                                    userId = userId,
                                                    ids = listOf(id),
                                                    fields =
                                                        listOf(
                                                            ItemFields.OVERVIEW,
                                                            ItemFields.GENRES,
                                                            ItemFields.PEOPLE,
                                                        ),
                                                    enableImages = true,
                                                    enableUserData = true,
                                                )
                                                .content
                                                ?.items
                                                ?.firstOrNull()
                                        } catch (_: Exception) {
                                            null
                                        }
                                    }
                                }

                        val seasonDeferred =
                            if (databaseRepository.getSeason(seasonId, userId) == null) {
                                async {
                                    try {
                                        itemsApi
                                            .getItems(
                                                userId = userId,
                                                ids = listOf(seasonId),
                                                fields = listOf(ItemFields.OVERVIEW),
                                                enableImages = true,
                                                enableUserData = true,
                                            )
                                            .content
                                            ?.items
                                            ?.firstOrNull()
                                    } catch (_: Exception) {
                                        null
                                    }
                                }
                            } else null

                        seriesId?.let {
                            seriesDeferred?.await()?.toAfinityShow(baseUrl)?.let { show ->
                                databaseRepository.insertShow(show, serverId)
                                downloadShowImages(apiClient, serverId, it, userId, volumeId)
                            }
                        }

                        seasonDeferred?.await()?.toAfinitySeason(baseUrl)?.let { season ->
                            databaseRepository.insertSeason(season, serverId)
                            downloadSeasonImages(apiClient, serverId, seasonId, userId, volumeId)
                        }
                    }

                    databaseRepository.insertEpisode(episode, serverId)
                }

                BaseItemKind.AUDIO -> {
                    val track = baseItemDto.toAfinityTrack(baseUrl)
                    databaseRepository.insertMusicTrack(track, serverId, userId.toString())
                }

                else -> Timber.w("Unsupported item type: ${baseItemDto.type}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to ensure item is in database")
        }
    }

    private suspend fun downloadImages(
        apiClient: ApiClient,
        serverId: String,
        itemId: UUID,
        itemType: String,
        userId: UUID,
    ) {
        try {
            val item =
                when (itemType.uppercase()) {
                    "MOVIE" -> databaseRepository.getMovie(itemId, userId)
                    "EPISODE" -> databaseRepository.getEpisode(itemId, userId)
                    else -> null
                } ?: return

            val itemDir = downloadRepository.getItemDownloadDirectory(itemId)
            val imagesDir = File(itemDir, "images")
            if (!imagesDir.exists() && !imagesDir.mkdirs()) {
                Timber.w("Failed to create images directory for item $itemId")
                return
            }
            val images = item.images
            val downloadedImages = mutableMapOf<String, Uri?>()

            suspend fun saveImage(uri: Uri?, key: String) {
                if (uri == null) return
                val localPath = downloadImage(apiClient, uri.toString(), imagesDir, key)
                if (localPath != null) {
                    downloadedImages[key] = localPath
                }
            }

            saveImage(images.primary, "primary")
            saveImage(images.backdrop, "backdrop")
            saveImage(images.thumb, "thumb")
            saveImage(images.logo, "logo")

            if (itemType.uppercase() == "EPISODE") {
                saveImage(images.showPrimary, "showPrimary")
                saveImage(images.showBackdrop, "showBackdrop")
                saveImage(images.showLogo, "showLogo")
            }

            val updatedImages =
                AfinityImages(
                    primary = downloadedImages["primary"] ?: images.primary,
                    backdrop = downloadedImages["backdrop"] ?: images.backdrop,
                    thumb = downloadedImages["thumb"] ?: images.thumb,
                    logo = downloadedImages["logo"] ?: images.logo,
                    showPrimary = downloadedImages["showPrimary"] ?: images.showPrimary,
                    showBackdrop = downloadedImages["showBackdrop"] ?: images.showBackdrop,
                    showLogo = downloadedImages["showLogo"] ?: images.showLogo,
                    primaryImageBlurHash = images.primaryImageBlurHash,
                    backdropImageBlurHash = images.backdropImageBlurHash,
                    thumbImageBlurHash = images.thumbImageBlurHash,
                    logoImageBlurHash = images.logoImageBlurHash,
                    showPrimaryImageBlurHash = images.showPrimaryImageBlurHash,
                    showBackdropImageBlurHash = images.showBackdropImageBlurHash,
                    showLogoImageBlurHash = images.showLogoImageBlurHash,
                )

            when (item) {
                is AfinityMovie -> {
                    databaseRepository.insertMovie(item.copy(images = updatedImages), serverId)
                    downloadPersonImages(apiClient, serverId, itemId, userId)
                }

                is AfinityEpisode -> {
                    databaseRepository.insertEpisode(item.copy(images = updatedImages), serverId)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to download images")
        }
    }

    private suspend fun downloadShowImages(
        apiClient: ApiClient,
        serverId: String,
        showId: UUID,
        userId: UUID,
        volumeId: String,
    ) {
        try {
            val show = databaseRepository.getShow(showId, userId) ?: return
            val showDir = downloadRepository.getShowDirectory(serverId, showId, volumeId)
            val imagesDir = File(showDir, "images")
            if (!imagesDir.exists() && !imagesDir.mkdirs()) {
                Timber.w("Failed to create show images directory for show $showId")
                return
            }
            val images = show.images
            val downloadedImages = mutableMapOf<String, Uri?>()

            suspend fun saveImage(uri: Uri?, key: String) {
                if (uri == null) return
                val localPath = downloadImage(apiClient, uri.toString(), imagesDir, key)
                if (localPath != null) downloadedImages[key] = localPath
            }

            saveImage(images.primary, "primary")
            saveImage(images.backdrop, "backdrop")
            saveImage(images.logo, "logo")

            val updatedImages =
                AfinityImages(
                    primary = downloadedImages["primary"] ?: images.primary,
                    backdrop = downloadedImages["backdrop"] ?: images.backdrop,
                    thumb = downloadedImages["thumb"] ?: images.thumb,
                    logo = downloadedImages["logo"] ?: images.logo,
                    primaryImageBlurHash = images.primaryImageBlurHash,
                    backdropImageBlurHash = images.backdropImageBlurHash,
                    thumbImageBlurHash = images.thumbImageBlurHash,
                    logoImageBlurHash = images.logoImageBlurHash,
                )
            databaseRepository.insertShow(show.copy(images = updatedImages), serverId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to download show images")
        }
    }

    private suspend fun downloadSeasonImages(
        apiClient: ApiClient,
        serverId: String,
        seasonId: UUID,
        userId: UUID,
        volumeId: String,
    ) {
        try {
            val season = databaseRepository.getSeason(seasonId, userId) ?: return
            val seasonDir =
                downloadRepository.getSeasonDirectory(
                    serverId,
                    season.seriesId,
                    season.indexNumber,
                    volumeId,
                )
            val imagesDir = File(seasonDir, "images")
            if (!imagesDir.exists() && !imagesDir.mkdirs()) {
                Timber.w("Failed to create season images directory for season $seasonId")
                return
            }
            val images = season.images
            val downloadedImages = mutableMapOf<String, Uri?>()

            suspend fun saveImage(uri: Uri?, key: String) {
                if (uri == null) return
                val localPath = downloadImage(apiClient, uri.toString(), imagesDir, key)
                if (localPath != null) downloadedImages[key] = localPath
            }

            saveImage(images.primary, "primary")
            saveImage(images.backdrop, "backdrop")

            val updatedImages =
                AfinityImages(
                    primary = downloadedImages["primary"] ?: images.primary,
                    backdrop = downloadedImages["backdrop"] ?: images.backdrop,
                    thumb = downloadedImages["thumb"] ?: images.thumb,
                    logo = downloadedImages["logo"] ?: images.logo,
                    primaryImageBlurHash = images.primaryImageBlurHash,
                    backdropImageBlurHash = images.backdropImageBlurHash,
                    thumbImageBlurHash = images.thumbImageBlurHash,
                    logoImageBlurHash = images.logoImageBlurHash,
                )
            databaseRepository.insertSeason(season.copy(images = updatedImages), serverId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to download season images")
        }
    }

    private suspend fun downloadPersonImages(
        apiClient: ApiClient,
        serverId: String,
        itemId: UUID,
        userId: UUID,
    ) {
        try {
            val movie = databaseRepository.getMovie(itemId, userId) ?: return
            if (movie.people.isEmpty()) return

            val movieDir = downloadRepository.getItemDownloadDirectory(itemId)
            val peopleImagesDir = File(movieDir, "people")
            if (!peopleImagesDir.exists() && !peopleImagesDir.mkdirs()) {
                Timber.w("Failed to create people images directory for item $itemId")
                return
            }

            val updatedPeople =
                movie.people.map { person ->
                    person.image.uri?.let { uri ->
                        val localPath =
                            downloadImage(
                                apiClient,
                                uri.toString(),
                                peopleImagesDir,
                                person.id.toString(),
                            )
                        if (localPath != null) {
                            person.copy(
                                image =
                                    AfinityPersonImage(
                                        uri = localPath,
                                        blurHash = person.image.blurHash,
                                    )
                            )
                        } else person
                    } ?: person
                }
            databaseRepository.insertMovie(movie.copy(people = updatedPeople), serverId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to download person images")
        }
    }

    private suspend fun downloadImage(
        apiClient: ApiClient,
        imageUrl: String,
        outputDir: File,
        baseName: String,
    ): Uri? {
        var resultUri: Uri? = null
        try {
            val request =
                Request.Builder()
                    .url(imageUrl)
                    .header("X-Emby-Token", apiClient.accessToken ?: "")
                    .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val contentType = response.header("Content-Type") ?: "image/jpeg"
                    val extension =
                        when {
                            contentType.contains("png") -> "png"
                            contentType.contains("webp") -> "webp"
                            contentType.contains("gif") -> "gif"
                            else -> "jpg"
                        }
                    val outputFile = File(outputDir, "$baseName.$extension")

                    response.body?.byteStream()?.use { input ->
                        FileOutputStream(outputFile).use { output -> input.copyTo(output) }
                    }

                    if (outputFile.exists() && outputFile.length() > 0) {
                        resultUri = Uri.fromFile(outputFile)
                    }
                }
            }
        } catch (e: Exception) {
            Timber.w("Failed to download image $baseName: ${e.message}")
        }
        return resultUri
    }

    private suspend fun downloadSegments(itemId: UUID) {
        try {
            segmentsRepository.getSegments(itemId)
        } catch (e: Exception) {
            Timber.w(e, "Failed to download segments")
        }
    }

    private suspend fun createLocalSource(
        itemId: UUID,
        sourceId: String,
        sourceName: String,
        file: File,
        originalStreams: List<AfinityMediaStream>,
    ) {
        try {
            val localSourceId = "${sourceId}_local"
            val localSource =
                AfinitySourceDto(
                    id = localSourceId,
                    itemId = itemId,
                    name = "$sourceName (Downloaded)",
                    type = AfinitySourceType.LOCAL,
                    path = file.absolutePath,
                    downloadId = null,
                )
            databaseRepository.insertSource(
                AfinitySource(
                    id = localSource.id,
                    name = localSource.name,
                    type = localSource.type,
                    path = localSource.path,
                    size = file.length(),
                    mediaStreams = emptyList(),
                    downloadId = null,
                ),
                itemId,
            )
            originalStreams.forEach { stream ->
                if (!stream.isExternal) {
                    try {
                        databaseRepository.insertMediaStream(
                            stream = stream.copy(path = file.absolutePath),
                            sourceId = localSourceId,
                        )
                    } catch (e: Exception) {
                        Timber.w("Failed to copy stream ${stream.type} to local source")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to create LOCAL source entry")
        }
    }

    private fun ensureNotificationChannel() {
        val channelId = "download_channel"
        val channel =
            NotificationChannel(channelId, "Downloads", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Background download tasks"
            }
        (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .createNotificationChannel(channel)
    }

    private fun createQueuedForegroundInfo(
        downloadId: UUID,
        title: String,
        subText: String?,
    ): ForegroundInfo {
        val channelId = "download_channel"
        val context: Context = applicationContext
        val notification =
            NotificationCompat.Builder(context, channelId)
                .setContentTitle(title)
                .setContentText("Queued")
                .apply { if (!subText.isNullOrBlank()) setSubText(subText) }
                .setSmallIcon(R.drawable.ic_download)
                .setOngoing(true)
                .setGroup(DownloadNotificationManager.GROUP_ACTIVE)
                .setContentIntent(downloadNotificationManager.downloadsContentIntent())
                .addAction(
                    R.drawable.ic_player_pause_filled,
                    "Pause",
                    downloadNotificationManager.pauseActionIntent(downloadId),
                )
                .addAction(
                    R.drawable.ic_cancel,
                    "Cancel",
                    downloadNotificationManager.cancelActionIntent(downloadId),
                )
                .setProgress(0, 0, true)
                .build()
        return ForegroundInfo(
            downloadId.hashCode(),
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun createForegroundInfo(
        downloadId: UUID,
        title: String,
        subText: String?,
        largeIcon: Bitmap?,
        bigPicture: Bitmap?,
        downloadedBytes: Long,
        totalBytes: Long,
    ): ForegroundInfo {
        val context: Context = applicationContext
        val channelId = "download_channel"

        val progressText =
            if (totalBytes > 0) {
                "${downloadedBytes * 100 / totalBytes}% • " +
                    "${formatBytes(downloadedBytes)} / ${formatBytes(totalBytes)}"
            } else {
                "Starting..."
            }

        val notification =
            NotificationCompat.Builder(context, channelId)
                .setContentTitle(title)
                .setContentText(progressText)
                .apply {
                    if (!subText.isNullOrBlank()) setSubText(subText)
                    if (largeIcon != null) setLargeIcon(largeIcon)
                    if (bigPicture != null) {
                        setStyle(
                            NotificationCompat.BigPictureStyle()
                                .bigPicture(bigPicture)
                                .bigLargeIcon(null as Bitmap?)
                        )
                    }
                }
                .setSmallIcon(R.drawable.ic_download)
                .setOngoing(true)
                .setGroup(DownloadNotificationManager.GROUP_ACTIVE)
                .setContentIntent(downloadNotificationManager.downloadsContentIntent())
                .addAction(
                    R.drawable.ic_player_pause_filled,
                    "Pause",
                    downloadNotificationManager.pauseActionIntent(downloadId),
                )
                .addAction(
                    R.drawable.ic_cancel,
                    "Cancel",
                    downloadNotificationManager.cancelActionIntent(downloadId),
                )
                .setProgress(
                    if (totalBytes > 0) 1000 else 0,
                    if (totalBytes > 0) (downloadedBytes * 1000 / totalBytes).toInt() else 0,
                    totalBytes <= 0,
                )
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build()

        return ForegroundInfo(
            downloadId.hashCode(),
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun notificationTitle(download: DownloadDto?, fallback: String): String =
        if (download?.itemType == "Episode" && !download.seriesName.isNullOrBlank()) {
            download.seriesName
        } else {
            download?.itemName ?: fallback
        }

    private fun notificationSubText(download: DownloadDto?): String? {
        download ?: return null
        return when (download.itemType) {
            "Episode" -> {
                val seasonEpisode =
                    listOfNotNull(
                            download.seasonNumber?.let {
                                String.format(Locale.ROOT, "S%02d", it)
                            },
                            download.episodeNumber?.let {
                                String.format(Locale.ROOT, "E%02d", it)
                            },
                        )
                        .joinToString("")
                listOf(seasonEpisode, download.itemName)
                    .filter { it.isNotBlank() }
                    .joinToString(" • ")
            }
            "Audio" -> download.seriesName
            else -> download.releaseYear
        }?.takeIf { it.isNotBlank() }
    }

    private fun loadNotificationIcon(download: DownloadDto, accessToken: String?): Bitmap? {
        val url =
            if (download.itemType == "Episode") {
                download.seriesImageUrl ?: download.imageUrl
            } else {
                download.imageUrl
            }
        return loadBitmap(url, accessToken, 256)
    }

    private fun backdropUrl(item: AfinityItem?): String? =
        when (item) {
            is AfinityMovie -> item.images.backdrop
            is AfinityEpisode -> item.images.showBackdrop ?: item.images.backdrop
            else -> null
        }?.toString()

    private fun loadBitmap(
        url: String?,
        accessToken: String?,
        targetMinDimension: Int,
    ): Bitmap? {
        url ?: return null
        return try {
            val request =
                Request.Builder().url(url).header("X-Emby-Token", accessToken ?: "").build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val bytes = response.body?.bytes() ?: return null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
                val options =
                    BitmapFactory.Options().apply {
                        inSampleSize =
                            maxOf(
                                1,
                                minOf(bounds.outWidth, bounds.outHeight) / targetMinDimension,
                            )
                    }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to load notification image")
            null
        }
    }

    private fun formatBytes(bytes: Long): String =
        when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 ->
                String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
            bytes < 1024L * 1024 * 1024 ->
                String.format(Locale.getDefault(), "%.1f MB", bytes / (1024.0 * 1024.0))
            else ->
                String.format(Locale.getDefault(), "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
}
