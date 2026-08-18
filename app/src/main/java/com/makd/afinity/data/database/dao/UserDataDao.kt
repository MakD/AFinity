package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.makd.afinity.data.models.user.AfinityUserDataDto
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface UserDataDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserData(userData: AfinityUserDataDto)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserDataList(userDataList: List<AfinityUserDataDto>)

    @Update suspend fun updateUserData(userData: AfinityUserDataDto)

    @Delete suspend fun deleteUserData(userData: AfinityUserDataDto)

    @Query(
        "DELETE FROM userdata WHERE userId = :userId AND itemId = :itemId AND serverId = :serverId"
    )
    suspend fun deleteUserDataByIds(userId: UUID, itemId: UUID, serverId: String)

    @Query("DELETE FROM userdata WHERE userId = :userId AND serverId = :serverId")
    suspend fun deleteUserDataByUserId(userId: UUID, serverId: String)

    @Query(
        "SELECT * FROM userdata WHERE userId = :userId AND itemId = :itemId AND serverId = :serverId"
    )
    suspend fun getUserData(userId: UUID, itemId: UUID, serverId: String): AfinityUserDataDto?

    @Query("SELECT * FROM userdata WHERE userId = :userId AND serverId = :serverId")
    suspend fun getAllUserData(userId: UUID, serverId: String): List<AfinityUserDataDto>

    @Query("SELECT * FROM userdata WHERE userId = :userId AND serverId = :serverId")
    fun getAllUserDataFlow(userId: UUID, serverId: String): Flow<List<AfinityUserDataDto>>

    @Query(
        "SELECT * FROM userdata WHERE userId = :userId AND serverId = :serverId AND favorite = 1"
    )
    suspend fun getFavoriteItems(userId: UUID, serverId: String): List<AfinityUserDataDto>

    @Query(
        "SELECT * FROM userdata WHERE userId = :userId AND serverId = :serverId AND favorite = 1"
    )
    fun getFavoriteItemsFlow(userId: UUID, serverId: String): Flow<List<AfinityUserDataDto>>

    @Query(
        "SELECT * FROM userdata WHERE userId = :userId AND serverId = :serverId AND playbackPositionTicks > 0 AND played = 0"
    )
    suspend fun getContinueWatchingItems(userId: UUID, serverId: String): List<AfinityUserDataDto>

    @Query(
        "SELECT * FROM userdata WHERE userId = :userId AND serverId = :serverId AND playbackPositionTicks > 0 AND played = 0"
    )
    fun getContinueWatchingItemsFlow(userId: UUID, serverId: String): Flow<List<AfinityUserDataDto>>

    @Query(
        "SELECT * FROM userdata WHERE userId = :userId AND serverId = :serverId AND toBeSynced = 1"
    )
    suspend fun getUnsyncedUserData(userId: UUID, serverId: String): List<AfinityUserDataDto>

    @Query(
        "UPDATE userdata SET toBeSynced = 0 WHERE userId = :userId AND itemId = :itemId AND serverId = :serverId"
    )
    suspend fun markUserDataSynced(userId: UUID, itemId: UUID, serverId: String)

    @Query(
        "UPDATE userdata SET toBeSynced = 1 WHERE userId = :userId AND itemId = :itemId AND serverId = :serverId"
    )
    suspend fun markUserDataUnsynced(userId: UUID, itemId: UUID, serverId: String)

    @Query("SELECT COUNT(*) FROM userdata WHERE userId = :userId AND serverId = :serverId")
    suspend fun getUserDataCount(userId: UUID, serverId: String): Int

    @Query("SELECT COUNT(*) FROM userdata WHERE toBeSynced = 1")
    suspend fun countUserDataToSync(): Int

    @Query(
        """
        UPDATE userdata
        SET played = :isPlayed,
            playbackPositionTicks = :positionTicks,
            favorite = :isFavorite,
            likes = :isLiked,
            unplayedItemCount = COALESCE(:unplayedItemCount, unplayedItemCount),
            playCount = COALESCE(:playCount, playCount)
        WHERE itemId = :itemId
          AND userId = :userId
          AND serverId = :serverId
          AND toBeSynced = 0
    """
    )
    suspend fun updateUserDataLocally(
        itemId: UUID,
        userId: UUID,
        serverId: String,
        isPlayed: Boolean,
        positionTicks: Long,
        isFavorite: Boolean,
        isLiked: Boolean,
        unplayedItemCount: Int?,
        playCount: Int?,
    ): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertUserDataIfAbsent(userData: AfinityUserDataDto): Long

    @Transaction
    suspend fun patchUserDataLocally(
        itemId: UUID,
        userId: UUID,
        serverId: String,
        isPlayed: Boolean,
        positionTicks: Long,
        isFavorite: Boolean,
        isLiked: Boolean,
        unplayedItemCount: Int?,
        playCount: Int?,
    ) {
        val updated =
            updateUserDataLocally(
                itemId = itemId,
                userId = userId,
                serverId = serverId,
                isPlayed = isPlayed,
                positionTicks = positionTicks,
                isFavorite = isFavorite,
                isLiked = isLiked,
                unplayedItemCount = unplayedItemCount,
                playCount = playCount,
            )
        if (updated > 0) return
        insertUserDataIfAbsent(
            AfinityUserDataDto(
                userId = userId,
                itemId = itemId,
                serverId = serverId,
                played = isPlayed,
                favorite = isFavorite,
                likes = isLiked,
                playbackPositionTicks = positionTicks,
                unplayedItemCount = unplayedItemCount,
                playCount = playCount,
            )
        )
    }

    @Query(
        """
        SELECT * FROM userdata
        WHERE userId = :userId AND serverId = :serverId AND itemId IN (:itemIds)
    """
    )
    fun getUserDataForItemsFlow(
        userId: UUID,
        serverId: String,
        itemIds: Collection<UUID>,
    ): Flow<List<AfinityUserDataDto>>

    @Query("SELECT * FROM userdata WHERE userId = :userId AND serverId = :serverId AND likes = 1")
    fun getWatchlistItemsFlow(userId: UUID, serverId: String): Flow<List<AfinityUserDataDto>>

    @Query("DELETE FROM userdata") suspend fun deleteAllUserData()
}
