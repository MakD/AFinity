package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.makd.afinity.data.database.entities.BookAnnotationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookAnnotationDao {

    @Query(
        "SELECT * FROM book_annotations WHERE serverId = :serverId AND userId = :userId AND itemId = :itemId ORDER BY progression ASC, createdAt ASC",
    )
    fun observe(serverId: String, userId: String, itemId: String): Flow<List<BookAnnotationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(annotation: BookAnnotationEntity)

    @Query("UPDATE book_annotations SET color = :color WHERE id = :id")
    suspend fun updateColor(id: String, color: String)

    @Query("DELETE FROM book_annotations WHERE id = :id") suspend fun delete(id: String)
}
