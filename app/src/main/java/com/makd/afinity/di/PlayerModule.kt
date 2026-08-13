package com.makd.afinity.di

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.VideoCacheCleaner
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.io.File
import javax.inject.Singleton

@UnstableApi
@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {

    @Provides
    @Singleton
    fun provideExoDatabaseProvider(@ApplicationContext context: Context): DatabaseProvider {
        return StandaloneDatabaseProvider(context)
    }

    @Provides
    @Singleton
    fun provideExoCache(
        @ApplicationContext context: Context,
        databaseProvider: DatabaseProvider,
        preferencesRepository: PreferencesRepository,
    ): SimpleCache {
        val cacheDir = File(context.cacheDir, "exo_media_cache")
        val sizeMb = runCatching {
            runBlocking { preferencesRepository.getVideoCacheSizeMb() }
        }
            .onFailure { Timber.e(it, "Failed to read video cache size, using default") }
            .getOrDefault(1024)
        val evictor = LeastRecentlyUsedCacheEvictor(sizeMb * 1024L * 1024L)

        return SimpleCache(cacheDir, evictor, databaseProvider)
    }

    @Provides
    @Singleton
    fun provideVideoCacheCleaner(exoCache: Lazy<SimpleCache>): VideoCacheCleaner =
        VideoCacheCleaner {
            val cache = exoCache.get()
            cache.keys.toList().forEach { key -> cache.removeResource(key) }
        }
}
