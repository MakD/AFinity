package com.makd.afinity.data.database.entities

import androidx.room.Entity

@Entity(
    tableName = "music_lyrics",
    primaryKeys = ["trackId", "serverId", "userId"],
)
data class MusicLyricsEntity(
    val trackId: String,
    val serverId: String,
    val userId: String,
    val lyricsJson: String,
    val cachedAt: Long = System.currentTimeMillis(),
)