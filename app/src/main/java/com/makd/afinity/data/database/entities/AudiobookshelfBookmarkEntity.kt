package com.makd.afinity.data.database.entities

import androidx.room.Entity

@Entity(
    tableName = "audiobookshelf_bookmarks",
    primaryKeys = ["jellyfinServerId", "jellyfinUserId", "libraryItemId", "time"],
)
data class AudiobookshelfBookmarkEntity(
    val jellyfinServerId: String,
    val jellyfinUserId: String,
    val libraryItemId: String,
    val time: Long,
    val serverTime: Double,
    val title: String,
    val createdAt: Long,
    val pendingSync: Boolean = false,
    val deleted: Boolean = false,
    val updatedAt: Long = 0L,
)
