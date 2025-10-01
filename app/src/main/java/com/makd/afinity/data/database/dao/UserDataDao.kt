package com.makd.afinity.data.database.dao

import androidx.room.*
import com.makd.afinity.data.models.user.AfinityUserDataDto
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface UserDataDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserData(userData: AfinityUserDataDto)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserDataList(userDataList: List<AfinityUserDataDto>)

    @Update
    suspend fun updateUserData(userData: AfinityUserDataDto)

    @Delete
    suspend fun deleteUserData(userData: AfinityUserDataDto)

    @Query("DELETE FROM userdata WHERE userId = :userId AND itemId = :itemId")
    suspend fun deleteUserDataByIds(userId: UUID, itemId: UUID)

    @Query("DELETE FROM userdata WHERE userId = :userId")
    suspend fun deleteUserDataByUserId(userId: UUID)

    @Query("SELECT * FROM userdata WHERE userId = :userId AND itemId = :itemId")
    suspend fun getUserData(userId: UUID, itemId: UUID): AfinityUserDataDto?

    @Query("SELECT * FROM userdata WHERE userId = :userId")
    suspend fun getAllUserData(userId: UUID): List<AfinityUserDataDto>

    @Query("SELECT * FROM userdata WHERE userId = :userId")
    fun getAllUserDataFlow(userId: UUID): Flow<List<AfinityUserDataDto>>

    @Query("SELECT * FROM userdata WHERE userId = :userId AND favorite = 1")
    suspend fun getFavoriteItems(userId: UUID): List<AfinityUserDataDto>

    @Query("SELECT * FROM userdata WHERE userId = :userId AND favorite = 1")
    fun getFavoriteItemsFlow(userId: UUID): Flow<List<AfinityUserDataDto>>

    @Query("SELECT * FROM userdata WHERE userId = :userId AND playbackPositionTicks > 0 AND played = 0")
    suspend fun getContinueWatchingItems(userId: UUID): List<AfinityUserDataDto>

    @Query("SELECT * FROM userdata WHERE userId = :userId AND playbackPositionTicks > 0 AND played = 0")
    fun getContinueWatchingItemsFlow(userId: UUID): Flow<List<AfinityUserDataDto>>

    @Query("SELECT * FROM userdata WHERE userId = :userId AND toBeSynced = 1")
    suspend fun getUnsyncedUserData(userId: UUID): List<AfinityUserDataDto>

    @Query("UPDATE userdata SET toBeSynced = 0 WHERE userId = :userId AND itemId = :itemId")
    suspend fun markUserDataSynced(userId: UUID, itemId: UUID)

    @Query("UPDATE userdata SET toBeSynced = 1 WHERE userId = :userId AND itemId = :itemId")
    suspend fun markUserDataUnsynced(userId: UUID, itemId: UUID)

    @Query("SELECT COUNT(*) FROM userdata WHERE userId = :userId")
    suspend fun getUserDataCount(userId: UUID): Int

    @Query("DELETE FROM userdata")
    suspend fun deleteAllUserData()
}