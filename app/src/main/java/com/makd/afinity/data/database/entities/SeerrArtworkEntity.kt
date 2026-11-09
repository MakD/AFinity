package com.makd.afinity.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "seerr_artwork")
data class SeerrArtworkEntity(
    @PrimaryKey val key: String,
    val tmdbId: Int,
    val mediaType: String,
    val title: String?,
    val posterPath: String?,
    val backdropPath: String?,
    val releaseDate: String?,
    val firstAirDate: String?,
    val voteAverage: Double?,
    val fetchedAt: Long = System.currentTimeMillis(),
)
