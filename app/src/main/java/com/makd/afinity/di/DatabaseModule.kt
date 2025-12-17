package com.makd.afinity.di

import android.content.Context
import androidx.room.Room
import com.makd.afinity.data.database.AfinityDatabase
import com.makd.afinity.data.database.DatabaseMigrations
import com.makd.afinity.data.database.dao.EpisodeDao
import com.makd.afinity.data.database.dao.LibraryCacheDao
import com.makd.afinity.data.database.dao.MediaStreamDao
import com.makd.afinity.data.database.dao.MovieDao
import com.makd.afinity.data.database.dao.SeasonDao
import com.makd.afinity.data.database.dao.ServerAddressDao
import com.makd.afinity.data.database.dao.ServerDao
import com.makd.afinity.data.database.dao.ServerDatabaseDao
import com.makd.afinity.data.database.dao.ShowDao
import com.makd.afinity.data.database.dao.SourceDao
import com.makd.afinity.data.database.dao.UserDao
import com.makd.afinity.data.database.dao.UserDataDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAfinityDatabase(
        @ApplicationContext context: Context
    ): AfinityDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AfinityDatabase::class.java,
            "afinity_database"
        )
            .addMigrations(*DatabaseMigrations.ALL_MIGRATIONS)
            .fallbackToDestructiveMigration() // TODO Remove in production
            .build()
    }

    @Provides
    fun provideServerDao(database: AfinityDatabase): ServerDao {
        return database.serverDao()
    }

    @Provides
    fun provideServerAddressDao(database: AfinityDatabase): ServerAddressDao {
        return database.serverAddressDao()
    }

    @Provides
    fun provideUserDao(database: AfinityDatabase): UserDao {
        return database.userDao()
    }

    @Provides
    fun provideMovieDao(database: AfinityDatabase): MovieDao {
        return database.movieDao()
    }

    @Provides
    fun provideShowDao(database: AfinityDatabase): ShowDao {
        return database.showDao()
    }

    @Provides
    fun provideSeasonDao(database: AfinityDatabase): SeasonDao {
        return database.seasonDao()
    }

    @Provides
    fun provideEpisodeDao(database: AfinityDatabase): EpisodeDao {
        return database.episodeDao()
    }

    @Provides
    fun provideSourceDao(database: AfinityDatabase): SourceDao {
        return database.sourceDao()
    }

    @Provides
    fun provideMediaStreamDao(database: AfinityDatabase): MediaStreamDao {
        return database.mediaStreamDao()
    }

    @Provides
    fun provideUserDataDao(database: AfinityDatabase): UserDataDao {
        return database.userDataDao()
    }

    @Provides
    fun provideServerDatabaseDao(database: AfinityDatabase): ServerDatabaseDao {
        return database.serverDatabaseDao()
    }

    @Provides
    fun provideLibraryCacheDao(database: AfinityDatabase): LibraryCacheDao {
        return database.libraryCacheDao()
    }

}