package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.makd.afinity.data.database.entities.ListCacheEntity
import java.util.UUID

@Dao
interface ListCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertListCache(listCache: ListCacheEntity)

    @Query("SELECT * FROM list_cache WHERE cacheKey = :cacheKey")
    suspend fun getListCache(cacheKey: String): ListCacheEntity?

    @Query("SELECT * FROM list_cache WHERE userId = :userId AND listType = :listType")
    suspend fun getListCacheByType(userId: UUID, listType: String): ListCacheEntity?

    @Query("DELETE FROM list_cache WHERE cacheKey = :cacheKey")
    suspend fun deleteListCache(cacheKey: String)

    @Query("DELETE FROM list_cache WHERE userId = :userId AND listType = :listType")
    suspend fun deleteListCacheByType(userId: UUID, listType: String)

    @Query("DELETE FROM list_cache WHERE userId = :userId")
    suspend fun deleteAllUserListCaches(userId: UUID)

    @Query("DELETE FROM list_cache WHERE expiresAt < :currentTime")
    suspend fun deleteExpiredListCaches(currentTime: Long)

    @Query("SELECT COUNT(*) FROM list_cache")
    suspend fun getListCacheCount(): Int

    @Query("DELETE FROM list_cache")
    suspend fun deleteAllListCaches()

    @Query("SELECT * FROM list_cache WHERE userId = :userId")
    suspend fun getAllUserListCaches(userId: UUID): List<ListCacheEntity>
}