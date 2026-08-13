package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.makd.afinity.data.database.entities.DeletedItemEntity

@Dao
interface DeletedItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<DeletedItemEntity>)

    @Query("SELECT itemId FROM deleted_items") suspend fun getAllIds(): List<String>

    @Query("DELETE FROM deleted_items WHERE itemId IN (:itemIds)")
    suspend fun deleteByIds(itemIds: List<String>)

    @Query("DELETE FROM deleted_items WHERE deletedAt < :threshold")
    suspend fun deleteOlderThan(threshold: Long)

    @Query("DELETE FROM deleted_items") suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM deleted_items") suspend fun cachedEntryCount(): Int
}
