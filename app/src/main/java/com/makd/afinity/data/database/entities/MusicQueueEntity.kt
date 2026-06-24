package com.makd.afinity.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "music_queue")
data class MusicQueueEntity(
    @PrimaryKey val position: Int,
    val trackId: String,
    val name: String,
    val artist: String?,
    val albumId: String?,
    val album: String?,
    val durationMs: Long,
    val imageUrl: String?,
    val normalizationGain: Float?,
    val indexNumber: Int?,
    val discNumber: Int?,
    val serverId: String,
)