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

    val ALL_MIGRATIONS = arrayOf(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8
    )
}