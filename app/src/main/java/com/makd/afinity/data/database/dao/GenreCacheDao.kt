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

    @Query("SELECT * FROM genre_cache ORDER BY genreName ASC")
    suspend fun getAllCachedGenres(): List<GenreCacheEntity>

    @Query("SELECT genreName FROM genre_cache ORDER BY genreName ASC")
    suspend fun getAllGenreNames(): List<String>

    @Query("SELECT * FROM genre_cache WHERE genreName = :genreName")
    suspend fun getGenreCache(genreName: String): GenreCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGenreCache(genre: GenreCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGenreCaches(genres: List<GenreCacheEntity>)

    @Query("DELETE FROM genre_cache WHERE genreName = :genreName")
    suspend fun deleteGenreCache(genreName: String)

    @Query("DELETE FROM genre_cache") suspend fun clearAllGenreCaches()

    @Query("SELECT * FROM genre_movie_cache WHERE genreName = :genreName ORDER BY position ASC")
    suspend fun getCachedMoviesForGenre(genreName: String): List<GenreMovieCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGenreMovies(movies: List<GenreMovieCacheEntity>)

    @Query("DELETE FROM genre_movie_cache WHERE genreName = :genreName")
    suspend fun deleteMoviesForGenre(genreName: String)

    @Query("DELETE FROM genre_movie_cache") suspend fun clearAllGenreMovies()

    @Query(
        """
        SELECT COUNT(*) > 0
        FROM genre_cache
        WHERE genreName = :genreName
        AND (lastFetchedTimestamp + :ttlMillis) > :currentTime
    """
    )
    suspend fun isGenreCacheFresh(genreName: String, ttlMillis: Long, currentTime: Long): Boolean

    @Query(
        """
        SELECT COUNT(*) > 0
        FROM genre_cache
        WHERE (lastFetchedTimestamp + :ttlMillis) > :currentTime
    """
    )
    suspend fun hasAnyFreshGenre(ttlMillis: Long, currentTime: Long): Boolean

    @Query("SELECT MIN(lastFetchedTimestamp) FROM genre_cache")
    suspend fun getOldestCacheTimestamp(): Long?

    @Transaction
    suspend fun cacheGenreWithMovies(
        genreName: String,
        movies: List<GenreMovieCacheEntity>,
        timestamp: Long,
    ) {
        insertGenreCache(
            GenreCacheEntity(
                genreName = genreName,
                lastFetchedTimestamp = timestamp,
                movieCount = movies.size,
            )
        )

        deleteMoviesForGenre(genreName)

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

    @Query("SELECT * FROM show_genre_cache ORDER BY genreName ASC")
    suspend fun getAllCachedShowGenres(): List<ShowGenreCacheEntity>

    @Query("SELECT genreName FROM show_genre_cache ORDER BY genreName ASC")
    suspend fun getAllShowGenreNames(): List<String>

    @Query("SELECT * FROM show_genre_cache WHERE genreName = :genreName")
    suspend fun getShowGenreCache(genreName: String): ShowGenreCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShowGenreCache(genre: ShowGenreCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShowGenreCaches(genres: List<ShowGenreCacheEntity>)

    @Query("DELETE FROM show_genre_cache WHERE genreName = :genreName")
    suspend fun deleteShowGenreCache(genreName: String)

    @Query("DELETE FROM show_genre_cache") suspend fun clearAllShowGenreCaches()

    @Query("SELECT * FROM genre_show_cache WHERE genreName = :genreName ORDER BY position ASC")
    suspend fun getCachedShowsForGenre(genreName: String): List<GenreShowCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGenreShows(shows: List<GenreShowCacheEntity>)

    @Query("DELETE FROM genre_show_cache WHERE genreName = :genreName")
    suspend fun deleteShowsForGenre(genreName: String)

    @Query("DELETE FROM genre_show_cache") suspend fun clearAllGenreShows()

    @Query(
        """
        SELECT COUNT(*) > 0
        FROM show_genre_cache
        WHERE genreName = :genreName
        AND (lastFetchedTimestamp + :ttlMillis) > :currentTime
    """
    )
    suspend fun isShowGenreCacheFresh(
        genreName: String,
        ttlMillis: Long,
        currentTime: Long,
    ): Boolean

    @Query(
        """
        SELECT COUNT(*) > 0
        FROM show_genre_cache
        WHERE (lastFetchedTimestamp + :ttlMillis) > :currentTime
    """
    )
    suspend fun hasAnyFreshShowGenre(ttlMillis: Long, currentTime: Long): Boolean

    @Query("SELECT MIN(lastFetchedTimestamp) FROM show_genre_cache")
    suspend fun getOldestShowCacheTimestamp(): Long?

    @Transaction
    suspend fun cacheGenreWithShows(
        genreName: String,
        shows: List<GenreShowCacheEntity>,
        timestamp: Long,
    ) {
        insertShowGenreCache(
            ShowGenreCacheEntity(
                genreName = genreName,
                lastFetchedTimestamp = timestamp,
                showCount = shows.size,
            )
        )

        deleteShowsForGenre(genreName)

        if (shows.isNotEmpty()) {
            insertGenreShows(shows)
        }
    }
}
