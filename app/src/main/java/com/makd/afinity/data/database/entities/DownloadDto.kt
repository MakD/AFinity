package com.makd.afinity.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.download.DownloadStatus
import java.util.UUID

@Entity(tableName = "downloads")
data class DownloadDto(
    @PrimaryKey
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
    val userId: UUID
)

fun DownloadDto.toDownloadInfo(): DownloadInfo {
    return DownloadInfo(
        id = id,
        itemId = itemId,
        itemName = itemName,
        itemType = itemType,
        sourceId = sourceId,
        sourceName = sourceName,
        status = status,
        progress = progress,
        bytesDownloaded = bytesDownloaded,
        totalBytes = totalBytes,
        filePath = filePath,
        error = error,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}