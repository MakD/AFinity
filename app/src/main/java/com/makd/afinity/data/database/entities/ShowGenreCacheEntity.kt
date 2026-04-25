package com.makd.afinity.data.database.entities

import androidx.room.Entity

@Entity(tableName = "show_genre_cache", primaryKeys = ["genreName", "serverId", "userId"])
data class ShowGenreCacheEntity(
    val genreName: String,
    val serverId: String,
    val userId: String,
    val lastFetchedTimestamp: Long,
    val showCount: Int = 0,
)