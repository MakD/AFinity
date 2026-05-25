package com.makd.afinity.di

import androidx.media3.common.util.UnstableApi
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
import dagger.hilt.components.SingletonComponent
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
}
