package com.makd.afinity.data.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.makd.afinity.data.database.entities.DownloadDto
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.extensions.toAfinityEpisode
import com.makd.afinity.data.models.extensions.toAfinityMovie
import com.makd.afinity.data.models.media.AfinityMediaStream
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.download.JellyfinDownloadRepository
import com.makd.afinity.di.DownloadClient
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.operations.ItemsApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.MediaStreamType
import timber.log.Timber

@HiltWorker
class SubtitleDownloadWorker
@AssistedInject
constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val sessionManager: SessionManager,
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

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val downloadIdString =
                inputData.getString(KEY_DOWNLOAD_ID)
                    ?: return@withContext Result.failure(
                        workDataOf("error" to "Missing download ID")
                    )

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

            try {
                Timber.d("Starting subtitle download for item: $itemId")

                val download: DownloadDto =
                    databaseRepository.getDownloadByItemId(itemId)
                        ?: return@withContext Result.failure(
                            workDataOf("error" to "Download not found")
                        )

                val apiClient =
                    sessionManager.getOrRestoreApiClient(download.serverId)
                        ?: return@withContext Result.failure(
                            workDataOf(
                                "error" to
                                    "Could not restore session for server ${download.serverId}"
                            )
                        )

                val userId =
                    try {
                        apiClient.userApi.getCurrentUser().content?.id
                    } catch (e: Exception) {
                        null
                    }
                        ?: return@withContext Result.failure(
                            workDataOf("error" to "User not authenticated")
                        )

                val baseUrl = apiClient.baseUrl ?: ""

                val itemsApi = ItemsApi(apiClient)
                val baseItemDto =
                    try {
                        itemsApi
                            .getItems(
                                userId = userId,
                                ids = listOf(itemId),
                                fields = listOf(ItemFields.MEDIA_SOURCES, ItemFields.MEDIA_STREAMS),
                                enableImages = false,
                                enableUserData = false,
                            )
                            .content
                            ?.items
                            ?.firstOrNull()
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to fetch item details for subtitles")
                        null
                    } ?: return@withContext Result.failure(workDataOf("error" to "Item not found"))

                val item =
                    when (baseItemDto.type) {
                        BaseItemKind.MOVIE -> baseItemDto.toAfinityMovie(baseUrl)
                        BaseItemKind.EPISODE ->
                            baseItemDto.toAfinityEpisode(baseUrl)
                                ?: return@withContext Result.failure(
                                    workDataOf("error" to "Failed to convert episode")
                                )

                        else ->
                            return@withContext Result.failure(
                                workDataOf("error" to "Unsupported item type: ${baseItemDto.type}")
                            )
                    }

                val source =
                    item.sources.find { it.id == sourceId }
                        ?: return@withContext Result.failure(
                            workDataOf("error" to "Source not found")
                        )

                val subtitleStreams =
                    source.mediaStreams.filter { stream ->
                        stream.type == MediaStreamType.SUBTITLE && stream.isExternal == true
                    }

                if (subtitleStreams.isEmpty()) {
                    Timber.d("No external subtitles found for item: $itemId")
                    return@withContext Result.success()
                }

                val itemDir = downloadRepository.getItemDownloadDirectory(itemId)
                val subtitlesDir = File(itemDir, "subtitles").also { it.mkdirs() }

                subtitleStreams.forEach { stream ->
                    try {
                        downloadSubtitle(
                            apiClient = apiClient,
                            itemId = itemId,
                            stream = stream,
                            baseUrl = baseUrl,
                            outputDir = subtitlesDir,
                            mediaSourceId = sourceId,
                        )
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to download subtitle: ${stream.language}")
                    }
                }

                Timber.i("Subtitle download completed for item: $itemId")

                return@withContext Result.success(
                    workDataOf(
                        KEY_DOWNLOAD_ID to downloadIdString,
                        KEY_ITEM_ID to itemIdString,
                        KEY_SOURCE_ID to sourceId,
                    )
                )
            } catch (e: Exception) {
                Timber.e(e, "Subtitle download failed")
                return@withContext Result.failure(
                    workDataOf("error" to (e.message ?: "Unknown error"))
                )
            }
        }

    private suspend fun downloadSubtitle(
        apiClient: ApiClient,
        itemId: UUID,
        stream: AfinityMediaStream,
        baseUrl: String,
        outputDir: File,
        mediaSourceId: String,
    ) {
        val language = stream.language ?: "unknown"
        val codec = stream.codec ?: "srt"
        val extension =
            when (codec.lowercase()) {
                "subrip",
                "srt" -> "srt"
                "ass" -> "ass"
                "ssa" -> "ssa"
                "vtt",
                "webvtt" -> "vtt"
                else -> "srt"
            }

        val outputFile = File(outputDir, "${language}_${stream.index}.$extension")

        if (!outputFile.exists()) {
            val subtitleUrl =
                "$baseUrl/Videos/$itemId/$mediaSourceId/Subtitles/${stream.index}/Stream.$extension?api_key=${apiClient.accessToken}"

            val request =
                Request.Builder()
                    .url(subtitleUrl)
                    .header("X-Emby-Token", apiClient.accessToken ?: "")
                    .build()

            try {
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Timber.w("Failed to download subtitle: ${response.code}")
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
            } catch (e: Exception) {
                Timber.e(e, "Error downloading subtitle file")
                return
            }
        }

        if (outputFile.exists() && outputFile.length() > 0) {
            val localSourceId = "${mediaSourceId}_local"
            try {
                val localStream = stream.copy(path = outputFile.absolutePath, isExternal = true)
                databaseRepository.insertMediaStream(localStream, localSourceId)
                Timber.d("Registered local subtitle in DB: ${outputFile.name}")
            } catch (e: Exception) {
                Timber.e(e, "Failed to insert subtitle stream into database")
            }
        }

        Timber.d("Processed subtitle: $language.$extension")
    }
}
