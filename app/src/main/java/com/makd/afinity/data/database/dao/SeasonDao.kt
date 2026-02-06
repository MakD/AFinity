package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.makd.afinity.data.database.entities.AfinitySeasonDto
import java.util.UUID
import kotlinx.coroutines.flow.Flow

@Dao
interface SeasonDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeason(season: AfinitySeasonDto)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeasons(seasons: List<AfinitySeasonDto>)

    @Update suspend fun updateSeason(season: AfinitySeasonDto)

    @Delete suspend fun deleteSeason(season: AfinitySeasonDto)

    @Query("DELETE FROM seasons WHERE id = :seasonId") suspend fun deleteSeasonById(seasonId: UUID)

    @Query("DELETE FROM seasons WHERE seriesId = :seriesId")
    suspend fun deleteSeasonsBySeriesId(seriesId: UUID)

    @Query("DELETE FROM seasons WHERE serverId = :serverId")
    suspend fun deleteSeasonsByServerId(serverId: String)

    @Query("SELECT * FROM seasons WHERE id = :seasonId AND serverId = :serverId")
    suspend fun getSeason(seasonId: UUID, serverId: String): AfinitySeasonDto?

    @Query(
        "SELECT * FROM seasons WHERE seriesId = :seriesId AND serverId = :serverId ORDER BY indexNumber ASC"
    )
    suspend fun getSeasonsForSeries(seriesId: UUID, serverId: String): List<AfinitySeasonDto>

    @Query(
        "SELECT * FROM seasons WHERE seriesId = :seriesId AND serverId = :serverId ORDER BY indexNumber ASC"
    )
    fun getSeasonsForSeriesFlow(seriesId: UUID, serverId: String): Flow<List<AfinitySeasonDto>>

    @Query(
        "SELECT * FROM seasons WHERE serverId = :serverId AND name LIKE '%' || :query || '%' ORDER BY name ASC"
    )
    suspend fun searchSeasons(query: String, serverId: String): List<AfinitySeasonDto>

    @Query(
        "SELECT * FROM seasons WHERE serverId = :serverId ORDER BY seriesName ASC, indexNumber ASC"
    )
    suspend fun getAllSeasons(serverId: String): List<AfinitySeasonDto>

    @Query("SELECT COUNT(*) FROM seasons WHERE serverId = :serverId")
    suspend fun getSeasonCount(serverId: String): Int

    @Query("DELETE FROM seasons") suspend fun deleteAllSeasons()
}
