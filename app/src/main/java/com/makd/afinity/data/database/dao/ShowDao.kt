package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.makd.afinity.data.database.entities.AfinityShowDto
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Dao
interface ShowDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertShow(show: AfinityShowDto)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShows(shows: List<AfinityShowDto>)

    @Update suspend fun updateShow(show: AfinityShowDto)

    @Delete suspend fun deleteShow(show: AfinityShowDto)

    @Query("DELETE FROM shows WHERE id = :showId") suspend fun deleteShowById(showId: UUID)

    @Query("DELETE FROM shows WHERE serverId = :serverId")
    suspend fun deleteShowsByServerId(serverId: String)

    @Query("SELECT * FROM shows WHERE id = :showId AND serverId = :serverId")
    suspend fun getShow(showId: UUID, serverId: String): AfinityShowDto?

    @Query("SELECT * FROM shows WHERE serverId = :serverId ORDER BY name ASC")
    suspend fun getShows(serverId: String): List<AfinityShowDto>

    @Query("SELECT * FROM shows WHERE serverId = :serverId ORDER BY name ASC")
    fun getShowsFlow(serverId: String): Flow<List<AfinityShowDto>>

    @Query(
        """
        SELECT * FROM shows
        WHERE serverId = :serverId
        AND (name LIKE '%' || :query || '%' OR originalTitle LIKE '%' || :query || '%')
        ORDER BY name ASC
    """
    )
    suspend fun searchShows(query: String, serverId: String): List<AfinityShowDto>

    @Query("SELECT * FROM shows WHERE serverId = :serverId ORDER BY name ASC")
    suspend fun getAllShows(serverId: String): List<AfinityShowDto>

    @Query("SELECT COUNT(*) FROM shows WHERE serverId = :serverId")
    suspend fun getShowCount(serverId: String): Int

    @Query("DELETE FROM shows") suspend fun deleteAllShows()
}
