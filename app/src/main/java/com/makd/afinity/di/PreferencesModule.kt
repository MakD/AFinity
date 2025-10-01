package com.makd.afinity.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppPreferences

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class UserPreferences

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ServerPreferences

private val Context.appPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")
private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "user_preferences")
private val Context.serverPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "server_preferences")

@Module
@InstallIn(SingletonComponent::class)
object PreferencesModule {

    @Provides
    @Singleton
    @AppPreferences
    fun provideAppPreferencesDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> {
        return context.appPreferencesDataStore
    }

    @Provides
    @Singleton
    @UserPreferences
    fun provideUserPreferencesDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> {
        return context.userPreferencesDataStore
    }

    @Provides
    @Singleton
    @ServerPreferences
    fun provideServerPreferencesDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> {
        return context.serverPreferencesDataStore
    }
}