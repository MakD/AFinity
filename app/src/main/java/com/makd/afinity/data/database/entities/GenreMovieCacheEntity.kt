package com.makd.afinity.data.database.entities

import androidx.room.Entity

@Entity(tableName = "genre_movie_cache", primaryKeys = ["genreName", "movieId"])
data class GenreMovieCacheEntity(
    val genreName: String,
    val movieId: String,
    val movieData: String,
    val position: Int,
    val cachedTimestamp: Long,
)
