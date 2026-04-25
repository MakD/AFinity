package com.makd.afinity.data.database.entities

import androidx.room.Entity

@Entity(
    tableName = "genre_show_cache",
    primaryKeys = ["genreName", "showId", "serverId", "userId"],
)
data class GenreShowCacheEntity(
    val genreName: String,
    val showId: String,
    val serverId: String,
    val userId: String,
    val showData: String,
    val position: Int,
    val cachedTimestamp: Long,
)