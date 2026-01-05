package com.makd.afinity.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "jellyseerr_requests")
data class JellyseerrRequestEntity(
    @PrimaryKey
    val id: Int,
    val status: Int,
    val mediaType: String,
    val tmdbId: Int?,
    val tvdbId: Int?,
    val title: String,
    val posterPath: String?,
    val requestedAt: Long,
    val updatedAt: Long,
    val requestedByName: String?,
    val requestedByAvatar: String?,
    val cachedAt: Long = System.currentTimeMillis()
)