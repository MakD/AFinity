package com.makd.afinity.data.updater

import android.app.ActivityManager
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

        val release = updateManager.checkForUpdates()

        if (release != null) {
            Timber.d("UpdateCheckWorker: Update available - ${release.tagName}")
            if (!isAppForegrounded()) {
                notificationManager.showUpdateAvailableNotification(release)
            } else {
                Timber.d("UpdateCheckWorker: App is foregrounded, skipping notification")
            }
        } else {
            Timber.d("UpdateCheckWorker: No update available")
        }

        return Result.success()
    }

    private fun isAppForegrounded(): Boolean {
        val am = applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.runningAppProcesses?.any {
            it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                it.processName == applicationContext.packageName
        } == true
    }

    companion object {
        const val WORK_NAME = "update_check_worker"
    }
}
