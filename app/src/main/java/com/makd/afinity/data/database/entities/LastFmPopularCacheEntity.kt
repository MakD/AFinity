package com.makd.afinity.data.database.entities

import androidx.room.Entity

@Entity(tableName = "lastfm_popular_cache", primaryKeys = ["serverId", "userId", "artistId"])
data class LastFmPopularCacheEntity(
    val serverId: String,
    val userId: String,
    val artistId: String,
    val trackIds: List<String> = emptyList(),
    val trackCount: Int = 0,
    val lastFmVersion: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)
