package com.makd.afinity.data.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object DatabaseMigrations {

    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE users ADD COLUMN primaryImageTag TEXT")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
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
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE movies ADD COLUMN dateCreated TEXT")

            db.execSQL("ALTER TABLE shows ADD COLUMN dateCreated TEXT")
            db.execSQL("ALTER TABLE shows ADD COLUMN dateLastContentAdded TEXT")
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE servers ADD COLUMN version TEXT")
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
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
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE movies ADD COLUMN images TEXT")

            db.execSQL("ALTER TABLE episodes ADD COLUMN images TEXT")
        }
    }

    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE shows ADD COLUMN images TEXT")
        }
    }

    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE movies ADD COLUMN genres TEXT")
            db.execSQL("ALTER TABLE movies ADD COLUMN tagline TEXT")
            db.execSQL("ALTER TABLE movies ADD COLUMN people TEXT")
        }
    }

    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE shows ADD COLUMN genres TEXT")
            db.execSQL("ALTER TABLE shows ADD COLUMN people TEXT")
        }
    }

    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE seasons ADD COLUMN images TEXT")
        }
    }

    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS watchlist")
        }
    }

    val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // This migration is left empty as there were no schema changes
        }
    }

    val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS genre_cache (
                    genreName TEXT PRIMARY KEY NOT NULL,
                    lastFetchedTimestamp INTEGER NOT NULL,
                    movieCount INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent()
            )

            db.execSQL(
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
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS show_genre_cache (
                    genreName TEXT PRIMARY KEY NOT NULL,
                    lastFetchedTimestamp INTEGER NOT NULL,
                    showCount INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent()
            )

            db.execSQL(
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
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
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

    val MIGRATION_21_22 = object : Migration(21, 22) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE jellyseerr_requests ADD COLUMN mediaTitle TEXT")
            db.execSQL("ALTER TABLE jellyseerr_requests ADD COLUMN mediaName TEXT")
            db.execSQL("ALTER TABLE jellyseerr_requests ADD COLUMN mediaBackdropPath TEXT")
            db.execSQL("ALTER TABLE jellyseerr_requests ADD COLUMN mediaReleaseDate TEXT")
            db.execSQL("ALTER TABLE jellyseerr_requests ADD COLUMN mediaFirstAirDate TEXT")
            db.execSQL("ALTER TABLE jellyseerr_requests ADD COLUMN mediaStatus INTEGER")
        }
    }

    val MIGRATION_22_23 = object : Migration(22, 23) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val defaultServerId = "default-server-migration"

            db.execSQL("DROP TABLE IF EXISTS servers_new")
            db.execSQL(
                """
                CREATE TABLE servers_new (
                    id TEXT PRIMARY KEY NOT NULL,
                    name TEXT NOT NULL,
                    version TEXT,
                    address TEXT NOT NULL
                )
            """.trimIndent()
            )

            db.execSQL(
                """
                INSERT INTO servers_new (id, name, version, address)
                SELECT id, name, version, '$defaultServerId'
                FROM servers
            """.trimIndent()
            )

            db.execSQL("DROP TABLE servers")
            db.execSQL("ALTER TABLE servers_new RENAME TO servers")

            db.execSQL("DROP TABLE IF EXISTS movies_new")
            db.execSQL(
                """
                CREATE TABLE movies_new (
                    id TEXT PRIMARY KEY NOT NULL,
                    serverId TEXT NOT NULL,
                    name TEXT NOT NULL,
                    originalTitle TEXT,
                    overview TEXT NOT NULL,
                    runtimeTicks INTEGER NOT NULL,
                    premiereDate TEXT,
                    dateCreated TEXT,
                    communityRating REAL,
                    officialRating TEXT,
                    criticRating REAL,
                    status TEXT NOT NULL,
                    productionYear INTEGER,
                    endDate TEXT,
                    chapters TEXT,
                    images TEXT,
                    genres TEXT,
                    tagline TEXT,
                    people TEXT
                )
            """.trimIndent()
            )

            db.execSQL(
                """
                INSERT INTO movies_new
                SELECT id, COALESCE(serverId, '$defaultServerId'), name, originalTitle, overview,
                       runtimeTicks, premiereDate, dateCreated, communityRating, officialRating,
                       criticRating, status, productionYear, endDate, chapters, images, genres, tagline, people
                FROM movies
            """.trimIndent()
            )

            db.execSQL("DROP TABLE movies")
            db.execSQL("ALTER TABLE movies_new RENAME TO movies")

            db.execSQL("DROP TABLE IF EXISTS shows_new")
            db.execSQL(
                """
                CREATE TABLE shows_new (
                    id TEXT PRIMARY KEY NOT NULL,
                    serverId TEXT NOT NULL,
                    name TEXT NOT NULL,
                    originalTitle TEXT,
                    overview TEXT NOT NULL,
                    runtimeTicks INTEGER NOT NULL,
                    communityRating REAL,
                    officialRating TEXT,
                    status TEXT NOT NULL,
                    productionYear INTEGER,
                    premiereDate TEXT,
                    dateCreated TEXT,
                    dateLastContentAdded TEXT,
                    endDate TEXT,
                    images TEXT,
                    genres TEXT,
                    people TEXT
                )
            """.trimIndent()
            )

            db.execSQL(
                """
                INSERT INTO shows_new
                SELECT id, COALESCE(serverId, '$defaultServerId'), name, originalTitle, overview,
                       runtimeTicks, communityRating, officialRating, status, productionYear,
                       premiereDate, dateCreated, dateLastContentAdded, endDate, images, genres, people
                FROM shows
            """.trimIndent()
            )

            db.execSQL("DROP TABLE shows")
            db.execSQL("ALTER TABLE shows_new RENAME TO shows")

            db.execSQL("DROP INDEX IF EXISTS index_seasons_seriesId")
            db.execSQL("DROP TABLE IF EXISTS seasons_new")
            db.execSQL(
                """
                CREATE TABLE seasons_new (
                    id TEXT PRIMARY KEY NOT NULL,
                    serverId TEXT NOT NULL,
                    seriesId TEXT NOT NULL,
                    name TEXT NOT NULL,
                    seriesName TEXT NOT NULL,
                    overview TEXT NOT NULL,
                    indexNumber INTEGER NOT NULL,
                    images TEXT,
                    FOREIGN KEY (seriesId) REFERENCES shows(id) ON DELETE CASCADE
                )
            """.trimIndent()
            )

            db.execSQL("CREATE INDEX index_seasons_seriesId ON seasons_new(seriesId)")

            db.execSQL(
                """
                INSERT INTO seasons_new
                SELECT id, '$defaultServerId', seriesId, name, seriesName, overview, indexNumber, images
                FROM seasons
            """.trimIndent()
            )

            db.execSQL("DROP TABLE seasons")
            db.execSQL("ALTER TABLE seasons_new RENAME TO seasons")

            db.execSQL("DROP INDEX IF EXISTS index_episodes_seasonId")
            db.execSQL("DROP INDEX IF EXISTS index_episodes_seriesId")
            db.execSQL("DROP TABLE IF EXISTS episodes_new")
            db.execSQL(
                """
                CREATE TABLE episodes_new (
                    id TEXT PRIMARY KEY NOT NULL,
                    serverId TEXT NOT NULL,
                    seasonId TEXT NOT NULL,
                    seriesId TEXT NOT NULL,
                    name TEXT NOT NULL,
                    seriesName TEXT NOT NULL,
                    overview TEXT NOT NULL,
                    indexNumber INTEGER NOT NULL,
                    indexNumberEnd INTEGER,
                    parentIndexNumber INTEGER NOT NULL,
                    runtimeTicks INTEGER NOT NULL,
                    premiereDate TEXT,
                    communityRating REAL,
                    chapters TEXT,
                    images TEXT,
                    FOREIGN KEY (seasonId) REFERENCES seasons(id) ON DELETE CASCADE,
                    FOREIGN KEY (seriesId) REFERENCES shows(id) ON DELETE CASCADE
                )
            """.trimIndent()
            )

            db.execSQL("CREATE INDEX index_episodes_seasonId ON episodes_new(seasonId)")
            db.execSQL("CREATE INDEX index_episodes_seriesId ON episodes_new(seriesId)")

            db.execSQL(
                """
                INSERT INTO episodes_new
                SELECT id, COALESCE(serverId, '$defaultServerId'), seasonId, seriesId, name, seriesName,
                       overview, indexNumber, indexNumberEnd, parentIndexNumber, runtimeTicks,
                       premiereDate, communityRating, chapters, images
                FROM episodes
            """.trimIndent()
            )

            db.execSQL("DROP TABLE episodes")
            db.execSQL("ALTER TABLE episodes_new RENAME TO episodes")

            db.execSQL("DROP TABLE IF EXISTS userdata_new")
            db.execSQL(
                """
                CREATE TABLE userdata_new (
                    userId TEXT NOT NULL,
                    itemId TEXT NOT NULL,
                    serverId TEXT NOT NULL,
                    played INTEGER NOT NULL,
                    favorite INTEGER NOT NULL,
                    playbackPositionTicks INTEGER NOT NULL,
                    toBeSynced INTEGER NOT NULL,
                    PRIMARY KEY (userId, itemId, serverId)
                )
            """.trimIndent()
            )

            db.execSQL(
                """
                INSERT INTO userdata_new
                SELECT userId, itemId, '$defaultServerId', played, favorite, playbackPositionTicks, toBeSynced
                FROM userdata
            """.trimIndent()
            )

            db.execSQL("DROP TABLE userdata")
            db.execSQL("ALTER TABLE userdata_new RENAME TO userdata")
        }
    }

    val MIGRATION_23_24 = object : Migration(23, 24) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS jellyseerr_config")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS jellyseerr_config (
                    jellyfinServerId TEXT NOT NULL,
                    jellyfinUserId TEXT NOT NULL,
                    serverUrl TEXT NOT NULL,
                    isLoggedIn INTEGER NOT NULL,
                    username TEXT,
                    userId INTEGER,
                    permissions INTEGER,
                    PRIMARY KEY(jellyfinServerId, jellyfinUserId)
                )
            """.trimIndent()
            )

            db.execSQL("DROP TABLE IF EXISTS jellyseerr_requests")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS jellyseerr_requests (
                    id INTEGER NOT NULL,
                    jellyfinServerId TEXT NOT NULL,
                    jellyfinUserId TEXT NOT NULL,
                    status INTEGER NOT NULL,
                    mediaType TEXT NOT NULL,
                    tmdbId INTEGER,
                    tvdbId INTEGER,
                    title TEXT NOT NULL,
                    posterPath TEXT,
                    requestedAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL,
                    requestedByName TEXT,
                    requestedByAvatar TEXT,
                    cachedAt INTEGER NOT NULL,
                    mediaTitle TEXT,
                    mediaName TEXT,
                    mediaBackdropPath TEXT,
                    mediaReleaseDate TEXT,
                    mediaFirstAirDate TEXT,
                    mediaStatus INTEGER,
                    PRIMARY KEY(id, jellyfinServerId, jellyfinUserId)
                )
            """.trimIndent()
            )
        }
    }

    val MIGRATION_24_25 = object : Migration(24, 25) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE downloads ADD COLUMN serverId TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE downloads ADD COLUMN userId TEXT NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000'")
        }
    }

    val MIGRATION_25_26 = object : Migration(25, 26) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS audiobookshelf_config (
                    jellyfinServerId TEXT NOT NULL,
                    jellyfinUserId TEXT NOT NULL,
                    serverUrl TEXT NOT NULL,
                    absUserId TEXT NOT NULL,
                    username TEXT NOT NULL,
                    isLoggedIn INTEGER NOT NULL,
                    lastSync INTEGER NOT NULL,
                    PRIMARY KEY(jellyfinServerId, jellyfinUserId)
                )
            """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS audiobookshelf_libraries (
                    id TEXT NOT NULL,
                    jellyfinServerId TEXT NOT NULL,
                    jellyfinUserId TEXT NOT NULL,
                    name TEXT NOT NULL,
                    mediaType TEXT NOT NULL,
                    icon TEXT,
                    displayOrder INTEGER NOT NULL,
                    totalItems INTEGER NOT NULL,
                    totalDuration REAL,
                    lastUpdated INTEGER NOT NULL,
                    cachedAt INTEGER NOT NULL,
                    PRIMARY KEY(id, jellyfinServerId, jellyfinUserId)
                )
            """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS audiobookshelf_items (
                    id TEXT NOT NULL,
                    jellyfinServerId TEXT NOT NULL,
                    jellyfinUserId TEXT NOT NULL,
                    libraryId TEXT NOT NULL,
                    title TEXT NOT NULL,
                    authorName TEXT,
                    narratorName TEXT,
                    seriesName TEXT,
                    seriesSequence TEXT,
                    mediaType TEXT NOT NULL,
                    duration REAL,
                    coverUrl TEXT,
                    description TEXT,
                    publishedYear TEXT,
                    genres TEXT,
                    numTracks INTEGER,
                    numChapters INTEGER,
                    addedAt INTEGER,
                    updatedAt INTEGER,
                    cachedAt INTEGER NOT NULL,
                    PRIMARY KEY(id, jellyfinServerId, jellyfinUserId)
                )
            """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS audiobookshelf_progress (
                    id TEXT NOT NULL,
                    jellyfinServerId TEXT NOT NULL,
                    jellyfinUserId TEXT NOT NULL,
                    libraryItemId TEXT NOT NULL,
                    episodeId TEXT,
                    currentTime REAL NOT NULL,
                    duration REAL NOT NULL,
                    progress REAL NOT NULL,
                    isFinished INTEGER NOT NULL,
                    lastUpdate INTEGER NOT NULL,
                    startedAt INTEGER NOT NULL,
                    finishedAt INTEGER,
                    pendingSync INTEGER NOT NULL,
                    PRIMARY KEY(id, jellyfinServerId, jellyfinUserId)
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
        MIGRATION_18_19,
        MIGRATION_21_22,
        MIGRATION_22_23,
        MIGRATION_23_24,
        MIGRATION_24_25,
        MIGRATION_25_26
    )
}