package com.makd.afinity.data.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.makd.afinity.data.database.entities.AfinitySourceDto
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
import com.makd.afinity.data.repository.media.MediaRepository
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
    private val apiClient: ApiClient,
    private val mediaRepository: MediaRepository,
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
            Timber.d("Starting media download for item: $itemName ($itemType)")

            val download = databaseRepository.getDownload(downloadId)
                ?: return@withContext Result.failure(workDataOf("error" to "Download not found"))

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

            try {
                preferencesRepository.setCurrentUserId(userId.toString())
                Timber.d("Saved userId to preferences for offline use: $userId")
            } catch (e: Exception) {
                Timber.w(e, "Failed to save userId to preferences")
            }

            val baseUrl = apiClient.baseUrl ?: ""
            val baseItemDto = mediaRepository.getItem(
                itemId = itemId,
                fields = listOf(
                    ItemFields.MEDIA_SOURCES,
                    ItemFields.MEDIA_STREAMS,
                    ItemFields.OVERVIEW
                )
            ) ?: return@withContext Result.failure(workDataOf("error" to "Item not found"))

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

                response.body?.byteStream()?.use { input ->
                    FileOutputStream(outputFile).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var bytes: Int

                        while (input.read(buffer).also { bytes = it } != -1) {
                            if (isStopped) {
                                Timber.d("Download cancelled by user")
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

            ensureItemInDatabase(itemId, itemType)

            downloadImages(itemId, itemType)

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

            return@withContext Result.failure(
                workDataOf(
                    "error" to (e.message ?: "Unknown error")
                )
            )
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

    private suspend fun ensureItemInDatabase(itemId: UUID, itemType: String) {
        try {
            Timber.d("Ensuring item $itemId is saved to database")

            val baseUrl = apiClient.baseUrl ?: ""
            val baseItemDto = mediaRepository.getItem(
                itemId = itemId,
                fields = listOf(
                    ItemFields.MEDIA_SOURCES,
                    ItemFields.MEDIA_STREAMS,
                    ItemFields.OVERVIEW,
                    ItemFields.GENRES,
                    ItemFields.PEOPLE,
                    ItemFields.TAGLINES,
                    ItemFields.CHAPTERS,
                    ItemFields.TRICKPLAY
                )
            )

            if (baseItemDto == null) {
                Timber.w("Could not fetch item details from server")
                return
            }

            when (baseItemDto.type) {
                BaseItemKind.MOVIE -> {
                    val movie = baseItemDto.toAfinityMovie(baseUrl)
                    databaseRepository.insertMovie(movie)
                    Timber.d("Saved movie to database: ${movie.name}")
                }

                BaseItemKind.EPISODE -> {
                    val episode = baseItemDto.toAfinityEpisode(baseUrl)
                    if (episode != null) {
                        val seriesId = episode.seriesId
                        if (seriesId != null) {
                            val userId = try {
                                apiClient.userApi.getCurrentUser().content?.id
                            } catch (e: Exception) {
                                null
                            }

                            if (userId != null) {
                                val existingShow = databaseRepository.getShow(seriesId, userId)
                                if (existingShow == null) {
                                    val showDto = mediaRepository.getItem(
                                        seriesId,
                                        listOf(
                                            ItemFields.OVERVIEW,
                                            ItemFields.GENRES,
                                            ItemFields.PEOPLE
                                        )
                                    )
                                    if (showDto != null) {
                                        val show = showDto.toAfinityShow(baseUrl)
                                        databaseRepository.insertShow(show)
                                        Timber.d("Saved show to database: ${show.name}")

                                        try {
                                            downloadShowImages(seriesId, userId)
                                        } catch (e: Exception) {
                                            Timber.w(e, "Failed to download show images")
                                        }
                                    }
                                }

                                val seasonId = episode.seasonId
                                val existingSeason = databaseRepository.getSeason(seasonId, userId)
                                if (existingSeason == null) {
                                    val seasonDto = mediaRepository.getItem(
                                        seasonId,
                                        listOf(ItemFields.OVERVIEW)
                                    )
                                    if (seasonDto != null) {
                                        try {
                                            val season = seasonDto.toAfinitySeason(baseUrl)
                                            databaseRepository.insertSeason(season)
                                            Timber.d("Saved season to database: ${season.name}")

                                            try {
                                                downloadSeasonImages(seasonId, userId)
                                            } catch (e: Exception) {
                                                Timber.w(e, "Failed to download season images")
                                            }
                                        } catch (e: Exception) {
                                            Timber.w(e, "Failed to convert season to AfinitySeason")
                                        }
                                    }
                                }
                            }
                        }

                        databaseRepository.insertEpisode(episode)
                        Timber.d("Saved episode to database: ${episode.name}")
                    }
                }

                else -> {
                    Timber.w("Unsupported item type for database save: ${baseItemDto.type}")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to ensure item is in database")
        }
    }

    private suspend fun downloadSegments(itemId: UUID) {
        try {
            Timber.d("Downloading media segments for item: $itemId")

            val segments = segmentsRepository.getSegments(itemId)

            if (segments.isEmpty()) {
                Timber.d("No segments available for item: $itemId")
            } else {
                Timber.i("Successfully cached ${segments.size} segments for offline use")
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to download segments (non-critical)")
        }
    }

    private suspend fun downloadImages(itemId: UUID, itemType: String) {
        try {
            Timber.d("Starting image download for item: $itemId, type: $itemType")

            val userId = try {
                apiClient.userApi.getCurrentUser().content?.id
            } catch (e: Exception) {
                Timber.e(e, "Failed to get user ID for image download")
                null
            }

            if (userId == null) {
                Timber.w("Cannot download images: user ID is null")
                return
            }

            Timber.d("Fetching item from database with userId: $userId")
            val item = when (itemType.uppercase()) {
                "MOVIE" -> databaseRepository.getMovie(itemId, userId)
                "EPISODE" -> databaseRepository.getEpisode(itemId, userId)
                else -> {
                    Timber.w("Unsupported item type for image download: $itemType")
                    null
                }
            }

            if (item == null) {
                Timber.w("Item not found in database for image download")
                return
            }

            Timber.d("Found item in database: ${item.name}")

            val itemDir = downloadRepository.getItemDownloadDirectory(itemId)
            val imagesDir = File(itemDir, "images").also {
                it.mkdirs()
                Timber.d("Created images directory: ${it.absolutePath}")
            }

            val images = item.images
            val downloadedImages = mutableMapOf<String, android.net.Uri?>()
            var successCount = 0

            Timber.d("Starting to download images - Primary: ${images.primary != null}, Backdrop: ${images.backdrop != null}, Thumb: ${images.thumb != null}, Logo: ${images.logo != null}")

            images.primary?.let { uri ->
                Timber.d("Downloading primary image from: $uri")
                val localPath = downloadImage(uri.toString(), imagesDir, "primary")
                if (localPath != null) {
                    downloadedImages["primary"] = localPath
                    successCount++
                    Timber.i("Primary image downloaded")
                }
            }

            images.backdrop?.let { uri ->
                Timber.d("Downloading backdrop image from: $uri")
                val localPath = downloadImage(uri.toString(), imagesDir, "backdrop")
                if (localPath != null) {
                    downloadedImages["backdrop"] = localPath
                    successCount++
                    Timber.i("Backdrop image downloaded")
                }
            }

            images.thumb?.let { uri ->
                Timber.d("Downloading thumb image from: $uri")
                val localPath = downloadImage(uri.toString(), imagesDir, "thumb")
                if (localPath != null) {
                    downloadedImages["thumb"] = localPath
                    successCount++
                    Timber.i("Thumb image downloaded")
                }
            }

            images.logo?.let { uri ->
                Timber.d("Downloading logo image from: $uri")
                val localPath = downloadImage(uri.toString(), imagesDir, "logo")
                if (localPath != null) {
                    downloadedImages["logo"] = localPath
                    successCount++
                    Timber.i("Logo image downloaded")
                }
            }

            if (itemType.uppercase() == "EPISODE") {
                images.showPrimary?.let { uri ->
                    Timber.d("Downloading show primary image from: $uri")
                    val localPath = downloadImage(uri.toString(), imagesDir, "show_primary")
                    if (localPath != null) {
                        downloadedImages["showPrimary"] = localPath
                        successCount++
                        Timber.i("Show primary image downloaded")
                    }
                }

                images.showBackdrop?.let { uri ->
                    Timber.d("Downloading show backdrop image from: $uri")
                    val localPath = downloadImage(uri.toString(), imagesDir, "show_backdrop")
                    if (localPath != null) {
                        downloadedImages["showBackdrop"] = localPath
                        successCount++
                        Timber.i("Show backdrop image downloaded")
                    }
                }

                images.showLogo?.let { uri ->
                    Timber.d("Downloading show logo image from: $uri")
                    val localPath = downloadImage(uri.toString(), imagesDir, "show_logo")
                    if (localPath != null) {
                        downloadedImages["showLogo"] = localPath
                        successCount++
                        Timber.i("Show logo image downloaded")
                    }
                }
            }

            Timber.i("Successfully downloaded $successCount images")

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
                    databaseRepository.insertMovie(item.copy(images = updatedImages))
                    Timber.i("Updated movie in database with local image paths")

                    try {
                        downloadPersonImages(itemId, userId)
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to download person images for movie")
                    }
                }

                is AfinityEpisode -> {
                    databaseRepository.insertEpisode(item.copy(images = updatedImages))
                    Timber.i("Updated episode in database with local image paths")
                }
            }

        } catch (e: Exception) {
            Timber.e(e, "Failed to download images")
        }
    }

    private suspend fun downloadShowImages(showId: UUID, userId: UUID) {
        try {
            Timber.d("Starting show image download for showId: $showId")

            val show = databaseRepository.getShow(showId, userId)
            if (show == null) {
                Timber.w("Show not found in database for image download")
                return
            }

            Timber.d("Found show in database: ${show.name}")

            val showDir = downloadRepository.getItemDownloadDirectory(showId)
            val imagesDir = File(showDir, "images").also {
                it.mkdirs()
                Timber.d("Created show images directory: ${it.absolutePath}")
            }

            val images = show.images
            val downloadedImages = mutableMapOf<String, android.net.Uri?>()
            var successCount = 0

            Timber.d("Starting to download show images - Primary: ${images.primary != null}, Backdrop: ${images.backdrop != null}, Logo: ${images.logo != null}")

            images.primary?.let { uri ->
                Timber.d("Downloading show primary image from: $uri")
                val localPath = downloadImage(uri.toString(), imagesDir, "primary")
                if (localPath != null) {
                    downloadedImages["primary"] = localPath
                    successCount++
                    Timber.i("Show primary image downloaded")
                }
            }

            images.backdrop?.let { uri ->
                Timber.d("Downloading show backdrop image from: $uri")
                val localPath = downloadImage(uri.toString(), imagesDir, "backdrop")
                if (localPath != null) {
                    downloadedImages["backdrop"] = localPath
                    successCount++
                    Timber.i("Show backdrop image downloaded")
                }
            }

            images.logo?.let { uri ->
                Timber.d("Downloading show logo image from: $uri")
                val localPath = downloadImage(uri.toString(), imagesDir, "logo")
                if (localPath != null) {
                    downloadedImages["logo"] = localPath
                    successCount++
                    Timber.i("Show logo image downloaded")
                }
            }

            Timber.i("Successfully downloaded $successCount show images")

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

            databaseRepository.insertShow(show.copy(images = updatedImages))
            Timber.i("Updated show in database with local image paths")

        } catch (e: Exception) {
            Timber.e(e, "Failed to download show images")
        }
    }

    private suspend fun downloadSeasonImages(seasonId: UUID, userId: UUID) {
        try {
            Timber.d("Starting season image download for seasonId: $seasonId")

            val season = databaseRepository.getSeason(seasonId, userId)
            if (season == null) {
                Timber.w("Season not found in database for image download")
                return
            }

            Timber.d("Found season in database: ${season.name}")

            val seasonDir = downloadRepository.getItemDownloadDirectory(seasonId)
            val imagesDir = File(seasonDir, "images").also {
                it.mkdirs()
                Timber.d("Created season images directory: ${it.absolutePath}")
            }

            val images = season.images
            val downloadedImages = mutableMapOf<String, android.net.Uri?>()
            var successCount = 0

            Timber.d("Starting to download season images - Primary: ${images.primary != null}, Backdrop: ${images.backdrop != null}")

            images.primary?.let { uri ->
                Timber.d("Downloading season primary image from: $uri")
                val localPath = downloadImage(uri.toString(), imagesDir, "primary")
                if (localPath != null) {
                    downloadedImages["primary"] = localPath
                    successCount++
                    Timber.i("Season primary image downloaded")
                }
            }

            images.backdrop?.let { uri ->
                Timber.d("Downloading season backdrop image from: $uri")
                val localPath = downloadImage(uri.toString(), imagesDir, "backdrop")
                if (localPath != null) {
                    downloadedImages["backdrop"] = localPath
                    successCount++
                    Timber.i("Season backdrop image downloaded")
                }
            }

            Timber.i("Successfully downloaded $successCount season images")

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

            databaseRepository.insertSeason(season.copy(images = updatedImages))
            Timber.i("Updated season in database with local image paths")

        } catch (e: Exception) {
            Timber.e(e, "Failed to download season images")
        }
    }

    private suspend fun downloadPersonImages(itemId: UUID, userId: UUID) {
        try {
            val movie = databaseRepository.getMovie(itemId, userId)
            if (movie == null) {
                Timber.w("Movie not found in database for person image download")
                return
            }

            if (movie.people.isEmpty()) {
                Timber.d("No people to download images for")
                return
            }

            Timber.d("Starting person image downloads for ${movie.people.size} people")

            val movieDir = downloadRepository.getItemDownloadDirectory(itemId)
            val peopleImagesDir = File(movieDir, "people").also {
                it.mkdirs()
                Timber.d("Created people images directory: ${it.absolutePath}")
            }

            val updatedPeople = movie.people.map { person ->
                person.image.uri?.let { uri ->
                    Timber.d("Downloading image for person: ${person.name}")
                    val localPath =
                        downloadImage(uri.toString(), peopleImagesDir, person.id.toString())
                    if (localPath != null) {
                        Timber.i("Downloaded image for ${person.name}")
                        person.copy(
                            image = AfinityPersonImage(
                                uri = localPath,
                                blurHash = person.image.blurHash
                            )
                        )
                    } else {
                        person
                    }
                } ?: person
            }

            databaseRepository.insertMovie(movie.copy(people = updatedPeople))
            Timber.i("Updated movie with ${updatedPeople.count { it.image.uri?.scheme == "file" }} local person images")

        } catch (e: Exception) {
            Timber.e(e, "Failed to download person images")
        }
    }

    private suspend fun downloadImage(
        imageUrl: String,
        outputDir: File,
        baseName: String
    ): android.net.Uri? {
        return try {
            Timber.d("Downloading image: $imageUrl")

            val request = Request.Builder()
                .url(imageUrl)
                .header("X-Emby-Token", apiClient.accessToken ?: "")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Timber.w("Image download failed: ${response.code} ${response.message} for $baseName")
                    return null
                }

                val contentType = response.header("Content-Type") ?: "image/jpeg"
                val extension = when {
                    contentType.contains("png") -> "png"
                    contentType.contains("webp") -> "webp"
                    contentType.contains("gif") -> "gif"
                    contentType.contains("jpeg") || contentType.contains("jpg") -> "jpg"
                    else -> "jpg"
                }

                val outputFile = File(outputDir, "$baseName.$extension")
                Timber.d("Saving image to: ${outputFile.absolutePath} (Content-Type: $contentType)")

                response.body?.byteStream()?.use { input ->
                    FileOutputStream(outputFile).use { output ->
                        val bytes = input.copyTo(output)
                        Timber.d("Wrote $bytes bytes to ${outputFile.name}")
                    }
                }

                if (!outputFile.exists()) {
                    Timber.w("Output file does not exist after download: ${outputFile.absolutePath}")
                    return null
                }

                Timber.i("Image downloaded successfully: ${outputFile.name} (${outputFile.length()} bytes)")
                android.net.Uri.fromFile(outputFile)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to download image $baseName: ${e.message}")
            null
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
            Timber.d("Created LOCAL source entry for downloaded media")
        } catch (e: Exception) {
            Timber.e(e, "Failed to create LOCAL source entry")
        }
    }
}
