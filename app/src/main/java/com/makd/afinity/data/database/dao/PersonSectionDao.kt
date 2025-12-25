package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.makd.afinity.data.database.entities.PersonSectionCacheEntity

@Dao
interface PersonSectionDao {

    @Query("SELECT * FROM person_section_cache WHERE cacheKey = :key")
    suspend fun getCachedSection(key: String): PersonSectionCacheEntity?

    @Query("""
        SELECT COUNT(*) > 0
        FROM person_section_cache
        WHERE cacheKey = :key
        AND (cachedTimestamp + :ttlMillis) > :currentTime
    """)
    suspend fun isSectionCacheFresh(key: String, ttlMillis: Long, currentTime: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSection(entity: PersonSectionCacheEntity)

    @Query("DELETE FROM person_section_cache WHERE cacheKey = :key")
    suspend fun deleteSection(key: String)

    @Query("DELETE FROM person_section_cache WHERE sectionType = :type")
    suspend fun deleteSectionsByType(type: String)

    @Query("DELETE FROM person_section_cache")
    suspend fun clearAllCache()
}