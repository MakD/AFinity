package com.makd.afinity.di

import com.makd.afinity.data.repository.segments.JellyfinSegmentsRepository
import com.makd.afinity.data.repository.segments.SegmentsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SegmentsModule {

    @Binds
    @Singleton
    abstract fun bindSegmentsRepository(
        jellyfinSegmentsRepository: JellyfinSegmentsRepository
    ): SegmentsRepository
}
