package com.makd.afinity.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "audiobookshelf_episodes",
    primaryKeys = ["id", "libraryItemId", "jellyfinServerId", "jellyfinUserId"],
    foreignKeys =
        [
            ForeignKey(
                entity = AudiobookshelfItemEntity::class,
                parentColumns = ["id", "jellyfinServerId", "jellyfinUserId"],
                childColumns = ["libraryItemId", "jellyfinServerId", "jellyfinUserId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index(value = ["libraryItemId", "jellyfinServerId", "jellyfinUserId"])],
)
data class AudiobookshelfEpisodeEntity(
    val id: String,
    val libraryItemId: String,
    val jellyfinServerId: String,
    val jellyfinUserId: String,
    val oldEpisodeId: String?,
    val episodeIndex: Int?,
    val season: String?,
    val episode: String?,
    val episodeType: String?,
    val title: String,
    val subtitle: String?,
    val description: String?,
    val enclosureUrl: String?,
    val enclosureType: String?,
    val enclosureLength: String?,
    val guid: String?,
    val pubDate: String?,
    val serializedChapters: String?,
    val serializedAudioFile: String?,
    val serializedAudioTrack: String?,
    val publishedAt: Long?,
    val addedAt: Long?,
    val updatedAt: Long?,
    val duration: Double?,
    val size: Long?,
)