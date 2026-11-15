package com.makd.afinity.ui.reader.epub

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
import com.makd.afinity.data.books.BookProgressReporter
import com.makd.afinity.data.books.BookSeriesResolver
import com.makd.afinity.data.books.PaceKind
import com.makd.afinity.data.books.PaceTracker
import com.makd.afinity.data.books.ReadingPaceStore
import com.makd.afinity.data.models.extensions.toAfinityBook
import com.makd.afinity.data.models.media.AfinityBook
import com.makd.afinity.data.models.media.BookAnnotation
import com.makd.afinity.data.models.media.BookAnnotationKind
import com.makd.afinity.data.models.media.BookFormat
import com.makd.afinity.data.models.media.HighlightColor
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.books.BookAnnotationRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.di.ApplicationScope
import com.makd.afinity.ui.reader.MAX_SEARCH_HITS
import com.makd.afinity.ui.reader.ReaderError
import com.makd.afinity.ui.reader.ReaderSearchHit
import com.makd.afinity.ui.reader.ReaderSearchState
import com.makd.afinity.ui.reader.normalizeSearchText
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.BaseItemKind
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.epub.EpubPreferencesSerializer
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.locateProgression
import org.readium.r2.shared.publication.services.positions
import org.readium.r2.shared.publication.services.search.isSearchable
import org.readium.r2.shared.publication.services.search.search
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.shared.util.mediatype.MediaType
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.DefaultPublicationParser
import timber.log.Timber

sealed interface EpubLoadState {
    data class Downloading(val downloadedBytes: Long, val totalBytes: Long?) : EpubLoadState

    data object Opening : EpubLoadState

    data class Ready(
        val publication: Publication,
        val initialLocator: Locator?,
        val initialPreferences: EpubPreferences,
    ) : EpubLoadState

    data class Failed(val error: ReaderError) : EpubLoadState
}

data class EpubTocEntry(
    val title: String,
    val depth: Int,
    val progression: Double?,
    val link: Link,
    val part: String?,
    val hasChildren: Boolean,
)

data class EpubReaderUiState
@OptIn(ExperimentalReadiumApi::class)
constructor(
    val title: String = "",
    val authors: String? = null,
    val load: EpubLoadState = EpubLoadState.Downloading(0L, null),
    val preferences: EpubPreferences = EpubPreferences(),
    val progression: Double = 0.0,
    val chapterTitle: String? = null,
    val toc: List<EpubTocEntry> = emptyList(),
    val boundaries: List<Float> = emptyList(),
    val positionCount: Int = 0,
)

internal fun List<EpubTocEntry>.indexAt(progression: Double, chapterTitle: String? = null): Int {
    var found = -1
    forEachIndexed { index, entry ->
        val start = entry.progression ?: return@forEachIndexed
        if (start <= progression + PROGRESSION_EPSILON) found = index
    }
    if (found < 0 || chapterTitle.isNullOrBlank()) return found
    val groupStart = this[found].progression
    return indices.lastOrNull {
        this[it].progression == groupStart && this[it].title == chapterTitle
    } ?: found
}

internal fun List<EpubTocEntry>.chapterEnd(index: Int): Double {
    val start = getOrNull(index)?.progression ?: return 1.0
    return asSequence()
        .mapNotNull { it.progression }
        .filter { it > start + PROGRESSION_EPSILON }
        .minOrNull() ?: 1.0
}

internal fun List<EpubTocEntry>.chapterFraction(index: Int, progression: Double): Double? {
    val start = getOrNull(index)?.progression ?: return null
    val end =
        asSequence()
            .mapNotNull { it.progression }
            .filter { it > start + PROGRESSION_EPSILON }
            .minOrNull() ?: 1.0
    if (end <= start) return null
    return ((progression - start) / (end - start)).coerceIn(0.0, 1.0)
}

private const val PROGRESSION_EPSILON = 1e-6
internal const val EPUB_FINISHED_PROGRESSION = 0.98

