package com.makd.afinity.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.makd.afinity.data.database.entities.AbsDownloadEntity
import com.makd.afinity.data.models.audiobookshelf.AbsDownloadStatus
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface AbsDownloadDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: AbsDownloadEntity)

    @Query("SELECT * FROM abs_downloads WHERE id = :id")
    suspend fun getById(id: UUID): AbsDownloadEntity?

    @Query(
        """SELECT * FROM abs_downloads
           WHERE libraryItemId = :libraryItemId
             AND episodeId IS NULL
             AND jellyfinServerId = :serverId
             AND jellyfinUserId = :userId
           LIMIT 1"""
    )
    suspend fun getDownloadForBook(
        libraryItemId: String,
        serverId: String,
        userId: String,
    ): AbsDownloadEntity?

    @Query(
        """SELECT * FROM abs_downloads
           WHERE libraryItemId = :libraryItemId
             AND episodeId = :episodeId
             AND jellyfinServerId = :serverId
             AND jellyfinUserId = :userId
           LIMIT 1"""
    )
    suspend fun getDownloadForEpisode(
        libraryItemId: String,
        episodeId: String,
        serverId: String,
        userId: String,
    ): AbsDownloadEntity?

    @Query(
        """SELECT * FROM abs_downloads
           WHERE libraryItemId = :libraryItemId
             AND episodeId IS NOT NULL
             AND status = 'COMPLETED'
             AND jellyfinServerId = :serverId
             AND jellyfinUserId = :userId
           ORDER BY updatedAt DESC
           LIMIT 1"""
    )
    suspend fun getFirstCompletedEpisodeForItem(
        libraryItemId: String,
        serverId: String,
        userId: String,
    ): AbsDownloadEntity?

    @Query(
        """SELECT * FROM abs_downloads
           WHERE libraryItemId = :libraryItemId
             AND episodeId IS NOT NULL
             AND status = 'COMPLETED'
             AND jellyfinServerId = :serverId
             AND jellyfinUserId = :userId
           ORDER BY updatedAt DESC"""
    )
    suspend fun getCompletedEpisodesForItem(
        libraryItemId: String,
        serverId: String,
        userId: String,
    ): List<AbsDownloadEntity>

    @Query(
        """SELECT * FROM abs_downloads
           WHERE status IN ('QUEUED', 'DOWNLOADING')
             AND jellyfinServerId = :serverId
             AND jellyfinUserId = :userId
           ORDER BY createdAt DESC"""
    )
    fun getActiveDownloadsFlow(serverId: String, userId: String): Flow<List<AbsDownloadEntity>>

    @Query(
        """SELECT * FROM abs_downloads
           WHERE status = 'COMPLETED'
             AND jellyfinServerId = :serverId
             AND jellyfinUserId = :userId
           ORDER BY updatedAt DESC"""
    )
    fun getCompletedDownloadsFlow(serverId: String, userId: String): Flow<List<AbsDownloadEntity>>

    @Query(
        """SELECT * FROM abs_downloads
           WHERE status = 'COMPLETED'
             AND jellyfinServerId = :serverId
             AND jellyfinUserId = :userId
           ORDER BY updatedAt DESC"""
    )
    suspend fun getCompletedDownloads(serverId: String, userId: String): List<AbsDownloadEntity>

    @Query(
        """SELECT COUNT(*) FROM abs_downloads
           WHERE libraryItemId = :libraryItemId
             AND episodeId IS NULL
             AND status = 'COMPLETED'
             AND jellyfinServerId = :serverId
             AND jellyfinUserId = :userId"""
    )
    suspend fun isBookDownloaded(libraryItemId: String, serverId: String, userId: String): Int

    @Query(
        """SELECT COUNT(*) FROM abs_downloads
           WHERE libraryItemId = :libraryItemId
             AND episodeId = :episodeId
             AND status = 'COMPLETED'
             AND jellyfinServerId = :serverId
             AND jellyfinUserId = :userId"""
    )
    suspend fun isEpisodeDownloaded(
        libraryItemId: String,
        episodeId: String,
        serverId: String,
        userId: String,
    ): Int

    @Query(
        """UPDATE abs_downloads
           SET status = :status,
               progress = :progress,
               bytesDownloaded = :bytesDownloaded,
               tracksDownloaded = :tracksDownloaded,
               serializedSession = COALESCE(:serializedSession, serializedSession),
               updatedAt = :updatedAt
           WHERE id = :id"""
    )
    suspend fun updateProgress(
        id: UUID,
        status: AbsDownloadStatus,
        progress: Float,
        bytesDownloaded: Long,
        tracksDownloaded: Int,
        serializedSession: String?,
        updatedAt: Long,
    )

    @Query("UPDATE abs_downloads SET status = :status, error = :error, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: UUID, status: AbsDownloadStatus, error: String?, updatedAt: Long)

    @Query("DELETE FROM abs_downloads WHERE id = :id")
    suspend fun deleteById(id: UUID)

    @Query(
        """SELECT COALESCE(SUM(bytesDownloaded), 0) FROM abs_downloads
           WHERE status = 'COMPLETED'
             AND jellyfinServerId = :serverId
             AND jellyfinUserId = :userId"""
    )
    suspend fun getTotalBytesForServer(serverId: String, userId: String): Long
}