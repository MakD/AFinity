package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.makd.afinity.data.database.entities.ServerStorageCacheEntity

@Dao
interface ServerStorageDao {
    @Query("SELECT * FROM server_storage_cache WHERE serverId = :serverId")
    suspend fun getStorage(serverId: String): ServerStorageCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStorage(storage: ServerStorageCacheEntity)

    @Query("DELETE FROM server_storage_cache") suspend fun clearAll()
}
