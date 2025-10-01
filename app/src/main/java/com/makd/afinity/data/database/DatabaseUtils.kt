package com.makd.afinity.data.database

import android.content.Context

object DatabaseUtils {

    fun getDatabaseSize(context: Context, databaseName: String = "afinity_database"): Long {
        val dbFile = context.getDatabasePath(databaseName)
        return if (dbFile.exists()) dbFile.length() else 0L
    }

    fun getDatabaseSizeFormatted(context: Context, databaseName: String = "afinity_database"): String {
        val sizeInBytes = getDatabaseSize(context, databaseName)
        return formatBytes(sizeInBytes)
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"

        val units = arrayOf("KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        var unitIndex = 0

        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }

        return "%.2f %s".format(size, units[unitIndex])
    }

    fun databaseExists(context: Context, databaseName: String = "afinity_database"): Boolean {
        return context.getDatabasePath(databaseName).exists()
    }

    fun deleteDatabase(context: Context, databaseName: String = "afinity_database"): Boolean {
        val dbFile = context.getDatabasePath(databaseName)
        return if (dbFile.exists()) {
            dbFile.delete()
        } else {
            false
        }
    }

    fun getDatabasePath(context: Context, databaseName: String = "afinity_database"): String {
        return context.getDatabasePath(databaseName).absolutePath
    }

    suspend fun vacuumDatabase(database: AfinityDatabase) {
        database.openHelper.writableDatabase.execSQL("VACUUM")
    }

    data class DatabaseStats(
        val sizeInBytes: Long,
        val formattedSize: String,
        val movieCount: Int,
        val showCount: Int,
        val episodeCount: Int,
        val downloadedItemCount: Int
    )

    suspend fun getDatabaseStats(context: Context, database: AfinityDatabase): DatabaseStats {
        val serverDao = database.serverDatabaseDao()
        return DatabaseStats(
            sizeInBytes = getDatabaseSize(context),
            formattedSize = getDatabaseSizeFormatted(context),
            movieCount = serverDao.getMovieCount(),
            showCount = serverDao.getShowCount(),
            episodeCount = serverDao.getEpisodeCount(),
            downloadedItemCount = serverDao.getDownloadedItemCount()
        )
    }
}