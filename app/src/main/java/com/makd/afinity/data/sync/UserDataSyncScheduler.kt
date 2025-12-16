package com.makd.afinity.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.makd.afinity.data.workers.UserDataSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserDataSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {

    fun scheduleSyncNow() {
        try {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncRequest = OneTimeWorkRequestBuilder<UserDataSyncWorker>()
                .setConstraints(constraints)
                .addTag(SYNC_WORK_TAG)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                SYNC_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                syncRequest
            )

            Timber.d("User data sync scheduled (will run when network is available)")
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
