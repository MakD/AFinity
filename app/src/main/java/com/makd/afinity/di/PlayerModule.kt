package com.makd.afinity.di

import com.makd.afinity.data.manager.PlaybackStateManager
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.playback.PlaybackRepository
import com.makd.afinity.data.sync.UserDataSyncScheduler
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@androidx.media3.common.util.UnstableApi
@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {
    @Provides
    @Singleton
    fun providePlaybackStateManager(
        mediaRepository: MediaRepository,
        playbackRepository: PlaybackRepository,
        syncScheduler: UserDataSyncScheduler
    ): PlaybackStateManager {
        return PlaybackStateManager(mediaRepository, playbackRepository, syncScheduler)
    }
}