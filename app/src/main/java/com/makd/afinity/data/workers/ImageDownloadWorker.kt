package com.makd.afinity.data.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.download.JellyfinDownloadRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.di.DownloadClient
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userApi
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@HiltWorker
class ImageDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val apiClient: ApiClient,
    private val mediaRepository: MediaRepository,
    private val databaseRepository: DatabaseRepository,
    private val downloadRepository: JellyfinDownloadRepository,
    @DownloadClient private val okHttpClient: OkHttpClient,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_ITEM_ID = "item_id"
        const val KEY_SOURCE_ID = "source_id"
        const val BUFFER_SIZE = 8192
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val downloadIdString = inputData.getString(KEY_DOWNLOAD_ID)
            ?: return@withContext Result.failure(workDataOf("error" to "Missing download ID"))

        val itemIdString = inputData.getString(KEY_ITEM_ID)
            ?: return@withContext Result.failure(workDataOf("error" to "Missing item ID"))

        val itemId = try {
            UUID.fromString(itemIdString)
        } catch (e: IllegalArgumentException) {
            return@withContext Result.failure(workDataOf("error" to "Invalid item ID"))
        }

        val sourceId = inputData.getString(KEY_SOURCE_ID)
            ?: return@withContext Result.failure(workDataOf("error" to "Missing source ID"))

        try {
            Timber.d("Starting image download for item: $itemId")

            val userId = try {
                apiClient.userApi.getCurrentUser().content?.id
            } catch (e: Exception) {
                null
            } ?: return@withContext Result.failure(workDataOf("error" to "User not authenticated"))

            val item = databaseRepository.getMovie(itemId, userId)
                ?: databaseRepository.getEpisode(itemId, userId)
                ?: return@withContext Result.failure(workDataOf("error" to "Item not found in database"))

            Timber.d("Loaded item from database: ${item.name}, has images: ${item.images.primary != null}")

            val itemDir = downloadRepository.getItemDownloadDirectory(itemId)
            val imagesDir = File(itemDir, "images").also { it.mkdirs() }

            val images = item.images
            val downloadedImages = mutableMapOf<String, android.net.Uri>()

            if (images.primary != null) {
                try {
                    val url = images.primary.toString()
                    if (url.startsWith("file://")) {
                        Timber.d("Primary image already local, skipping download: $url")
                    } else {
                        Timber.d("Downloading primary image from: $url")
                        val localUri = downloadImage(
                            url = url,
                            outputDir = imagesDir,
                            baseName = "primary"
                        )
                        if (localUri != null) {
                            downloadedImages["primary"] = localUri
                            Timber.i("Primary image downloaded successfully")
                        }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to download primary image")
                }
            }

            if (images.backdrop != null) {
                try {
                    val url = images.backdrop.toString()
                    if (url.startsWith("file://")) {
                        Timber.d("Backdrop image already local, skipping download: $url")
                    } else {
                        Timber.d("Downloading backdrop image from: $url")
                        val localUri = downloadImage(
                            url = url,
                            outputDir = imagesDir,
                            baseName = "backdrop"
                        )
                        if (localUri != null) {
                            downloadedImages["backdrop"] = localUri
                            Timber.i("Backdrop image downloaded successfully")
                        }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to download backdrop image")
                }
            }

            if (images.logo != null) {
                try {
                    val url = images.logo.toString()
                    if (url.startsWith("file://")) {
                        Timber.d("Logo image already local, skipping download: $url")
                    } else {
                        Timber.d("Downloading logo image from: $url")
                        val localUri = downloadImage(
                            url = url,
                            outputDir = imagesDir,
                            baseName = "logo"
                        )
                        if (localUri != null) {
                            downloadedImages["logo"] = localUri
                            Timber.i("Logo image downloaded successfully")
                        }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to download logo image")
                }
            }

            if (images.thumb != null) {
                try {
                    val url = images.thumb.toString()
                    if (url.startsWith("file://")) {
                        Timber.d("Thumb image already local, skipping download: $url")
                    } else {
                        Timber.d("Downloading thumb image from: $url")
                        val localUri = downloadImage(
                            url = url,
                            outputDir = imagesDir,
                            baseName = "thumb"
                        )
                        if (localUri != null) {
                            downloadedImages["thumb"] = localUri
                            Timber.i("Thumb image downloaded successfully")
                        }
                    }
                } catch (e: Exception) {
                    Timber.w(e, "Failed to download thumb image")
                }
            }

            if (item is com.makd.afinity.data.models.media.AfinityEpisode) {
                if (item.seriesLogo != null) {
                    try {
                        val url = item.seriesLogo.toString()
                        if (url.startsWith("file://")) {
                            Timber.d("Series logo already local, skipping download: $url")
                        } else {
                            Timber.d("Downloading series logo image from: $url")
                            val localUri = downloadImage(
                                url = url,
                                outputDir = imagesDir,
                                baseName = "series_logo"
                            )
                            if (localUri != null) {
                                downloadedImages["series_logo"] = localUri
                                Timber.i("Series logo image downloaded successfully")
                            }
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to download series logo")
                    }
                }
            }

            Timber.i("Image download completed for item: $itemId - ${downloadedImages.size} images downloaded")

            if (downloadedImages.isNotEmpty()) {
                updateItemWithLocalImages(item, downloadedImages, userId)
            }

            return@withContext Result.success(
                workDataOf(
                    KEY_DOWNLOAD_ID to downloadIdString,
                    KEY_ITEM_ID to itemIdString,
                    KEY_SOURCE_ID to sourceId
                )
            )

        } catch (e: Exception) {
            Timber.e(e, "Image download failed")
            return@withContext Result.failure(
                workDataOf(
                    "error" to (e.message ?: "Unknown error")
                )
            )
        }
    }

    private fun downloadImage(url: String, outputDir: File, baseName: String): android.net.Uri? {
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Token", apiClient.accessToken ?: "")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to download image: ${response.code} ${response.message}")
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

            if (outputFile.exists()) {
                Timber.d("Image already exists, returning existing: ${outputFile.name}")
                return android.net.Uri.fromFile(outputFile)
            }

            Timber.d("Saving image to: ${outputFile.absolutePath} (Content-Type: $contentType)")

            response.body?.byteStream()?.use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytes: Int
                    var totalBytes = 0L
                    while (input.read(buffer).also { bytes = it } != -1) {
                        output.write(buffer, 0, bytes)
                        totalBytes += bytes
                    }
                    Timber.d("Wrote $totalBytes bytes to ${outputFile.name}")
                }
            }

            Timber.d("Downloaded image: ${outputFile.name}")
            return android.net.Uri.fromFile(outputFile)
        }
    }

    private suspend fun updateItemWithLocalImages(
        item: com.makd.afinity.data.models.media.AfinityItem,
        downloadedImages: Map<String, android.net.Uri>,
        userId: UUID
    ) {
        try {
            val images = item.images
            val updatedImages = com.makd.afinity.data.models.media.AfinityImages(
                primary = downloadedImages["primary"] ?: images.primary,
                backdrop = downloadedImages["backdrop"] ?: images.backdrop,
                thumb = downloadedImages["thumb"] ?: images.thumb,
                logo = downloadedImages["logo"] ?: images.logo,
                showPrimary = images.showPrimary,
                showBackdrop = images.showBackdrop,
                showLogo = downloadedImages["series_logo"] ?: images.showLogo,
                primaryImageBlurHash = images.primaryImageBlurHash,
                backdropImageBlurHash = images.backdropImageBlurHash,
                thumbImageBlurHash = images.thumbImageBlurHash,
                logoImageBlurHash = images.logoImageBlurHash,
                showPrimaryImageBlurHash = images.showPrimaryImageBlurHash,
                showBackdropImageBlurHash = images.showBackdropImageBlurHash,
                showLogoImageBlurHash = images.showLogoImageBlurHash
            )

            when (item) {
                is com.makd.afinity.data.models.media.AfinityMovie -> {
                    databaseRepository.insertMovie(item.copy(images = updatedImages))
                    Timber.i("Updated movie in database with ${downloadedImages.size} local image paths")
                }

                is com.makd.afinity.data.models.media.AfinityEpisode -> {
                    databaseRepository.insertEpisode(item.copy(images = updatedImages))
                    Timber.i("Updated episode in database with ${downloadedImages.size} local image paths")
                }

                else -> {
                    Timber.w("Unsupported item type for image update: ${item::class.simpleName}")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to update item with local images")
        }
    }
}
