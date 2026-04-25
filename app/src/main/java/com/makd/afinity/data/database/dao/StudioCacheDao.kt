package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.makd.afinity.data.database.entities.StudioCacheEntity

@Dao
interface StudioCacheDao {

    @Query("SELECT * FROM studio_cache WHERE serverId = :serverId AND userId = :userId ORDER BY position ASC")
    suspend fun getAllCachedStudios(serverId: String, userId: String): List<StudioCacheEntity>

    @Query("SELECT * FROM studio_cache WHERE studioId = :studioId AND serverId = :serverId AND userId = :userId")
    suspend fun getStudioById(studioId: String, serverId: String, userId: String): StudioCacheEntity?

    @Query(
        "SELECT COUNT(*) > 0 FROM studio_cache WHERE serverId = :serverId AND userId = :userId AND (cachedTimestamp + :ttlMillis) > :currentTime"
    )
    suspend fun isStudioCacheFresh(serverId: String, userId: String, ttlMillis: Long, currentTime: Long): Boolean

    @Query("SELECT MIN(cachedTimestamp) FROM studio_cache WHERE serverId = :serverId AND userId = :userId")
    suspend fun getOldestCacheTimestamp(serverId: String, userId: String): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudios(studios: List<StudioCacheEntity>)

    @Query("DELETE FROM studio_cache WHERE serverId = :serverId AND userId = :userId")
    suspend fun deleteAllStudios(serverId: String, userId: String)

    @Query("DELETE FROM studio_cache") suspend fun clearAll()

    @Transaction
    suspend fun replaceStudios(serverId: String, userId: String, studios: List<StudioCacheEntity>) {
        deleteAllStudios(serverId, userId)
        insertStudios(studios)
    }
}