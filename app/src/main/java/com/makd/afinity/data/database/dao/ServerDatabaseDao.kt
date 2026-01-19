package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.makd.afinity.data.database.entities.AfinityEpisodeDto
import com.makd.afinity.data.database.entities.AfinityMediaStreamDto
import com.makd.afinity.data.database.entities.AfinityMovieDto
import com.makd.afinity.data.database.entities.AfinitySeasonDto
import com.makd.afinity.data.database.entities.AfinitySegmentDto
import com.makd.afinity.data.database.entities.AfinityShowDto
import com.makd.afinity.data.database.entities.AfinitySourceDto
import com.makd.afinity.data.database.entities.AfinityTrickplayInfoDto
import com.makd.afinity.data.database.entities.DownloadDto
import com.makd.afinity.data.models.download.DownloadStatus
import com.makd.afinity.data.models.user.AfinityUserDataDto
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
abstract class ServerDatabaseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertMovie(movie: AfinityMovieDto)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertShow(show: AfinityShowDto)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSeason(season: AfinitySeasonDto)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertEpisode(episode: AfinityEpisodeDto)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSource(source: AfinitySourceDto)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertMediaStream(stream: AfinityMediaStreamDto)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertTrickplayInfo(trickplayInfo: AfinityTrickplayInfoDto)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertSegment(segment: AfinitySegmentDto)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertUserData(userData: AfinityUserDataDto)

    @Query("SELECT * FROM movies WHERE id = :movieId")
    abstract suspend fun getMovie(movieId: UUID): AfinityMovieDto?

    @Query("SELECT * FROM shows WHERE id = :showId")
    abstract suspend fun getShow(showId: UUID): AfinityShowDto?

    @Query("SELECT * FROM seasons WHERE id = :seasonId")
    abstract suspend fun getSeason(seasonId: UUID): AfinitySeasonDto?

    @Query("SELECT * FROM episodes WHERE id = :episodeId")
    abstract suspend fun getEpisode(episodeId: UUID): AfinityEpisodeDto?

    @Query("SELECT * FROM sources WHERE id = :sourceId")
    abstract suspend fun getSource(sourceId: String): AfinitySourceDto?

    @Query("SELECT * FROM sources WHERE itemId = :itemId")
    abstract suspend fun getSources(itemId: UUID): List<AfinitySourceDto>

    @Query("SELECT * FROM mediastreams WHERE sourceId = :sourceId")
    abstract suspend fun getMediaStreamsBySourceId(sourceId: String): List<AfinityMediaStreamDto>

    @Query("SELECT * FROM trickplayInfos WHERE sourceId = :sourceId")
    abstract suspend fun getTrickplayInfo(sourceId: String): AfinityTrickplayInfoDto?

    @Query("SELECT * FROM userdata WHERE userId = :userId AND itemId = :itemId")
    abstract suspend fun getUserData(userId: UUID, itemId: UUID): AfinityUserDataDto?

    @Transaction
    open suspend fun getUserDataOrCreateNew(itemId: UUID, userId: UUID, serverId: String): AfinityUserDataDto {
        return getUserData(userId, itemId) ?: AfinityUserDataDto(
            userId = userId,
            itemId = itemId,
            serverId = serverId,
            played = false,
            favorite = false,
            playbackPositionTicks = 0L
        ).also { insertUserData(it) }
    }

    @Query("SELECT * FROM seasons WHERE seriesId = :seriesId ORDER BY indexNumber ASC")
    abstract suspend fun getSeasonsForSeries(seriesId: UUID): List<AfinitySeasonDto>

    @Query("SELECT * FROM episodes WHERE seasonId = :seasonId ORDER BY indexNumber ASC")
    abstract suspend fun getEpisodesForSeason(seasonId: UUID): List<AfinityEpisodeDto>

    @Query("SELECT * FROM episodes WHERE seriesId = :seriesId ORDER BY parentIndexNumber ASC, indexNumber ASC")
    abstract suspend fun getEpisodesForSeries(seriesId: UUID): List<AfinityEpisodeDto>

    @Query("SELECT * FROM segments WHERE itemId = :itemId")
    abstract suspend fun getSegmentsForItem(itemId: UUID): List<AfinitySegmentDto>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertDownload(download: DownloadDto)

    @Query("SELECT * FROM downloads WHERE id = :downloadId")
    abstract suspend fun getDownload(downloadId: UUID): DownloadDto?

    @Query("SELECT * FROM downloads WHERE itemId = :itemId")
    abstract suspend fun getDownloadByItemId(itemId: UUID): DownloadDto?

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    abstract fun getAllDownloadsFlow(): Flow<List<DownloadDto>>

    @Query("SELECT * FROM downloads WHERE status IN (:statuses) ORDER BY createdAt DESC")
    abstract fun getDownloadsByStatusFlow(statuses: List<DownloadStatus>): Flow<List<DownloadDto>>

    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY createdAt DESC")
    abstract suspend fun getDownloadsByStatus(status: DownloadStatus): List<DownloadDto>

    @Query("DELETE FROM downloads WHERE id = :downloadId")
    abstract suspend fun deleteDownload(downloadId: UUID)

    @Query("DELETE FROM downloads WHERE status = :status")
    abstract suspend fun deleteDownloadsByStatus(status: DownloadStatus)

    @Query("DELETE FROM movies WHERE id = :movieId")
    abstract suspend fun deleteMovie(movieId: UUID)

    @Query("DELETE FROM shows WHERE id = :showId")
    abstract suspend fun deleteShow(showId: UUID)

    @Query("DELETE FROM seasons WHERE id = :seasonId")
    abstract suspend fun deleteSeason(seasonId: UUID)

    @Query("DELETE FROM episodes WHERE id = :episodeId")
    abstract suspend fun deleteEpisode(episodeId: UUID)

    @Query("DELETE FROM sources WHERE id = :sourceId")
    abstract suspend fun deleteSource(sourceId: String)

    @Query("DELETE FROM userdata WHERE userId = :userId AND itemId = :itemId")
    abstract suspend fun deleteUserData(userId: UUID, itemId: UUID)

    @Query("DELETE FROM movies WHERE serverId = :serverId")
    abstract suspend fun deleteMoviesByServerId(serverId: String)

    @Query("DELETE FROM shows WHERE serverId = :serverId")
    abstract suspend fun deleteShowsByServerId(serverId: String)

    @Query("DELETE FROM episodes WHERE serverId = :serverId")
    abstract suspend fun deleteEpisodesByServerId(serverId: String)

    @Query("""
        SELECT * FROM movies 
        WHERE name LIKE '%' || :query || '%' OR originalTitle LIKE '%' || :query || '%'
        ORDER BY name ASC
        LIMIT :limit
    """)
    abstract suspend fun searchMovies(query: String, limit: Int = 50): List<AfinityMovieDto>

    @Query("""
        SELECT * FROM shows 
        WHERE name LIKE '%' || :query || '%' OR originalTitle LIKE '%' || :query || '%'
        ORDER BY name ASC
        LIMIT :limit
    """)
    abstract suspend fun searchShows(query: String, limit: Int = 50): List<AfinityShowDto>

    @Query("""
        SELECT * FROM episodes 
        WHERE name LIKE '%' || :query || '%' OR overview LIKE '%' || :query || '%'
        ORDER BY seriesName ASC, parentIndexNumber ASC, indexNumber ASC
        LIMIT :limit
    """)
    abstract suspend fun searchEpisodes(query: String, limit: Int = 50): List<AfinityEpisodeDto>

    @Query("""
        SELECT m.* FROM movies m
        INNER JOIN userdata u ON m.id = u.itemId
        WHERE u.userId = :userId AND u.favorite = 1
        ORDER BY m.name ASC
    """)
    abstract suspend fun getFavoriteMovies(userId: UUID): List<AfinityMovieDto>

    @Query("""
        SELECT s.* FROM shows s
        INNER JOIN userdata u ON s.id = u.itemId
        WHERE u.userId = :userId AND u.favorite = 1
        ORDER BY s.name ASC
    """)
    abstract suspend fun getFavoriteShows(userId: UUID): List<AfinityShowDto>

    @Query("""
        SELECT e.* FROM episodes e
        INNER JOIN userdata u ON e.id = u.itemId
        WHERE u.userId = :userId AND u.favorite = 1
        ORDER BY e.seriesName ASC, e.parentIndexNumber ASC, e.indexNumber ASC
    """)
    abstract suspend fun getFavoriteEpisodes(userId: UUID): List<AfinityEpisodeDto>

    @Query("""
        SELECT m.* FROM movies m
        INNER JOIN userdata u ON m.id = u.itemId
        WHERE u.userId = :userId AND u.playbackPositionTicks > 0 AND u.played = 0
        ORDER BY u.playbackPositionTicks DESC
        LIMIT :limit
    """)
    abstract suspend fun getContinueWatchingMovies(userId: UUID, limit: Int): List<AfinityMovieDto>

    @Query("""
        SELECT e.* FROM episodes e
        INNER JOIN userdata u ON e.id = u.itemId
        WHERE u.userId = :userId AND u.playbackPositionTicks > 0 AND u.played = 0
        ORDER BY u.playbackPositionTicks DESC
        LIMIT :limit
    """)
    abstract suspend fun getContinueWatchingEpisodes(userId: UUID, limit: Int): List<AfinityEpisodeDto>

    @Query("SELECT COUNT(*) FROM movies")
    abstract suspend fun getMovieCount(): Int

    @Query("SELECT COUNT(*) FROM shows")
    abstract suspend fun getShowCount(): Int

    @Query("SELECT COUNT(*) FROM episodes")
    abstract suspend fun getEpisodeCount(): Int

    @Query("SELECT COUNT(*) FROM sources WHERE type = 'LOCAL'")
    abstract suspend fun getDownloadedItemCount(): Int

    @Transaction
    open suspend fun clearAllData() {
        deleteAllMovies()
        deleteAllShows()
        deleteAllSeasons()
        deleteAllEpisodes()
        deleteAllSources()
        deleteAllMediaStreams()
        deleteAllTrickplayInfos()
        deleteAllSegments()
        deleteAllUserData()
        deleteAllDownloads()
    }

    @Query("DELETE FROM movies")
    abstract suspend fun deleteAllMovies()

    @Query("DELETE FROM shows")
    abstract suspend fun deleteAllShows()

    @Query("DELETE FROM seasons")
    abstract suspend fun deleteAllSeasons()

    @Query("DELETE FROM episodes")
    abstract suspend fun deleteAllEpisodes()

    @Query("DELETE FROM sources")
    abstract suspend fun deleteAllSources()

    @Query("DELETE FROM mediastreams")
    abstract suspend fun deleteAllMediaStreams()

    @Query("DELETE FROM trickplayInfos")
    abstract suspend fun deleteAllTrickplayInfos()

    @Query("DELETE FROM segments")
    abstract suspend fun deleteAllSegments()

    @Query("DELETE FROM userdata")
    abstract suspend fun deleteAllUserData()

    @Query("DELETE FROM downloads")
    abstract suspend fun deleteAllDownloads()
}