package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.makd.afinity.data.database.entities.StudioCacheEntity

@Dao
interface StudioCacheDao {

    @Query("SELECT * FROM studio_cache ORDER BY position ASC")
    suspend fun getAllCachedStudios(): List<StudioCacheEntity>

    @Query("SELECT * FROM studio_cache WHERE studioId = :studioId")
    suspend fun getStudioById(studioId: String): StudioCacheEntity?

    @Query(
        "SELECT COUNT(*) > 0 FROM studio_cache WHERE (cachedTimestamp + :ttlMillis) > :currentTime"
    )
    suspend fun isStudioCacheFresh(ttlMillis: Long, currentTime: Long): Boolean

    @Query("SELECT MIN(cachedTimestamp) FROM studio_cache")
    suspend fun getOldestCacheTimestamp(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudios(studios: List<StudioCacheEntity>)

    @Query("DELETE FROM studio_cache") suspend fun deleteAllStudios()

    @Transaction
    suspend fun replaceStudios(studios: List<StudioCacheEntity>) {
        deleteAllStudios()
        insertStudios(studios)
    }
}
