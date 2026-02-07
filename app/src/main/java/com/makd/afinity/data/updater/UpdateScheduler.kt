package com.makd.afinity.data.updater

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.makd.afinity.data.updater.models.UpdateCheckFrequency
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

@Singleton
class UpdateScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    private val workManager = WorkManager.getInstance(context)

    fun scheduleUpdateChecks(frequency: UpdateCheckFrequency) {
        if (frequency == UpdateCheckFrequency.ON_APP_OPEN) {
            cancelUpdateChecks()
            Timber.d("Update checks set to ON_APP_OPEN, periodic checks cancelled")
            return
        }

        val constraints =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        val workRequest =
            PeriodicWorkRequestBuilder<UpdateCheckWorker>(frequency.hours.toLong(), TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

        workManager.enqueueUniquePeriodicWork(
            UpdateCheckWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.REPLACE,
            workRequest,
        )

        Timber.d("Scheduled update checks every ${frequency.hours} hours")
    }

    fun cancelUpdateChecks() {
        workManager.cancelUniqueWork(UpdateCheckWorker.WORK_NAME)
        Timber.d("Cancelled scheduled update checks")
    }
}
