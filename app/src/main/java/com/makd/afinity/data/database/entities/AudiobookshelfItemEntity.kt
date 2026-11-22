package com.makd.afinity.data.database.entities

import androidx.room.Entity

@Entity(
    tableName = "audiobookshelf_items",
    primaryKeys = ["id", "jellyfinServerId", "jellyfinUserId"],
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
    val cachedAt: Long,
    val numEpisodesIncomplete: Int? = null,
    val titleIgnorePrefix: String? = null,
    val authorNameLF: String? = null,
    val podcastAuthor: String? = null,
    val tags: String? = null,
    val publisher: String? = null,
    val language: String? = null,
    val explicit: Boolean? = null,
    val abridged: Boolean? = null,
    val size: Long? = null,
    val numEpisodes: Int? = null,
)
