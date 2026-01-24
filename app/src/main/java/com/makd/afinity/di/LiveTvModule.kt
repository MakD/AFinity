package com.makd.afinity.di

import com.makd.afinity.data.repository.livetv.JellyfinLiveTvRepository
import com.makd.afinity.data.repository.livetv.LiveTvRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LiveTvModule {

    @Binds
    @Singleton
    abstract fun bindLiveTvRepository(
        jellyfinLiveTvRepository: JellyfinLiveTvRepository
    ): LiveTvRepository
}