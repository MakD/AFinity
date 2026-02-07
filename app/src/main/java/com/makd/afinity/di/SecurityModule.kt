package com.makd.afinity.di

import com.makd.afinity.data.repository.SecurePreferencesRepository
import com.makd.afinity.data.repository.impl.SecurePreferencesRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SecurityModule {

    @Binds
    @Singleton
    abstract fun bindSecurePreferencesRepository(
        securePreferencesRepositoryImpl: SecurePreferencesRepositoryImpl
    ): SecurePreferencesRepository
}
