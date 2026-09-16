package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.makd.afinity.data.models.server.ServerAddressMemory

@Dao
interface ServerAddressMemoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memory: ServerAddressMemory)

    @Query(
        "SELECT * FROM serverAddressMemory WHERE serverId = :serverId AND networkKey = :networkKey"
    )
    suspend fun get(serverId: String, networkKey: String): ServerAddressMemory?

    @Query("DELETE FROM serverAddressMemory WHERE serverId = :serverId")
    suspend fun deleteForServer(serverId: String)
}
