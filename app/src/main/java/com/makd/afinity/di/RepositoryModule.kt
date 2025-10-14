package com.makd.afinity.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.JellyfinRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.auth.AuthRepository
import com.makd.afinity.data.repository.auth.JellyfinAuthRepository
import com.makd.afinity.data.repository.impl.DatabaseRepositoryImpl
import com.makd.afinity.data.repository.impl.JellyfinRepositoryImpl
import com.makd.afinity.data.repository.impl.PreferencesRepositoryImpl
import com.makd.afinity.data.repository.media.JellyfinMediaRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.playback.JellyfinPlaybackRepository
import com.makd.afinity.data.repository.playback.PlaybackRepository
import com.makd.afinity.data.repository.server.JellyfinServerRepository
import com.makd.afinity.data.repository.server.ServerRepository
import com.makd.afinity.data.repository.userdata.JellyfinUserDataRepository
import com.makd.afinity.data.repository.userdata.UserDataRepository
import com.makd.afinity.data.websocket.JellyfinWebSocketManager
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.jellyfin.sdk.api.client.ApiClient
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "afinity_preferences")

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        jellyfinAuthRepository: JellyfinAuthRepository
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindServerRepository(
        jellyfinServerRepository: JellyfinServerRepository
    ): ServerRepository

    @Binds
    @Singleton
    abstract fun bindMediaRepository(
        jellyfinMediaRepository: JellyfinMediaRepository
    ): MediaRepository

    @Binds
    @Singleton
    abstract fun bindUserDataRepository(
        jellyfinUserDataRepository: JellyfinUserDataRepository
    ): UserDataRepository

    @Binds
    @Singleton
    abstract fun bindPlaybackRepository(
        jellyfinPlaybackRepository: JellyfinPlaybackRepository
    ): PlaybackRepository

    @Binds
    @Singleton
    abstract fun bindJellyfinRepository(
        jellyfinRepositoryImpl: JellyfinRepositoryImpl
    ): JellyfinRepository

    @Binds
    @Singleton
    abstract fun bindDatabaseRepository(
        databaseRepositoryImpl: DatabaseRepositoryImpl
    ): DatabaseRepository

    @Binds
    @Singleton
    abstract fun bindPreferencesRepository(
        preferencesRepositoryImpl: PreferencesRepositoryImpl
    ): PreferencesRepository

    companion object {
        @Provides
        @Singleton
        fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
            return context.dataStore
        }

        @Provides
        @Singleton
        fun provideWebSocketManager(
            apiClient: ApiClient,
            mediaRepository: MediaRepository,
            userDataRepository: UserDataRepository
        ): JellyfinWebSocketManager {
            return JellyfinWebSocketManager(apiClient, mediaRepository, userDataRepository)
        }
    }
}