package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.makd.afinity.data.models.server.ServerAddress
import java.util.UUID

@Dao
interface ServerAddressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServerAddress(serverAddress: ServerAddress)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServerAddresses(serverAddresses: List<ServerAddress>)

    @Update suspend fun updateServerAddress(serverAddress: ServerAddress)

    @Delete suspend fun deleteServerAddress(serverAddress: ServerAddress)

    @Query("DELETE FROM serverAddresses WHERE id = :addressId")
    suspend fun deleteServerAddressById(addressId: UUID)

    @Query("DELETE FROM serverAddresses WHERE serverId = :serverId")
    suspend fun deleteServerAddressesByServerId(serverId: String)

    @Query("SELECT * FROM serverAddresses WHERE id = :addressId")
    suspend fun getServerAddress(addressId: UUID): ServerAddress?

    @Query("SELECT * FROM serverAddresses WHERE serverId = :serverId")
    suspend fun getServerAddresses(serverId: String): List<ServerAddress>

    @Query("SELECT * FROM serverAddresses WHERE serverId = :serverId AND address = :address")
    suspend fun getServerAddressByUrl(serverId: String, address: String): ServerAddress?
}
