package com.makd.afinity.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.makd.afinity.data.models.audiobookshelf.AbsDownloadStatus
import java.util.UUID

@Entity(
    tableName = "abs_downloads",
    indices = [
        Index(
            value = ["libraryItemId", "episodeId", "jellyfinServerId", "jellyfinUserId"],
            unique = true,
        )
    ],
)
data class AbsDownloadEntity(
    @PrimaryKey val id: UUID,
    val libraryItemId: String,
    val episodeId: String?,
    val jellyfinServerId: String,
    val jellyfinUserId: String,
    val title: String,
    val authorName: String?,
    val mediaType: String,
    val coverUrl: String?,
    val duration: Double,
    val status: AbsDownloadStatus,
    val progress: Float,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val tracksTotal: Int,
    val tracksDownloaded: Int,
    val error: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val localDirPath: String?,
    val serializedSession: String?,
    val episodeDescription: String? = null,
    val publishedAt: Long? = null,
)