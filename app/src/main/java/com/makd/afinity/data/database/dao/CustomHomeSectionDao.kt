package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.makd.afinity.data.database.entities.CustomHomeSectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomHomeSectionDao {

    @Query(
        "SELECT * FROM custom_home_sections WHERE sessionKey = :sessionKey ORDER BY position ASC"
    )
    fun observeForSession(sessionKey: String): Flow<List<CustomHomeSectionEntity>>

    @Query(
        "SELECT * FROM custom_home_sections WHERE sessionKey = :sessionKey ORDER BY position ASC"
    )
    suspend fun getForSession(sessionKey: String): List<CustomHomeSectionEntity>

    @Query("SELECT * FROM custom_home_sections WHERE id = :id")
    suspend fun getById(id: String): CustomHomeSectionEntity?

    @Query("SELECT COUNT(*) FROM custom_home_sections WHERE sessionKey = :sessionKey")
    suspend fun countForSession(sessionKey: String): Int

    @Query(
        "SELECT COALESCE(MAX(position), -1) FROM custom_home_sections WHERE sessionKey = :sessionKey"
    )
    suspend fun maxPosition(sessionKey: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CustomHomeSectionEntity)

    @Delete suspend fun delete(entity: CustomHomeSectionEntity)

    @Query("DELETE FROM custom_home_sections WHERE id = :id") suspend fun deleteById(id: String)

    @Query("DELETE FROM custom_home_sections WHERE sessionKey = :sessionKey")
    suspend fun deleteForSession(sessionKey: String)

    @Query("UPDATE custom_home_sections SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: String, position: Int)

    @Transaction
    suspend fun applyOrder(orderedIds: List<String>) {
        orderedIds.forEachIndexed { index, id -> updatePosition(id, index) }
    }
}
