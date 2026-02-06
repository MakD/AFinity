package com.makd.afinity.data.database.entities

import androidx.room.Entity

@Entity(
    tableName = "audiobookshelf_progress",
    primaryKeys = ["id", "jellyfinServerId", "jellyfinUserId"],
)
data class AudiobookshelfProgressEntity(
    val id: String,
    val jellyfinServerId: String,
    val jellyfinUserId: String,
    val libraryItemId: String,
    val episodeId: String?,
    val currentTime: Double,
    val duration: Double,
    val progress: Double,
    val isFinished: Boolean,
    val lastUpdate: Long,
    val startedAt: Long,
    val finishedAt: Long?,
    val pendingSync: Boolean,
)
