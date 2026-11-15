package com.makd.afinity.ui.reader

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.books.BookContainer
import com.makd.afinity.data.books.BookFileCache
import com.makd.afinity.data.books.BookFileSniffer
import com.makd.afinity.data.books.BookMetadataStore
import com.makd.afinity.data.books.BookOpenError
import com.makd.afinity.data.books.BookOpenException
import com.makd.afinity.data.books.BookPageSource
import com.makd.afinity.data.books.BookProgressReporter
import com.makd.afinity.data.books.BookSeriesResolver
import com.makd.afinity.data.books.ComicPageSource
import com.makd.afinity.data.books.PaceKind
import com.makd.afinity.data.books.PaceTracker
import com.makd.afinity.data.books.PdfPageSource
import com.makd.afinity.data.books.ReadingPaceStore
import com.makd.afinity.data.models.extensions.toAfinityBook
import com.makd.afinity.data.models.media.AfinityBook
import com.makd.afinity.data.models.media.BookAnnotation
import com.makd.afinity.data.models.media.BookAnnotationKind
import com.makd.afinity.data.models.media.BookFormat
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.books.BookAnnotationRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.di.ApplicationScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlin.math.max
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber

sealed interface ReaderLoadState {
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long?) : ReaderLoadState

    data object Preparing : ReaderLoadState

    data class Ready(val pageCount: Int, val startPage: Int) : ReaderLoadState

    data class Failed(val error: ReaderError) : ReaderLoadState
}

enum class ReaderError {
    NOT_FOUND,
    DOWNLOAD_NOT_ALLOWED,
    UNSUPPORTED_FORMAT,
    NETWORK,
    CORRUPT,
    NO_PAGES,
    PASSWORD_PROTECTED,
}

