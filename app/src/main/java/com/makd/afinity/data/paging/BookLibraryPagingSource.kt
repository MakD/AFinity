package com.makd.afinity.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.extensions.toAfinityBook
import com.makd.afinity.data.models.media.AfinityAudiobook
import com.makd.afinity.data.models.media.AfinityBook
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.LibraryFilters
import com.makd.afinity.data.models.media.groupedIntoBooks
import com.makd.afinity.data.models.media.toItemFilterCriteria
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.media.MediaRepository
import java.time.LocalDateTime
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber

data class BookLibraryKey(val bookOffset: Int, val audiobookOffset: Int)

class BookLibraryPagingSource(
    private val mediaRepository: MediaRepository,
    private val parentId: UUID?,
    private val sortBy: SortBy,
    private val sortDescending: Boolean,
    private val filters: LibraryFilters,
    private val baseUrl: String,
    private val nameStartsWith: String?,
    private val includeBooks: Boolean,
    private val includeAudiobooks: Boolean,
) : PagingSource<BookLibraryKey, AfinityItem>() {

    private var audiobooks: List<AfinityAudiobook>? = null
    private val random = Random(System.nanoTime())
    private val comparator = bookLibraryComparator(sortBy, sortDescending)

    override suspend fun load(
        params: LoadParams<BookLibraryKey>,
    ): LoadResult<BookLibraryKey, AfinityItem> =
        try {
            val key = params.key ?: BookLibraryKey(0, 0)
            val size = params.loadSize
            val audio = if (includeAudiobooks) audiobooks ?: loadAudiobooks() else emptyList()
            val books = if (includeBooks) loadBooks(key.bookOffset, size) else emptyList()
            val booksExhausted = !includeBooks || books.size < size

            val page = ArrayList<AfinityItem>(size)
            var bookIndex = 0
            var audioIndex = key.audiobookOffset
            while (page.size < size) {
                val book = books.getOrNull(bookIndex)
                val audiobook = audio.getOrNull(audioIndex)
                when {
                    book == null && audiobook == null -> break
                    book == null -> {
                        if (!booksExhausted) break
                        page += audiobook!!
                        audioIndex++
                    }
                    audiobook == null -> {
                        page += book
                        bookIndex++
                    }
                    takesAudiobookFirst(audiobook, book) -> {
                        page += audiobook
                        audioIndex++
                    }
                    else -> {
                        page += book
                        bookIndex++
                    }
                }
            }

            val finished = (booksExhausted && bookIndex >= books.size) && audioIndex >= audio.size
            LoadResult.Page(
                data = page,
                prevKey = null,
                nextKey =
                    if (page.isEmpty() || finished) null
                    else BookLibraryKey(key.bookOffset + bookIndex, audioIndex),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to load book library page")
            LoadResult.Error(e)
        }

    override fun getRefreshKey(state: PagingState<BookLibraryKey, AfinityItem>): BookLibraryKey? =
        null

    private fun takesAudiobookFirst(audiobook: AfinityItem, book: AfinityItem): Boolean =
        if (sortBy == SortBy.RANDOM) random.nextBoolean()
        else comparator.compare(audiobook, book) <= 0

    private suspend fun loadBooks(offset: Int, size: Int): List<AfinityBook> =
        mediaRepository
            .getItemsResult(
                parentId = parentId,
                sortBy = sortBy,
                sortDescending = sortDescending,
                limit = size,
                startIndex = offset,
                includeItemTypes = listOf("BOOK"),
                nameStartsWith = nameStartsWith,
                fields = FieldSets.BOOK_GRID,
                recursive = true,
                criteria = filters.toItemFilterCriteria(),
            )
            .getOrThrow()
            .items
            .filter { it.type == BaseItemKind.BOOK }
            .map { it.toAfinityBook(baseUrl) }

    private suspend fun loadAudiobooks(): List<AfinityAudiobook> {
        val serverFilters =
            filters.copy(
                played = false,
                unplayed = false,
                resumable = false,
                favorites = false,
                watchlist = false,
            )
        val books =
            mediaRepository
                .getAudiobooksResult(
                    parentId = parentId,
                    fields = FieldSets.AUDIOBOOK_GRID,
                    criteria = serverFilters.toItemFilterCriteria(),
                )
                .getOrThrow()
                .groupedIntoBooks()
                .filter { it.matchesStatus(filters) && it.matchesLetter(nameStartsWith) }
        val ordered =
            if (sortBy == SortBy.RANDOM) books.shuffled(random) else books.sortedWith(comparator)
        audiobooks = ordered
        return ordered
    }
}

private fun AfinityAudiobook.matchesStatus(filters: LibraryFilters): Boolean {
    if (filters.played && !filters.unplayed && !played) return false
    if (filters.unplayed && !filters.played && played) return false
    if (filters.resumable && (played || playbackPositionTicks <= 0)) return false
    if (filters.favorites && !favorite) return false
    if (filters.watchlist && !liked) return false
    return true
}

private fun AfinityAudiobook.matchesLetter(letter: String?): Boolean {
    if (letter.isNullOrEmpty()) return true
    val first = sortableName(name).firstOrNull() ?: return false
    return if (letter == "0") !first.isLetter() else first.toString().equals(letter, true)
}

private val LEADING_ARTICLES = listOf("the ", "a ", "an ")

internal fun sortableName(name: String): String {
    val lower = name.trim().lowercase()
    val article = LEADING_ARTICLES.firstOrNull { lower.startsWith(it) } ?: return lower
    return lower.removePrefix(article).trimStart()
}

private fun AfinityItem.addedAt(): LocalDateTime? =
    when (this) {
        is AfinityBook -> dateCreated
        is AfinityAudiobook -> dateCreated
        else -> null
    }

private fun AfinityItem.releasedAt(): LocalDateTime? =
    when (this) {
        is AfinityBook -> premiereDate ?: productionYear?.let { LocalDateTime.of(it, 1, 1, 0, 0) }
        is AfinityAudiobook ->
            premiereDate ?: productionYear?.let { LocalDateTime.of(it, 1, 1, 0, 0) }
        else -> null
    }

private fun AfinityItem.playedAt(): LocalDateTime? =
    when (this) {
        is AfinityBook -> lastPlayedDate
        is AfinityAudiobook -> lastPlayedDate
        else -> null
    }

private fun AfinityItem.seriesPosition(): Int? = (this as? AfinityBook)?.seriesIndex

fun bookLibraryComparator(sortBy: SortBy, descending: Boolean): Comparator<AfinityItem> {
    val byName = compareBy<AfinityItem> { sortableName(it.name) }
    val primary: Comparator<AfinityItem> =
        when (sortBy) {
            SortBy.DATE_ADDED -> compareBy(nullsFirst<LocalDateTime>()) { it.addedAt() }
            SortBy.RELEASE_DATE -> compareBy(nullsFirst<LocalDateTime>()) { it.releasedAt() }
            SortBy.DATE_PLAYED -> compareBy(nullsFirst<LocalDateTime>()) { it.playedAt() }
            SortBy.SERIES_NUMBER -> compareBy(nullsFirst<Int>()) { it.seriesPosition() }
            else -> byName
        }
    val ordered = if (descending) primary.reversed() else primary
    return ordered.thenComparing(byName)
}
