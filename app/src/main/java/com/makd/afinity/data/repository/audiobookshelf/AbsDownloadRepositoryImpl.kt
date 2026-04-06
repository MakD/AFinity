package com.makd.afinity.data.repository.audiobookshelf

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.makd.afinity.data.database.dao.AbsDownloadDao
import com.makd.afinity.data.database.entities.AbsDownloadEntity
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.audiobookshelf.AbsDownloadInfo
import com.makd.afinity.data.models.audiobookshelf.AbsDownloadStatus
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.workers.AbsMediaDownloadWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AbsDownloadRepositoryImpl
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
    private val absDownloadDao: AbsDownloadDao,
    private val preferencesRepository: PreferencesRepository,
    private val workManager: WorkManager,
) : AbsDownloadRepository {

    companion object {
        const val KEY_DOWNLOAD_ID = "abs_download_id"
        const val KEY_LIBRARY_ITEM_ID = "abs_library_item_id"
        const val KEY_EPISODE_ID = "abs_episode_id"
    }

    private val downloadBaseDir: File
        get() =
            File(context.getExternalFilesDir(null), "AFinity/Audiobookshelf").also {
                if (!it.exists()) it.mkdirs()
            }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getActiveDownloadsFlow(): Flow<List<AbsDownloadInfo>> =
        sessionManager.currentSession.flatMapLatest { session ->
            if (session == null) return@flatMapLatest flowOf(emptyList())
            absDownloadDao.getActiveDownloadsFlow(session.serverId, session.userId.toString())
                .map { entities -> entities.map { it.toAbsDownloadInfo() } }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getCompletedDownloadsFlow(): Flow<List<AbsDownloadInfo>> =
        sessionManager.currentSession.flatMapLatest { session ->
            if (session == null) return@flatMapLatest flowOf(emptyList())
            absDownloadDao.getCompletedDownloadsFlow(session.serverId, session.userId.toString())
                .map { entities -> entities.map { it.toAbsDownloadInfo() } }
        }

    override suspend fun isItemDownloaded(libraryItemId: String, episodeId: String?): Boolean {
        val session = sessionManager.currentSession.value ?: return false
        val serverId = session.serverId
        val userId = session.userId.toString()
        return if (episodeId != null) {
            absDownloadDao.isEpisodeDownloaded(libraryItemId, episodeId, serverId, userId) > 0
        } else {
            absDownloadDao.isBookDownloaded(libraryItemId, serverId, userId) > 0
        }
    }

    override suspend fun getDownload(libraryItemId: String, episodeId: String?): AbsDownloadInfo? {
        val session = sessionManager.currentSession.value ?: return null
        val serverId = session.serverId
        val userId = session.userId.toString()
        val entity = if (episodeId != null) {
            absDownloadDao.getDownloadForEpisode(libraryItemId, episodeId, serverId, userId)
        } else {
            absDownloadDao.getDownloadForBook(libraryItemId, serverId, userId)
        }
        return entity?.toAbsDownloadInfo()
    }

    override suspend fun startDownload(libraryItemId: String, episodeId: String?): Result<UUID> {
        val session =
            sessionManager.currentSession.value
                ?: return Result.failure(Exception("No active session"))

        val serverId = session.serverId
        val userId = session.userId.toString()

        val existing = if (episodeId != null) {
            absDownloadDao.getDownloadForEpisode(libraryItemId, episodeId, serverId, userId)
        } else {
            absDownloadDao.getDownloadForBook(libraryItemId, serverId, userId)
        }
        if (existing != null && existing.status in listOf(
                AbsDownloadStatus.QUEUED,
                AbsDownloadStatus.DOWNLOADING
            )
        ) {
            Timber.d("AbsDownload already active for $libraryItemId / $episodeId")
            return Result.success(existing.id)
        }

        val downloadId = UUID.randomUUID()
        val localDirPath = buildLocalDirPath(serverId, libraryItemId, episodeId)

        val entity = AbsDownloadEntity(
            id = downloadId,
            libraryItemId = libraryItemId,
            episodeId = episodeId,
            jellyfinServerId = serverId,
            jellyfinUserId = userId,
            title = "",
            authorName = null,
            mediaType = if (episodeId != null) "podcast" else "book",
            coverUrl = null,
            duration = 0.0,
            status = AbsDownloadStatus.QUEUED,
            progress = 0f,
            bytesDownloaded = 0L,
            totalBytes = 0L,
            tracksTotal = 0,
            tracksDownloaded = 0,
            error = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            localDirPath = localDirPath,
            serializedSession = null,
        )
        absDownloadDao.upsert(entity)

        enqueueWorker(downloadId, libraryItemId, episodeId)

        Timber.d("AbsDownload enqueued: $downloadId for $libraryItemId / $episodeId")
        return Result.success(downloadId)
    }

    override suspend fun cancelDownload(downloadId: UUID): Result<Unit> {
        workManager.cancelUniqueWork("abs_download_$downloadId")
        absDownloadDao.updateStatus(
            id = downloadId,
            status = AbsDownloadStatus.CANCELLED,
            error = null,
            updatedAt = System.currentTimeMillis(),
        )
        return Result.success(Unit)
    }

    override suspend fun deleteDownload(downloadId: UUID): Result<Unit> {
        workManager.cancelUniqueWork("abs_download_$downloadId")
        val entity = absDownloadDao.getById(downloadId)
        if (entity?.localDirPath != null) {
            val dir = File(entity.localDirPath)
            if (dir.exists()) {
                dir.deleteRecursively()
                Timber.d("AbsDownload: deleted local files at ${entity.localDirPath}")
            }
        }
        absDownloadDao.deleteById(downloadId)
        return Result.success(Unit)
    }

    override suspend fun getTotalStorageUsed(): Long {
        val session = sessionManager.currentSession.value ?: return 0L
        return absDownloadDao.getTotalBytesForServer(session.serverId, session.userId.toString())
    }

    private fun buildLocalDirPath(
        serverId: String,
        libraryItemId: String,
        episodeId: String?
    ): String {
        val base = File(downloadBaseDir, serverId)
        return if (episodeId != null) {
            File(base, "podcasts/$libraryItemId/episodes/$episodeId").absolutePath
        } else {
            File(base, "books/$libraryItemId").absolutePath
        }
    }

    private suspend fun enqueueWorker(downloadId: UUID, libraryItemId: String, episodeId: String?) {
        val wifiOnly = preferencesRepository.getDownloadOverWifiOnly()
        val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .setRequiresStorageNotLow(true)
            .build()

        val inputData = Data.Builder()
            .putString(KEY_DOWNLOAD_ID, downloadId.toString())
            .putString(KEY_LIBRARY_ITEM_ID, libraryItemId)
            .apply { if (episodeId != null) putString(KEY_EPISODE_ID, episodeId) }
            .build()

        val request = OneTimeWorkRequestBuilder<AbsMediaDownloadWorker>()
            .setConstraints(constraints)
            .setInputData(inputData)
            .addTag("abs_download_active")
            .addTag("abs_download_$downloadId")
            .build()

        workManager.enqueueUniqueWork(
            "abs_download_$downloadId",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}

fun AbsDownloadEntity.toAbsDownloadInfo(): AbsDownloadInfo =
    AbsDownloadInfo(
        id = id,
        libraryItemId = libraryItemId,
        episodeId = episodeId,
        title = title,
        authorName = authorName,
        mediaType = mediaType,
        coverUrl = coverUrl,
        duration = duration,
        status = status,
        progress = progress,
        bytesDownloaded = bytesDownloaded,
        totalBytes = totalBytes,
        tracksTotal = tracksTotal,
        tracksDownloaded = tracksDownloaded,
        error = error,
        createdAt = createdAt,
        updatedAt = updatedAt,
        localDirPath = localDirPath,
        episodeDescription = episodeDescription,
        publishedAt = publishedAt,
    )