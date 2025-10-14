package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.makd.afinity.data.models.server.Server
import com.makd.afinity.data.models.server.ServerWithAddressAndUser
import com.makd.afinity.data.models.server.ServerWithAddresses
import com.makd.afinity.data.models.server.ServerWithAddressesAndUsers
import com.makd.afinity.data.models.server.ServerWithUsers
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: Server)

    @Update
    suspend fun updateServer(server: Server)

    @Delete
    suspend fun deleteServer(server: Server)

    @Query("DELETE FROM servers WHERE id = :serverId")
    suspend fun deleteServerById(serverId: String)

    @Query("SELECT * FROM servers WHERE id = :serverId")
    suspend fun getServer(serverId: String): Server?

    @Query("SELECT * FROM servers")
    suspend fun getAllServers(): List<Server>

    @Query("SELECT * FROM servers")
    fun getAllServersFlow(): Flow<List<Server>>

    @Transaction
    @Query("SELECT * FROM servers WHERE id = :serverId")
    suspend fun getServerWithAddresses(serverId: String): ServerWithAddresses?

    @Transaction
    @Query("SELECT * FROM servers")
    suspend fun getAllServersWithAddresses(): List<ServerWithAddresses>

    @Transaction
    @Query("SELECT * FROM servers WHERE id = :serverId")
    suspend fun getServerWithAddressAndUser(serverId: String): ServerWithAddressAndUser?

    @Transaction
    @Query("SELECT * FROM servers WHERE id = :serverId")
    suspend fun getServerWithAddressesAndUsers(serverId: String): ServerWithAddressesAndUsers?

    @Transaction
    @Query("SELECT * FROM servers WHERE currentUserId IS NOT NULL LIMIT 1")
    suspend fun getCurrentServerWithAddressAndUser(): ServerWithAddressAndUser?

    @Transaction
    @Query("SELECT * FROM servers WHERE id = :serverId")
    suspend fun getServerWithUsers(serverId: String): ServerWithUsers?
}