data class PageReaderUiState(
    val title: String = "",
    val seriesName: String? = null,
    val seriesIndex: Int? = null,
    val format: BookFormat = BookFormat.UNKNOWN,
    val load: ReaderLoadState = ReaderLoadState.Downloading(0L, null),
    val currentPage: Int = 0,
    val verticalScroll: Boolean = false,
    val rightToLeft: Boolean = false,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class PageReaderViewModel
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val preferencesRepository: PreferencesRepository,
    private val bookFileCache: BookFileCache,
    private val bookMetadataStore: BookMetadataStore,
    private val progressReporter: BookProgressReporter,
    private val annotationRepository: BookAnnotationRepository,
    private val paceStore: ReadingPaceStore,
    private val seriesResolver: BookSeriesResolver,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _nextBook = MutableStateFlow<AfinityBook?>(null)
    val nextBook: StateFlow<AfinityBook?> = _nextBook.asStateFlow()

    private val paceTracker = PaceTracker(maxStep = MAX_PACE_STEP_PAGES)

    private val _millisPerPage = MutableStateFlow(PaceKind.PAGES.defaultMillisPerUnit)
    val millisPerPage: StateFlow<Double> = _millisPerPage.asStateFlow()

    val itemId: UUID = UUID.fromString(savedStateHandle.get<String>("itemId")!!)

    private val _uiState = MutableStateFlow(PageReaderUiState())
    val uiState: StateFlow<PageReaderUiState> = _uiState.asStateFlow()

    private val bookmarks: StateFlow<List<BookAnnotation>> =
        annotationRepository
            .observe(itemId)
            .map { list -> list.filter { it.kind == BookAnnotationKind.BOOKMARK } }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val bookmarkedPages: StateFlow<Set<Int>> =
        bookmarks
            .map { list -> list.mapNotNull { it.page }.toSet() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val _search = MutableStateFlow(ReaderSearchState())
    val search: StateFlow<ReaderSearchState> = _search.asStateFlow()

    private val _canSearch = MutableStateFlow(false)
    val canSearch: StateFlow<Boolean> = _canSearch.asStateFlow()

    private var searchJob: Job? = null

    fun search(query: String) {
        searchJob?.cancel()
        val normalized = normalizeSearchText(query)
        val pdf = source as? PdfPageSource
        if (normalized.isEmpty() || pdf == null) {
            _search.value = ReaderSearchState()
            return
        }
        _search.value = ReaderSearchState(query = normalized, searching = true)
        searchJob = viewModelScope.launch {
            try {
                for (page in 0 until pdf.pageCount) {
                    val text = normalizeSearchText(pdf.pageText(page))
                    val hits = findHits(text, normalized, page)
                    if (hits.isNotEmpty()) {
                        _search.update { it.copy(hits = (it.hits + hits).take(MAX_SEARCH_HITS)) }
                    }
                    if (_search.value.hits.size >= MAX_SEARCH_HITS) break
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "PDF search failed for $itemId")
            } finally {
                _search.update { it.copy(searching = false) }
            }
        }
    }

    fun selectSearchHit(index: Int): Int? {
        val hits = _search.value.hits
        if (hits.isEmpty()) return null
        val wrapped = ((index % hits.size) + hits.size) % hits.size
        _search.update { it.copy(selected = wrapped) }
        return hits[wrapped].page
    }

    fun closeSearchBar() {
        _search.update { it.copy(selected = null) }
    }

    fun toggleBookmark() {
        val ready = _uiState.value.load as? ReaderLoadState.Ready ?: return
        val page = _uiState.value.currentPage
        val existing = bookmarks.value.firstOrNull { it.page == page }
        viewModelScope.launch {
            if (existing != null) {
                annotationRepository.delete(existing.id)
            } else {
                annotationRepository.save(
                    itemId,
                    BookAnnotation(
                        id = UUID.randomUUID().toString(),
                        kind = BookAnnotationKind.BOOKMARK,
                        locatorJson = null,
                        page = page,
                        progression = page.toDouble() / (ready.pageCount - 1).coerceAtLeast(1),
                        label = null,
                        text = null,
                        color = null,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    private var source: BookPageSource? = null
    private val pageFlow = MutableStateFlow<Int?>(null)

    init {
        viewModelScope.launch { _millisPerPage.value = paceStore.millisPerUnit(PaceKind.PAGES) }
        viewModelScope.launch {
            val vertical = preferencesRepository.getReaderVerticalScroll()
            val rtl = preferencesRepository.getReaderRightToLeft()
            _uiState.update { it.copy(verticalScroll = vertical, rightToLeft = rtl) }
        }
        open()
        viewModelScope.launch {
            pageFlow
                .filterNotNull()
                .distinctUntilChanged()
                .drop(1)
                .debounce(PROGRESS_DEBOUNCE_MS)
                .collect { page -> progressReporter.progress(ticksFor(page)) }
        }
    }

    fun retry() {
        open()
    }

    private fun open() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(load = ReaderLoadState.Downloading(0L, null)) }
                val downloaded = bookMetadataStore.downloaded(itemId)
                val remote = mediaRepository.getItemResult(itemId, fields = FieldSets.BOOK_GRID)
                val loaded =
                    remote
                        .getOrNull()
                        ?.takeIf { it.type == BaseItemKind.BOOK }
                        ?.toAfinityBook(mediaRepository.getBaseUrl()) ?: downloaded?.book
                if (loaded == null) {
                    fail(if (remote.isFailure) ReaderError.NETWORK else ReaderError.NOT_FOUND)
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        title = loaded.name,
                        seriesName = loaded.seriesName,
                        seriesIndex = loaded.seriesIndex,
                        format = loaded.format,
                    )
                }
                if (loaded.format == BookFormat.EPUB || loaded.format == BookFormat.KINDLE) {
                    fail(ReaderError.UNSUPPORTED_FORMAT)
                    return@launch
                }
                val pageSource = openSource(loaded, downloaded?.file)
                source?.close()
                source = pageSource
                bookFileCache.touch(itemId)
                bookFileCache.evict(keep = itemId)
                val lastPage = pageSource.pageCount - 1
                val resumePage =
                    if (loaded.played) 0
                    else (loaded.playbackPositionTicks / AfinityBook.TICKS_PER_PAGE).toInt()
                val startPage = resumePage.coerceIn(0, max(0, lastPage))
                _uiState.update {
                    it.copy(
                        load = ReaderLoadState.Ready(pageSource.pageCount, startPage),
                        currentPage = startPage,
                    )
                }
                pageFlow.value = startPage
                progressReporter.start(itemId, loaded.runtimeTicks, ticksFor(startPage))
                viewModelScope.launch { _nextBook.value = seriesResolver.nextAfter(loaded) }
                _canSearch.value =
                    (pageSource as? PdfPageSource)?.let {
                        runCatching { it.hasText() }.getOrDefault(false)
                    } ?: false
            } catch (e: CancellationException) {
                throw e
            } catch (e: BookOpenException) {
                fail(e.error.toReaderError())
            } catch (e: ReaderDownloadNotAllowed) {
                fail(ReaderError.DOWNLOAD_NOT_ALLOWED)
            } catch (e: Exception) {
                Timber.e(e, "Failed to open book $itemId")
                fail(ReaderError.CORRUPT)
            }
        }
    }

    private suspend fun openSource(loaded: AfinityBook, offlineFile: File?): BookPageSource {
        val dir = bookFileCache.bookDir(itemId)
        ComicPageSource.cached(dir)?.let {
            return it
        }
        val file = offlineFile ?: bookFileCache.sourceFile(itemId)
        if (!file.exists()) {
            if (!loaded.canDownload) throw ReaderDownloadNotAllowed()
            bookFileCache.download(itemId) { downloaded, total ->
                _uiState.update { it.copy(load = ReaderLoadState.Downloading(downloaded, total)) }
            }
        }
        _uiState.update { it.copy(load = ReaderLoadState.Preparing) }
        return when (BookFileSniffer.detect(file)) {
            BookContainer.PDF -> PdfPageSource.open(dir, file, renderWidth())
            BookContainer.ARCHIVE ->
                ComicPageSource.extract(dir, file, deleteSource = offlineFile == null)
            BookContainer.UNKNOWN -> throw BookOpenException(BookOpenError.UNSUPPORTED_FORMAT)
        }
    }

    private fun renderWidth(): Int {
        val metrics = context.resources.displayMetrics
        val longest = max(metrics.widthPixels, metrics.heightPixels)
        return (longest * PDF_RENDER_SCALE).toInt().coerceAtMost(PDF_MAX_RENDER_WIDTH)
    }

    suspend fun pageFile(index: Int): File? =
        try {
            source?.page(index)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to load page $index of $itemId")
            null
        }

    suspend fun thumbnailFile(index: Int): File? =
        try {
            source?.thumbnail(index)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to load thumbnail $index of $itemId")
            null
        }

    fun onPageChanged(index: Int) {
        val pageCount = (_uiState.value.load as? ReaderLoadState.Ready)?.pageCount ?: return
        val page = index.coerceIn(0, pageCount - 1)
        paceTracker.onPosition(page.toDouble())?.let { (pages, millis) ->
            viewModelScope.launch {
                paceStore.record(PaceKind.PAGES, pages, millis)
                _millisPerPage.value = paceStore.millisPerUnit(PaceKind.PAGES)
            }
        }
        if (_uiState.value.currentPage == page) return
        _uiState.update { it.copy(currentPage = page) }
        pageFlow.value = page
    }

    fun setVerticalScroll(enabled: Boolean) {
        _uiState.update { it.copy(verticalScroll = enabled) }
        viewModelScope.launch { preferencesRepository.setReaderVerticalScroll(enabled) }
    }

    fun setRightToLeft(enabled: Boolean) {
        _uiState.update { it.copy(rightToLeft = enabled) }
        viewModelScope.launch { preferencesRepository.setReaderRightToLeft(enabled) }
    }

    private fun ticksFor(page: Int): Long = page.toLong() * AfinityBook.TICKS_PER_PAGE

    override fun onCleared() {
        val opened = source
        source = null
        val ready = _uiState.value.load as? ReaderLoadState.Ready
        if (ready != null) {
            val page = _uiState.value.currentPage
            progressReporter.stop(ticksFor(page), finished = page >= ready.pageCount - 1)
        }
        applicationScope.launch { opened?.close() }
        super.onCleared()
    }

    private fun fail(error: ReaderError) {
        _uiState.update { it.copy(load = ReaderLoadState.Failed(error)) }
    }

    private class ReaderDownloadNotAllowed : Exception()

    private fun BookOpenError.toReaderError(): ReaderError =
        when (this) {
            BookOpenError.NOT_SIGNED_IN,
            BookOpenError.NETWORK -> ReaderError.NETWORK
            BookOpenError.UNSUPPORTED_FORMAT -> ReaderError.UNSUPPORTED_FORMAT
            BookOpenError.CORRUPT -> ReaderError.CORRUPT
            BookOpenError.NO_PAGES -> ReaderError.NO_PAGES
            BookOpenError.PASSWORD_PROTECTED -> ReaderError.PASSWORD_PROTECTED
        }

    companion object {
        private const val PROGRESS_DEBOUNCE_MS = 1_500L
        private const val PDF_RENDER_SCALE = 1.5f
        private const val PDF_MAX_RENDER_WIDTH = 3000
        private const val MAX_PACE_STEP_PAGES = 3.0
    }
}
