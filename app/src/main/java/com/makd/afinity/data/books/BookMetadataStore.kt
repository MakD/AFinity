package com.makd.afinity.data.books

import androidx.core.net.toUri
import com.makd.afinity.data.models.download.DownloadStatus
import com.makd.afinity.data.models.extensions.toAfinityBook
import com.makd.afinity.data.models.media.AfinityBook
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.download.DownloadRepository
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.api.BaseItemDto
import timber.log.Timber

data class DownloadedBook(val book: AfinityBook, val file: File)

@Singleton
class BookMetadataStore
@Inject
constructor(
    private val downloadRepository: DownloadRepository,
    private val databaseRepository: DatabaseRepository,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun write(itemDir: File, item: BaseItemDto) {
        File(itemDir, METADATA_FILE).writeText(json.encodeToString(BaseItemDto.serializer(), item))
    }

    suspend fun downloaded(itemId: UUID, userId: UUID? = null): DownloadedBook? =
        withContext(Dispatchers.IO) {
            val download = downloadRepository.getDownloadByItemId(itemId) ?: return@withContext null
            if (download.status != DownloadStatus.COMPLETED) return@withContext null
            val file =
                download.filePath?.let(::File)?.takeIf { it.exists() } ?: return@withContext null
            val dir = file.parentFile?.parentFile ?: return@withContext null
            val book = read(dir, userId ?: download.userId) ?: return@withContext null
            DownloadedBook(book, file)
        }

    private suspend fun read(dir: File, userId: UUID): AfinityBook? {
        val metadata = File(dir, METADATA_FILE)
        if (!metadata.exists()) return null
        val dto =
            try {
                json.decodeFromString(BaseItemDto.serializer(), metadata.readText())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Unreadable book metadata in ${dir.name}")
                return null
            }
        val cover = File(dir, BookDownloadCover.FILE_NAME)
        val book = dto.toAfinityBook(baseUrl = "")
        val local = databaseRepository.getUserData(userId, dto.id)
        val withImages =
            if (cover.exists()) book.copy(images = book.images.copy(primary = cover.toUri()))
            else book
        return if (local != null) {
            withImages.copy(
                played = local.played,
                favorite = local.favorite,
                playbackPositionTicks = local.playbackPositionTicks,
            )
        } else {
            withImages
        }
    }

    companion object {
        const val METADATA_FILE = "book.json"
    }
}

object BookDownloadCover {
    const val FILE_NAME = "cover.jpg"
}
