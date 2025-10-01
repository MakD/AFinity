package com.makd.afinity.data.database.dao

import androidx.room.*
import com.makd.afinity.data.database.entities.AfinityShowDto
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface ShowDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShow(show: AfinityShowDto)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShows(shows: List<AfinityShowDto>)

    @Update
    suspend fun updateShow(show: AfinityShowDto)

    @Delete
    suspend fun deleteShow(show: AfinityShowDto)

    @Query("DELETE FROM shows WHERE id = :showId")
    suspend fun deleteShowById(showId: UUID)

    @Query("DELETE FROM shows WHERE serverId = :serverId")
    suspend fun deleteShowsByServerId(serverId: String)

    @Query("SELECT * FROM shows WHERE id = :showId")
    suspend fun getShow(showId: UUID): AfinityShowDto?

    @Query("SELECT * FROM shows WHERE serverId = :serverId OR serverId IS NULL ORDER BY name ASC")
    suspend fun getShows(serverId: String? = null): List<AfinityShowDto>

    @Query("SELECT * FROM shows WHERE serverId = :serverId OR serverId IS NULL ORDER BY name ASC")
    fun getShowsFlow(serverId: String? = null): Flow<List<AfinityShowDto>>

    @Query("""
        SELECT * FROM shows 
        WHERE (serverId = :serverId OR serverId IS NULL) 
        AND (name LIKE '%' || :query || '%' OR originalTitle LIKE '%' || :query || '%')
        ORDER BY name ASC
    """)
    suspend fun searchShows(query: String, serverId: String? = null): List<AfinityShowDto>

    @Query("SELECT * FROM shows ORDER BY name ASC")
    suspend fun getAllShows(): List<AfinityShowDto>

    @Query("SELECT COUNT(*) FROM shows")
    suspend fun getShowCount(): Int

    @Query("DELETE FROM shows")
    suspend fun deleteAllShows()
}