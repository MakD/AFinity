package com.makd.afinity.data.database.dao

import androidx.room.*
import com.makd.afinity.data.database.entities.AfinitySeasonDto
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface SeasonDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeason(season: AfinitySeasonDto)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeasons(seasons: List<AfinitySeasonDto>)

    @Update
    suspend fun updateSeason(season: AfinitySeasonDto)

    @Delete
    suspend fun deleteSeason(season: AfinitySeasonDto)

    @Query("DELETE FROM seasons WHERE id = :seasonId")
    suspend fun deleteSeasonById(seasonId: UUID)

    @Query("DELETE FROM seasons WHERE seriesId = :seriesId")
    suspend fun deleteSeasonsBySeriesId(seriesId: UUID)

    @Query("SELECT * FROM seasons WHERE id = :seasonId")
    suspend fun getSeason(seasonId: UUID): AfinitySeasonDto?

    @Query("SELECT * FROM seasons WHERE seriesId = :seriesId ORDER BY indexNumber ASC")
    suspend fun getSeasonsForSeries(seriesId: UUID): List<AfinitySeasonDto>

    @Query("SELECT * FROM seasons WHERE seriesId = :seriesId ORDER BY indexNumber ASC")
    fun getSeasonsForSeriesFlow(seriesId: UUID): Flow<List<AfinitySeasonDto>>

    @Query("SELECT * FROM seasons WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    suspend fun searchSeasons(query: String): List<AfinitySeasonDto>

    @Query("SELECT * FROM seasons ORDER BY seriesName ASC, indexNumber ASC")
    suspend fun getAllSeasons(): List<AfinitySeasonDto>

    @Query("SELECT COUNT(*) FROM seasons")
    suspend fun getSeasonCount(): Int

    @Query("DELETE FROM seasons")
    suspend fun deleteAllSeasons()
}