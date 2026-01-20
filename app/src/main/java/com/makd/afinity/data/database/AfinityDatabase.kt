package com.makd.afinity.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.makd.afinity.data.database.dao.BoxSetCacheDao
import com.makd.afinity.data.database.dao.EpisodeDao
import com.makd.afinity.data.database.dao.GenreCacheDao
import com.makd.afinity.data.database.dao.JellyseerrDao
import com.makd.afinity.data.database.dao.LibraryCacheDao
import com.makd.afinity.data.database.dao.MediaStreamDao
import com.makd.afinity.data.database.dao.MovieDao
import com.makd.afinity.data.database.dao.MovieSectionDao
import com.makd.afinity.data.database.dao.PersonSectionDao
import com.makd.afinity.data.database.dao.SeasonDao
import com.makd.afinity.data.database.dao.ServerAddressDao
import com.makd.afinity.data.database.dao.ServerDao
import com.makd.afinity.data.database.dao.ServerDatabaseDao
import com.makd.afinity.data.database.dao.ShowDao
import com.makd.afinity.data.database.dao.SourceDao
import com.makd.afinity.data.database.dao.StudioCacheDao
import com.makd.afinity.data.database.dao.TopPeopleDao
import com.makd.afinity.data.database.dao.UserDao
import com.makd.afinity.data.database.dao.UserDataDao
import com.makd.afinity.data.database.entities.AfinityEpisodeDto
import com.makd.afinity.data.database.entities.AfinityMediaStreamDto
import com.makd.afinity.data.database.entities.AfinityMovieDto
import com.makd.afinity.data.database.entities.AfinitySeasonDto
import com.makd.afinity.data.database.entities.AfinitySegmentDto
import com.makd.afinity.data.database.entities.AfinityShowDto
import com.makd.afinity.data.database.entities.AfinitySourceDto
import com.makd.afinity.data.database.entities.AfinityTrickplayInfoDto
import com.makd.afinity.data.database.entities.BoxSetCacheEntity
import com.makd.afinity.data.database.entities.BoxSetCacheMetadata
import com.makd.afinity.data.database.entities.DownloadDto
import com.makd.afinity.data.database.entities.GenreCacheEntity
import com.makd.afinity.data.database.entities.GenreMovieCacheEntity
import com.makd.afinity.data.database.entities.GenreShowCacheEntity
import com.makd.afinity.data.database.entities.JellyseerrConfigEntity
import com.makd.afinity.data.database.entities.JellyseerrRequestEntity
import com.makd.afinity.data.database.entities.LibraryCacheEntity
import com.makd.afinity.data.database.entities.MovieSectionCacheEntity
import com.makd.afinity.data.database.entities.PersonSectionCacheEntity
import com.makd.afinity.data.database.entities.ShowGenreCacheEntity
import com.makd.afinity.data.database.entities.StudioCacheEntity
import com.makd.afinity.data.database.entities.TopPeopleCacheEntity
import com.makd.afinity.data.models.server.Server
import com.makd.afinity.data.models.server.ServerAddress
import com.makd.afinity.data.models.user.AfinityUserDataDto
import com.makd.afinity.data.models.user.User

@Database(
    entities = [

        Server::class,
        ServerAddress::class,
        User::class,

        LibraryCacheEntity::class,
        BoxSetCacheEntity::class,
        BoxSetCacheMetadata::class,

        GenreCacheEntity::class,
        GenreMovieCacheEntity::class,

        ShowGenreCacheEntity::class,
        GenreShowCacheEntity::class,

        StudioCacheEntity::class,

        TopPeopleCacheEntity::class,
        PersonSectionCacheEntity::class,
        MovieSectionCacheEntity::class,

        AfinityMovieDto::class,
        AfinityShowDto::class,
        AfinitySeasonDto::class,
        AfinityEpisodeDto::class,

        AfinitySourceDto::class,
        AfinityMediaStreamDto::class,
        AfinityTrickplayInfoDto::class,
        AfinitySegmentDto::class,

        AfinityUserDataDto::class,

        DownloadDto::class,

        JellyseerrRequestEntity::class,
        JellyseerrConfigEntity::class,
    ],
    version = 24,
    exportSchema = false
)
@TypeConverters(AfinityTypeConverters::class)
abstract class AfinityDatabase : RoomDatabase() {

    abstract fun serverDao(): ServerDao
    abstract fun serverAddressDao(): ServerAddressDao
    abstract fun userDao(): UserDao

    abstract fun movieDao(): MovieDao
    abstract fun showDao(): ShowDao
    abstract fun seasonDao(): SeasonDao
    abstract fun episodeDao(): EpisodeDao

    abstract fun sourceDao(): SourceDao
    abstract fun mediaStreamDao(): MediaStreamDao
    abstract fun userDataDao(): UserDataDao

    abstract fun serverDatabaseDao(): ServerDatabaseDao

    abstract fun libraryCacheDao(): LibraryCacheDao
    abstract fun boxSetCacheDao(): BoxSetCacheDao
    abstract fun genreCacheDao(): GenreCacheDao
    abstract fun studioCacheDao(): StudioCacheDao
    abstract fun topPeopleDao(): TopPeopleDao
    abstract fun personSectionDao(): PersonSectionDao
    abstract fun movieSectionDao(): MovieSectionDao
    abstract fun jellyseerrDao(): JellyseerrDao

    companion object {
        @Volatile
        private var INSTANCE: AfinityDatabase? = null

        fun getDatabase(context: Context): AfinityDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AfinityDatabase::class.java,
                    "afinity_database"
                )
                    .addMigrations(*DatabaseMigrations.ALL_MIGRATIONS)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun getProductionDatabase(context: Context): AfinityDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AfinityDatabase::class.java,
                    "afinity_database"
                )
                    .addMigrations(*DatabaseMigrations.ALL_MIGRATIONS)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun createInMemoryDatabase(context: Context): AfinityDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                AfinityDatabase::class.java
            ).build()
        }

        fun clearInstance() {
            INSTANCE = null
        }
    }
}