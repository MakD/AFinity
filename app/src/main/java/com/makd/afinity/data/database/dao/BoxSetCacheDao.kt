package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.makd.afinity.data.database.entities.BoxSetCacheEntity
import com.makd.afinity.data.database.entities.BoxSetCacheMetadata

@Dao
interface BoxSetCacheDao {

    @Query("SELECT * FROM boxset_cache WHERE itemId = :itemId")
    suspend fun getCacheEntry(itemId: String): BoxSetCacheEntity?

    @Query("SELECT * FROM boxset_cache") suspend fun getAllCacheEntries(): List<BoxSetCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCacheEntries(entries: List<BoxSetCacheEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCacheEntry(entry: BoxSetCacheEntity)

    @Query("DELETE FROM boxset_cache") suspend fun clearAllCacheEntries()

    @Query("DELETE FROM boxset_cache WHERE itemId = :itemId")
    suspend fun deleteCacheEntry(itemId: String)

    @Query("SELECT COUNT(*) FROM boxset_cache") suspend fun getCacheSize(): Int

    @Query("SELECT * FROM boxset_cache_metadata WHERE id = 1")
    suspend fun getMetadata(): BoxSetCacheMetadata?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: BoxSetCacheMetadata)

    @Query("DELETE FROM boxset_cache_metadata") suspend fun clearMetadata()

    @Query("DELETE FROM boxset_cache") suspend fun clearCache()

    @androidx.room.Transaction
    suspend fun clearAllCache() {
        clearAllCacheEntries()
        clearMetadata()
    }
}
