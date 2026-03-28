package com.makd.afinity.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "jellyfin_stats_cache")
data class JellyfinStatsCacheEntity(
    @PrimaryKey val serverId: String,
    val movieCount: Int,
    val seriesCount: Int,
    val episodeCount: Int,
    val boxsetCount: Int,
    val lastUpdated: Long = System.currentTimeMillis(),
)
