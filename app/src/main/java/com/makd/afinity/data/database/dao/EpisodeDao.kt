package com.makd.afinity.data.database.dao

import androidx.room.*
import com.makd.afinity.data.database.entities.AfinityEpisodeDto
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface EpisodeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisode(episode: AfinityEpisodeDto)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodes(episodes: List<AfinityEpisodeDto>)

    @Update
    suspend fun updateEpisode(episode: AfinityEpisodeDto)

    @Delete
    suspend fun deleteEpisode(episode: AfinityEpisodeDto)

    @Query("DELETE FROM episodes WHERE id = :episodeId")
    suspend fun deleteEpisodeById(episodeId: UUID)

    @Query("DELETE FROM episodes WHERE seasonId = :seasonId")
    suspend fun deleteEpisodesBySeasonId(seasonId: UUID)

    @Query("DELETE FROM episodes WHERE seriesId = :seriesId")
    suspend fun deleteEpisodesBySeriesId(seriesId: UUID)

    @Query("DELETE FROM episodes WHERE serverId = :serverId")
    suspend fun deleteEpisodesByServerId(serverId: String)

    @Query("SELECT * FROM episodes WHERE id = :episodeId")
    suspend fun getEpisode(episodeId: UUID): AfinityEpisodeDto?

    @Query("SELECT * FROM episodes WHERE seasonId = :seasonId ORDER BY indexNumber ASC")
    suspend fun getEpisodesForSeason(seasonId: UUID): List<AfinityEpisodeDto>

    @Query("SELECT * FROM episodes WHERE seasonId = :seasonId ORDER BY indexNumber ASC")
    fun getEpisodesForSeasonFlow(seasonId: UUID): Flow<List<AfinityEpisodeDto>>

    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId ORDER BY parentIndexNumber ASC, indexNumber ASC")
    suspend fun getEpisodesForSeries(seriesId: UUID): List<AfinityEpisodeDto>

    @Query("""
        SELECT * FROM episodes 
        WHERE (serverId = :serverId OR serverId IS NULL) 
        AND (name LIKE '%' || :query || '%' OR overview LIKE '%' || :query || '%')
        ORDER BY seriesName ASC, parentIndexNumber ASC, indexNumber ASC
    """)
    suspend fun searchEpisodes(query: String, serverId: String? = null): List<AfinityEpisodeDto>

    @Query("""
        SELECT e.* FROM episodes e
        INNER JOIN userdata u ON e.id = u.itemId
        WHERE u.userId = :userId AND u.playbackPositionTicks > 0 AND u.played = 0
        ORDER BY u.playbackPositionTicks DESC
        LIMIT :limit
    """)
    suspend fun getContinueWatchingEpisodes(userId: UUID, limit: Int): List<AfinityEpisodeDto>

    @Query("SELECT * FROM episodes ORDER BY seriesName ASC, parentIndexNumber ASC, indexNumber ASC")
    suspend fun getAllEpisodes(): List<AfinityEpisodeDto>

    @Query("SELECT COUNT(*) FROM episodes")
    suspend fun getEpisodeCount(): Int

    @Query("DELETE FROM episodes")
    suspend fun deleteAllEpisodes()
}