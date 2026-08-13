package com.makd.afinity.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.makd.afinity.data.workers.UserDataSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserDataSyncScheduler
@Inject
constructor(@param:ApplicationContext private val context: Context) {

    fun triggerSync() {
        scheduleSync(ExistingWorkPolicy.REPLACE)
    }

    fun ifIdle() {
        scheduleSync(ExistingWorkPolicy.KEEP)
    }

    private fun scheduleSync(policy: ExistingWorkPolicy) {
        try {
            val constraints =
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

            val syncRequest =
                OneTimeWorkRequestBuilder<UserDataSyncWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                    .addTag(SYNC_WORK_TAG)
                    .build()

            WorkManager.getInstance(context).enqueueUniqueWork(SYNC_WORK_NAME, policy, syncRequest)

            Timber.d("User data sync scheduled with policy $policy")
        } catch (e: Exception) {
            Timber.e(e, "Failed to schedule user data sync")
        }
    }

    fun cancelSync() {
        try {
            WorkManager.getInstance(context).cancelUniqueWork(SYNC_WORK_NAME)
            Timber.d("User data sync cancelled")
        } catch (e: Exception) {
            Timber.e(e, "Failed to cancel user data sync")
        }
    }

    companion object {
        private const val SYNC_WORK_NAME = "user_data_sync"
        private const val SYNC_WORK_TAG = "sync"
    }
}
