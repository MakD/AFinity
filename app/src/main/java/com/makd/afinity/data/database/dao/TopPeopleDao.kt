package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.makd.afinity.data.database.entities.TopPeopleCacheEntity

@Dao
interface TopPeopleDao {

    @Query("SELECT * FROM top_people_cache WHERE personType = :type AND serverId = :serverId AND userId = :userId")
    suspend fun getCachedTopPeople(type: String, serverId: String, userId: String): TopPeopleCacheEntity?

    @Query(
        """
        SELECT COUNT(*) > 0
        FROM top_people_cache
        WHERE personType = :type
        AND serverId = :serverId
        AND userId = :userId
        AND (cachedTimestamp + :ttlMillis) > :currentTime
    """
    )
    suspend fun isTopPeopleCacheFresh(type: String, serverId: String, userId: String, ttlMillis: Long, currentTime: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopPeople(entity: TopPeopleCacheEntity)

    @Query("DELETE FROM top_people_cache WHERE personType = :type AND serverId = :serverId AND userId = :userId")
    suspend fun deleteTopPeople(type: String, serverId: String, userId: String)

    @Query("DELETE FROM top_people_cache") suspend fun clearAllCache()
}