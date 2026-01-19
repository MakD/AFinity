package com.makd.afinity.data.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.makd.afinity.data.models.extensions.toAfinityEpisode
import com.makd.afinity.data.models.extensions.toAfinityMovie
import com.makd.afinity.data.models.media.AfinityTrickplayInfo
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.download.JellyfinDownloadRepository
import com.makd.afinity.data.repository.media.MediaRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@HiltWorker
class TrickplayDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val apiClient: ApiClient,
    private val mediaRepository: MediaRepository,
    private val databaseRepository: DatabaseRepository,
    private val downloadRepository: JellyfinDownloadRepository,
    private val okHttpClient: OkHttpClient,
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
            Timber.d("Starting trickplay download for item: $itemId")

            val userId = try {
                apiClient.userApi.getCurrentUser().content?.id
            } catch (e: Exception) {
                null
            } ?: return@withContext Result.failure(workDataOf("error" to "User not authenticated"))

            val baseUrl = apiClient.baseUrl ?: ""
            val baseItemDto = mediaRepository.getItem(
                itemId = itemId,
                fields = listOf(ItemFields.TRICKPLAY, ItemFields.OVERVIEW)
            ) ?: return@withContext Result.failure(workDataOf("error" to "Item not found"))

            val item = when (baseItemDto.type) {
                BaseItemKind.MOVIE -> baseItemDto.toAfinityMovie(baseUrl)
                BaseItemKind.EPISODE -> baseItemDto.toAfinityEpisode(baseUrl)
                    ?: return@withContext Result.failure(workDataOf("error" to "Failed to convert episode"))

                else -> return@withContext Result.failure(
                    workDataOf("error" to "Unsupported item type: ${baseItemDto.type}")
                )
            }

            val trickplayInfo = item.trickplayInfo
            if (trickplayInfo.isNullOrEmpty()) {
                Timber.d("No trickplay info available for item: $itemId")
                return@withContext Result.success()
            }

            val itemDir = downloadRepository.getItemDownloadDirectory(itemId)
            val trickplayDir = File(itemDir, "trickplay").also { it.mkdirs() }
            Timber.d("Trickplay download base directory: ${trickplayDir.absolutePath}")
            Timber.d("Trickplay resolutions available: ${trickplayInfo.keys.joinToString()}")

            trickplayInfo.forEach { (resolution, info) ->
                Timber.d("Processing trickplay resolution: key='$resolution', info.width=${info.width}")
                try {
                    downloadTrickplayTiles(
                        itemId = itemId,
                        resolution = resolution,
                        info = info,
                        baseUrl = baseUrl,
                        outputDir = trickplayDir
                    )
                } catch (e: Exception) {
                    Timber.w(e, "Failed to download trickplay for resolution: $resolution")
                }
            }

            val localSourceId = "${sourceId}_local"
            trickplayInfo.forEach { (_, info) ->
                try {
                    databaseRepository.insertTrickplayInfo(info, localSourceId)
                } catch (e: Exception) {
                    Timber.w(e, "Failed to save trickplay info to database")
                }
            }

            Timber.i("Trickplay download completed for item: $itemId")

            return@withContext Result.success(
                workDataOf(
                    KEY_DOWNLOAD_ID to downloadIdString,
                    KEY_ITEM_ID to itemIdString,
                    KEY_SOURCE_ID to sourceId
                )
            )

        } catch (e: Exception) {
            Timber.e(e, "Trickplay download failed")
            return@withContext Result.failure(
                workDataOf(
                    "error" to (e.message ?: "Unknown error")
                )
            )
        }
    }

    private suspend fun downloadTrickplayTiles(
        itemId: UUID,
        resolution: String,
        info: AfinityTrickplayInfo,
        baseUrl: String,
        outputDir: File
    ) {
        val resolutionDir = File(outputDir, resolution).also { it.mkdirs() }

        val width = info.width

        val thumbnailsPerTile = info.tileWidth * info.tileHeight
        val totalTiles =
            kotlin.math.ceil(info.thumbnailCount.toDouble() / thumbnailsPerTile).toInt()

        Timber.d("Downloading trickplay: ${info.thumbnailCount} thumbnails across $totalTiles tiled images (${info.tileWidth}x${info.tileHeight} grid per tile)")

        for (tileIndex in 0 until totalTiles) {
            try {
                val tileUrl =
                    "$baseUrl/Videos/$itemId/Trickplay/$width/$tileIndex.jpg?api_key=${apiClient.accessToken}"
                val outputFile = File(resolutionDir, "$tileIndex.jpg")

                Timber.d("Downloading trickplay tile to: ${outputFile.absolutePath}")

                if (outputFile.exists()) {
                    Timber.d("Trickplay tile $tileIndex already exists, skipping")
                    continue
                }

                val request = Request.Builder()
                    .url(tileUrl)
                    .header("X-Emby-Token", apiClient.accessToken ?: "")
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Timber.w("Failed to download trickplay tile $tileIndex: ${response.code}")
                        return
                    }

                    response.body?.byteStream()?.use { input ->
                        FileOutputStream(outputFile).use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var bytes: Int
                            while (input.read(buffer).also { bytes = it } != -1) {
                                output.write(buffer, 0, bytes)
                            }
                        }
                    }
                }

                Timber.i("Downloaded trickplay tiled image: $resolution/$tileIndex.jpg (${outputFile.length()} bytes)")

            } catch (e: Exception) {
                Timber.w(e, "Failed to download trickplay tile $tileIndex")
            }
        }
    }
}
