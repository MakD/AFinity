package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.makd.afinity.data.database.entities.JellyseerrConfigEntity
import com.makd.afinity.data.database.entities.JellyseerrRequestEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface JellyseerrDao {

    @Query("SELECT * FROM jellyseerr_requests WHERE jellyfinServerId = :serverId AND jellyfinUserId = :userId ORDER BY requestedAt DESC")
    fun getAllRequests(serverId: String, userId: String): Flow<List<JellyseerrRequestEntity>>

    @Query("SELECT * FROM jellyseerr_requests WHERE id = :requestId AND jellyfinServerId = :serverId AND jellyfinUserId = :userId")
    suspend fun getRequestById(requestId: Int, serverId: String, userId: String): JellyseerrRequestEntity?

    @Query("SELECT * FROM jellyseerr_requests WHERE status = :status AND jellyfinServerId = :serverId AND jellyfinUserId = :userId ORDER BY requestedAt DESC")
    fun getRequestsByStatus(status: Int, serverId: String, userId: String): Flow<List<JellyseerrRequestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(request: JellyseerrRequestEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequests(requests: List<JellyseerrRequestEntity>)

    @Query("DELETE FROM jellyseerr_requests WHERE id = :requestId AND jellyfinServerId = :serverId AND jellyfinUserId = :userId")
    suspend fun deleteRequest(requestId: Int, serverId: String, userId: String)

    @Query("DELETE FROM jellyseerr_requests WHERE jellyfinServerId = :serverId AND jellyfinUserId = :userId")
    suspend fun clearAllRequests(serverId: String, userId: String)

    @Query("DELETE FROM jellyseerr_requests WHERE cachedAt < :expiryTime AND jellyfinServerId = :serverId AND jellyfinUserId = :userId")
    suspend fun deleteExpiredRequests(expiryTime: Long, serverId: String, userId: String)

    @Query("SELECT * FROM jellyseerr_config WHERE jellyfinServerId = :serverId AND jellyfinUserId = :userId")
    suspend fun getConfig(serverId: String, userId: String): JellyseerrConfigEntity?

    @Query("SELECT * FROM jellyseerr_config WHERE jellyfinServerId = :serverId AND jellyfinUserId = :userId")
    fun getConfigFlow(serverId: String, userId: String): Flow<JellyseerrConfigEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConfig(config: JellyseerrConfigEntity)

    @Query("DELETE FROM jellyseerr_config WHERE jellyfinServerId = :serverId AND jellyfinUserId = :userId")
    suspend fun clearConfig(serverId: String, userId: String)
}