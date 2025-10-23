package com.makd.afinity.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database migrations for schema changes between versions.
 * Replace fallbackToDestructiveMigration() with these migrations in production.
 */
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
            database.execSQL("""
            CREATE TABLE IF NOT EXISTS watchlist (
                itemId TEXT PRIMARY KEY NOT NULL,
                itemType TEXT NOT NULL,
                addedAt INTEGER NOT NULL
            )
        """.trimIndent())
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
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS item_images (
                    itemId TEXT PRIMARY KEY NOT NULL,
                    primaryImageUrl TEXT,
                    backdropImageUrl TEXT,
                    logoImageUrl TEXT,
                    thumbImageUrl TEXT,
                    primaryBlurHash TEXT,
                    backdropBlurHash TEXT,
                    logoBlurHash TEXT,
                    thumbBlurHash TEXT,
                    cachedAt INTEGER NOT NULL
                )
            """.trimIndent())
        }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
            CREATE TABLE IF NOT EXISTS item_metadata (
                itemId TEXT PRIMARY KEY NOT NULL,
                genres TEXT NOT NULL,
                tags TEXT,
                providerIds TEXT,
                externalUrls TEXT,
                trailer TEXT,
                tagline TEXT,
                cachedAt INTEGER NOT NULL
            )
        """.trimIndent())

            database.execSQL("""
            CREATE TABLE IF NOT EXISTS people (
                id TEXT PRIMARY KEY NOT NULL,
                name TEXT NOT NULL,
                role TEXT,
                imageUrl TEXT,
                imageBlurHash TEXT
            )
        """.trimIndent())

            database.execSQL("""
            CREATE TABLE IF NOT EXISTS item_people (
                itemId TEXT NOT NULL,
                personId TEXT NOT NULL,
                role TEXT,
                character TEXT,
                sortOrder INTEGER NOT NULL,
                PRIMARY KEY (itemId, personId),
                FOREIGN KEY (personId) REFERENCES people(id) ON DELETE CASCADE
            )
        """.trimIndent())

            database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_item_people_personId 
            ON item_people(personId)
        """.trimIndent())
        }
    }

    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("""
            CREATE TABLE IF NOT EXISTS list_cache (
                cacheKey TEXT PRIMARY KEY NOT NULL,
                userId TEXT NOT NULL,
                listType TEXT NOT NULL,
                itemIds TEXT NOT NULL,
                itemTypes TEXT NOT NULL,
                cachedAt INTEGER NOT NULL,
                expiresAt INTEGER NOT NULL,
                metadata TEXT
            )
        """.trimIndent())

            database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_list_cache_userId 
            ON list_cache(userId)
        """.trimIndent())

            database.execSQL("""
            CREATE INDEX IF NOT EXISTS index_list_cache_expiresAt 
            ON list_cache(expiresAt)
        """.trimIndent())
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
        MIGRATION_10_11
    )
}