package com.makd.afinity.di

import android.app.Application
import com.makd.afinity.data.manager.PlaybackStateManager
import com.makd.afinity.data.repository.media.MediaRepository
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
        mediaRepository: MediaRepository
    ): PlaybackStateManager {
        return PlaybackStateManager(mediaRepository)
    }
}