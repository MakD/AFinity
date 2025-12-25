package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.makd.afinity.data.database.entities.TopPeopleCacheEntity

@Dao
interface TopPeopleDao {

    @Query("SELECT * FROM top_people_cache WHERE personType = :type")
    suspend fun getCachedTopPeople(type: String): TopPeopleCacheEntity?

    @Query("""
        SELECT COUNT(*) > 0
        FROM top_people_cache
        WHERE personType = :type
        AND (cachedTimestamp + :ttlMillis) > :currentTime
    """)
    suspend fun isTopPeopleCacheFresh(type: String, ttlMillis: Long, currentTime: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopPeople(entity: TopPeopleCacheEntity)

    @Query("DELETE FROM top_people_cache WHERE personType = :type")
    suspend fun deleteTopPeople(type: String)

    @Query("DELETE FROM top_people_cache")
    suspend fun clearAllCache()
}