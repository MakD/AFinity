package com.makd.afinity.data.database.entities

import androidx.room.Entity

@Entity(
    tableName = "audiobookshelf_libraries",
    primaryKeys = ["id", "jellyfinServerId", "jellyfinUserId"],
)
data class AudiobookshelfLibraryEntity(
    val id: String,
    val jellyfinServerId: String,
    val jellyfinUserId: String,
    val name: String,
    val mediaType: String,
    val icon: String?,
    val displayOrder: Int,
    val totalItems: Int,
    val totalDuration: Double?,
    val lastUpdated: Long,
    val cachedAt: Long,
)
