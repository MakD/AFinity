package com.makd.afinity.data.database.entities

import androidx.room.Entity

@Entity(tableName = "boxset_cache", primaryKeys = ["itemId", "serverId", "userId"])
data class BoxSetCacheEntity(
    val itemId: String,
    val serverId: String,
    val userId: String,
    val boxSetIds: String,
    val lastUpdated: Long,
)