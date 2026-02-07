package com.makd.afinity.di

import com.makd.afinity.data.repository.JellyfinRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.userdata.UserDataRepository
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
        userDataRepository: UserDataRepository,
        mediaRepository: MediaRepository,
        jellyfinRepository: JellyfinRepository,
    ): WatchlistRepository {
        return WatchlistRepositoryImpl(
            userDataRepository = userDataRepository,
            mediaRepository = mediaRepository,
            jellyfinRepository = jellyfinRepository,
        )
    }
}
