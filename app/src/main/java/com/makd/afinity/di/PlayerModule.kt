package com.makd.afinity.di

import android.content.Context
import com.makd.afinity.data.manager.PlaybackStateManager
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.player.ExoPlayerRepository
import com.makd.afinity.data.repository.player.LibMpvPlayerRepository
import com.makd.afinity.data.repository.player.PlayerRepository
import com.makd.afinity.data.repository.playback.PlaybackRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.jellyfin.sdk.api.client.ApiClient
import timber.log.Timber
import javax.inject.Singleton

@androidx.media3.common.util.UnstableApi
@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {

    @Provides
    @Singleton
    fun providePlayerRepository(
        @ApplicationContext context: Context,
        playbackRepository: PlaybackRepository,
        apiClient: ApiClient,
        preferencesRepository: PreferencesRepository
    ): PlayerRepository {
        val useExoPlayer = runBlocking {
            try {
                preferencesRepository.useExoPlayer.first()
            } catch (e: Exception) {
                Timber.w("Failed to read player preference, defaulting to ExoPlayer")
                true
            }
        }

        return if (useExoPlayer) {
            Timber.d("Providing ExoPlayerRepository")
            ExoPlayerRepository(context, playbackRepository, apiClient)
        } else {
            Timber.d("Providing LibMpvPlayerRepository")
            LibMpvPlayerRepository(context, playbackRepository, apiClient)
        }
    }

    @Provides
    @Singleton
    fun providePlaybackStateManager(
        playerRepository: PlayerRepository,
        mediaRepository: MediaRepository
    ): PlaybackStateManager {
        val manager = PlaybackStateManager(playerRepository, mediaRepository)
        manager.initialize()
        return manager
    }
}