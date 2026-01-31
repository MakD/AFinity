package com.makd.afinity.data.models.download

import java.util.UUID

data class DownloadInfo(
    val id: UUID,
    val itemId: UUID,
    val itemName: String,
    val itemType: String,
    val sourceId: String,
    val sourceName: String,
    val status: DownloadStatus,
    val progress: Float,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val filePath: String?,
    val error: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val serverId: String,
    val userId: UUID,
)
