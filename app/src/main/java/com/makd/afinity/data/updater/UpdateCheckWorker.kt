package com.makd.afinity.data.updater

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.makd.afinity.data.updater.notification.UpdateNotificationManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class UpdateCheckWorker
@AssistedInject
constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val updateManager: UpdateManager,
    private val notificationManager: UpdateNotificationManager,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Timber.d("UpdateCheckWorker: Starting update check")

        return try {
            val release = updateManager.checkForUpdates()

            if (release != null) {
                Timber.d("UpdateCheckWorker: Update available - ${release.tagName}")
                notificationManager.showUpdateAvailableNotification(release)
            } else {
                Timber.d("UpdateCheckWorker: No update available")
            }

            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "UpdateCheckWorker: Failed to check for updates")
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "update_check_worker"
    }
}
