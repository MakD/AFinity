package com.makd.afinity.data.database.entities

import androidx.room.Entity

@Entity(tableName = "boxset_cache_metadata", primaryKeys = ["serverId", "userId"])
data class BoxSetCacheMetadata(
    val serverId: String,
    val userId: String,
    val lastFullBuild: Long,
    val cacheVersion: Int,
)