package com.makd.afinity.data.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.makd.afinity.data.repository.AudiobookshelfRepository
import com.makd.afinity.data.repository.audiobookshelf.AbsProgressSyncScheduler
import dagger.Lazy
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID

@HiltWorker
class AbsProgressSyncWorker
@AssistedInject
constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val audiobookshelfRepository: Lazy<AudiobookshelfRepository>,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val serverId = inputData.getString(AbsProgressSyncScheduler.KEY_SERVER_ID)
        val userIdStr = inputData.getString(AbsProgressSyncScheduler.KEY_USER_ID)

        if (serverId == null || userIdStr == null) {
            Timber.w("AbsProgressSync: missing serverId/userId in input data, skipping")
            return@withContext Result.failure(workDataOf("error" to "missing context"))
        }

        val userId = runCatching { UUID.fromString(userIdStr) }.getOrElse {
            Timber.w("AbsProgressSync: invalid userId '$userIdStr'")
            return@withContext Result.failure(workDataOf("error" to "invalid userId"))
        }

        Timber.d("AbsProgressSync: syncing pending progress for serverId=$serverId")
        val result = audiobookshelfRepository.get().syncPendingProgress(serverId, userId)
        return@withContext when {
            result.isSuccess -> {
                Timber.d("AbsProgressSync: synced ${result.getOrDefault(0)} items")
                Result.success(workDataOf("synced" to result.getOrDefault(0)))
            }
            else -> {
                Timber.w("AbsProgressSync: failed — ${result.exceptionOrNull()?.message}")
                Result.failure(workDataOf("error" to result.exceptionOrNull()?.message))
            }
        }
    }
}