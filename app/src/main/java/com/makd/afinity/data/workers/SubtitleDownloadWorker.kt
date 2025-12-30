package com.makd.afinity.data.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.makd.afinity.data.models.extensions.toAfinityEpisode
import com.makd.afinity.data.models.extensions.toAfinityMovie
import com.makd.afinity.data.models.media.AfinityMediaStream
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
import org.jellyfin.sdk.model.api.MediaStreamType
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@HiltWorker
class SubtitleDownloadWorker @AssistedInject constructor(
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
            Timber.d("Starting subtitle download for item: $itemId")

            val userId = try {
                apiClient.userApi.getCurrentUser().content?.id
            } catch (e: Exception) {
                null
            } ?: return@withContext Result.failure(workDataOf("error" to "User not authenticated"))

            val baseUrl = apiClient.baseUrl ?: ""
            val baseItemDto = mediaRepository.getItem(
                itemId = itemId,
                fields = listOf(ItemFields.MEDIA_SOURCES, ItemFields.MEDIA_STREAMS)
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

            val subtitleStreams = source.mediaStreams.filter { stream ->
                stream.type == MediaStreamType.SUBTITLE &&
                        stream.isExternal == true
            }

            source.mediaStreams.forEach { stream ->
                Timber.d("Stream: type=${stream.type}, isExternal=${stream.isExternal}, language=${stream.language}, index=${stream.index}, path=${stream.path}")
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
                        itemId = itemId,
                        stream = stream,
                        baseUrl = baseUrl,
                        outputDir = subtitlesDir,
                        mediaSourceId = sourceId
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
                    KEY_SOURCE_ID to sourceId
                )
            )

        } catch (e: Exception) {
            Timber.e(e, "Subtitle download failed")
            return@withContext Result.failure(
                workDataOf(
                    "error" to (e.message ?: "Unknown error")
                )
            )
        }
    }

    private fun downloadSubtitle(
        itemId: UUID,
        stream: AfinityMediaStream,
        baseUrl: String,
        outputDir: File,
        mediaSourceId: String
    ) {
        val language = stream.language ?: "unknown"
        val codec = stream.codec ?: "srt"
        val extension = when (codec.lowercase()) {
            "subrip", "srt" -> "srt"
            "ass" -> "ass"
            "ssa" -> "ssa"
            "vtt", "webvtt" -> "vtt"
            else -> "srt"
        }

        val outputFile = File(outputDir, "${language}_${stream.index}.$extension")

        if (outputFile.exists()) {
            return
        }

        val subtitleUrl =
            "$baseUrl/Videos/$itemId/$mediaSourceId/Subtitles/${stream.index}/Stream.$extension?api_key=${apiClient.accessToken}"

        val request = Request.Builder()
            .url(subtitleUrl)
            .header("X-Emby-Token", apiClient.accessToken ?: "")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to download subtitle: ${response.code}")
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

        Timber.d("Downloaded subtitle: $language.$extension")
    }
}
