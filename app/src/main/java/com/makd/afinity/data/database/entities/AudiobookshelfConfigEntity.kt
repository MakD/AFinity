package com.makd.afinity.data.database.entities

import androidx.room.Entity

@Entity(
    tableName = "audiobookshelf_config",
    primaryKeys = ["jellyfinServerId", "jellyfinUserId"]
)
data class AudiobookshelfConfigEntity(
    val jellyfinServerId: String,
    val jellyfinUserId: String,
    val serverUrl: String,
    val absUserId: String,
    val username: String,
    val isLoggedIn: Boolean,
    val lastSync: Long
)