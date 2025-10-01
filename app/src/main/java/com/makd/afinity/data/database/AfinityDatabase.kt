package com.makd.afinity.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import com.makd.afinity.data.database.dao.*
import com.makd.afinity.data.database.entities.*
import com.makd.afinity.data.models.server.Server
import com.makd.afinity.data.models.server.ServerAddress
import com.makd.afinity.data.models.user.User
import com.makd.afinity.data.models.user.AfinityUserDataDto

@Database(
    entities = [

        Server::class,
        ServerAddress::class,
        User::class,

        LibraryCacheEntity::class,

        AfinityMovieDto::class,
        AfinityShowDto::class,
        AfinitySeasonDto::class,
        AfinityEpisodeDto::class,

        AfinitySourceDto::class,
        AfinityMediaStreamDto::class,
        AfinityTrickplayInfoDto::class,
        AfinitySegmentDto::class,

        AfinityUserDataDto::class,
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(com.makd.afinity.data.database.TypeConverters::class)
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
                    .fallbackToDestructiveMigration() // Remove this in production
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