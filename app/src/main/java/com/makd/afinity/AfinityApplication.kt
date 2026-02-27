package com.makd.afinity

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.makd.afinity.cast.CastManager
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.updater.UpdateScheduler
import com.makd.afinity.data.updater.models.UpdateCheckFrequency
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class AfinityApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var updateScheduler: UpdateScheduler

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    @Inject
    lateinit var castManager: CastManager

    @Inject
    lateinit var okHttpClient: OkHttpClient

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var imageLoaderPrefs: Deferred<Pair<Boolean, Int>>

    override fun onCreate() {
        super.onCreate()

        imageLoaderPrefs = applicationScope.async(Dispatchers.IO) {
            Pair(
                preferencesRepository.getImageCacheEnabled(),
                preferencesRepository.getImageCacheSizeMb(),
            )
        }

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.d("Afinity Application started")
        }

        castManager.initialize(this)

        applicationScope.launch {
            val frequency = preferencesRepository.getUpdateCheckFrequency()
            val checkFrequency = UpdateCheckFrequency.fromHours(frequency)
            updateScheduler.scheduleUpdateChecks(checkFrequency)
            Timber.d("Update scheduler initialized with frequency: ${checkFrequency.displayName}")
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val (isCacheEnabled, cacheSizeMb) = runBlocking { imageLoaderPrefs.await() }

        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
                add(SvgDecoder.Factory())
                add(AnimatedImageDecoder.Factory())
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context)
                    .strongReferencesEnabled(true)
                    .weakReferencesEnabled(true)
                    .build()
            }
            .diskCachePolicy(
                if (isCacheEnabled) CachePolicy.ENABLED else CachePolicy.DISABLED
            )
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(cacheSizeMb * 1024L * 1024L)
                    .build()
            }
            .memoryCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()
    }
}