@OptIn(ExperimentalReadiumApi::class, FlowPreview::class)
@HiltViewModel
class EpubReaderViewModel
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

    private val paceTracker = PaceTracker(maxStep = MAX_PACE_STEP_POSITIONS)

    private val _millisPerPosition = MutableStateFlow(PaceKind.TEXT.defaultMillisPerUnit)
    val millisPerPosition: StateFlow<Double> = _millisPerPosition.asStateFlow()

    val itemId: UUID = UUID.fromString(savedStateHandle.get<String>(EXTRA_ITEM_ID)!!)

    private val _uiState = MutableStateFlow(EpubReaderUiState())
    val uiState: StateFlow<EpubReaderUiState> = _uiState.asStateFlow()

    val annotations: StateFlow<List<BookAnnotation>> =
        annotationRepository
            .observe(itemId)
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var positionSpan: Double = DEFAULT_POSITION_SPAN

    fun bookmarkAt(
        progression: Double,
        list: List<BookAnnotation> = annotations.value,
    ): BookAnnotation? =
        list
            .filter { it.kind == BookAnnotationKind.BOOKMARK }
            .firstOrNull { abs(it.progression - progression) < positionSpan / 2 }

    fun toggleBookmark() {
        val locator = locatorFlow.value ?: return
        val existing = bookmarkAt(locator.locations.totalProgression ?: return)
        viewModelScope.launch {
            if (existing != null) {
                annotationRepository.delete(existing.id)
            } else {
                annotationRepository.save(
                    itemId,
                    BookAnnotation(
                        id = UUID.randomUUID().toString(),
                        kind = BookAnnotationKind.BOOKMARK,
                        locatorJson = locator.toJSON().toString(),
                        page = null,
                        progression = locator.locations.totalProgression ?: 0.0,
                        label = _uiState.value.chapterTitle ?: locator.title,
                        text = null,
                        color = null,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    fun addHighlight(locator: Locator) {
        val text = locator.text.highlight?.trim()
        if (text.isNullOrEmpty()) return
        viewModelScope.launch {
            annotationRepository.save(
                itemId,
                BookAnnotation(
                    id = UUID.randomUUID().toString(),
                    kind = BookAnnotationKind.HIGHLIGHT,
                    locatorJson = locator.toJSON().toString(),
                    page = null,
                    progression =
                        locator.locations.totalProgression
                            ?: locatorFlow.value?.locations?.totalProgression
                            ?: 0.0,
                    label = locator.title ?: _uiState.value.chapterTitle,
                    text = text,
                    color = annotationRepository.lastHighlightColor(),
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    fun setHighlightColor(id: String, color: HighlightColor) {
        viewModelScope.launch {
            annotationRepository.setColor(id, color)
            annotationRepository.setLastHighlightColor(color)
        }
    }

    fun deleteAnnotation(id: String) {
        viewModelScope.launch { annotationRepository.delete(id) }
    }

    private val _search = MutableStateFlow(ReaderSearchState())
    val search: StateFlow<ReaderSearchState> = _search.asStateFlow()

    private val _canSearch = MutableStateFlow(false)
    val canSearch: StateFlow<Boolean> = _canSearch.asStateFlow()

    private var searchJob: Job? = null

    fun search(query: String) {
        searchJob?.cancel()
        val normalized = normalizeSearchText(query)
        val opened = publication
        if (normalized.isEmpty() || opened == null) {
            _search.value = ReaderSearchState()
            return
        }
        _search.value = ReaderSearchState(query = normalized, searching = true)
        searchJob = viewModelScope.launch {
            val iterator =
                try {
                    opened.search(normalized)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "EPUB search failed for $itemId")
                    null
                }
            if (iterator == null) {
                _search.update { it.copy(searching = false) }
                return@launch
            }
            try {
                while (_search.value.hits.size < MAX_SEARCH_HITS) {
                    val page = iterator.next().getOrNull() ?: break
                    val hits =
                        page.locators.map { locator ->
                            ReaderSearchHit(
                                group = locator.title,
                                before = locator.text.before.orEmpty().takeLast(SNIPPET_CHARS),
                                match = locator.text.highlight.orEmpty(),
                                after = locator.text.after.orEmpty().take(SNIPPET_CHARS),
                                locatorJson = locator.toJSON().toString(),
                            )
                        }
                    _search.update { it.copy(hits = (it.hits + hits).take(MAX_SEARCH_HITS)) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "EPUB search iteration failed for $itemId")
            } finally {
                iterator.close()
                _search.update { it.copy(searching = false) }
            }
        }
    }

    fun selectSearchHit(index: Int): Locator? {
        val hits = _search.value.hits
        if (hits.isEmpty()) return null
        val wrapped = ((index % hits.size) + hits.size) % hits.size
        _search.update { it.copy(selected = wrapped) }
        return hits[wrapped].locatorJson?.let {
            runCatching { Locator.fromJSON(JSONObject(it)) }.getOrNull()
        }
    }

    fun closeSearchBar() {
        _search.update { it.copy(selected = null) }
    }

    private val serializer = EpubPreferencesSerializer()
    private val locatorFlow = MutableStateFlow<Locator?>(null)
    private var publication: Publication? = null

    init {
        viewModelScope.launch { _millisPerPosition.value = paceStore.millisPerUnit(PaceKind.TEXT) }
        open()
        viewModelScope.launch {
            locatorFlow
                .filterNotNull()
                .distinctUntilChanged()
                .drop(1)
                .debounce(PROGRESS_DEBOUNCE_MS.milliseconds)
                .collect { locator ->
                    saveLocator(locator)
                    progressReporter.progress(ticksFor(locator))
                }
        }
    }

    fun retry() {
        open()
    }

    private fun open() {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(load = EpubLoadState.Downloading(0L, null)) }
                val downloaded = bookMetadataStore.downloaded(itemId)
                val remote = mediaRepository.getItemResult(itemId, fields = FieldSets.BOOK_GRID)
                val book =
                    remote
                        .getOrNull()
                        ?.takeIf { it.type == BaseItemKind.BOOK }
                        ?.toAfinityBook(mediaRepository.getBaseUrl()) ?: downloaded?.book
                if (book == null) {
                    fail(if (remote.isFailure) ReaderError.NETWORK else ReaderError.NOT_FOUND)
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        title = book.name,
                        authors =
                            book.authors
                                .map { person -> person.name }
                                .distinct()
                                .joinToString(", ")
                                .ifBlank { null },
                    )
                }
                if (book.format != BookFormat.EPUB && book.format != BookFormat.UNKNOWN) {
                    fail(ReaderError.UNSUPPORTED_FORMAT)
                    return@launch
                }
                val file = downloaded?.file ?: bookFileCache.sourceFile(itemId)
                if (!file.exists()) {
                    if (!book.canDownload) {
                        fail(ReaderError.DOWNLOAD_NOT_ALLOWED)
                        return@launch
                    }
                    bookFileCache.download(itemId) { downloaded, total ->
                        _uiState.update {
                            it.copy(load = EpubLoadState.Downloading(downloaded, total))
                        }
                    }
                }
                _uiState.update { it.copy(load = EpubLoadState.Opening) }
                if (BookFileSniffer.detect(file) != BookContainer.ARCHIVE) {
                    fail(ReaderError.UNSUPPORTED_FORMAT)
                    return@launch
                }
                val opened = openPublication(file) ?: return@launch
                publication?.close()
                publication = opened
                bookFileCache.touch(itemId)
                bookFileCache.evict(keep = itemId)

                val serverProgression =
                    if (book.played || book.runtimeTicks <= 0) 0.0
                    else
                        (book.playbackPositionTicks.toDouble() / book.runtimeTicks).coerceIn(
                            0.0,
                            1.0,
                        )
                val initialLocator = initialLocator(opened, serverProgression)
                val preferences = loadPreferences()
                _uiState.update {
                    it.copy(
                        load = EpubLoadState.Ready(opened, initialLocator, preferences),
                        preferences = preferences,
                        progression = initialLocator?.locations?.totalProgression ?: 0.0,
                    )
                }
                progressReporter.start(
                    itemId = itemId,
                    runtimeTicks = book.runtimeTicks.takeIf { it > 0 } ?: TICKS_FULL,
                    positionTicks =
                        ((initialLocator?.locations?.totalProgression ?: 0.0) * TICKS_FULL)
                            .roundToLong(),
                )
                _canSearch.value = opened.isSearchable
                viewModelScope.launch {
                    _nextBook.value =
                        seriesResolver.nextAfter(book)?.takeIf {
                            it.format == BookFormat.EPUB || it.format == BookFormat.UNKNOWN
                        }
                }
                loadContents(opened)
            } catch (e: BookOpenException) {
                fail(
                    when (e.error) {
                        BookOpenError.NOT_SIGNED_IN,
                        BookOpenError.NETWORK -> ReaderError.NETWORK
                        BookOpenError.UNSUPPORTED_FORMAT -> ReaderError.UNSUPPORTED_FORMAT
                        BookOpenError.PASSWORD_PROTECTED -> ReaderError.PASSWORD_PROTECTED
                        BookOpenError.NO_PAGES -> ReaderError.NO_PAGES
                        BookOpenError.CORRUPT -> ReaderError.CORRUPT
                    }
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to open EPUB $itemId")
                fail(ReaderError.CORRUPT)
            }
        }
    }

    private suspend fun openPublication(file: File): Publication? {
        val httpClient = DefaultHttpClient()
        val assetRetriever = AssetRetriever(context.contentResolver, httpClient)
        val opener =
            PublicationOpener(
                publicationParser =
                    DefaultPublicationParser(
                        context = context,
                        httpClient = httpClient,
                        assetRetriever = assetRetriever,
                        pdfFactory = null,
                    )
            )
        val asset =
            assetRetriever.retrieve(file, MediaType.EPUB).getOrElse { error ->
                Timber.w("EPUB asset retrieval failed for $itemId: ${error.message}")
                fail(ReaderError.CORRUPT)
                return null
            }
        return opener.open(asset, allowUserInteraction = false).getOrElse { error ->
            Timber.w("EPUB open failed for $itemId: ${error.message}")
            asset.close()
            fail(ReaderError.CORRUPT)
            null
        }
    }

    private suspend fun loadContents(opened: Publication) {
        val positions =
            try {
                opened.positions()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "EPUB positions failed for $itemId")
                emptyList()
            }
        if (positions.isNotEmpty()) {
            positionSpan = 1.0 / positions.size
            _uiState.update { it.copy(positionCount = positions.size) }
            paceStore.setTextLength(itemId, positions.size)
        }
        val starts = HashMap<String, Double>()
        positions.forEach { locator ->
            val key = locator.href.removeFragment().normalize().toString()
            val progression = locator.locations.totalProgression ?: return@forEach
            if (key !in starts) starts[key] = progression
        }
        val roots = opened.tableOfContents
        val top = roots.singleOrNull()?.children?.takeIf { it.isNotEmpty() } ?: roots
        val entries = mutableListOf<EpubTocEntry>()
        fun walk(links: List<Link>, depth: Int, part: String?) {
            links.forEach { link ->
                val title = link.title?.trim().orEmpty()
                val key = link.url().removeFragment().normalize().toString()
                if (title.isNotEmpty()) {
                    entries +=
                        EpubTocEntry(
                            title = title,
                            depth = depth,
                            progression = starts[key],
                            link = link,
                            part = part,
                            hasChildren = link.children.isNotEmpty(),
                        )
                }
                walk(link.children, depth + 1, if (depth == 0) title.ifEmpty { part } else part)
            }
        }
        walk(top, 0, null)
        val topLevel = entries.filter { it.depth == 0 }
        val segmentSource = if (topLevel.size >= 2) topLevel else entries
        val boundaries =
            segmentSource
                .mapNotNull { it.progression?.toFloat() }
                .filter { it > SEGMENT_EDGE && it < 1f - SEGMENT_EDGE }
                .distinct()
                .sorted()
        _uiState.update { it.copy(toc = entries, boundaries = boundaries) }
    }

    private suspend fun initialLocator(opened: Publication, serverProgression: Double): Locator? {
        val saved = readSavedLocator()
        val savedProgression = saved?.locations?.totalProgression
        if (saved != null && savedProgression != null) {
            if (abs(savedProgression - serverProgression) <= LOCATOR_MATCH_TOLERANCE) return saved
        }
        if (serverProgression <= 0.0) return null
        return opened.locateProgression(serverProgression)
    }

    private fun locatorFile(): File = File(bookFileCache.bookDir(itemId), LOCATOR_FILE)

    private suspend fun readSavedLocator(): Locator? =
        withContext(Dispatchers.IO) {
            runCatching { Locator.fromJSON(JSONObject(locatorFile().readText())) }.getOrNull()
        }

    private suspend fun saveLocator(locator: Locator) {
        withContext(Dispatchers.IO) {
            runCatching { locatorFile().writeText(locator.toJSON().toString()) }
        }
    }

    private suspend fun loadPreferences(): EpubPreferences {
        val stored = preferencesRepository.getStringPreference(PREFERENCES_KEY)
        if (stored != null) {
            runCatching { serializer.deserialize(stored) }
                .getOrNull()
                ?.let {
                    return it
                }
        }
        return EpubPreferences()
    }

    fun updatePreferences(transform: (EpubPreferences) -> EpubPreferences) {
        val next = transform(_uiState.value.preferences)
        _uiState.update { it.copy(preferences = next) }
        viewModelScope.launch {
            preferencesRepository.setStringPreference(PREFERENCES_KEY, serializer.serialize(next))
        }
    }

    fun onLocatorChanged(locator: Locator) {
        val count = _uiState.value.positionCount
        val total = locator.locations.totalProgression
        if (count > 0 && total != null) {
            paceTracker.onPosition(total * count)?.let { (positions, millis) ->
                viewModelScope.launch {
                    paceStore.record(PaceKind.TEXT, positions, millis)
                    _millisPerPosition.value = paceStore.millisPerUnit(PaceKind.TEXT)
                }
            }
        }
        _uiState.update {
            it.copy(
                progression = locator.locations.totalProgression ?: it.progression,
                chapterTitle = locator.title ?: it.chapterTitle,
            )
        }
        locatorFlow.value = locator
    }

    fun seek(progression: Double, go: (Locator) -> Unit) {
        viewModelScope.launch {
            publication?.locateProgression(progression.coerceIn(0.0, 1.0))?.let(go)
        }
    }

    private fun ticksFor(locator: Locator): Long =
        ((locator.locations.totalProgression ?: 0.0).coerceIn(0.0, 1.0) * TICKS_FULL).roundToLong()

    private fun fail(error: ReaderError) {
        _uiState.update { it.copy(load = EpubLoadState.Failed(error)) }
    }

    override fun onCleared() {
        val last = locatorFlow.value
        val opened = publication
        publication = null
        if (_uiState.value.load is EpubLoadState.Ready) {
            if (last != null) {
                val finished = (last.locations.totalProgression ?: 0.0) >= EPUB_FINISHED_PROGRESSION
                progressReporter.stop(ticksFor(last), finished = finished)
            } else {
                progressReporter.stop()
            }
        }
        applicationScope.launch {
            last?.let { saveLocator(it) }
            opened?.close()
        }
        super.onCleared()
    }

    companion object {
        const val EXTRA_ITEM_ID = "itemId"
        private const val PREFERENCES_KEY = "reader_epub_preferences"
        private const val LOCATOR_FILE = "locator.json"
        private const val PROGRESS_DEBOUNCE_MS = 2_000L
        private const val TICKS_FULL = 10_000_000L
        private const val LOCATOR_MATCH_TOLERANCE = 0.005
        private const val SEGMENT_EDGE = 0.002f
        private const val DEFAULT_POSITION_SPAN = 0.002
        private const val SNIPPET_CHARS = 60
        private const val MAX_PACE_STEP_POSITIONS = 4.0
    }
}
