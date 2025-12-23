package com.makd.afinity.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "genre_cache")
data class GenreCacheEntity(
    @PrimaryKey
    val genreName: String,
    val lastFetchedTimestamp: Long,
    val movieCount: Int = 0
)