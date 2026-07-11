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
import com.makd.afinity.data.models.extensions.toAfinityTrack
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinitySourceType
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.music.MusicRepository
import com.makd.afinity.data.storage.StorageLocationProvider
import com.makd.afinity.data.storage.VolumeUnavailableException
import com.makd.afinity.data.workers.ImageDownloadWorker
import com.makd.afinity.data.workers.LyricsDownloadWorker
import com.makd.afinity.data.workers.MediaDownloadWorker
import com.makd.afinity.data.workers.SubtitleDownloadWorker
import com.makd.afinity.data.workers.TrickplayDownloadWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import timber.log.Timber
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyfinDownloadRepository
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val sessionManager: SessionManager,
    private val mediaRepository: MediaRepository,
    private val musicRepository: MusicRepository,
    private val databaseRepository: DatabaseRepository,
    private val preferencesRepository: PreferencesRepository,
    private val storageLocationProvider: StorageLocationProvider,
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

    /**
     * Resolves the `AFinity/Downloads` base directory for the given volume. Throws
     * [VolumeUnavailableException] when a non-primary volume is no longer mounted (e.g. the SD card
     * was removed) rather than silently retargeting to primary — silently falling back would let
     * deletes/cleanup no-op on the absent volume's files while still mutating state, orphaning the
     * media. The primary volume is always resolvable.
     */
    private fun baseDir(volumeId: String): File {
        val dir =
            if (volumeId == StorageLocationProvider.PRIMARY_VOLUME_ID) {
                storageLocationProvider.primaryBaseDir()
            } else {
                storageLocationProvider.resolveBaseDir(volumeId)
                    ?: throw VolumeUnavailableException(
                        volumeId,
                        storageLocationProvider.displayNameFor(volumeId),
                    )
            }
        if (!dir.exists() && !dir.mkdirs()) {
            Timber.e("Failed to create base download directory at ${dir.absolutePath}")
        }
        return dir
    }

    override suspend fun startDownload(
        itemId: UUID,
        sourceId: String,
        volumeId: String?,
        playlistId: String?,
    ): Result<UUID> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                // An explicit volume choice that is no longer mounted is an error — fail loudly
                // rather than silently retargeting the user's deliberate selection.
                if (volumeId != null && !storageLocationProvider.isVolumeAvailable(volumeId)) {
                    return@withContext Result.failure(
                        VolumeUnavailableException(
                            volumeId,
                            storageLocationProvider.displayNameFor(volumeId),
                        )
                    )
                }

                // Fall back to the primary volume when the global default is unavailable so a
                // tap-to-download never fails just because an SD card was removed.
                val requestedVolumeId =
                    volumeId ?: preferencesRepository.getDownloadStorageVolumeId()
                val resolvedVolumeId =
                    if (storageLocationProvider.isVolumeAvailable(requestedVolumeId))
                        requestedVolumeId
                    else StorageLocationProvider.PRIMARY_VOLUME_ID

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

                if (baseItemDto.type == BaseItemKind.AUDIO) {
                    val track = baseItemDto.toAfinityTrack(baseUrl)
                    val mediaSource =
                        if (sourceId.isEmpty()) {
                            baseItemDto.mediaSources?.firstOrNull()
                        } else {
                            baseItemDto.mediaSources?.firstOrNull { it.id == sourceId }
                                ?: baseItemDto.mediaSources?.firstOrNull()
                        }
                            ?: return@withContext Result.failure(
                                Exception("No media source for audio item")
                            )

                    val resolvedSourceId = mediaSource.id ?: itemId.toString()
                    val albumId = track.albumId ?: itemId
                    val downloadId = UUID.randomUUID()

                    val download =
                        DownloadDto(
                            id = downloadId,
                            itemId = itemId,
                            itemName = track.name,
                            itemType = "Audio",
                            sourceId = resolvedSourceId,
                            sourceName = mediaSource.name ?: track.name,
                            status = DownloadStatus.QUEUED,
                            progress = 0f,
                            bytesDownloaded = 0L,
                            totalBytes = mediaSource.size ?: 0L,
                            filePath = null,
                            error = null,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis(),
                            serverId = serverId,
                            userId = userId,
                            imageUrl = track.images.primary?.toString(),
                            seriesImageUrl = null,
                            seriesName = track.album,
                            seasonNumber = track.discNumber,
                            episodeNumber = track.indexNumber,
                            releaseYear = track.productionYear?.toString(),
                            runtimeTicks = track.runtimeTicks,
                            folderPath = "$serverId/music/$albumId/$itemId",
                            seriesId = albumId.toString(),
                            storageVolumeId = resolvedVolumeId,
                            playlistId = playlistId,
                        )

                    databaseRepository.insertDownload(download)
                    databaseRepository.insertMusicTrack(track, serverId, userId.toString())
                    queueDownloadWork(download)
                    return@withContext Result.success(downloadId)
                }

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
                    if (sourceId.isEmpty()) {
                        item.sources.firstOrNull { it.type == AfinitySourceType.REMOTE }
                            ?: return@withContext Result.failure(
                                Exception("No remote source available")
                            )
                    } else {
                        item.sources.find { it.id == sourceId }
                            ?: return@withContext Result.failure(Exception("Source not found"))
                    }

                val downloadId = UUID.randomUUID()

                val imageUrl =
                    when (item) {
                        is AfinityMovie -> item.images.primary?.toString()
                        is AfinityEpisode -> item.images.primary?.toString()
                        else -> null
                    }

                val seriesImageUrl = (item as? AfinityEpisode)?.images?.showPrimary?.toString()
                val seriesName = (item as? AfinityEpisode)?.seriesName
                val seasonNumber = (item as? AfinityEpisode)?.parentIndexNumber
                val episodeNumber = (item as? AfinityEpisode)?.indexNumber
                val releaseYear = (item as? AfinityMovie)?.productionYear?.toString()
                val runtimeTicks =
                    when (item) {
                        is AfinityMovie -> item.runtimeTicks
                        is AfinityEpisode -> item.runtimeTicks
                        else -> 0L
                    }

                val folderPath =
                    when (item) {
                        is AfinityMovie -> "$serverId/movies/$itemId"
                        is AfinityEpisode ->
                            "$serverId/shows/${item.seriesId}/seasons/${item.parentIndexNumber}/$itemId"
                        else -> "$serverId/$itemId"
                    }

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
                        sourceId = source.id,
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
                        imageUrl = imageUrl,
                        seriesImageUrl = seriesImageUrl,
                        seriesName = seriesName,
                        seasonNumber = seasonNumber,
                        episodeNumber = episodeNumber,
                        releaseYear = releaseYear,
                        runtimeTicks = runtimeTicks,
                        folderPath = folderPath,
                        seriesId = (item as? AfinityEpisode)?.seriesId?.toString(),
                        storageVolumeId = resolvedVolumeId,
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

        val lyricsDownloadRequest =
            OneTimeWorkRequestBuilder<LyricsDownloadWorker>()
                .setConstraints(constraints)
                .setInputData(inputData)
                .addTag("download_${download.id}")
                .build()

        if (download.itemType == "Audio") {
            workManager
                .beginUniqueWork("download_${download.id}", policy, mediaDownloadRequest)
                .then(listOf(imageDownloadRequest, lyricsDownloadRequest))
                .enqueue()
        } else {
            workManager
                .beginUniqueWork("download_${download.id}", policy, mediaDownloadRequest)
                .then(
                    listOf(trickplayDownloadRequest, imageDownloadRequest, subtitleDownloadRequest)
                )
                .enqueue()
        }
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
                    try {
                        val itemDir = getItemDownloadDirectory(download)
                        val mediaDir = File(itemDir, "media")
                        if (mediaDir.exists()) {
                            mediaDir
                                .listFiles { _, name -> name.startsWith(download.sourceId) }
                                ?.forEach { file ->
                                    Timber.d("Deleting download file: ${file.name}")
                                    file.delete()
                                }
                        }
                        if (itemDir.exists()) {
                            val children = itemDir.listFiles() ?: emptyArray()
                            val onlyEmptyMediaDir =
                                children.size == 1 &&
                                    children[0].name == "media" &&
                                    children[0].listFiles()?.isEmpty() == true
                            if (children.isEmpty() || onlyEmptyMediaDir) {
                                itemDir.deleteRecursively()
                            }
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to delete files for cancelled download")
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

                // baseDir throws VolumeUnavailableException when the item's volume is gone, so a
                // delete can never no-op on absent files while dropping the DB rows. The UI catches
                // that and offers "remove from list" instead.
                val baseDir = baseDir(download.storageVolumeId)

                val targetPath = download.folderPath ?: download.itemId.toString()
                val itemFolder = File(baseDir, targetPath)

                if (itemFolder.exists()) {
                    itemFolder.deleteRecursively()
                }

                val sources = databaseRepository.getSources(download.itemId)
                sources
                    .filter { it.type == AfinitySourceType.LOCAL }
                    .forEach { databaseRepository.deleteSource(it.id) }

                if (download.itemType == "Audio") {
                    databaseRepository.clearMusicTrackLocalFilePath(
                        download.itemId,
                        download.serverId,
                        download.userId.toString(),
                    )
                }

                databaseRepository.deleteDownload(downloadId)

                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete download")
                Result.failure(e)
            }
        }

    override suspend fun removeDownloadRecord(downloadId: UUID): Result<Unit> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val download =
                    databaseRepository.getDownload(downloadId)
                        ?: return@withContext Result.failure(Exception("Download not found"))

                val sources = databaseRepository.getSources(download.itemId)
                sources
                    .filter { it.type == AfinitySourceType.LOCAL }
                    .forEach { databaseRepository.deleteSource(it.id) }

                databaseRepository.deleteDownload(downloadId)

                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to remove download record")
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

    override suspend fun getStorageUsedPerVolume(): Map<String, Long> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val session = sessionManager.currentSession.value ?: return@withContext emptyMap()
                databaseRepository.getTotalBytesPerVolumeForServer(session.serverId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to calculate per-volume storage used")
                emptyMap()
            }
        }

    override suspend fun getStorageUsedPerVolumeAllServers(): Map<String, Long> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                databaseRepository.getTotalBytesPerVolumeAllServers()
            } catch (e: Exception) {
                Timber.e(e, "Failed to calculate per-volume storage used")
                emptyMap()
            }
        }

    fun getDownloadDirectory(): File = baseDir(StorageLocationProvider.PRIMARY_VOLUME_ID)

    fun getItemDownloadDirectory(download: DownloadDto): File {
        val dir =
            File(
                baseDir(download.storageVolumeId),
                download.folderPath ?: download.itemId.toString(),
            )
        if (!dir.exists() && !dir.mkdirs()) Timber.w("Failed to create item directory")
        return dir
    }

    suspend fun getItemDownloadDirectory(itemId: UUID): File {
        val session = sessionManager.currentSession.value
        val download =
            if (session != null) {
                databaseRepository.getDownloadByItemIdScoped(
                    itemId,
                    session.serverId,
                    session.userId,
                )
            } else {
                databaseRepository.getDownloadByItemId(itemId)
            }
        val folderPath = download?.folderPath
        val volumeId = download?.storageVolumeId ?: StorageLocationProvider.PRIMARY_VOLUME_ID
        val dir = File(baseDir(volumeId), folderPath ?: itemId.toString())
        if (!dir.exists() && !dir.mkdirs()) Timber.w("Failed to create item directory")
        return dir
    }

    fun getShowDirectory(serverId: String, showId: UUID, volumeId: String): File {
        val dir = File(baseDir(volumeId), "$serverId/shows/$showId")
        if (!dir.exists() && !dir.mkdirs()) Timber.w("Failed to create show directory")
        return dir
    }

    fun getSeasonDirectory(
        serverId: String,
        showId: UUID,
        seasonNumber: Int,
        volumeId: String,
    ): File {
        val dir =
            File(
                baseDir(volumeId),
                "$serverId/shows/$showId/seasons/$seasonNumber",
            )
        if (!dir.exists() && !dir.mkdirs()) Timber.w("Failed to create season directory")
        return dir
    }

    override suspend fun startSeasonDownload(
        seasonId: UUID,
        seriesId: UUID?,
        volumeId: String?,
    ): Result<Int> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val episodes = mediaRepository.getEpisodes(seasonId, seriesId)
                var started = 0
                for (episode in episodes) {
                    startDownload(episode.id, "", volumeId)
                        .onSuccess { started++ }
                        .onFailure { error ->
                            if (error is VolumeUnavailableException)
                                return@withContext Result.failure(error)
                            Timber.w(error, "Skipping episode ${episode.name}: ${error.message}")
                        }
                }
                Timber.i("Season download queued $started/${episodes.size} episodes")
                Result.success(started)
            } catch (e: Exception) {
                Timber.e(e, "Failed to start season download")
                Result.failure(e)
            }
        }

    override suspend fun startSeriesDownload(showId: UUID, volumeId: String?): Result<Int> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val seasons = mediaRepository.getSeasons(showId)
                var totalStarted = 0
                for (season in seasons) {
                    startSeasonDownload(season.id, showId, volumeId)
                        .onSuccess { count -> totalStarted += count }
                        .onFailure { Timber.w(it, "Skipping season ${season.name}: ${it.message}") }
                }
                Timber.i(
                    "Series download queued $totalStarted episodes across ${seasons.size} seasons"
                )
                Result.success(totalStarted)
            } catch (e: Exception) {
                Timber.e(e, "Failed to start series download")
                Result.failure(e)
            }
        }

    override suspend fun cancelAllSeriesDownloads(showId: UUID): Result<Unit> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val downloads = getAllDownloadsFlow().first()
                val toCancel = downloads.filter {
                    it.seriesId == showId.toString() && it.status != DownloadStatus.COMPLETED
                }
                for (download in toCancel) {
                    cancelDownload(download.id)
                }
                Timber.i("Cancelled ${toCancel.size} downloads for series $showId")
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to cancel series downloads")
                Result.failure(e)
            }
        }

    override suspend fun startAlbumDownload(albumId: UUID, volumeId: String?): Result<Int> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val session =
                    sessionManager.currentSession.value
                        ?: return@withContext Result.failure(Exception("No active session"))

                val album = musicRepository.getAlbumById(albumId)
                if (album != null) {
                    databaseRepository.insertMusicAlbum(
                        album,
                        session.serverId,
                        session.userId.toString(),
                    )
                }

                val tracks = musicRepository.getAlbumTracks(albumId)
                var started = 0
                for (track in tracks) {
                    startDownload(track.id, "", volumeId)
                        .onSuccess { started++ }
                        .onFailure { error ->
                            if (error is VolumeUnavailableException)
                                return@withContext Result.failure(error)
                            Timber.w(error, "Skipping track ${track.name}: ${error.message}")
                        }
                }
                Timber.i("Album download queued $started/${tracks.size} tracks for album $albumId")
                Result.success(started)
            } catch (e: Exception) {
                Timber.e(e, "Failed to start album download")
                Result.failure(e)
            }
        }

    override suspend fun startArtistDownload(artistId: UUID, volumeId: String?): Result<Int> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val albums = musicRepository.getArtistAlbums(artistId)
                var totalStarted = 0
                for (album in albums) {
                    startAlbumDownload(album.id, volumeId)
                        .onSuccess { count -> totalStarted += count }
                        .onFailure { Timber.w(it, "Skipping album ${album.name}: ${it.message}") }
                }
                Timber.i(
                    "Artist download queued $totalStarted tracks across ${albums.size} albums for artist $artistId"
                )
                Result.success(totalStarted)
            } catch (e: Exception) {
                Timber.e(e, "Failed to start artist download")
                Result.failure(e)
            }
        }

    override suspend fun startPlaylistDownload(playlistId: UUID, volumeId: String?): Result<Int> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val tracks = musicRepository.getPlaylistTracks(playlistId)
                var started = 0
                val playlistIdStr = playlistId.toString()
                for (track in tracks) {
                    startDownload(track.id, "", volumeId, playlistIdStr)
                        .onSuccess { started++ }
                        .onFailure { error ->
                            if (error is VolumeUnavailableException)
                                return@withContext Result.failure(error)
                            Timber.w(error, "Skipping track ${track.name}: ${error.message}")
                        }
                }
                Timber.i(
                    "Playlist download queued $started/${tracks.size} tracks for playlist $playlistId"
                )
                Result.success(started)
            } catch (e: Exception) {
                Timber.e(e, "Failed to start playlist download")
                Result.failure(e)
            }
        }

    override suspend fun cancelAllSeasonDownloads(seriesId: UUID, seasonNumber: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            return@withContext try {
                val downloads = getAllDownloadsFlow().first()
                val toCancel = downloads.filter {
                    it.seriesId == seriesId.toString() &&
                        it.seasonNumber == seasonNumber &&
                        it.status != DownloadStatus.COMPLETED
                }
                for (download in toCancel) {
                    cancelDownload(download.id)
                }
                Timber.i(
                    "Cancelled ${toCancel.size} downloads for series $seriesId season $seasonNumber"
                )
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to cancel season downloads")
                Result.failure(e)
            }
        }
}
