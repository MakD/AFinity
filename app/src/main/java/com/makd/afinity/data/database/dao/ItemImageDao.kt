package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.makd.afinity.data.database.entities.ItemImageEntity
import java.util.UUID

@Dao
interface ItemImageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImage(image: ItemImageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImages(images: List<ItemImageEntity>)

    @Query("SELECT * FROM item_images WHERE itemId = :itemId")
    suspend fun getImage(itemId: UUID): ItemImageEntity?

    @Query("SELECT * FROM item_images WHERE itemId IN (:itemIds)")
    suspend fun getImages(itemIds: List<UUID>): List<ItemImageEntity>

    @Query("DELETE FROM item_images WHERE itemId = :itemId")
    suspend fun deleteImage(itemId: UUID)

    @Query("DELETE FROM item_images WHERE cachedAt < :expiryTime")
    suspend fun deleteExpiredImages(expiryTime: Long)

    @Query("SELECT COUNT(*) FROM item_images")
    suspend fun getImageCount(): Int

    @Query("DELETE FROM item_images")
    suspend fun deleteAllImages()
}