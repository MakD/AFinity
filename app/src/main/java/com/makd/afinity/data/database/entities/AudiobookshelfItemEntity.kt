package com.makd.afinity.data.database.entities

import androidx.room.Entity

@Entity(
    tableName = "audiobookshelf_items",
    primaryKeys = ["id", "jellyfinServerId", "jellyfinUserId"]
)
data class AudiobookshelfItemEntity(
    val id: String,
    val jellyfinServerId: String,
    val jellyfinUserId: String,
    val libraryId: String,
    val title: String,
    val authorName: String?,
    val narratorName: String?,
    val seriesName: String?,
    val seriesSequence: String?,
    val mediaType: String,
    val duration: Double?,
    val coverUrl: String?,
    val description: String?,
    val publishedYear: String?,
    val genres: String?,
    val numTracks: Int?,
    val numChapters: Int?,
    val addedAt: Long?,
    val updatedAt: Long?,
    val cachedAt: Long
)
