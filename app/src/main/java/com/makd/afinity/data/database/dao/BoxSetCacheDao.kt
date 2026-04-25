package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.makd.afinity.data.database.entities.BoxSetCacheEntity
import com.makd.afinity.data.database.entities.BoxSetCacheMetadata

@Dao
interface BoxSetCacheDao {

    @Query("SELECT * FROM boxset_cache WHERE itemId = :itemId AND serverId = :serverId AND userId = :userId")
    suspend fun getCacheEntry(itemId: String, serverId: String, userId: String): BoxSetCacheEntity?

    @Query("SELECT * FROM boxset_cache WHERE serverId = :serverId AND userId = :userId")
    suspend fun getAllCacheEntries(serverId: String, userId: String): List<BoxSetCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCacheEntries(entries: List<BoxSetCacheEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCacheEntry(entry: BoxSetCacheEntity)

    @Query("DELETE FROM boxset_cache WHERE serverId = :serverId AND userId = :userId")
    suspend fun clearAllCacheEntries(serverId: String, userId: String)

    @Query("DELETE FROM boxset_cache WHERE itemId = :itemId AND serverId = :serverId AND userId = :userId")
    suspend fun deleteCacheEntry(itemId: String, serverId: String, userId: String)

    @Query("SELECT COUNT(*) FROM boxset_cache WHERE serverId = :serverId AND userId = :userId")
    suspend fun getCacheSize(serverId: String, userId: String): Int

    @Query("SELECT * FROM boxset_cache_metadata WHERE serverId = :serverId AND userId = :userId")
    suspend fun getMetadata(serverId: String, userId: String): BoxSetCacheMetadata?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: BoxSetCacheMetadata)

    @Query("DELETE FROM boxset_cache_metadata WHERE serverId = :serverId AND userId = :userId")
    suspend fun clearMetadata(serverId: String, userId: String)

    @Query("DELETE FROM boxset_cache") suspend fun clearAllCache()

    @androidx.room.Transaction
    suspend fun clearAllCache(serverId: String, userId: String) {
        clearAllCacheEntries(serverId, userId)
        clearMetadata(serverId, userId)
    }
}