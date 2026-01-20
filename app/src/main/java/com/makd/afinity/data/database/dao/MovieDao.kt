package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.makd.afinity.data.database.entities.AfinityMovieDto
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface MovieDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovie(movie: AfinityMovieDto)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMovies(movies: List<AfinityMovieDto>)

    @Update
    suspend fun updateMovie(movie: AfinityMovieDto)

    @Delete
    suspend fun deleteMovie(movie: AfinityMovieDto)

    @Query("DELETE FROM movies WHERE id = :movieId")
    suspend fun deleteMovieById(movieId: UUID)

    @Query("DELETE FROM movies WHERE serverId = :serverId")
    suspend fun deleteMoviesByServerId(serverId: String)

    @Query("SELECT * FROM movies WHERE id = :movieId AND serverId = :serverId")
    suspend fun getMovie(movieId: UUID, serverId: String): AfinityMovieDto?

    @Query("SELECT * FROM movies WHERE serverId = :serverId ORDER BY name ASC")
    suspend fun getMovies(serverId: String): List<AfinityMovieDto>

    @Query("SELECT * FROM movies WHERE serverId = :serverId ORDER BY name ASC")
    fun getMoviesFlow(serverId: String): Flow<List<AfinityMovieDto>>

    @Query("""
        SELECT * FROM movies
        WHERE serverId = :serverId
        AND (name LIKE '%' || :query || '%' OR originalTitle LIKE '%' || :query || '%')
        ORDER BY name ASC
    """)
    suspend fun searchMovies(query: String, serverId: String): List<AfinityMovieDto>

    @Query("SELECT * FROM movies WHERE serverId = :serverId AND premiereDate >= :fromDate ORDER BY premiereDate DESC LIMIT :limit")
    suspend fun getRecentMovies(fromDate: Long, limit: Int, serverId: String): List<AfinityMovieDto>

    @Query("SELECT * FROM movies WHERE serverId = :serverId ORDER BY name ASC")
    suspend fun getAllMovies(serverId: String): List<AfinityMovieDto>

    @Query("SELECT COUNT(*) FROM movies WHERE serverId = :serverId")
    suspend fun getMovieCount(serverId: String): Int

    @Query("DELETE FROM movies")
    suspend fun deleteAllMovies()
}