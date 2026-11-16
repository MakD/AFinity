package com.makd.afinity.data.books

import com.makd.afinity.data.models.download.DownloadStatus
import com.makd.afinity.data.models.media.AfinityBook
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.download.DownloadRepository
import com.makd.afinity.data.repository.download.JellyfinDownloadRepository
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class DownloadedBook(val book: AfinityBook, val file: File)

@Singleton
class BookMetadataStore
@Inject
constructor(
    private val downloadRepository: DownloadRepository,
    private val databaseRepository: DatabaseRepository,
) {

    suspend fun downloaded(itemId: UUID, userId: UUID? = null): DownloadedBook? =
        withContext(Dispatchers.IO) {
            val download = downloadRepository.getDownloadByItemId(itemId) ?: return@withContext null
            if (download.status != DownloadStatus.COMPLETED) return@withContext null
            val file =
                download.filePath?.let(::File)?.takeIf { it.exists() } ?: return@withContext null
            val book =
                databaseRepository.getBook(itemId, userId ?: download.userId)
                    ?: return@withContext null
            DownloadedBook(book, file)
        }

    suspend fun downloadedInSeries(seriesName: String): List<AfinityBook> =
        withContext(Dispatchers.IO) {
            val downloads =
                downloadRepository.getAllDownloadsFlow().first().filter {
                    it.itemType == JellyfinDownloadRepository.BOOK_ITEM_TYPE &&
                        it.status == DownloadStatus.COMPLETED &&
                        it.filePath?.let(::File)?.exists() == true
                }
            val userId = downloads.firstOrNull()?.userId ?: return@withContext emptyList()
            val downloadedIds = downloads.map { it.itemId }.toSet()
            databaseRepository.getBooksInSeries(seriesName, userId).filter {
                it.id in downloadedIds
            }
        }
}

object BookDownloadCover {
    const val FILE_NAME = "cover.jpg"
}
