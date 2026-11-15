package com.makd.afinity.data.audiobooks

import androidx.core.net.toUri
import com.makd.afinity.data.books.BookDownloadCover
import com.makd.afinity.data.books.BookMetadataStore
import com.makd.afinity.data.database.entities.DownloadDto
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.download.DownloadStatus
import com.makd.afinity.data.models.extensions.toAfinityAudiobook
import com.makd.afinity.data.models.media.AfinityAudiobook
import com.makd.afinity.data.models.media.inPartOrder
import com.makd.afinity.data.repository.DatabaseRepository
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.api.BaseItemDto
import timber.log.Timber

const val AUDIOBOOK_DOWNLOAD_TYPE = "AudioBook"

@Singleton
class AudiobookDownloadStore
@Inject
constructor(
    private val sessionManager: SessionManager,
    private val databaseRepository: DatabaseRepository,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    suspend fun localFile(partId: UUID): File? =
        withContext(Dispatchers.IO) {
            completedDownloads()
                .firstOrNull { it.itemId == partId }
                ?.filePath
                ?.let(::File)
                ?.takeIf { it.exists() }
        }

    suspend fun downloadedParts(): List<AfinityAudiobook> =
        withContext(Dispatchers.IO) {
            val userId =
                sessionManager.currentSession.value?.userId ?: return@withContext emptyList()
            completedDownloads().mapNotNull { read(it, userId) }
        }

    suspend fun downloadedBookParts(itemId: UUID): List<AfinityAudiobook>? =
        withContext(Dispatchers.IO) {
            val userId = sessionManager.currentSession.value?.userId ?: return@withContext null
            val downloads = completedDownloads()
            val anchor = downloads.firstOrNull { it.itemId == itemId } ?: return@withContext null
            val bookKey = anchor.seriesId
            val siblings =
                if (bookKey == null) listOf(anchor) else downloads.filter { it.seriesId == bookKey }
            siblings.mapNotNull { read(it, userId) }.takeIf { it.isNotEmpty() }?.inPartOrder()
        }

    private suspend fun completedDownloads(): List<DownloadDto> {
        val session = sessionManager.currentSession.value ?: return emptyList()
        return databaseRepository
            .getAllDownloadsFlowScoped(session.serverId, session.userId)
            .first()
            .filter {
                it.itemType == AUDIOBOOK_DOWNLOAD_TYPE && it.status == DownloadStatus.COMPLETED
            }
    }

    private suspend fun read(download: DownloadDto, userId: UUID): AfinityAudiobook? {
        val file = download.filePath?.let(::File)?.takeIf { it.exists() } ?: return null
        val dir = file.parentFile?.parentFile ?: return null
        val metadata = File(dir, BookMetadataStore.METADATA_FILE)
        if (!metadata.exists()) return null
        val dto =
            try {
                json.decodeFromString(BaseItemDto.serializer(), metadata.readText())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Unreadable audiobook metadata in ${dir.name}")
                return null
            }
        val part = dto.toAfinityAudiobook(baseUrl = "")
        val cover = File(dir, BookDownloadCover.FILE_NAME)
        val withImages =
            if (cover.exists()) part.copy(images = part.images.copy(primary = cover.toUri()))
            else part
        val local = databaseRepository.getUserData(userId, dto.id) ?: return withImages
        return withImages.copy(
            played = local.played,
            favorite = local.favorite,
            playbackPositionTicks = local.playbackPositionTicks,
        )
    }
}
