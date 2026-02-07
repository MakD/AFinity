package com.makd.afinity.data.database.entities

import androidx.room.Entity

@Entity(tableName = "jellyseerr_config", primaryKeys = ["jellyfinServerId", "jellyfinUserId"])
data class JellyseerrConfigEntity(
    val jellyfinServerId: String,
    val jellyfinUserId: String,
    val serverUrl: String,
    val isLoggedIn: Boolean,
    val username: String?,
    val userId: Int?,
    val permissions: Int?,
)
