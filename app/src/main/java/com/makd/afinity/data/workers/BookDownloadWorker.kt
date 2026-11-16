package com.makd.afinity.data.workers

import android.content.Context
import androidx.core.net.toUri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.makd.afinity.data.audiobooks.AUDIOBOOK_DOWNLOAD_TYPE
import com.makd.afinity.data.books.BookDownloadCover
import com.makd.afinity.data.database.entities.DownloadDto
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.download.DownloadStatus
import com.makd.afinity.data.models.extensions.toAfinityAudiobook
import com.makd.afinity.data.models.extensions.toAfinityBook
import com.makd.afinity.data.models.media.AfinityPerson
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.download.JellyfinDownloadRepository
import com.makd.afinity.di.DownloadClient
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import timber.log.Timber

@HiltWorker
class BookDownloadWorker
@AssistedInject
constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val sessionManager: SessionManager,
    private val databaseRepository: DatabaseRepository,
    private val downloadRepository: JellyfinDownloadRepository,
    @param:DownloadClient private val okHttpClient: OkHttpClient,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result =
        withContext(Dispatchers.IO) {
            val downloadId =
                inputData.getString(JellyfinDownloadRepository.KEY_DOWNLOAD_ID)?.let {
                    UUID.fromString(it)
                } ?: return@withContext Result.failure()
            try {
                val download =
                    databaseRepository.getDownload(downloadId)
                        ?: return@withContext Result.failure(
                            workDataOf("error" to "Download not found")
                        )
                val apiClient =
                    sessionManager.getOrRestoreApiClient(download.serverId)
                        ?: throw IllegalStateException("No session for server ${download.serverId}")
                val token = apiClient.accessToken.orEmpty()
                val baseUrl = apiClient.baseUrl.orEmpty().trimEnd('/')

                var current =
                    download.copy(
                        status = DownloadStatus.DOWNLOADING,
                        updatedAt = System.currentTimeMillis(),
                    )
                databaseRepository.insertDownload(current)

                val itemDir = downloadRepository.getItemDownloadDirectory(download)
                val mediaDir = File(itemDir, MEDIA_DIR).apply { mkdirs() }

                val item =
                    apiClient.libraryApi
                        .getItems(
                            userId = download.userId,
                            ids = listOf(download.itemId),
                            fields =
                                if (download.itemType == AUDIOBOOK_DOWNLOAD_TYPE) {
                                    FieldSets.AUDIOBOOK_DETAIL
                                } else {
                                    FieldSets.BOOK_DETAIL
                                },
                            enableImages = true,
                            enableUserData = true,
                        )
                        .content
                        .items
                        .firstOrNull() ?: throw IllegalStateException("Book not found")

                val coverFile = File(itemDir, BookDownloadCover.FILE_NAME)
                if (item.imageTags?.containsKey(ImageType.PRIMARY) == true && !coverFile.exists()) {
                    runCatching {
                        fetch(
                            "$baseUrl/Items/${download.itemId}/Images/Primary?maxWidth=$COVER_WIDTH",
                            token,
                            coverFile,
                            resume = false,
                        ) { _, _ ->
                        }
                    }
                        .onFailure { Timber.w(it, "Book cover download failed") }
                }
                saveItemRow(item, baseUrl, itemDir, token, download)

                val finalFile = File(mediaDir, "${download.itemId}.${download.sourceName}")
                if (!finalFile.exists()) {
                    val partial = File(mediaDir, finalFile.name + ".download")
                    val url = apiClient.libraryApi.getDownloadUrl(itemId = download.itemId)
                    var lastWrite = 0L
                    val completed =
                        fetch(url, token, partial, resume = true) { downloaded, total ->
                            val now = System.currentTimeMillis()
                            if (now - lastWrite >= PROGRESS_WRITE_INTERVAL_MS) {
                                lastWrite = now
                                current =
                                    current.copy(
                                        bytesDownloaded = downloaded,
                                        totalBytes = total ?: current.totalBytes,
                                        progress =
                                            total
                                                ?.takeIf { it > 0 }
                                                ?.let {
                                                    (downloaded.toFloat() / it).coerceIn(0f, 1f)
                                                } ?: 0f,
                                        updatedAt = now,
                                    )
                                databaseRepository.insertDownload(current)
                            }
                        }
                    if (!completed) return@withContext Result.success()
                    if (!partial.renameTo(finalFile)) {
                        throw IllegalStateException("Could not finalise ${finalFile.name}")
                    }
                }

                databaseRepository.insertDownload(
                    current.copy(
                        status = DownloadStatus.COMPLETED,
                        progress = 1f,
                        bytesDownloaded = finalFile.length(),
                        totalBytes = finalFile.length(),
                        filePath = finalFile.absolutePath,
                        imageUrl =
                            if (coverFile.exists()) coverFile.toURI().toString()
                            else current.imageUrl,
                        error = null,
                        updatedAt = System.currentTimeMillis(),
                    )
                )
                Result.success()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Book download failed")
                if (!isStopped) {
                    databaseRepository.getDownload(downloadId)?.let {
                        databaseRepository.insertDownload(
                            it.copy(
                                status = DownloadStatus.FAILED,
                                error = e.message ?: "Unknown error",
                                updatedAt = System.currentTimeMillis(),
                            )
                        )
                    }
                }
                Result.failure(workDataOf("error" to (e.message ?: "Unknown error")))
            }
        }

    private suspend fun saveItemRow(
        item: BaseItemDto,
        baseUrl: String,
        itemDir: File,
        token: String,
        download: DownloadDto,
    ) {
        val serverId = download.serverId
        val cover = File(itemDir, BookDownloadCover.FILE_NAME).takeIf { it.exists() }?.toUri()
        if (item.type == BaseItemKind.AUDIO_BOOK) {
            val part = item.toAfinityAudiobook(baseUrl)
            val images = if (cover == null) part.images else part.images.copy(primary = cover)
            databaseRepository.insertAudiobookPart(
                part.copy(
                    images = images,
                    people = localizePeople(part.people, itemDir, token),
                    bookPartCount = databaseRepository.getAudiobookPartCount(item.id, serverId),
                ),
                serverId,
            )
        } else {
            val book = item.toAfinityBook(baseUrl)
            val images = if (cover == null) book.images else book.images.copy(primary = cover)
            databaseRepository.insertBook(
                book.copy(images = images, people = localizePeople(book.people, itemDir, token)),
                serverId,
            )
        }
        item.userData?.let {
            databaseRepository.patchUserDataLocally(item.id, download.userId, serverId, it)
        }
    }

    private suspend fun localizePeople(
        people: List<AfinityPerson>,
        itemDir: File,
        token: String,
    ): List<AfinityPerson> {
        if (people.none { it.image.uri != null }) return people
        val peopleDir = File(itemDir, PEOPLE_DIR)
        if (!peopleDir.exists() && !peopleDir.mkdirs()) return people
        return people.map { person ->
            val remote = person.image.uri ?: return@map person
            val target = File(peopleDir, "${person.id}.jpg")
            val saved =
                target.exists() ||
                    try {
                        fetch(remote.toString(), token, target, resume = false) { _, _ -> }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.w(e, "Credit photo download failed for ${person.name}")
                        target.delete()
                        false
                    }
            if (saved && target.exists()) {
                person.copy(image = person.image.copy(uri = target.toUri()))
            } else {
                person
            }
        }
    }

    private suspend fun fetch(
        url: String,
        token: String,
        target: File,
        resume: Boolean,
        onProgress: suspend (Long, Long?) -> Unit,
    ): Boolean {
        val existing = if (resume && target.exists()) target.length() else 0L
        val request =
            Request.Builder()
                .url(url)
                .header("Authorization", "MediaBrowser Token=\"$token\"")
                .apply { if (existing > 0) header("Range", "bytes=$existing-") }
                .build()
        okHttpClient.newCall(request).execute().use { response ->
            if (response.code == 416 && existing > 0) return true
            if (!response.isSuccessful) {
                throw IllegalStateException("HTTP ${response.code}")
            }
            val append = response.code == 206 && existing > 0
            val body = response.body
            val total =
                body.contentLength().takeIf { it > 0 }?.let { if (append) it + existing else it }
            var downloaded = if (append) existing else 0L
            FileOutputStream(target, append).use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        if (isStopped) return false
                        val read = input.read(buffer)
                        if (read < 0) break
                        out.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, total)
                    }
                }
            }
        }
        return true
    }

    companion object {
        private const val MEDIA_DIR = "media"
        private const val PEOPLE_DIR = "people"
        private const val COVER_WIDTH = 600
        private const val BUFFER_SIZE = 64 * 1024
        private const val PROGRESS_WRITE_INTERVAL_MS = 500L
    }
}
