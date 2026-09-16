package com.makd.afinity.data.repository.audiobookshelf

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.makd.afinity.data.workers.AbsProgressSyncWorker
import com.makd.afinity.util.requireServerNetwork
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class AbsProgressSyncScheduler
@Inject
constructor(@param:ApplicationContext private val context: Context) {
    fun scheduleSync(serverId: String, userId: UUID) {
        try {
            val constraints = Constraints.Builder().requireServerNetwork().build()

            val inputData = workDataOf(KEY_SERVER_ID to serverId, KEY_USER_ID to userId.toString())

            val request =
                OneTimeWorkRequestBuilder<AbsProgressSyncWorker>()
                    .setConstraints(constraints)
                    .setInputData(inputData)
                    .addTag(SYNC_WORK_TAG)
                    .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(SYNC_WORK_NAME, ExistingWorkPolicy.REPLACE, request)

            Timber.d(
                "ABS progress sync scheduled for serverId=$serverId (runs when network available)"
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to schedule ABS progress sync")
        }
    }

    companion object {
        const val SYNC_WORK_NAME = "abs_progress_sync"
        const val SYNC_WORK_TAG = "abs_sync"
        const val KEY_SERVER_ID = "serverId"
        const val KEY_USER_ID = "userId"
    }
}
