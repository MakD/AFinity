package com.makd.afinity.data.database.entities

import androidx.room.Entity

@Entity(tableName = "studio_cache", primaryKeys = ["studioId", "serverId", "userId"])
data class StudioCacheEntity(
    val studioId: String,
    val serverId: String,
    val userId: String,
    val studioData: String,
    val position: Int,
    val cachedTimestamp: Long,
)