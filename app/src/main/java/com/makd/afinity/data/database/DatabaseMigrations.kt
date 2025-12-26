package com.makd.afinity.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE users ADD COLUMN primaryImageTag TEXT")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
            CREATE TABLE IF NOT EXISTS watchlist (
                itemId TEXT PRIMARY KEY NOT NULL,
                itemType TEXT NOT NULL,
                addedAt INTEGER NOT NULL
            )
        """.trimIndent()
            )
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE movies ADD COLUMN dateCreated TEXT")

            database.execSQL("ALTER TABLE shows ADD COLUMN dateCreated TEXT")
            database.execSQL("ALTER TABLE shows ADD COLUMN dateLastContentAdded TEXT")
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE servers ADD COLUMN version TEXT")
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS downloads (
                    id TEXT PRIMARY KEY NOT NULL,
                    itemId TEXT NOT NULL,
                    itemName TEXT NOT NULL,
                    itemType TEXT NOT NULL,
                    sourceId TEXT NOT NULL,
                    sourceName TEXT NOT NULL,
                    status TEXT NOT NULL,
                    progress REAL NOT NULL,
                    bytesDownloaded INTEGER NOT NULL,
                    totalBytes INTEGER NOT NULL,
                    filePath TEXT,
                    error TEXT,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
            """.trimIndent()
            )
        }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE movies ADD COLUMN images TEXT")

            database.execSQL("ALTER TABLE episodes ADD COLUMN images TEXT")
        }
    }

    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE shows ADD COLUMN images TEXT")
        }
    }

    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE movies ADD COLUMN genres TEXT")
            database.execSQL("ALTER TABLE movies ADD COLUMN tagline TEXT")
            database.execSQL("ALTER TABLE movies ADD COLUMN people TEXT")
        }
    }

    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE shows ADD COLUMN genres TEXT")
            database.execSQL("ALTER TABLE shows ADD COLUMN people TEXT")
        }
    }

    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE seasons ADD COLUMN images TEXT")
        }
    }

    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("DROP TABLE IF EXISTS watchlist")
        }
    }

    val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // This migration is left empty as there were no schema changes
        }
    }

    val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS genre_cache (
                    genreName TEXT PRIMARY KEY NOT NULL,
                    lastFetchedTimestamp INTEGER NOT NULL,
                    movieCount INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent()
            )

            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS genre_movie_cache (
                    genreName TEXT NOT NULL,
                    movieId TEXT NOT NULL,
                    movieData TEXT NOT NULL,
                    position INTEGER NOT NULL,
                    cachedTimestamp INTEGER NOT NULL,
                    PRIMARY KEY (genreName, movieId)
                )
            """.trimIndent()
            )
        }
    }

    val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS show_genre_cache (
                    genreName TEXT PRIMARY KEY NOT NULL,
                    lastFetchedTimestamp INTEGER NOT NULL,
                    showCount INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent()
            )

            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS genre_show_cache (
                    genreName TEXT NOT NULL,
                    showId TEXT NOT NULL,
                    showData TEXT NOT NULL,
                    position INTEGER NOT NULL,
                    cachedTimestamp INTEGER NOT NULL,
                    PRIMARY KEY (genreName, showId)
                )
            """.trimIndent()
            )
        }
    }

    val MIGRATION_18_19 = object : Migration(18, 19) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS studio_cache (
                    studioId TEXT PRIMARY KEY NOT NULL,
                    studioData TEXT NOT NULL,
                    position INTEGER NOT NULL,
                    cachedTimestamp INTEGER NOT NULL
                )
            """.trimIndent()
            )
        }
    }

    val ALL_MIGRATIONS = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11,
        MIGRATION_11_12,
        MIGRATION_12_13,
        MIGRATION_13_14,
        MIGRATION_14_15,
        MIGRATION_15_16,
        MIGRATION_16_17,
        MIGRATION_17_18,
        MIGRATION_18_19
    )
}