package com.makd.afinity.data.repository.download

import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.download.DownloadStatus
import kotlinx.coroutines.flow.Flow
import java.util.UUID

interface DownloadRepository {

    suspend fun startDownload(
        itemId: UUID,
        sourceId: String,
        volumeId: String? = null,
    ): Result<UUID>

    suspend fun pauseDownload(downloadId: UUID): Result<Unit>

    suspend fun resumeDownload(downloadId: UUID): Result<Unit>

    suspend fun cancelDownload(downloadId: UUID): Result<Unit>

    suspend fun deleteDownload(downloadId: UUID): Result<Unit>

    /**
     * Removes a download's database records (and local sources) **without** touching files on disk.
     * Used to clear an entry whose storage volume is currently unavailable (e.g. SD card removed),
     * leaving any on-volume files to be reclaimed when the volume returns.
     */
    suspend fun removeDownloadRecord(downloadId: UUID): Result<Unit>

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

    suspend fun getStorageUsedPerVolume(): Map<String, Long>

    suspend fun getStorageUsedPerVolumeAllServers(): Map<String, Long>

    suspend fun startSeasonDownload(
        seasonId: UUID,
        seriesId: UUID? = null,
        volumeId: String? = null,
    ): Result<Int>

    suspend fun startSeriesDownload(showId: UUID, volumeId: String? = null): Result<Int>

    suspend fun cancelAllSeriesDownloads(showId: UUID): Result<Unit>

    suspend fun cancelAllSeasonDownloads(seriesId: UUID, seasonNumber: Int): Result<Unit>

    suspend fun startAlbumDownload(albumId: UUID, volumeId: String? = null): Result<Int>

    suspend fun startArtistDownload(artistId: UUID, volumeId: String? = null): Result<Int>

    suspend fun startPlaylistDownload(playlistId: UUID, volumeId: String? = null): Result<Int>
}
