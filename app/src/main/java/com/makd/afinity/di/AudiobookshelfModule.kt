package com.makd.afinity.di

import com.makd.afinity.data.database.AfinityDatabase
import com.makd.afinity.data.database.dao.AbsDownloadDao
import com.makd.afinity.data.database.dao.AudiobookshelfDao
import com.makd.afinity.data.repository.AudiobookshelfRepository
import com.makd.afinity.data.repository.audiobookshelf.AbsDownloadRepository
import com.makd.afinity.data.repository.audiobookshelf.AbsDownloadRepositoryImpl
import com.makd.afinity.data.repository.audiobookshelf.AudiobookshelfRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AudiobookshelfModule {

    @Binds
    @Singleton
    abstract fun bindAudiobookshelfRepository(
        impl: AudiobookshelfRepositoryImpl
    ): AudiobookshelfRepository

    @Binds
    @Singleton
    abstract fun bindAbsDownloadRepository(
        impl: AbsDownloadRepositoryImpl
    ): AbsDownloadRepository

    companion object {
        @Provides
        @Singleton
        fun provideAudiobookshelfDao(database: AfinityDatabase): AudiobookshelfDao {
            return database.audiobookshelfDao()
        }

        @Provides
        @Singleton
        fun provideAbsDownloadDao(database: AfinityDatabase): AbsDownloadDao {
            return database.absDownloadDao()
        }
    }
}
