package com.makd.afinity.data.workers

import android.app.NotificationChannel
import android.content.pm.ServiceInfo
import android.app.NotificationManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.makd.afinity.R
import com.makd.afinity.data.database.entities.AfinitySourceDto
import com.makd.afinity.data.database.entities.DownloadDto
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.download.DownloadStatus
import com.makd.afinity.data.models.extensions.toAfinityEpisode
import com.makd.afinity.data.models.extensions.toAfinityMovie
import com.makd.afinity.data.models.extensions.toAfinitySeason
import com.makd.afinity.data.models.extensions.toAfinityShow
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityPersonImage
import com.makd.afinity.data.models.media.AfinitySource
import com.makd.afinity.data.models.media.AfinitySourceType
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.download.JellyfinDownloadRepository
import com.makd.afinity.data.repository.segments.SegmentsRepository
import com.makd.afinity.di.DownloadClient
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.operations.ItemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@HiltWorker
class MediaDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val sessionManager: SessionManager,
    private val databaseRepository: DatabaseRepository,
    private val downloadRepository: JellyfinDownloadRepository,
    private val preferencesRepository: PreferencesRepository,
    private val segmentsRepository: SegmentsRepository,
    @DownloadClient private val okHttpClient: OkHttpClient,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_ITEM_ID = "item_id"
        const val KEY_SOURCE_ID = "source_id"
        const val KEY_ITEM_NAME = "item_name"
        const val KEY_ITEM_TYPE = "item_type"
        const val KEY_FILE_PATH = "file_path"
        const val PROGRESS_KEY = "progress"
        const val BUFFER_SIZE = 8192
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val downloadIdString = inputData.getString(KEY_DOWNLOAD_ID)
            ?: return@withContext Result.failure(workDataOf("error" to "Missing download ID"))

        val downloadId = try {
            UUID.fromString(downloadIdString)
        } catch (e: IllegalArgumentException) {
            return@withContext Result.failure(workDataOf("error" to "Invalid download ID"))
        }

        val itemIdString = inputData.getString(KEY_ITEM_ID)
            ?: return@withContext Result.failure(workDataOf("error" to "Missing item ID"))

        val itemId = try {
            UUID.fromString(itemIdString)
        } catch (e: IllegalArgumentException) {
            return@withContext Result.failure(workDataOf("error" to "Invalid item ID"))
        }

        val sourceId = inputData.getString(KEY_SOURCE_ID)
            ?: return@withContext Result.failure(workDataOf("error" to "Missing source ID"))

        val itemName = inputData.getString(KEY_ITEM_NAME) ?: "Unknown"
        val itemType = inputData.getString(KEY_ITEM_TYPE) ?: "Unknown"

        try {
            val foregroundInfo = createForegroundInfo(downloadId.hashCode(), itemName, 0, 0)
            setForeground(foregroundInfo)
        } catch (e: Exception) {
            Timber.e(e, "Failed to promote to foreground service")
        }

        try {
            Timber.d("Starting media download for item: $itemName ($itemType)")

            val download: DownloadDto = databaseRepository.getDownload(downloadId)
                ?: return@withContext Result.failure(workDataOf("error" to "Download not found"))

            val apiClient = sessionManager.getOrRestoreApiClient(download.serverId)
                ?: return@withContext Result.failure(workDataOf("error" to "Could not restore session for server ${download.serverId}"))

            databaseRepository.insertDownload(
                download.copy(
                    status = DownloadStatus.DOWNLOADING,
                    updatedAt = System.currentTimeMillis()
                )
            )

            val userId = try {
                apiClient.userApi.getCurrentUser().content?.id
            } catch (e: Exception) {
                null
            } ?: return@withContext Result.failure(workDataOf("error" to "User not authenticated"))

            if (userId != download.userId) {
                Timber.w("User ID mismatch. Download started by ${download.userId}, active token is for $userId")
            }

            try {
                preferencesRepository.setCurrentUserId(userId.toString())
            } catch (e: Exception) {
                Timber.w(e, "Failed to save userId to preferences")
            }

            val baseUrl = apiClient.baseUrl ?: ""

            val itemsApi = ItemsApi(apiClient)
            val baseItemDto = try {
                itemsApi.getItems(
                    userId = userId,
                    ids = listOf(itemId),
                    fields = listOf(
                        ItemFields.MEDIA_SOURCES,
                        ItemFields.MEDIA_STREAMS,
                        ItemFields.OVERVIEW
                    ),
                    enableImages = true,
                    enableUserData = true
                ).content?.items?.firstOrNull()
            } catch (e: Exception) {
                Timber.e(e, "Failed to fetch item details")
                null
            } ?: return@withContext Result.failure(workDataOf("error" to "Item not found"))

            val item = when (baseItemDto.type) {
                BaseItemKind.MOVIE -> baseItemDto.toAfinityMovie(baseUrl)
                BaseItemKind.EPISODE -> baseItemDto.toAfinityEpisode(baseUrl)
                    ?: return@withContext Result.failure(workDataOf("error" to "Failed to convert episode"))

                else -> return@withContext Result.failure(
                    workDataOf("error" to "Unsupported item type: ${baseItemDto.type}")
                )
            }

            val source = item.sources.find { it.id == sourceId }
                ?: return@withContext Result.failure(workDataOf("error" to "Source not found"))

            val itemDir = downloadRepository.getItemDownloadDirectory(itemId)
            val mediaDir = File(itemDir, "media").also { it.mkdirs() }

            val extension = when {
                source.path.contains(".mkv") -> "mkv"
                source.path.contains(".mp4") -> "mp4"
                source.path.contains(".avi") -> "avi"
                else -> "mkv"
            }

            val outputFile = File(mediaDir, "$sourceId.$extension.download")
            val finalFile = File(mediaDir, "$sourceId.$extension")

            val downloadUrl = apiClient.libraryApi.getDownloadUrl(itemId = itemId)

            Timber.d("Downloading from: $downloadUrl")
            Timber.d("Saving to: ${outputFile.absolutePath}")

            val request = Request.Builder()
                .url(downloadUrl)
                .header("X-Emby-Token", apiClient.accessToken ?: "")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("Download failed: ${response.code} ${response.message}")
                }

                val totalBytes = response.body?.contentLength() ?: -1L
                var downloadedBytes = 0L
                var lastUpdateTime = 0L

                response.body?.byteStream()?.use { input ->
                    FileOutputStream(outputFile).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var bytes: Int

                        while (input.read(buffer).also { bytes = it } != -1) {
                            if (isStopped) {
                                Timber.d("Download cancelled by user")
                                try {
                                    output.close()
                                } catch (_: Exception) {
                                }
                                outputFile.delete()
                                return@withContext Result.failure(workDataOf("error" to "Cancelled"))
                            }

                            output.write(buffer, 0, bytes)
                            downloadedBytes += bytes

                            if (totalBytes > 0) {
                                val progress = (downloadedBytes.toFloat() / totalBytes.toFloat())
                                updateProgress(downloadId, progress, downloadedBytes, totalBytes)
                                setProgressAsync(
                                    workDataOf(
                                        PROGRESS_KEY to progress,
                                        "downloadedBytes" to downloadedBytes,
                                        "totalBytes" to totalBytes
                                    )
                                )
                                val currentTime = System.currentTimeMillis()
                                if (currentTime - lastUpdateTime > 500 || downloadedBytes == totalBytes) {
                                    lastUpdateTime = currentTime
                                    setForeground(
                                        createForegroundInfo(
                                            downloadId.hashCode(),
                                            itemName,
                                            downloadedBytes,
                                            totalBytes
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (outputFile.exists()) {
                outputFile.renameTo(finalFile)
                Timber.d("Download completed: ${finalFile.absolutePath}")
            }

            val updatedDownload = download.copy(
                status = DownloadStatus.COMPLETED,
                progress = 1.0f,
                bytesDownloaded = finalFile.length(),
                totalBytes = finalFile.length(),
                filePath = finalFile.absolutePath,
                updatedAt = System.currentTimeMillis()
            )
            databaseRepository.insertDownload(updatedDownload)

            ensureItemInDatabase(apiClient, download.serverId, itemId, itemType, userId)
            downloadImages(apiClient, download.serverId, itemId, itemType, userId)
            downloadSegments(itemId)
            createLocalSource(itemId, sourceId, source.name, finalFile)

            Timber.i("Media download completed successfully for: $itemName")

            return@withContext Result.success(
                workDataOf(
                    KEY_DOWNLOAD_ID to downloadIdString,
                    KEY_ITEM_ID to itemIdString,
                    KEY_SOURCE_ID to sourceId,
                    KEY_FILE_PATH to finalFile.absolutePath
                )
            )

        } catch (e: Exception) {
            Timber.e(e, "Media download failed")
            try {
                val download = databaseRepository.getDownload(downloadId)
                if (download != null) {
                    databaseRepository.insertDownload(
                        download.copy(
                            status = DownloadStatus.FAILED,
                            error = e.message ?: "Unknown error",
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            } catch (dbEx: Exception) {
                Timber.e(dbEx, "Failed to update download status to FAILED")
            }
            return@withContext Result.failure(workDataOf("error" to (e.message ?: "Unknown error")))
        }
    }

    private suspend fun updateProgress(
        downloadId: UUID,
        progress: Float,
        downloadedBytes: Long,
        totalBytes: Long
    ) {
        try {
            val download = databaseRepository.getDownload(downloadId)
            if (download != null) {
                databaseRepository.insertDownload(
                    download.copy(
                        progress = progress,
                        bytesDownloaded = downloadedBytes,
                        totalBytes = totalBytes,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to update download progress")
        }
    }

    private suspend fun ensureItemInDatabase(
        apiClient: ApiClient,
        serverId: String,
        itemId: UUID,
        itemType: String,
        userId: UUID
    ) {
        try {
            Timber.d("Ensuring item $itemId is saved to database")
            val baseUrl = apiClient.baseUrl ?: ""
            val itemsApi = ItemsApi(apiClient)

            val baseItemDto = try {
                itemsApi.getItems(
                    userId = userId,
                    ids = listOf(itemId),
                    fields = listOf(
                        ItemFields.MEDIA_SOURCES,
                        ItemFields.MEDIA_STREAMS,
                        ItemFields.OVERVIEW,
                        ItemFields.GENRES,
                        ItemFields.PEOPLE,
                        ItemFields.TAGLINES,
                        ItemFields.CHAPTERS,
                        ItemFields.TRICKPLAY
                    ),
                    enableImages = true,
                    enableUserData = true
                ).content?.items?.firstOrNull()
            } catch (e: Exception) {
                null
            }

            if (baseItemDto == null) return

            when (baseItemDto.type) {
                BaseItemKind.MOVIE -> {
                    val movie = baseItemDto.toAfinityMovie(baseUrl)
                    databaseRepository.insertMovie(movie, serverId)
                }

                BaseItemKind.EPISODE -> {
                    val episode = baseItemDto.toAfinityEpisode(baseUrl)
                    if (episode != null) {
                        episode.seriesId?.let { seriesId ->
                            if (databaseRepository.getShow(seriesId, userId) == null) {
                                val showDto = try {
                                    itemsApi.getItems(
                                        userId = userId,
                                        ids = listOf(seriesId),
                                        fields = listOf(
                                            ItemFields.OVERVIEW,
                                            ItemFields.GENRES,
                                            ItemFields.PEOPLE
                                        ),
                                        enableImages = true,
                                        enableUserData = true
                                    ).content?.items?.firstOrNull()
                                } catch (e: Exception) {
                                    null
                                }

                                showDto?.toAfinityShow(baseUrl)?.let { show ->
                                    databaseRepository.insertShow(show, serverId)
                                    downloadShowImages(apiClient, serverId, seriesId, userId)
                                }
                            }
                        }

                        episode.seasonId.let { seasonId ->
                            if (databaseRepository.getSeason(seasonId, userId) == null) {
                                val seasonDto = try {
                                    itemsApi.getItems(
                                        userId = userId,
                                        ids = listOf(seasonId),
                                        fields = listOf(ItemFields.OVERVIEW),
                                        enableImages = true,
                                        enableUserData = true
                                    ).content?.items?.firstOrNull()
                                } catch (e: Exception) {
                                    null
                                }

                                seasonDto?.toAfinitySeason(baseUrl)?.let { season ->
                                    databaseRepository.insertSeason(season, serverId)
                                    downloadSeasonImages(apiClient, serverId, seasonId, userId)
                                }
                            }
                        }

                        databaseRepository.insertEpisode(episode, serverId)
                    }
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
        userId: UUID
    ) {
        try {
            val item = when (itemType.uppercase()) {
                "MOVIE" -> databaseRepository.getMovie(itemId, userId)
                "EPISODE" -> databaseRepository.getEpisode(itemId, userId)
                else -> null
            } ?: return

            val itemDir = downloadRepository.getItemDownloadDirectory(itemId)
            val imagesDir = File(itemDir, "images").also { it.mkdirs() }
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

            val updatedImages = AfinityImages(
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
                showLogoImageBlurHash = images.showLogoImageBlurHash
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
        userId: UUID
    ) {
        try {
            val show = databaseRepository.getShow(showId, userId) ?: return
            val showDir = downloadRepository.getItemDownloadDirectory(showId)
            val imagesDir = File(showDir, "images").also { it.mkdirs() }
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

            val updatedImages = AfinityImages(
                primary = downloadedImages["primary"] ?: images.primary,
                backdrop = downloadedImages["backdrop"] ?: images.backdrop,
                thumb = downloadedImages["thumb"] ?: images.thumb,
                logo = downloadedImages["logo"] ?: images.logo,
                primaryImageBlurHash = images.primaryImageBlurHash,
                backdropImageBlurHash = images.backdropImageBlurHash,
                thumbImageBlurHash = images.thumbImageBlurHash,
                logoImageBlurHash = images.logoImageBlurHash
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
        userId: UUID
    ) {
        try {
            val season = databaseRepository.getSeason(seasonId, userId) ?: return
            val seasonDir = downloadRepository.getItemDownloadDirectory(seasonId)
            val imagesDir = File(seasonDir, "images").also { it.mkdirs() }
            val images = season.images
            val downloadedImages = mutableMapOf<String, Uri?>()

            suspend fun saveImage(uri: Uri?, key: String) {
                if (uri == null) return
                val localPath = downloadImage(apiClient, uri.toString(), imagesDir, key)
                if (localPath != null) downloadedImages[key] = localPath
            }

            saveImage(images.primary, "primary")
            saveImage(images.backdrop, "backdrop")

            val updatedImages = AfinityImages(
                primary = downloadedImages["primary"] ?: images.primary,
                backdrop = downloadedImages["backdrop"] ?: images.backdrop,
                thumb = downloadedImages["thumb"] ?: images.thumb,
                logo = downloadedImages["logo"] ?: images.logo,
                primaryImageBlurHash = images.primaryImageBlurHash,
                backdropImageBlurHash = images.backdropImageBlurHash,
                thumbImageBlurHash = images.thumbImageBlurHash,
                logoImageBlurHash = images.logoImageBlurHash
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
        userId: UUID
    ) {
        try {
            val movie = databaseRepository.getMovie(itemId, userId) ?: return
            if (movie.people.isEmpty()) return

            val movieDir = downloadRepository.getItemDownloadDirectory(itemId)
            val peopleImagesDir = File(movieDir, "people").also { it.mkdirs() }

            val updatedPeople = movie.people.map { person ->
                person.image.uri?.let { uri ->
                    val localPath = downloadImage(
                        apiClient,
                        uri.toString(),
                        peopleImagesDir,
                        person.id.toString()
                    )
                    if (localPath != null) {
                        person.copy(
                            image = AfinityPersonImage(
                                uri = localPath,
                                blurHash = person.image.blurHash
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
        baseName: String
    ): Uri? {
        var resultUri: Uri? = null
        try {
            val request = Request.Builder()
                .url(imageUrl)
                .header("X-Emby-Token", apiClient.accessToken ?: "")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val contentType = response.header("Content-Type") ?: "image/jpeg"
                    val extension = when {
                        contentType.contains("png") -> "png"
                        contentType.contains("webp") -> "webp"
                        contentType.contains("gif") -> "gif"
                        else -> "jpg"
                    }
                    val outputFile = File(outputDir, "$baseName.$extension")

                    response.body?.byteStream()?.use { input ->
                        FileOutputStream(outputFile).use { output ->
                            input.copyTo(output)
                        }
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
        file: File
    ) {
        try {
            val localSource = AfinitySourceDto(
                id = "${sourceId}_local",
                itemId = itemId,
                name = "$sourceName (Downloaded)",
                type = AfinitySourceType.LOCAL,
                path = file.absolutePath,
                downloadId = null
            )
            databaseRepository.insertSource(
                AfinitySource(
                    id = localSource.id,
                    name = localSource.name,
                    type = localSource.type,
                    path = localSource.path,
                    size = file.length(),
                    mediaStreams = emptyList(),
                    downloadId = null
                ),
                itemId
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to create LOCAL source entry")
        }
    }

    private fun createForegroundInfo(
        notificationId: Int,
        itemName: String,
        downloadedBytes: Long,
        totalBytes: Long
    ): ForegroundInfo {
        val context: Context = applicationContext
        val channelId = "download_channel"
        val title = "Downloading $itemName"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background download tasks"
            }
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }

        val progressText = if (totalBytes > 0) {
            "${(downloadedBytes * 100 / totalBytes)}%"
        } else {
            "Starting..."
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(progressText)
            .setSmallIcon(R.drawable.ic_download)
            .setOngoing(true)
            .setProgress(
                if (totalBytes > 0) totalBytes.toInt() else 0,
                if (totalBytes > 0) downloadedBytes.toInt() else 0,
                totalBytes <= 0
            )
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }
}