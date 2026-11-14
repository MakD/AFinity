package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.makd.afinity.data.database.entities.OfflinePlayEntity
import java.util.UUID

@Dao
interface OfflinePlayDao {

    @Insert suspend fun insert(play: OfflinePlayEntity)

    @Query(
        "SELECT * FROM offline_plays WHERE userId = :userId AND serverId = :serverId ORDER BY playedAt ASC, id ASC"
    )
    suspend fun getForAccount(userId: UUID, serverId: String): List<OfflinePlayEntity>

    @Query("DELETE FROM offline_plays WHERE id = :id") suspend fun deleteById(id: Long)

    @Query("DELETE FROM offline_plays WHERE userId = :userId AND serverId = :serverId")
    suspend fun deleteForAccount(userId: UUID, serverId: String)

    @Query("SELECT COUNT(*) FROM offline_plays") suspend fun countAll(): Int
}
