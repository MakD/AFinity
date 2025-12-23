package com.makd.afinity.data.database.entities

import androidx.room.Entity

@Entity(
    tableName = "genre_show_cache",
    primaryKeys = ["genreName", "showId"]
)
data class GenreShowCacheEntity(
    val genreName: String,
    val showId: String,
    val showData: String,
    val position: Int,
    val cachedTimestamp: Long
)