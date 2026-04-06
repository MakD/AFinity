package com.makd.afinity.data.repository.audiobookshelf

import com.makd.afinity.data.models.audiobookshelf.AbsDownloadInfo
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface AbsDownloadRepository {
    fun getActiveDownloadsFlow(): Flow<List<AbsDownloadInfo>>

    fun getCompletedDownloadsFlow(): Flow<List<AbsDownloadInfo>>

    suspend fun isItemDownloaded(libraryItemId: String, episodeId: String? = null): Boolean

    suspend fun getDownload(libraryItemId: String, episodeId: String? = null): AbsDownloadInfo?

    suspend fun startDownload(libraryItemId: String, episodeId: String? = null): Result<UUID>

    suspend fun cancelDownload(downloadId: UUID): Result<Unit>

    suspend fun deleteDownload(downloadId: UUID): Result<Unit>

    suspend fun getTotalStorageUsed(): Long
}