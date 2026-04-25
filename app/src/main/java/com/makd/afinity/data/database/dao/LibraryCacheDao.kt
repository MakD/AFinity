package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.makd.afinity.data.database.entities.LibraryCacheEntity

@Dao
interface LibraryCacheDao {
    @Query(
        """
        SELECT * FROM library_cache
        WHERE libraryId = :libraryId
        AND serverId = :serverId
        AND userId = :userId
        AND sortBy = :sortBy
        AND sortDescending = :sortDescending
        AND filterType = :filterType
        ORDER BY itemName ASC
    """
    )
    suspend fun getCachedItems(
        libraryId: String,
        serverId: String,
        userId: String,
        sortBy: String,
        sortDescending: Boolean,
        filterType: String,
    ): List<LibraryCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedItems(items: List<LibraryCacheEntity>)

    @Query("DELETE FROM library_cache WHERE libraryId = :libraryId AND serverId = :serverId AND userId = :userId")
    suspend fun clearCacheForLibrary(libraryId: String, serverId: String, userId: String)

    @Query("DELETE FROM library_cache WHERE cacheTimestamp < :expiredTimestamp")
    suspend fun clearExpiredCache(expiredTimestamp: Long)
}