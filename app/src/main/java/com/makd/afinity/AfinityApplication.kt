package com.makd.afinity

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.makd.afinity.data.updater.UpdateScheduler
import com.makd.afinity.data.updater.models.UpdateCheckFrequency
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import timber.log.Timber

@HiltAndroidApp
class AfinityApplication : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var updateScheduler: UpdateScheduler

    @Inject
    lateinit var preferencesRepository: com.makd.afinity.data.repository.PreferencesRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.d("Afinity Application started")
        }
        applicationScope.launch {
            val frequency = preferencesRepository.getUpdateCheckFrequency()
            val checkFrequency = UpdateCheckFrequency.fromHours(frequency)
            updateScheduler.scheduleUpdateChecks(checkFrequency)
            Timber.d("Update scheduler initialized with frequency: ${checkFrequency.displayName}")
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}