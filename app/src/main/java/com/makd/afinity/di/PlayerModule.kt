package com.makd.afinity.di

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.makd.afinity.data.manager.MediaChangeManager
import com.makd.afinity.data.manager.MediaRefreshBus
import com.makd.afinity.data.manager.PlaybackStateManager
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.playback.PlaybackRepository
import com.makd.afinity.data.sync.UserDataSyncScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@UnstableApi
@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {

    @Provides
    @Singleton
    fun providePlaybackStateManager(
        mediaRepository: MediaRepository,
        appDataRepository: AppDataRepository,
        playbackRepository: PlaybackRepository,
        syncScheduler: UserDataSyncScheduler,
        mediaChangeManager: MediaChangeManager,
        mediaRefreshBus: MediaRefreshBus,
    ): PlaybackStateManager {
        return PlaybackStateManager(
            mediaRepository,
            appDataRepository,
            playbackRepository,
            syncScheduler,
            mediaChangeManager,
            mediaRefreshBus,
        )
    }

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
    ): SimpleCache {
        val cacheDir = File(context.cacheDir, "exo_media_cache")
        val maxCacheSizeBytes: Long = 1024L * 1024L * 1024L
        val evictor = LeastRecentlyUsedCacheEvictor(maxCacheSizeBytes)

        return SimpleCache(cacheDir, evictor, databaseProvider)
    }
}
