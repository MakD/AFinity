package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.makd.afinity.data.database.entities.ItemMetadataCacheEntity
import java.util.UUID

@Dao
interface ItemMetadataCacheDao {

    @Query("SELECT * FROM item_metadata_cache WHERE itemId = :itemId")
    suspend fun getMetadata(itemId: UUID): ItemMetadataCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateMetadata(metadata: ItemMetadataCacheEntity)

    @Query("DELETE FROM item_metadata_cache WHERE lastUpdated < :timestampLimit")
    suspend fun clearOldCache(timestampLimit: Long)

    @Query("DELETE FROM item_metadata_cache") suspend fun clearAll()
}
