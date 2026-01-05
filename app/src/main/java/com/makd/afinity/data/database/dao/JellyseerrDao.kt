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

    @Query("SELECT * FROM jellyseerr_requests ORDER BY requestedAt DESC")
    fun getAllRequests(): Flow<List<JellyseerrRequestEntity>>

    @Query("SELECT * FROM jellyseerr_requests WHERE id = :requestId")
    suspend fun getRequestById(requestId: Int): JellyseerrRequestEntity?

    @Query("SELECT * FROM jellyseerr_requests WHERE status = :status ORDER BY requestedAt DESC")
    fun getRequestsByStatus(status: Int): Flow<List<JellyseerrRequestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(request: JellyseerrRequestEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequests(requests: List<JellyseerrRequestEntity>)

    @Query("DELETE FROM jellyseerr_requests WHERE id = :requestId")
    suspend fun deleteRequest(requestId: Int)

    @Query("DELETE FROM jellyseerr_requests")
    suspend fun clearAllRequests()

    @Query("DELETE FROM jellyseerr_requests WHERE cachedAt < :expiryTime")
    suspend fun deleteExpiredRequests(expiryTime: Long)

    @Query("SELECT * FROM jellyseerr_config WHERE id = 1")
    suspend fun getConfig(): JellyseerrConfigEntity?

    @Query("SELECT * FROM jellyseerr_config WHERE id = 1")
    fun getConfigFlow(): Flow<JellyseerrConfigEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveConfig(config: JellyseerrConfigEntity)

    @Query("DELETE FROM jellyseerr_config")
    suspend fun clearConfig()
}
