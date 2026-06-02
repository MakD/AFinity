package com.makd.afinity.di

import com.makd.afinity.data.repository.admin.AdminRepository
import com.makd.afinity.data.repository.admin.JellyfinAdminRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AdminModule {

    @Binds
    @Singleton
    abstract fun bindAdminRepository(
        jellyfinAdminRepository: JellyfinAdminRepository,
    ): AdminRepository
}