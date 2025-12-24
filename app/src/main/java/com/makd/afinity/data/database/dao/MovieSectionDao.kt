package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.makd.afinity.data.database.entities.MovieSectionCacheEntity

@Dao
interface MovieSectionDao {

    @Query("SELECT * FROM movie_section_cache WHERE sectionId = :id")
    suspend fun getCachedSection(id: String): MovieSectionCacheEntity?

    @Query("""
        SELECT COUNT(*) > 0
        FROM movie_section_cache
        WHERE sectionId = :id
        AND (cachedTimestamp + :ttlMillis) > :currentTime
    """)
    suspend fun isSectionCacheFresh(id: String, ttlMillis: Long, currentTime: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSection(entity: MovieSectionCacheEntity)

    @Query("DELETE FROM movie_section_cache WHERE sectionId = :id")
    suspend fun deleteSection(id: String)

    @Query("DELETE FROM movie_section_cache WHERE sectionType = :type")
    suspend fun deleteSectionsByType(type: String)

    @Query("DELETE FROM movie_section_cache")
    suspend fun clearAllCache()
}