package com.makd.afinity.data.workers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.makd.afinity.R
import com.makd.afinity.data.database.dao.AbsDownloadDao
import com.makd.afinity.data.database.dao.AudiobookshelfDao
import com.makd.afinity.data.database.entities.AudiobookshelfItemEntity
import com.makd.afinity.data.models.audiobookshelf.AbsDownloadStatus
import com.makd.afinity.data.models.audiobookshelf.AudioFile
import com.makd.afinity.data.models.audiobookshelf.AudioTrack
import com.makd.afinity.data.models.audiobookshelf.PlaybackSession
import com.makd.afinity.data.network.AudiobookshelfApiService
import com.makd.afinity.data.repository.SecurePreferencesRepository
import com.makd.afinity.data.repository.audiobookshelf.AbsDownloadRepositoryImpl
import com.makd.afinity.di.DownloadClient
import dagger.Lazy
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

@HiltWorker
class AbsMediaDownloadWorker
@AssistedInject
constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val absDownloadDao: AbsDownloadDao,
    private val audiobookshelfDao: AudiobookshelfDao,
    private val apiService: Lazy<AudiobookshelfApiService>,
    private val securePreferencesRepository: SecurePreferencesRepository,
    @DownloadClient private val okHttpClient: OkHttpClient,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val BUFFER_SIZE = 8192
        private const val NOTIFICATION_CHANNEL_ID = "abs_downloads"
        private const val NOTIFICATION_CHANNEL_NAME = "Audiobook Downloads"
    }

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val downloadIdStr = inputData.getString(AbsDownloadRepositoryImpl.KEY_DOWNLOAD_ID)
            ?: return@withContext Result.failure(workDataOf("error" to "Missing download ID"))
        val downloadId = runCatching { UUID.fromString(downloadIdStr) }.getOrElse {
            return@withContext Result.failure(workDataOf("error" to "Invalid download ID"))
        }

        val libraryItemId = inputData.getString(AbsDownloadRepositoryImpl.KEY_LIBRARY_ITEM_ID)
            ?: return@withContext Result.failure(workDataOf("error" to "Missing libraryItemId"))
        val episodeId = inputData.getString(AbsDownloadRepositoryImpl.KEY_EPISODE_ID)

        val entity = absDownloadDao.getById(downloadId)
            ?: return@withContext Result.failure(workDataOf("error" to "Download record not found"))

        Timber.d("AbsDownload: entity loaded — serverId=${entity.jellyfinServerId} userId=${entity.jellyfinUserId} libraryItemId=${entity.libraryItemId} episodeId=${entity.episodeId}")

        try {
            setForeground(
                createForegroundInfo(
                    downloadId.hashCode(),
                    entity.title.ifEmpty { "Audiobook" },
                    0f
                )
            )
        } catch (e: Exception) {
            Timber.w(e, "AbsDownload: could not set foreground")
        }

        absDownloadDao.updateProgress(
            id = downloadId,
            status = AbsDownloadStatus.DOWNLOADING,
            progress = 0f,
            bytesDownloaded = 0L,
            tracksDownloaded = 0,
            serializedSession = null,
            updatedAt = System.currentTimeMillis(),
        )

        val token = securePreferencesRepository.getCachedAudiobookshelfToken()
        val baseUrl = securePreferencesRepository.getCachedAudiobookshelfServerUrl()?.trimEnd('/')
            ?: run {
                markFailed(downloadId, "No ABS server URL available")
                return@withContext Result.failure(workDataOf("error" to "No ABS server URL"))
            }

        val itemResult = runCatching {
            val response = apiService.get().getItem(libraryItemId, expanded = 1, include = null)
            if (!response.isSuccessful || response.body() == null) {
                throw Exception("Item API returned ${response.code()}: ${response.message()}")
            }
            response.body()!!
        }

        val item = itemResult.getOrElse { e ->
            Timber.e(e, "AbsDownload: failed to fetch item metadata")
            markFailed(downloadId, e.message ?: "Item fetch failed")
            return@withContext Result.failure(workDataOf("error" to e.message))
        }

        if (item.media != null) {
            runCatching {
                audiobookshelfDao.insertItem(
                    AudiobookshelfItemEntity(
                        id = item.id ?: libraryItemId,
                        jellyfinServerId = entity.jellyfinServerId,
                        jellyfinUserId = entity.jellyfinUserId,
                        libraryId = item.libraryId ?: "",
                        title = item.media.metadata.title ?: "",
                        authorName = item.media.metadata.authorName,
                        narratorName = item.media.metadata.narratorName,
                        seriesName = item.media.metadata.seriesName,
                        seriesSequence = null,
                        mediaType = item.mediaType ?: if (episodeId != null) "podcast" else "book",
                        duration = item.media.duration,
                        coverUrl = item.media.coverPath,
                        description = item.media.metadata.description,
                        publishedYear = item.media.metadata.publishedYear,
                        genres = item.media.metadata.genres?.let { json.encodeToString(it) },
                        numTracks = item.media.numTracks,
                        numChapters = item.media.numChapters,
                        addedAt = item.addedAt,
                        updatedAt = item.updatedAt,
                        cachedAt = System.currentTimeMillis(),
                        serializedEpisodes = item.media.episodes?.let { json.encodeToString(it) },
                    )
                )
                Timber.d("AbsDownload: cached item $libraryItemId for offline browsing (${item.media.episodes?.size ?: 0} episodes)")
            }.onFailure { Timber.w(it, "AbsDownload: failed to cache item $libraryItemId") }
        }

        val audioFilesToDownload: List<AudioFile>
        val episodeDuration: Double
        val displayTitle: String
        val displayAuthor: String?

        if (episodeId != null) {
            val episode = item.media?.episodes?.find { it.id == episodeId }
            if (episode == null) {
                Timber.e("AbsDownload: episode $episodeId not found. Episodes in response: ${item.media?.episodes?.map { it.id }}")
                markFailed(downloadId, "Episode $episodeId not found in item response")
                return@withContext Result.failure(workDataOf("error" to "Episode not found"))
            }
            val audioFile = episode.audioFile
            if (audioFile == null) {
                Timber.e("AbsDownload: episode $episodeId has no audioFile. audioTrack=${episode.audioTrack?.contentUrl}")
                markFailed(downloadId, "Episode $episodeId has no audio file (ino unavailable)")
                return@withContext Result.failure(workDataOf("error" to "No audio file"))
            }
            Timber.d("AbsDownload: episode audioFile ino=${audioFile.ino}, filename=${audioFile.metadata.filename}")
            audioFilesToDownload = listOf(audioFile)
            episodeDuration = episode.duration ?: audioFile.duration ?: 0.0
            displayTitle = episode.title
            displayAuthor = item.media?.metadata?.title
            if (episode.description != null || episode.publishedAt != null) {
                absDownloadDao.upsert(
                    entity.copy(
                        episodeDescription = episode.description,
                        publishedAt = episode.publishedAt,
                    )
                )
            }
        } else {
            val files = item.media?.audioFiles
                ?.filter { it.invalid != true && it.exclude != true }
                ?.sortedBy { it.index ?: Int.MAX_VALUE }
                ?: emptyList()
            if (files.isEmpty()) {
                markFailed(downloadId, "No audio files in item")
                return@withContext Result.failure(workDataOf("error" to "No audio files"))
            }
            audioFilesToDownload = files
            episodeDuration = item.media?.duration ?: files.sumOf { it.duration ?: 0.0 }
            displayTitle = item.media?.metadata?.title ?: entity.title
            displayAuthor = item.media?.metadata?.authorName
        }

        val localDirPath = entity.localDirPath ?: run {
            markFailed(downloadId, "No local dir path set")
            return@withContext Result.failure(workDataOf("error" to "No local dir"))
        }
        val localDir = File(localDirPath).also { it.mkdirs() }

        absDownloadDao.upsert(
            absDownloadDao.getById(downloadId)!!.copy(
                title = displayTitle,
                authorName = displayAuthor,
                duration = episodeDuration,
                tracksTotal = audioFilesToDownload.size,
                coverUrl = "$baseUrl/api/items/$libraryItemId/cover",
                updatedAt = System.currentTimeMillis(),
            )
        )

        var downloadedBytesAllTracks = 0L
        val localTrackPaths = mutableListOf<String>()

        for ((index, audioFile) in audioFilesToDownload.withIndex()) {
            if (isStopped) {
                markFailed(downloadId, "Cancelled")
                return@withContext Result.failure(workDataOf("error" to "Cancelled"))
            }

            val ext = audioFileExtension(audioFile)
            val outputFile = File(localDir, "track_$index.$ext.download")
            val finalFile = File(localDir, "track_$index.$ext")

            if (finalFile.exists() && finalFile.length() > 0) {
                Timber.d("AbsDownload: track $index already downloaded, skipping")
                localTrackPaths.add(finalFile.absolutePath)
                downloadedBytesAllTracks += finalFile.length()
                absDownloadDao.updateProgress(
                    id = downloadId,
                    status = AbsDownloadStatus.DOWNLOADING,
                    progress = (index + 1).toFloat() / audioFilesToDownload.size,
                    bytesDownloaded = downloadedBytesAllTracks,
                    tracksDownloaded = index + 1,
                    serializedSession = null,
                    updatedAt = System.currentTimeMillis(),
                )
                continue
            }

            val downloadUrl = "$baseUrl/api/items/$libraryItemId/file/${audioFile.ino}/download"
            val resumeFrom = if (outputFile.exists()) outputFile.length() else 0L
            val requestBuilder = Request.Builder()
                .url(downloadUrl)
                .apply {
                    if (token != null) header("Authorization", "Bearer $token")
                    if (resumeFrom > 0) header("Range", "bytes=$resumeFrom-")
                }
            val request = requestBuilder.build()

            var trackBytesDownloaded = resumeFrom
            var trackTotalBytes = 0L

            val downloadSuccess = runCatching {
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful && response.code != 416) {
                        throw Exception("HTTP ${response.code}: ${response.message}")
                    }
                    if (response.code == 416) return@runCatching

                    val contentLength = response.body?.contentLength() ?: -1L
                    trackTotalBytes = if (contentLength != -1L) resumeFrom + contentLength else -1L

                    var lastDbUpdate = 0L

                    response.body?.byteStream()?.use { input ->
                        FileOutputStream(outputFile, resumeFrom > 0).use { output ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            var bytes: Int
                            while (input.read(buffer).also { bytes = it } != -1) {
                                if (isStopped) throw Exception("Cancelled")
                                output.write(buffer, 0, bytes)
                                trackBytesDownloaded += bytes
                                downloadedBytesAllTracks += bytes

                                val now = System.currentTimeMillis()
                                if (now - lastDbUpdate > 500) {
                                    lastDbUpdate = now
                                    val overallProgress =
                                        (index.toFloat() + (trackBytesDownloaded.toFloat() / trackTotalBytes.coerceAtLeast(
                                            1L
                                        ))) / audioFilesToDownload.size
                                    absDownloadDao.updateProgress(
                                        id = downloadId,
                                        status = AbsDownloadStatus.DOWNLOADING,
                                        progress = overallProgress,
                                        bytesDownloaded = downloadedBytesAllTracks,
                                        tracksDownloaded = index,
                                        serializedSession = null,
                                        updatedAt = now,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (downloadSuccess.isFailure) {
                val err = downloadSuccess.exceptionOrNull()?.message ?: "Download failed"
                if (err == "Cancelled") {
                    markFailed(downloadId, "Cancelled")
                    return@withContext Result.failure(workDataOf("error" to "Cancelled"))
                }
                markFailed(downloadId, "Track $index failed: $err")
                return@withContext Result.failure(workDataOf("error" to err))
            }

            if (outputFile.exists()) outputFile.renameTo(finalFile)
            localTrackPaths.add(finalFile.absolutePath)

            absDownloadDao.updateProgress(
                id = downloadId,
                status = AbsDownloadStatus.DOWNLOADING,
                progress = (index + 1).toFloat() / audioFilesToDownload.size,
                bytesDownloaded = downloadedBytesAllTracks,
                tracksDownloaded = index + 1,
                serializedSession = null,
                updatedAt = System.currentTimeMillis(),
            )

            Timber.d("AbsDownload: track $index downloaded to ${finalFile.absolutePath}")
        }

        val localCoverPath = downloadCover(libraryItemId, baseUrl, token, localDir)

        var cumulativeStart = 0.0
        val audioTracks = audioFilesToDownload.mapIndexed { index, audioFile ->
            val ext = audioFileExtension(audioFile)
            val localPath = File(localDir, "track_$index.$ext").absolutePath
            val startOffset = cumulativeStart
            cumulativeStart += audioFile.duration ?: 0.0
            AudioTrack(
                index = index + 1,
                startOffset = startOffset,
                duration = audioFile.duration ?: 0.0,
                title = audioFile.metadata.filename,
                contentUrl = "file://$localPath",
                mimeType = audioFile.mimeType,
                codec = audioFile.codec,
                metadata = audioFile.metadata,
            )
        }

        val now = System.currentTimeMillis()
        val localSession = PlaybackSession(
            id = "local_${libraryItemId}_${episodeId ?: ""}",
            userId = "",
            libraryId = item.libraryId ?: "",
            libraryItemId = libraryItemId,
            episodeId = episodeId,
            mediaType = item.mediaType ?: if (episodeId != null) "podcast" else "book",
            mediaMetadata = item.media?.metadata,
            chapters = if (episodeId != null) {
                item.media?.episodes?.find { it.id == episodeId }?.chapters
            } else {
                item.media?.chapters
            },
            displayTitle = displayTitle,
            displayAuthor = displayAuthor,
            coverPath = localCoverPath,
            duration = episodeDuration,
            playMethod = 0,
            startTime = 0.0,
            currentTime = 0.0,
            startedAt = now,
            updatedAt = now,
            audioTracks = audioTracks,
        )

        val serializedSession = json.encodeToString(localSession)

        absDownloadDao.updateProgress(
            id = downloadId,
            status = AbsDownloadStatus.COMPLETED,
            progress = 1f,
            bytesDownloaded = downloadedBytesAllTracks,
            tracksDownloaded = audioFilesToDownload.size,
            serializedSession = serializedSession,
            updatedAt = System.currentTimeMillis(),
        )

        Timber.d("AbsDownload: completed $libraryItemId / $episodeId — ${audioFilesToDownload.size} tracks")
        Result.success()
    }

    private fun downloadCover(
        libraryItemId: String,
        baseUrl: String,
        token: String?,
        localDir: File,
    ): String? {
        val coverFile = File(localDir, "cover.jpg")
        if (coverFile.exists() && coverFile.length() > 0) {
            return "file://${coverFile.absolutePath}"
        }
        return try {
            val request = Request.Builder()
                .url("$baseUrl/api/items/$libraryItemId/cover?format=jpeg")
                .apply { if (token != null) header("Authorization", "Bearer $token") }
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.byteStream()?.use { input ->
                        FileOutputStream(coverFile).use { output -> input.copyTo(output) }
                    }
                    "file://${coverFile.absolutePath}"
                } else {
                    Timber.w("Cover download failed: ${response.code}")
                    null
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to download cover")
            null
        }
    }

    private fun audioFileExtension(audioFile: AudioFile): String {
        val metaExt = audioFile.metadata.ext?.trimStart('.')
        if (!metaExt.isNullOrBlank()) return metaExt

        val filename = audioFile.metadata.filename
        if (filename.contains('.')) return filename.substringAfterLast('.')

        val subtype = audioFile.mimeType?.substringAfter("/")?.substringBefore(";") ?: return "mp3"
        return when (subtype) {
            "mpeg" -> "mp3"
            "mp4", "x-m4a" -> "m4a"
            else -> subtype
        }
    }

    private suspend fun markFailed(downloadId: UUID, reason: String) {
        Timber.e("AbsDownload failed ($downloadId): $reason")
        absDownloadDao.updateStatus(
            id = downloadId,
            status = AbsDownloadStatus.FAILED,
            error = reason,
            updatedAt = System.currentTimeMillis(),
        )
    }

    private fun createForegroundInfo(
        notificationId: Int,
        title: String,
        progress: Float
    ): ForegroundInfo {
        val notificationManager =
            appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) == null) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    NOTIFICATION_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }
        val notification = NotificationCompat.Builder(appContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle("Downloading: $title")
            .setProgress(100, (progress * 100).toInt(), progress == 0f)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ForegroundInfo(
                notificationId,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }
}