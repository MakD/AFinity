package com.makd.afinity.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "show_genre_cache")
data class ShowGenreCacheEntity(
    @PrimaryKey val genreName: String,
    val lastFetchedTimestamp: Long,
    val showCount: Int = 0,
)
