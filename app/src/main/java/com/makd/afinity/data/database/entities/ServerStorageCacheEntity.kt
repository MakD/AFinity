package com.makd.afinity.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "server_storage_cache")
data class ServerStorageCacheEntity(
    @PrimaryKey val serverId: String,
    val payload: String,
    val lastUpdated: Long = System.currentTimeMillis(),
)
