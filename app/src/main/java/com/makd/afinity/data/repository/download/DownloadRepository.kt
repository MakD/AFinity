package com.makd.afinity.data.repository.download

import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.download.DownloadStatus
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface DownloadRepository {

    suspend fun startDownload(itemId: UUID, sourceId: String): Result<UUID>

    suspend fun pauseDownload(downloadId: UUID): Result<Unit>

    suspend fun resumeDownload(downloadId: UUID): Result<Unit>

    suspend fun cancelDownload(downloadId: UUID): Result<Unit>

    suspend fun deleteDownload(downloadId: UUID): Result<Unit>

    suspend fun getDownload(downloadId: UUID): DownloadInfo?

    suspend fun getDownloadByItemId(itemId: UUID): DownloadInfo?

    fun getAllDownloadsFlow(): Flow<List<DownloadInfo>>

    fun getDownloadsByStatusFlow(statuses: List<DownloadStatus>): Flow<List<DownloadInfo>>

    fun getActiveDownloadsFlow(): Flow<List<DownloadInfo>>

    fun getCompletedDownloadsFlow(): Flow<List<DownloadInfo>>

    suspend fun isItemDownloaded(itemId: UUID): Boolean

    suspend fun isItemDownloading(itemId: UUID): Boolean

    suspend fun getTotalStorageUsed(): Long

    suspend fun getTotalStorageUsedAllServers(): Long
}
