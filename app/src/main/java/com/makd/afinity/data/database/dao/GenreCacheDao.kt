package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.makd.afinity.data.database.entities.GenreCacheEntity
import com.makd.afinity.data.database.entities.GenreMovieCacheEntity
import com.makd.afinity.data.database.entities.GenreShowCacheEntity
import com.makd.afinity.data.database.entities.ShowGenreCacheEntity

@Dao
interface GenreCacheDao {

    @Query("SELECT * FROM genre_cache WHERE serverId = :serverId AND userId = :userId ORDER BY genreName ASC")
    suspend fun getAllCachedGenres(serverId: String, userId: String): List<GenreCacheEntity>

    @Query("SELECT genreName FROM genre_cache WHERE serverId = :serverId AND userId = :userId ORDER BY genreName ASC")
    suspend fun getAllGenreNames(serverId: String, userId: String): List<String>

    @Query("SELECT * FROM genre_cache WHERE genreName = :genreName AND serverId = :serverId AND userId = :userId")
    suspend fun getGenreCache(genreName: String, serverId: String, userId: String): GenreCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGenreCache(genre: GenreCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGenreCaches(genres: List<GenreCacheEntity>)

    @Query("DELETE FROM genre_cache WHERE genreName = :genreName AND serverId = :serverId AND userId = :userId")
    suspend fun deleteGenreCache(genreName: String, serverId: String, userId: String)

    @Query("DELETE FROM genre_cache") suspend fun clearAllGenreCaches()

    @Query("SELECT * FROM genre_movie_cache WHERE genreName = :genreName AND serverId = :serverId AND userId = :userId ORDER BY position ASC")
    suspend fun getCachedMoviesForGenre(genreName: String, serverId: String, userId: String): List<GenreMovieCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGenreMovies(movies: List<GenreMovieCacheEntity>)

    @Query("DELETE FROM genre_movie_cache WHERE genreName = :genreName AND serverId = :serverId AND userId = :userId")
    suspend fun deleteMoviesForGenre(genreName: String, serverId: String, userId: String)

    @Query("DELETE FROM genre_movie_cache") suspend fun clearAllGenreMovies()

    @Query(
        """
        SELECT COUNT(*) > 0
        FROM genre_cache
        WHERE genreName = :genreName
        AND serverId = :serverId
        AND userId = :userId
        AND (lastFetchedTimestamp + :ttlMillis) > :currentTime
    """
    )
    suspend fun isGenreCacheFresh(
        genreName: String,
        serverId: String,
        userId: String,
        ttlMillis: Long,
        currentTime: Long,
    ): Boolean

    @Query(
        """
        SELECT COUNT(*) > 0
        FROM genre_cache
        WHERE serverId = :serverId
        AND userId = :userId
        AND (lastFetchedTimestamp + :ttlMillis) > :currentTime
    """
    )
    suspend fun hasAnyFreshGenre(serverId: String, userId: String, ttlMillis: Long, currentTime: Long): Boolean

    @Query("SELECT MIN(lastFetchedTimestamp) FROM genre_cache WHERE serverId = :serverId AND userId = :userId")
    suspend fun getOldestCacheTimestamp(serverId: String, userId: String): Long?

    @Transaction
    suspend fun cacheGenreWithMovies(
        genreName: String,
        serverId: String,
        userId: String,
        movies: List<GenreMovieCacheEntity>,
        timestamp: Long,
    ) {
        insertGenreCache(
            GenreCacheEntity(
                genreName = genreName,
                serverId = serverId,
                userId = userId,
                lastFetchedTimestamp = timestamp,
                movieCount = movies.size,
            )
        )

        deleteMoviesForGenre(genreName, serverId, userId)

        if (movies.isNotEmpty()) {
            insertGenreMovies(movies)
        }
    }

    @Transaction
    suspend fun clearAllCache() {
        clearAllGenreCaches()
        clearAllGenreMovies()
        clearAllShowGenreCaches()
        clearAllGenreShows()
    }

    @Query("SELECT * FROM show_genre_cache WHERE serverId = :serverId AND userId = :userId ORDER BY genreName ASC")
    suspend fun getAllCachedShowGenres(serverId: String, userId: String): List<ShowGenreCacheEntity>

    @Query("SELECT genreName FROM show_genre_cache WHERE serverId = :serverId AND userId = :userId ORDER BY genreName ASC")
    suspend fun getAllShowGenreNames(serverId: String, userId: String): List<String>

    @Query("SELECT * FROM show_genre_cache WHERE genreName = :genreName AND serverId = :serverId AND userId = :userId")
    suspend fun getShowGenreCache(genreName: String, serverId: String, userId: String): ShowGenreCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShowGenreCache(genre: ShowGenreCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShowGenreCaches(genres: List<ShowGenreCacheEntity>)

    @Query("DELETE FROM show_genre_cache WHERE genreName = :genreName AND serverId = :serverId AND userId = :userId")
    suspend fun deleteShowGenreCache(genreName: String, serverId: String, userId: String)

    @Query("DELETE FROM show_genre_cache") suspend fun clearAllShowGenreCaches()

    @Query("SELECT * FROM genre_show_cache WHERE genreName = :genreName AND serverId = :serverId AND userId = :userId ORDER BY position ASC")
    suspend fun getCachedShowsForGenre(genreName: String, serverId: String, userId: String): List<GenreShowCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGenreShows(shows: List<GenreShowCacheEntity>)

    @Query("DELETE FROM genre_show_cache WHERE genreName = :genreName AND serverId = :serverId AND userId = :userId")
    suspend fun deleteShowsForGenre(genreName: String, serverId: String, userId: String)

    @Query("DELETE FROM genre_show_cache") suspend fun clearAllGenreShows()

    @Query(
        """
        SELECT COUNT(*) > 0
        FROM show_genre_cache
        WHERE genreName = :genreName
        AND serverId = :serverId
        AND userId = :userId
        AND (lastFetchedTimestamp + :ttlMillis) > :currentTime
    """
    )
    suspend fun isShowGenreCacheFresh(
        genreName: String,
        serverId: String,
        userId: String,
        ttlMillis: Long,
        currentTime: Long,
    ): Boolean

    @Query(
        """
        SELECT COUNT(*) > 0
        FROM show_genre_cache
        WHERE serverId = :serverId
        AND userId = :userId
        AND (lastFetchedTimestamp + :ttlMillis) > :currentTime
    """
    )
    suspend fun hasAnyFreshShowGenre(serverId: String, userId: String, ttlMillis: Long, currentTime: Long): Boolean

    @Query("SELECT MIN(lastFetchedTimestamp) FROM show_genre_cache WHERE serverId = :serverId AND userId = :userId")
    suspend fun getOldestShowCacheTimestamp(serverId: String, userId: String): Long?

    @Query("UPDATE genre_movie_cache SET movieData = :newData WHERE movieId = :itemId AND serverId = :serverId AND userId = :userId")
    suspend fun updateCachedMovieData(itemId: String, serverId: String, userId: String, newData: String)

    @Query("UPDATE genre_show_cache SET showData = :newData WHERE showId = :itemId AND serverId = :serverId AND userId = :userId")
    suspend fun updateCachedShowData(itemId: String, serverId: String, userId: String, newData: String)

    @Transaction
    suspend fun cacheGenreWithShows(
        genreName: String,
        serverId: String,
        userId: String,
        shows: List<GenreShowCacheEntity>,
        timestamp: Long,
    ) {
        insertShowGenreCache(
            ShowGenreCacheEntity(
                genreName = genreName,
                serverId = serverId,
                userId = userId,
                lastFetchedTimestamp = timestamp,
                showCount = shows.size,
            )
        )

        deleteShowsForGenre(genreName, serverId, userId)

        if (shows.isNotEmpty()) {
            insertGenreShows(shows)
        }
    }
}