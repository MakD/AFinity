package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.makd.afinity.data.database.entities.ItemMetadataEntity
import java.util.UUID

@Dao
interface ItemMetadataDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(metadata: ItemMetadataEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadataList(metadata: List<ItemMetadataEntity>)

    @Query("SELECT * FROM item_metadata WHERE itemId = :itemId")
    suspend fun getMetadata(itemId: UUID): ItemMetadataEntity?

    @Query("SELECT * FROM item_metadata WHERE itemId IN (:itemIds)")
    suspend fun getMetadataList(itemIds: List<UUID>): List<ItemMetadataEntity>

    @Query("DELETE FROM item_metadata WHERE itemId = :itemId")
    suspend fun deleteMetadata(itemId: UUID)

    @Query("DELETE FROM item_metadata WHERE cachedAt < :expiryTime")
    suspend fun deleteExpiredMetadata(expiryTime: Long)

    @Query("SELECT COUNT(*) FROM item_metadata")
    suspend fun getMetadataCount(): Int

    @Query("DELETE FROM item_metadata")
    suspend fun deleteAllMetadata()
}