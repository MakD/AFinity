package com.makd.afinity.data.database.entities

import androidx.room.Entity

@Entity(tableName = "genre_cache", primaryKeys = ["genreName", "serverId", "userId"])
data class GenreCacheEntity(
    val genreName: String,
    val serverId: String,
    val userId: String,
    val lastFetchedTimestamp: Long,
    val movieCount: Int = 0,
)