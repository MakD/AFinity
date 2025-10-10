package com.makd.afinity.di

import com.makd.afinity.data.database.dao.WatchlistDao
import com.makd.afinity.data.repository.JellyfinRepository
import com.makd.afinity.data.repository.watchlist.WatchlistRepository
import com.makd.afinity.data.repository.watchlist.WatchlistRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WatchlistModule {

    @Provides
    @Singleton
    fun provideWatchlistRepository(
        watchlistDao: WatchlistDao,
        jellyfinRepository: JellyfinRepository
    ): WatchlistRepository {
        return WatchlistRepositoryImpl(watchlistDao, jellyfinRepository)
    }
}