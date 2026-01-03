package com.makd.afinity.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "jellyseerr_config")
data class JellyseerrConfigEntity(
    @PrimaryKey
    val id: Int = 1,
    val serverUrl: String,
    val isLoggedIn: Boolean,
    val username: String?,
    val userId: Int?,
    val permissions: Int?
)
