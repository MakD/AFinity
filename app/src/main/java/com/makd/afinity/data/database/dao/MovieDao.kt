package com.makd.afinity.data.database.dao

import androidx.room.*
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

    @Query("SELECT * FROM movies WHERE id = :movieId")
    suspend fun getMovie(movieId: UUID): AfinityMovieDto?

    @Query("SELECT * FROM movies WHERE serverId = :serverId OR serverId IS NULL ORDER BY name ASC")
    suspend fun getMovies(serverId: String? = null): List<AfinityMovieDto>

    @Query("SELECT * FROM movies WHERE serverId = :serverId OR serverId IS NULL ORDER BY name ASC")
    fun getMoviesFlow(serverId: String? = null): Flow<List<AfinityMovieDto>>

    @Query("""
        SELECT * FROM movies 
        WHERE (serverId = :serverId OR serverId IS NULL) 
        AND (name LIKE '%' || :query || '%' OR originalTitle LIKE '%' || :query || '%')
        ORDER BY name ASC
    """)
    suspend fun searchMovies(query: String, serverId: String? = null): List<AfinityMovieDto>

    @Query("SELECT * FROM movies WHERE premiereDate >= :fromDate ORDER BY premiereDate DESC LIMIT :limit")
    suspend fun getRecentMovies(fromDate: Long, limit: Int): List<AfinityMovieDto>

    @Query("SELECT * FROM movies ORDER BY name ASC")
    suspend fun getAllMovies(): List<AfinityMovieDto>

    @Query("SELECT COUNT(*) FROM movies")
    suspend fun getMovieCount(): Int

    @Query("DELETE FROM movies")
    suspend fun deleteAllMovies()
}