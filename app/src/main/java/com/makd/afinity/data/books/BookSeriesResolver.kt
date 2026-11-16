package com.makd.afinity.data.books

import com.makd.afinity.data.models.extensions.toAfinityBook
import com.makd.afinity.data.models.media.AfinityBook
import com.makd.afinity.data.models.media.BookFormat
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.NoActiveSessionException
import com.makd.afinity.data.repository.media.MediaRepository
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import timber.log.Timber

class BookSeriesResolver
@Inject
constructor(
    private val mediaRepository: MediaRepository,
    private val bookMetadataStore: BookMetadataStore,
) {
    suspend fun series(book: AfinityBook, offline: Boolean = false): List<AfinityBook> {
        val seriesName = book.seriesName ?: return emptyList()
        if (offline) return downloadedSeries(seriesName)
        return try {
            remoteSeries(book, seriesName) ?: downloadedSeries(seriesName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e !is NoActiveSessionException)
                Timber.w(e, "Failed to resolve series for ${book.id}")
            downloadedSeries(seriesName)
        }
    }

    suspend fun nextAfter(book: AfinityBook, offline: Boolean = false): AfinityBook? {
        val list = series(book, offline)
        val index = list.indexOfFirst { it.id == book.id }
        if (index < 0) return null
        return list.getOrNull(index + 1)?.takeIf { it.format != BookFormat.KINDLE }
    }

    private suspend fun remoteSeries(book: AfinityBook, seriesName: String): List<AfinityBook>? {
        val baseUrl = mediaRepository.getBaseUrl()
        val folio =
            mediaRepository
                .getBoxSetsContaining(book.id, fields = listOf(ItemFields.PROVIDER_IDS))
                .filter { FOLIO_PROVIDER_ID in it.providerIds.orEmpty() }
                .let { sets ->
                    sets.firstOrNull {
                        it.providerIds?.get(FOLIO_PROVIDER_ID).equals(seriesName, ignoreCase = true)
                    } ?: sets.firstOrNull()
                }
        val parentId = folio?.id ?: book.parentId ?: return null
        val items =
            mediaRepository
                .getItemsResult(
                    parentId = parentId,
                    includeItemTypes = listOf("BOOK"),
                    fields = FieldSets.BOOK_GRID,
                    limit = SERIES_LIMIT,
                    recursive = folio == null,
                )
                .getOrElse { e ->
                    if (e is CancellationException) throw e
                    if (e !is NoActiveSessionException) {
                        Timber.w(e, "Failed to load series books for ${book.id}")
                    }
                    return null
                }
                .items
        return items
            .filter { it.type == BaseItemKind.BOOK }
            .map { it.toAfinityBook(baseUrl) }
            .filter { folio != null || it.seriesName.equals(seriesName, ignoreCase = true) }
            .sortedWith(seriesOrder)
    }

    private suspend fun downloadedSeries(seriesName: String): List<AfinityBook> =
        bookMetadataStore
            .downloadedInSeries(seriesName)
            .distinctBy { it.id }
            .sortedWith(seriesOrder)

    companion object {
        const val FOLIO_PROVIDER_ID = "f982556e-29c5-4fc0-9f68-83d00257041a"
        const val SERIES_LIMIT = 500
        private val seriesOrder =
            compareBy<AfinityBook>(
                { it.seriesIndex ?: Int.MAX_VALUE },
                { it.premiereDate },
                { it.name.lowercase() },
            )
    }
}
