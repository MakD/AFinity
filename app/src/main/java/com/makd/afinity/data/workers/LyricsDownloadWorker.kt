package com.makd.afinity.data.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.repository.DatabaseRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.api.operations.LyricsApi
import timber.log.Timber
import java.util.UUID

@HiltWorker
class LyricsDownloadWorker
@AssistedInject
constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val sessionManager: SessionManager,
    private val databaseRepository: DatabaseRepository,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_ITEM_ID = "item_id"
        const val KEY_ITEM_TYPE = "item_type"
    }

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val itemIdString =
                inputData.getString(KEY_ITEM_ID)
                    ?: return@withContext Result.failure(workDataOf("error" to "Missing item ID"))

            val itemType = inputData.getString(KEY_ITEM_TYPE) ?: ""
            if (itemType != "Audio") {
                return@withContext Result.success(workDataOf())
            }

            val itemId =
                try {
                    UUID.fromString(itemIdString)
                } catch (e: IllegalArgumentException) {
                    return@withContext Result.failure(workDataOf("error" to "Invalid item ID"))
                }

            try {
                val session =
                    sessionManager.currentSession.value
                        ?: return@withContext Result.failure(
                            workDataOf("error" to "No active session")
                        )

                val apiClient =
                    sessionManager.getOrRestoreApiClient(session.serverId)
                        ?: return@withContext Result.failure(workDataOf("error" to "No API client"))

                val response = LyricsApi(apiClient).getLyrics(itemId = itemId)
                val lines = response.content.lyrics ?: emptyList()

                if (lines.isEmpty()) {
                    Timber.d("No lyrics found for track $itemId")
                    return@withContext Result.success(workDataOf())
                }

                val lyricsJson =
                    Json.encodeToString(
                        lines.mapNotNull { line ->
                            val start = line.start ?: return@mapNotNull null
                            listOf(line.text ?: "", (start / 10_000_000.0).toString())
                        }
                    )

                databaseRepository.insertMusicLyrics(
                    trackId = itemId,
                    serverId = session.serverId,
                    userId = session.userId.toString(),
                    lyricsJson = lyricsJson,
                )

                Timber.i("Cached ${lines.size} lyric lines for track $itemId")
                Result.success(workDataOf())
            } catch (e: Exception) {
                Timber.w(e, "Failed to cache lyrics for track $itemId, not fatal")
                Result.success(workDataOf())
            }
        }
}
