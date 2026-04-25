package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.makd.afinity.data.database.entities.PersonSectionCacheEntity

@Dao
interface PersonSectionDao {

    @Query("SELECT * FROM person_section_cache WHERE cacheKey = :key AND serverId = :serverId AND userId = :userId")
    suspend fun getCachedSection(key: String, serverId: String, userId: String): PersonSectionCacheEntity?

    @Query(
        """
        SELECT COUNT(*) > 0
        FROM person_section_cache
        WHERE cacheKey = :key
        AND serverId = :serverId
        AND userId = :userId
        AND (cachedTimestamp + :ttlMillis) > :currentTime
    """
    )
    suspend fun isSectionCacheFresh(key: String, serverId: String, userId: String, ttlMillis: Long, currentTime: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSection(entity: PersonSectionCacheEntity)

    @Query("DELETE FROM person_section_cache WHERE cacheKey = :key AND serverId = :serverId AND userId = :userId")
    suspend fun deleteSection(key: String, serverId: String, userId: String)

    @Query("DELETE FROM person_section_cache WHERE sectionType = :type AND serverId = :serverId AND userId = :userId")
    suspend fun deleteSectionsByType(type: String, serverId: String, userId: String)

    @Query("SELECT * FROM person_section_cache WHERE serverId = :serverId AND userId = :userId")
    suspend fun getAllCachedSections(serverId: String, userId: String): List<PersonSectionCacheEntity>

    @Query("DELETE FROM person_section_cache") suspend fun clearAllCache()
}