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
import com.makd.afinity.data.database.entities.ItemImageEntity
import com.makd.afinity.data.models.user.AfinityUserDataDto
import java.util.UUID

/**
 * Master DAO that combines all database operations for a Jellyfin server.
 * This provides a unified interface for all database operations.
 */
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertImage(image: ItemImageEntity)

    @Query("SELECT * FROM item_images WHERE itemId = :itemId")
    abstract suspend fun getImage(itemId: UUID): ItemImageEntity?

    @Transaction
    open suspend fun saveMovieWithImages(
        movie: AfinityMovieDto,
        images: ItemImageEntity,
        sources: List<AfinitySourceDto>,
        userData: AfinityUserDataDto
    ) {
        insertMovie(movie)
        insertImage(images)
        sources.forEach { insertSource(it) }
        insertUserData(userData)
    }

    @Transaction
    open suspend fun saveShowWithImages(
        show: AfinityShowDto,
        images: ItemImageEntity,
        userData: AfinityUserDataDto
    ) {
        insertShow(show)
        insertImage(images)
        insertUserData(userData)
    }

    @Transaction
    open suspend fun saveEpisodeWithImages(
        episode: AfinityEpisodeDto,
        images: ItemImageEntity,
        sources: List<AfinitySourceDto>,
        userData: AfinityUserDataDto,
        segments: List<AfinitySegmentDto>
    ) {
        insertEpisode(episode)
        insertImage(images)
        sources.forEach { insertSource(it) }
        segments.forEach { insertSegment(it) }
        insertUserData(userData)
    }

    @Query("SELECT * FROM sources WHERE itemId = :itemId")
    abstract suspend fun getSourcesForItem(itemId: UUID): List<AfinitySourceDto>

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
    open suspend fun getUserDataOrCreateNew(itemId: UUID, userId: UUID): AfinityUserDataDto {
        return getUserData(userId, itemId) ?: AfinityUserDataDto(
            userId = userId,
            itemId = itemId,
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

    @Query("SELECT * FROM movies WHERE serverId = :serverId OR serverId IS NULL ORDER BY name ASC")
    abstract suspend fun getMoviesByServerId(serverId: String?): List<AfinityMovieDto>

    @Query("SELECT * FROM shows WHERE serverId = :serverId OR serverId IS NULL ORDER BY name ASC")
    abstract suspend fun getShowsByServerId(serverId: String?): List<AfinityShowDto>

    @Query("SELECT * FROM segments WHERE itemId = :itemId")
    abstract suspend fun getSegmentsForItem(itemId: UUID): List<AfinitySegmentDto>

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

    @Transaction
    open suspend fun setPlaybackPositionTicks(itemId: UUID, userId: UUID, positionTicks: Long) {
        val userData = getUserDataOrCreateNew(itemId, userId)
        insertUserData(userData.copy(playbackPositionTicks = positionTicks))
    }

    @Transaction
    open suspend fun setPlayed(userId: UUID, itemId: UUID, played: Boolean) {
        val userData = getUserDataOrCreateNew(itemId, userId)
        insertUserData(userData.copy(played = played))
    }

    @Transaction
    open suspend fun setUserDataToBeSynced(userId: UUID, itemId: UUID, toBeSynced: Boolean) {
        val userData = getUserDataOrCreateNew(itemId, userId)
        insertUserData(userData.copy(toBeSynced = toBeSynced))
    }

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
        deleteAllImages()
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

    @Query("DELETE FROM item_images")
    abstract suspend fun deleteAllImages()
}