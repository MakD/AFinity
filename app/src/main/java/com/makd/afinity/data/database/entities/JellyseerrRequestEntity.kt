package com.makd.afinity.data.database.entities

import androidx.room.Entity

@Entity(
    tableName = "jellyseerr_requests",
    primaryKeys = ["id", "jellyfinServerId", "jellyfinUserId"],
)
data class JellyseerrRequestEntity(
    val id: Int,
    val jellyfinServerId: String,
    val jellyfinUserId: String,
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
    val cachedAt: Long = System.currentTimeMillis(),
    val mediaTitle: String? = null,
    val mediaName: String? = null,
    val mediaBackdropPath: String? = null,
    val mediaReleaseDate: String? = null,
    val mediaFirstAirDate: String? = null,
    val mediaStatus: Int? = null,
    val is4k: Boolean = false,
    val serverId: Int? = null,
    val profileId: Int? = null,
    val rootFolder: String? = null,
    val seasonsJson: String? = null,
)
