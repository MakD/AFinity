package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.makd.afinity.data.database.entities.AudibleRatingEntity

@Dao
interface AudibleRatingDao {

    @Query(
        "SELECT * FROM audible_ratings WHERE itemId = :itemId AND jellyfinServerId = :serverId AND jellyfinUserId = :userId"
    )
    suspend fun getRating(itemId: String, serverId: String, userId: String): AudibleRatingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRating(rating: AudibleRatingEntity)

    @Query(
        "DELETE FROM audible_ratings WHERE jellyfinServerId = :serverId AND jellyfinUserId = :userId"
    )
    suspend fun deleteAllRatings(serverId: String, userId: String)
}