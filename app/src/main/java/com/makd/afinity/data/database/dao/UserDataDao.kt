package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.makd.afinity.data.models.user.AfinityUserDataDto
import java.util.UUID
import kotlinx.coroutines.flow.Flow

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

    @Query("DELETE FROM userdata") suspend fun deleteAllUserData()
}
