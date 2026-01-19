package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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

    @Query("SELECT * FROM episodes WHERE id = :episodeId AND serverId = :serverId")
    suspend fun getEpisode(episodeId: UUID, serverId: String): AfinityEpisodeDto?

    @Query("SELECT * FROM episodes WHERE seasonId = :seasonId AND serverId = :serverId ORDER BY indexNumber ASC")
    suspend fun getEpisodesForSeason(seasonId: UUID, serverId: String): List<AfinityEpisodeDto>

    @Query("SELECT * FROM episodes WHERE seasonId = :seasonId AND serverId = :serverId ORDER BY indexNumber ASC")
    fun getEpisodesForSeasonFlow(seasonId: UUID, serverId: String): Flow<List<AfinityEpisodeDto>>

    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId AND serverId = :serverId ORDER BY parentIndexNumber ASC, indexNumber ASC")
    suspend fun getEpisodesForSeries(seriesId: UUID, serverId: String): List<AfinityEpisodeDto>

    @Query("""
        SELECT * FROM episodes
        WHERE serverId = :serverId
        AND (name LIKE '%' || :query || '%' OR overview LIKE '%' || :query || '%')
        ORDER BY seriesName ASC, parentIndexNumber ASC, indexNumber ASC
    """)
    suspend fun searchEpisodes(query: String, serverId: String): List<AfinityEpisodeDto>

    @Query("""
        SELECT e.* FROM episodes e
        INNER JOIN userdata u ON e.id = u.itemId
        WHERE e.serverId = :serverId AND u.serverId = :serverId
        AND u.userId = :userId AND u.playbackPositionTicks > 0 AND u.played = 0
        ORDER BY u.playbackPositionTicks DESC
        LIMIT :limit
    """)
    suspend fun getContinueWatchingEpisodes(userId: UUID, serverId: String, limit: Int): List<AfinityEpisodeDto>

    @Query("SELECT * FROM episodes WHERE serverId = :serverId ORDER BY seriesName ASC, parentIndexNumber ASC, indexNumber ASC")
    suspend fun getAllEpisodes(serverId: String): List<AfinityEpisodeDto>

    @Query("SELECT COUNT(*) FROM episodes WHERE serverId = :serverId")
    suspend fun getEpisodeCount(serverId: String): Int

    @Query("DELETE FROM episodes")
    suspend fun deleteAllEpisodes()
}