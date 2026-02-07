package com.makd.afinity.data.repository.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.makd.afinity.data.database.entities.DownloadDto
import com.makd.afinity.data.database.entities.toDownloadInfo
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.download.DownloadStatus
import com.makd.afinity.data.models.extensions.toAfinityEpisode
import com.makd.afinity.data.models.extensions.toAfinityMovie
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinitySourceType
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.workers.ImageDownloadWorker
import com.makd.afinity.data.workers.MediaDownloadWorker
import com.makd.afinity.data.workers.SubtitleDownloadWorker
import com.makd.afinity.data.workers.TrickplayDownloadWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import timber.log.Timber

@Singleton
class JellyfinDownloadRepository
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
    private val mediaRepository: MediaRepository,
    private val databaseRepository: DatabaseRepository,
    private val preferencesRepository: PreferencesRepository,
    private val workManager: WorkManager,
) : DownloadRepository {

    companion object {
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_ITEM_ID = "item_id"
        const val KEY_SOURCE_ID = "source_id"
        const val KEY_ITEM_NAME = "item_name"
        const val KEY_ITEM_TYPE = "item_type"
        const val MAX_CONCURRENT_DOWNLOADS = 2
    }

    private val downloadDir: File
        get() =
            File(context.getExternalFilesDir(null), "AFinity/Downloads").also {
                if (!it.exists()) it.mkdirs()
            }

    override suspend fun startDownload(itemId: UUID, sourceId: String): Result<UUID> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val currentSession =
                    sessionManager.currentSession.value
                        ?: return@withContext Result.failure(Exception("No active session"))

                val userId = currentSession.userId
                val serverId = currentSession.serverId
                val baseUrl = currentSession.serverUrl

                val existingDownload =
                    databaseRepository.getDownloadByItemIdScoped(itemId, serverId, userId)
                if (existingDownload != null) {
                    if (existingDownload.status == DownloadStatus.COMPLETED) {
                        return@withContext Result.failure(Exception("Item already downloaded"))
                    }
                    if (
                        existingDownload.status == DownloadStatus.DOWNLOADING ||
                            existingDownload.status == DownloadStatus.QUEUED
                    ) {
                        return@withContext Result.failure(
                            Exception("Item is already being downloaded")
                        )
                    }
                }

                val baseItemDto =
                    mediaRepository.getItem(
                        itemId = itemId,
                        fields =
                            listOf(
                                ItemFields.MEDIA_SOURCES,
                                ItemFields.MEDIA_STREAMS,
                                ItemFields.OVERVIEW,
                            ),
                    ) ?: return@withContext Result.failure(Exception("Item not found"))

                val item =
                    when (baseItemDto.type) {
                        BaseItemKind.MOVIE -> baseItemDto.toAfinityMovie(baseUrl)
                        BaseItemKind.EPISODE ->
                            baseItemDto.toAfinityEpisode(baseUrl)
                                ?: return@withContext Result.failure(
                                    Exception("Failed to convert episode")
                                )

                        else ->
                            return@withContext Result.failure(
                                Exception("Unsupported item type: ${baseItemDto.type}")
                            )
                    }

                val source =
                    item.sources.find { it.id == sourceId }
                        ?: return@withContext Result.failure(Exception("Source not found"))

                val downloadId = UUID.randomUUID()
                val download =
                    DownloadDto(
                        id = downloadId,
                        itemId = itemId,
                        itemName = item.name,
                        itemType =
                            when (item) {
                                is AfinityMovie -> "Movie"
                                is AfinityEpisode -> "Episode"
                                else -> "Unknown"
                            },
                        sourceId = sourceId,
                        sourceName = source.name,
                        status = DownloadStatus.QUEUED,
                        progress = 0f,
                        bytesDownloaded = 0L,
                        totalBytes = source.size,
                        filePath = null,
                        error = null,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis(),
                        serverId = serverId,
                        userId = userId,
                    )

                databaseRepository.insertDownload(download)

                queueDownloadWork(download)

                Result.success(downloadId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to start download")
                Result.failure(e)
            }
        }

    private suspend fun queueDownloadWork(
        download: DownloadDto,
        policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP,
    ) {
        val wifiOnly = preferencesRepository.getDownloadOverWifiOnly()

        val constraints =
            Constraints.Builder()
                .setRequiredNetworkType(
                    if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
                )
                .setRequiresStorageNotLow(true)
                .build()

        val inputData =
            Data.Builder()
                .putString(KEY_DOWNLOAD_ID, download.id.toString())
                .putString(KEY_ITEM_ID, download.itemId.toString())
                .putString(KEY_SOURCE_ID, download.sourceId)
                .putString(KEY_ITEM_NAME, download.itemName)
                .putString(KEY_ITEM_TYPE, download.itemType)
                .build()

        val mediaDownloadRequest =
            OneTimeWorkRequestBuilder<MediaDownloadWorker>()
                .setConstraints(constraints)
                .setInputData(inputData)
                .addTag("download_${download.id}")
                .addTag("download_active")
                .build()

        val trickplayDownloadRequest =
            OneTimeWorkRequestBuilder<TrickplayDownloadWorker>()
                .setConstraints(constraints)
                .setInputData(inputData)
                .addTag("download_${download.id}")
                .build()

        val imageDownloadRequest =
            OneTimeWorkRequestBuilder<ImageDownloadWorker>()
                .setConstraints(constraints)
                .setInputData(inputData)
                .addTag("download_${download.id}")
                .build()

        val subtitleDownloadRequest =
            OneTimeWorkRequestBuilder<SubtitleDownloadWorker>()
                .setConstraints(constraints)
                .setInputData(inputData)
                .addTag("download_${download.id}")
                .build()

        workManager
            .beginUniqueWork("download_${download.id}", policy, mediaDownloadRequest)
            .then(listOf(trickplayDownloadRequest, imageDownloadRequest, subtitleDownloadRequest))
            .enqueue()
    }

    override suspend fun pauseDownload(downloadId: UUID): Result<Unit> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                workManager.cancelUniqueWork("download_$downloadId")

                val download = databaseRepository.getDownload(downloadId)
                if (download != null) {
                    databaseRepository.insertDownload(
                        download.copy(
                            status = DownloadStatus.PAUSED,
                            updatedAt = System.currentTimeMillis(),
                        )
                    )
                }

                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to pause download")
                Result.failure(e)
            }
        }

    override suspend fun resumeDownload(downloadId: UUID): Result<Unit> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val download =
                    databaseRepository.getDownload(downloadId)
                        ?: return@withContext Result.failure(Exception("Download not found"))

                if (
                    download.status != DownloadStatus.PAUSED &&
                        download.status != DownloadStatus.FAILED
                ) {
                    return@withContext Result.failure(Exception("Download is not paused or failed"))
                }

                val updatedDownload =
                    download.copy(
                        status = DownloadStatus.QUEUED,
                        error = null,
                        updatedAt = System.currentTimeMillis(),
                    )
                databaseRepository.insertDownload(updatedDownload)

                queueDownloadWork(updatedDownload, ExistingWorkPolicy.REPLACE)

                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to resume download")
                Result.failure(e)
            }
        }

    override suspend fun cancelDownload(downloadId: UUID): Result<Unit> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                workManager.cancelUniqueWork("download_$downloadId")

                val download = databaseRepository.getDownload(downloadId)
                if (download != null) {
                    val itemDir = getItemDownloadDirectory(download.itemId)
                    val mediaDir = File(itemDir, "media")
                    if (mediaDir.exists()) {
                        mediaDir
                            .listFiles { _, name -> name.startsWith(download.sourceId) }
                            ?.forEach { file ->
                                Timber.d("Deleting download file: ${file.name}")
                                file.delete()
                            }
                    }
                    if (
                        itemDir.exists() &&
                            (itemDir.listFiles()?.isEmpty() == true ||
                                itemDir.listFiles()?.all {
                                    it.name == "media" && it.listFiles()?.isEmpty() == true
                                } == true)
                    ) {
                        itemDir.deleteRecursively()
                    }
                    databaseRepository.deleteDownload(downloadId)
                }

                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to cancel download")
                Result.failure(e)
            }
        }

    override suspend fun deleteDownload(downloadId: UUID): Result<Unit> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val download =
                    databaseRepository.getDownload(downloadId)
                        ?: return@withContext Result.failure(Exception("Download not found"))

                val itemFolder = File(downloadDir, download.itemId.toString())
                if (itemFolder.exists()) {
                    itemFolder.deleteRecursively()
                }

                val sources = databaseRepository.getSources(download.itemId)
                sources
                    .filter { it.type == AfinitySourceType.LOCAL }
                    .forEach { databaseRepository.deleteSource(it.id) }

                databaseRepository.deleteDownload(downloadId)

                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete download")
                Result.failure(e)
            }
        }

    override suspend fun getDownload(downloadId: UUID): DownloadInfo? =
        withContext(Dispatchers.IO) { databaseRepository.getDownload(downloadId)?.toDownloadInfo() }

    override suspend fun getDownloadByItemId(itemId: UUID): DownloadInfo? =
        withContext(Dispatchers.IO) {
            val session = sessionManager.currentSession.value ?: return@withContext null
            databaseRepository
                .getDownloadByItemIdScoped(itemId, session.serverId, session.userId)
                ?.toDownloadInfo()
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getAllDownloadsFlow(): Flow<List<DownloadInfo>> {
        return sessionManager.currentSession
            .filterNotNull()
            .flatMapLatest { session ->
                databaseRepository.getAllDownloadsFlowScoped(session.serverId, session.userId)
            }
            .map { downloads -> downloads.map { it.toDownloadInfo() } }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getDownloadsByStatusFlow(
        statuses: List<DownloadStatus>
    ): Flow<List<DownloadInfo>> {
        return sessionManager.currentSession
            .filterNotNull()
            .flatMapLatest { session ->
                databaseRepository.getDownloadsByStatusFlowScoped(
                    statuses,
                    session.serverId,
                    session.userId,
                )
            }
            .map { downloads -> downloads.map { it.toDownloadInfo() } }
    }

    override fun getActiveDownloadsFlow(): Flow<List<DownloadInfo>> {
        return getDownloadsByStatusFlow(
            listOf(
                DownloadStatus.QUEUED,
                DownloadStatus.DOWNLOADING,
                DownloadStatus.PAUSED,
                DownloadStatus.FAILED,
            )
        )
    }

    override fun getCompletedDownloadsFlow(): Flow<List<DownloadInfo>> {
        return getDownloadsByStatusFlow(listOf(DownloadStatus.COMPLETED))
    }

    override suspend fun isItemDownloaded(itemId: UUID): Boolean =
        withContext(Dispatchers.IO) {
            val session = sessionManager.currentSession.value ?: return@withContext false
            val download =
                databaseRepository.getDownloadByItemIdScoped(
                    itemId,
                    session.serverId,
                    session.userId,
                )
            download?.status == DownloadStatus.COMPLETED
        }

    override suspend fun isItemDownloading(itemId: UUID): Boolean =
        withContext(Dispatchers.IO) {
            val session = sessionManager.currentSession.value ?: return@withContext false
            val download =
                databaseRepository.getDownloadByItemIdScoped(
                    itemId,
                    session.serverId,
                    session.userId,
                )
            download?.status == DownloadStatus.DOWNLOADING ||
                download?.status == DownloadStatus.QUEUED
        }

    override suspend fun getTotalStorageUsed(): Long =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val session = sessionManager.currentSession.value ?: return@withContext 0L
                databaseRepository.getTotalBytesForServer(session.serverId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to calculate storage used")
                0L
            }
        }

    override suspend fun getTotalStorageUsedAllServers(): Long =
        withContext(Dispatchers.IO) {
            return@withContext try {
                databaseRepository.getTotalBytesAllServers()
            } catch (e: Exception) {
                Timber.e(e, "Failed to calculate total storage used")
                0L
            }
        }

    fun getDownloadDirectory(): File = downloadDir

    fun getItemDownloadDirectory(itemId: UUID): File {
        return File(downloadDir, itemId.toString()).also { if (!it.exists()) it.mkdirs() }
    }
}
