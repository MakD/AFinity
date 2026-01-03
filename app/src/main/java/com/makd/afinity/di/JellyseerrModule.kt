package com.makd.afinity.di

import com.makd.afinity.data.database.AfinityDatabase
import com.makd.afinity.data.database.dao.JellyseerrDao
import com.makd.afinity.data.repository.JellyseerrRepository
import com.makd.afinity.data.repository.jellyseerr.JellyseerrRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class JellyseerrModule {

    @Binds
    @Singleton
    abstract fun bindJellyseerrRepository(
        impl: JellyseerrRepositoryImpl
    ): JellyseerrRepository

    companion object {
        @Provides
        @Singleton
        fun provideJellyseerrDao(database: AfinityDatabase): JellyseerrDao {
            return database.jellyseerrDao()
        }
    }
}
