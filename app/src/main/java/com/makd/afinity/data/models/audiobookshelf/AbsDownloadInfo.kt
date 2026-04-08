package com.makd.afinity.data.models.audiobookshelf

import java.util.UUID

data class AbsDownloadInfo(
    val id: UUID,
    val libraryItemId: String,
    val episodeId: String?,
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
    val episodeDescription: String? = null,
    val publishedAt: Long? = null,
)