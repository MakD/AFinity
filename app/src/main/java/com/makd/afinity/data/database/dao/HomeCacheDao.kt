package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.makd.afinity.data.database.entities.HomeCacheEntity

@Dao
interface HomeCacheDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: HomeCacheEntity)

    @Query("SELECT * FROM home_cache WHERE `key` = :key")
    suspend fun get(key: String): HomeCacheEntity?

    @Query("DELETE FROM home_cache WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM home_cache")
    suspend fun deleteAll()
}