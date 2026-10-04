package com.makd.afinity.ui.audiobookshelf.item

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.manager.OfflineModeManager
import com.makd.afinity.data.models.audiobookshelf.AbsDownloadInfo
import com.makd.afinity.data.models.audiobookshelf.AbsDownloadStatus
import com.makd.afinity.data.models.audiobookshelf.AudibleRating
import com.makd.afinity.data.models.audiobookshelf.Author
import com.makd.afinity.data.models.audiobookshelf.BookChapter
import com.makd.afinity.data.models.audiobookshelf.EpisodeSort
import com.makd.afinity.data.models.audiobookshelf.LibraryItem
import com.makd.afinity.data.models.audiobookshelf.ListeningSession
import com.makd.afinity.data.models.audiobookshelf.MediaProgress
import com.makd.afinity.data.models.audiobookshelf.PodcastEpisode
import com.makd.afinity.data.models.audiobookshelf.PodcastEpisodeOrder
import com.makd.afinity.data.models.audiobookshelf.PodcastUpNext
import com.makd.afinity.data.models.audiobookshelf.SeriesItem
import com.makd.afinity.data.models.common.DetailLayout
import com.makd.afinity.data.repository.AbsItemFilter
import com.makd.afinity.data.repository.AudiobookshelfRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.audiobookshelf.AbsDownloadRepository
import com.makd.afinity.data.websocket.AudiobookshelfSocketManager
import com.makd.afinity.data.websocket.WebSocketState
import com.makd.afinity.player.audiobookshelf.AudiobookshelfPlaybackManager
import com.makd.afinity.util.NetworkConnectivityMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class AudiobookshelfItemViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val audiobookshelfRepository: AudiobookshelfRepository,
    private val absDownloadRepository: AbsDownloadRepository,
    private val offlineModeManager: OfflineModeManager,
    private val preferencesRepository: PreferencesRepository,
    private val networkMonitor: NetworkConnectivityMonitor,
    private val playbackManager: AudiobookshelfPlaybackManager,
    private val socketManager: AudiobookshelfSocketManager,
) : ViewModel() {

    val itemId: String = savedStateHandle.get<String>("itemId") ?: ""

    private val _uiState = MutableStateFlow(AudiobookshelfItemUiState())
    val uiState: StateFlow<AudiobookshelfItemUiState> = _uiState.asStateFlow()

    private val _item = MutableStateFlow<LibraryItem?>(null)
    val item: StateFlow<LibraryItem?> = _item.asStateFlow()

    private val _audibleRating = MutableStateFlow<AudibleRating?>(null)
    val audibleRating: StateFlow<AudibleRating?> = _audibleRating.asStateFlow()

    private val _authorDetails = MutableStateFlow<AuthorDetails?>(null)
    val authorDetails: StateFlow<AuthorDetails?> = _authorDetails.asStateFlow()

    private val _listening = MutableStateFlow<ListeningSummary?>(null)
    val listening: StateFlow<ListeningSummary?> = _listening.asStateFlow()

    private val _recommendationRows = MutableStateFlow<List<RecommendationRow>>(emptyList())
    val recommendationRows: StateFlow<List<RecommendationRow>> =
        _recommendationRows
            .combine(audiobookshelfRepository.getAllProgressFlow()) { rows, progressMap ->
                if (rows.isEmpty()) rows
                else
                    rows.map { row ->
                        row.copy(
                            items =
                                row.items.map { entry ->
                                    progressMap[entry.id]?.let {
                                        entry.copy(userMediaProgress = it)
                                    } ?: entry
                                }
                        )
                    }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var authorRequested = false
    private var listeningRequested = false
    private var recommendationsRequested = false

    val progress: StateFlow<MediaProgress?> =
        audiobookshelfRepository
            .getProgressForItemFlow(itemId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val episodeProgressMap: StateFlow<Map<String, MediaProgress>> =
        audiobookshelfRepository
            .getEpisodeProgressMapFlow(itemId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val currentConfig = audiobookshelfRepository.currentConfig

    val isOffline: StateFlow<Boolean> =
        offlineModeManager.isOffline.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            false,
        )

    val canDownload: StateFlow<Boolean> =
        preferencesRepository
            .getDownloadWifiOnlyFlow()
            .combine(networkMonitor.isOnWifiFlow) { wifiOnly, onWifi -> !wifiOnly || onWifi }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val downloadInfo: StateFlow<AbsDownloadInfo?> =
        absDownloadRepository
            .getActiveDownloadsFlow()
            .combine(absDownloadRepository.getCompletedDownloadsFlow()) { active, completed ->
                (active + completed).find { it.libraryItemId == itemId && it.episodeId == null }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val episodeDownloadMap: StateFlow<Map<String, AbsDownloadInfo>> =
        absDownloadRepository
            .getActiveDownloadsFlow()
            .combine(absDownloadRepository.getCompletedDownloadsFlow()) { active, completed ->
                (active + completed)
                    .filter { it.libraryItemId == itemId && it.episodeId != null }
                    .associateBy { it.episodeId!! }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val detailLayout: StateFlow<DetailLayout?> =
        preferencesRepository
            .getDetailLayoutFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _episodeSort = MutableStateFlow(EpisodeSort.Default)
    val episodeSort: StateFlow<EpisodeSort> = _episodeSort.asStateFlow()

    private val availableEpisodes: StateFlow<List<PodcastEpisode>> =
        combine(
                _uiState.map { it.episodes }.distinctUntilChanged(),
                isOffline,
                episodeDownloadMap,
            ) { episodes, offline, downloads ->
                if (offline) {
                    episodes.filter { downloads[it.id]?.status == AbsDownloadStatus.COMPLETED }
                } else {
                    episodes
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sortedEpisodes: StateFlow<List<PodcastEpisode>> =
        combine(availableEpisodes, _episodeSort) { episodes, sort ->
                PodcastEpisodeOrder.sorted(episodes, sort)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val podcastUpNext: StateFlow<PodcastUpNext> =
        combine(availableEpisodes, episodeProgressMap, _episodeSort, _item) {
                episodes,
                progressMap,
                sort,
                current ->
                PodcastEpisodeOrder.upNext(
                    episodes = episodes,
                    progress = progressMap,
                    sort = sort,
                    isSerial = current.isSerialPodcast(),
                )
            }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                PodcastUpNext(null, null, null, null),
            )

    fun setEpisodeSort(sort: EpisodeSort) {
        _episodeSort.value = sort
        viewModelScope.launch {
            preferencesRepository.setStringPreference(episodeSortPreferenceKey(), sort.param)
        }
    }

    private fun loadEpisodeSort() {
        viewModelScope.launch {
            EpisodeSort.parse(preferencesRepository.getStringPreference(episodeSortPreferenceKey()))
                ?.let { _episodeSort.value = it }
        }
    }

    private fun episodeSortPreferenceKey() = "abs_episode_sort_$itemId"

    fun startDownload(episodeId: String? = null) {
        viewModelScope.launch {
            absDownloadRepository.startDownload(itemId, episodeId).onFailure {
                Timber.e(it, "Failed to start download")
            }
        }
    }

    fun cancelDownload(episodeId: String? = null) {
        viewModelScope.launch {
            val info =
                if (episodeId == null) downloadInfo.value else episodeDownloadMap.value[episodeId]
            info?.let {
                absDownloadRepository.cancelDownload(it.id).onFailure { e ->
                    Timber.e(e, "Failed to cancel download")
                }
            }
        }
    }

    fun deleteDownload(episodeId: String? = null) {
        viewModelScope.launch {
            val info =
                if (episodeId == null) downloadInfo.value else episodeDownloadMap.value[episodeId]
            info?.let {
                absDownloadRepository.deleteDownload(it.id).onFailure { e ->
                    Timber.e(e, "Failed to delete download")
                }
            }
        }
    }

    init {
        loadEpisodeSort()
        loadItem()
    }

    private fun loadItem() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val result = audiobookshelfRepository.getItemDetails(itemId)

            result.fold(
                onSuccess = { item ->
                    _item.value = item
                    _uiState.value =
                        _uiState.value.copy(
                            isLoading = false,
                            chapters = item.media.chapters ?: emptyList(),
                            episodes = item.media.episodes ?: emptyList(),
                        )
                    Timber.d("Loaded item: ${item.media.metadata.title}")

                    if (item.mediaType.lowercase() == "podcast") {
                        if (socketManager.connectionState.value != WebSocketState.CONNECTED) {
                            audiobookshelfRepository.refreshProgress()
                        }
                    } else {
                        loadAudibleRating(item)
                    }

                    item.media.metadata.series?.let { seriesList ->
                        if (seriesList.isNotEmpty()) {
                            loadSeriesDetails(item.libraryId, seriesList)
                        }
                    }
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = error.message)
                    Timber.e(error, "Failed to load item")
                },
            )
        }
    }

    private fun loadAudibleRating(item: LibraryItem) {
        viewModelScope.launch {
            audiobookshelfRepository
                .getAudibleRating(
                    itemId = item.id,
                    asin = item.media.metadata.asin,
                    title = item.media.metadata.title ?: return@launch,
                    authorName = item.media.metadata.authorName,
                )
                .onSuccess { rating -> _audibleRating.value = rating }
                .onFailure { e -> Timber.w(e, "Audible rating fetch failed") }
        }
    }

    private fun loadSeriesDetails(libraryId: String, seriesItems: List<SeriesItem>) {
        viewModelScope.launch {
            val loadedSeries = coroutineScope {
                seriesItems
                    .map { seriesItem ->
                        async {
                            audiobookshelfRepository
                                .getSeriesItems(libraryId, seriesItem.id, limit = 4)
                                .fold(
                                    onSuccess = { result ->
                                        SeriesDisplayData(
                                            id = seriesItem.id,
                                            name = seriesItem.name,
                                            totalBooks = result.totalBooks,
                                            bookItems = result.items,
                                        )
                                    },
                                    onFailure = { e ->
                                        Timber.w(
                                            e,
                                            "Failed to load series items: ${seriesItem.name}",
                                        )
                                        null
                                    },
                                )
                        }
                    }
                    .awaitAll()
                    .filterNotNull()
            }

            _uiState.value = _uiState.value.copy(seriesDetails = loadedSeries)
        }
    }

    fun ensureAuthor() {
        if (authorRequested) return
        val current = _item.value ?: return
        val author = current.media.metadata.authors?.firstOrNull() ?: return
        authorRequested = true
        viewModelScope.launch {
            val (authorResult, countResult) =
                coroutineScope {
                    val details = async { audiobookshelfRepository.getAuthor(author.id) }
                    val count = async {
                        audiobookshelfRepository.getFilteredLibraryItems(
                            libraryId = current.libraryId,
                            filter = AbsItemFilter.ByAuthor(author.id),
                            limit = 1,
                        )
                    }
                    details.await() to count.await()
                }
            authorResult
                .onSuccess { loaded ->
                    _authorDetails.value =
                        AuthorDetails(author = loaded, titleCount = countResult.getOrNull()?.total)
                }
                .onFailure { authorRequested = false }
        }
    }

    fun ensureListening() {
        if (listeningRequested) return
        val current = _item.value ?: return
        listeningRequested = true
        viewModelScope.launch {
            audiobookshelfRepository
                .getItemListeningSessions(current.id, itemsPerPage = LISTENING_PAGE_SIZE)
                .onSuccess { response ->
                    val sessions = response.sessions
                    if (sessions.isEmpty()) return@onSuccess
                    _listening.value =
                        ListeningSummary(
                            totalListened = sessions.sumOf { it.timeListening },
                            sessionCount = maxOf(response.total, sessions.size),
                            listeningDays = sessions.mapNotNull { it.date }.distinct().size,
                            firstStartedAt = sessions.mapNotNull { it.startedAt }.minOrNull(),
                            lastPlayedAt = sessions.mapNotNull { it.updatedAt }.maxOrNull(),
                            recent =
                                sessions
                                    .sortedByDescending { it.updatedAt ?: 0L }
                                    .take(RECENT_SESSIONS),
                        )
                }
                .onFailure { listeningRequested = false }
        }
    }

    fun ensureRecommendations() {
        if (recommendationsRequested) return
        val current = _item.value ?: return
        recommendationsRequested = true
        val metadata = current.media.metadata
        val author = metadata.authors?.firstOrNull()
        val narrator =
            metadata.narrators
                ?.takeIf { it.size in 1..MAX_NARRATORS_FOR_ROW }
                ?.firstOrNull()
                ?.takeIf { name -> !name.equals(author?.name, ignoreCase = true) }
        val genre = metadata.genres?.firstOrNull()
        val libraryId = current.libraryId

        viewModelScope.launch {
            val (byAuthor, byNarrator, byGenre) =
                coroutineScope {
                    val authorRow = async {
                        author?.let {
                            audiobookshelfRepository.getFilteredLibraryItems(
                                libraryId,
                                AbsItemFilter.ByAuthor(it.id),
                                ROW_LIMIT,
                                collapseSeries = true,
                            )
                        }
                    }
                    val narratorRow = async {
                        narrator?.let {
                            audiobookshelfRepository.getFilteredLibraryItems(
                                libraryId,
                                AbsItemFilter.ByNarrator(it),
                                ROW_LIMIT,
                            )
                        }
                    }
                    val genreRow = async {
                        genre?.let {
                            audiobookshelfRepository.getFilteredLibraryItems(
                                libraryId,
                                AbsItemFilter.ByGenre(it),
                                ROW_LIMIT,
                            )
                        }
                    }
                    Triple(authorRow.await(), narratorRow.await(), genreRow.await())
                }

            val seenIds = mutableSetOf(current.id)
            val seenSeriesIds = metadata.series.orEmpty().mapTo(mutableSetOf()) { it.id }
            val seenSeriesNames = metadata.series.orEmpty().mapTo(mutableSetOf()) { it.name }

            fun List<LibraryItem>.unseen(): List<LibraryItem> {
                val kept = filter { candidate ->
                    val collapsed = candidate.collapsedSeries
                    if (collapsed != null) {
                        collapsed.id !in seenSeriesIds
                    } else {
                        val seriesName = candidate.media.metadata.seriesName
                        candidate.id !in seenIds &&
                            seenSeriesNames.none { seriesName?.startsWith(it) == true }
                    }
                }
                kept.forEach { entry ->
                    seenIds += entry.id
                    entry.collapsedSeries?.let {
                        seenSeriesIds += it.id
                        seenSeriesNames += it.name
                    }
                }
                return kept
            }

            val candidates =
                listOfNotNull(
                    author?.let { Triple(RecommendationRow.Kind.AUTHOR, it.name, byAuthor) },
                    narrator?.let { Triple(RecommendationRow.Kind.NARRATOR, it, byNarrator) },
                    genre?.let { Triple(RecommendationRow.Kind.GENRE, it, byGenre) },
                )
            val rows = candidates.mapNotNull { (kind, subject, result) ->
                result
                    ?.getOrNull()
                    ?.items
                    ?.unseen()
                    ?.takeIf { it.size >= MIN_ROW_ITEMS }
                    ?.let { RecommendationRow(kind, subject, it) }
            }
            if (candidates.isNotEmpty() && candidates.all { it.third?.isFailure != false }) {
                recommendationsRequested = false
            }
            _recommendationRows.value = rows
        }
    }

    val nowPlayingEpisodeId: StateFlow<String?> =
        playbackManager.playbackState
            .map { state ->
                if (state.sessionId != null && state.itemId == itemId) state.episodeId else null
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isThisItemPlaying: StateFlow<Boolean> =
        playbackManager.playbackState
            .map { state ->
                state.sessionId != null && state.itemId == itemId && state.episodeId == null
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun toggleItemFinished() {
        val current = item.value ?: return
        if (isThisItemPlaying.value) return
        val finished = progress.value?.isFinished == true
        val duration = current.media.duration ?: progress.value?.duration ?: 0.0
        viewModelScope.launch {
            audiobookshelfRepository
                .updateProgress(
                    itemId = current.id,
                    episodeId = null,
                    currentTime = if (finished) 0.0 else duration,
                    duration = duration,
                    isFinished = !finished,
                )
                .onFailure { Timber.e(it, "Failed to toggle finished for ${current.id}") }
        }
    }

    fun toggleEpisodeFinished(episode: PodcastEpisode) {
        val current = item.value ?: return
        if (nowPlayingEpisodeId.value == episode.id) return
        val existing = episodeProgressMap.value[episode.id]
        val finished = existing?.isFinished == true
        val duration = episode.duration ?: existing?.duration ?: 0.0
        viewModelScope.launch {
            audiobookshelfRepository
                .updateProgress(
                    itemId = current.id,
                    episodeId = episode.id,
                    currentTime = if (finished) 0.0 else duration,
                    duration = duration,
                    isFinished = !finished,
                )
                .onFailure { Timber.e(it, "Failed to toggle finished for episode ${episode.id}") }
        }
    }

    fun refresh() {
        loadItem()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private companion object {
        const val ROW_LIMIT = 12
        const val MIN_ROW_ITEMS = 2
        const val MAX_NARRATORS_FOR_ROW = 2
        const val LISTENING_PAGE_SIZE = 50
        const val RECENT_SESSIONS = 5
    }
}

fun LibraryItem?.isSerialPodcast(): Boolean =
    this?.media?.metadata?.type.equals("serial", ignoreCase = true)

data class AuthorDetails(val author: Author, val titleCount: Int?)

data class ListeningSummary(
    val totalListened: Double,
    val sessionCount: Int,
    val listeningDays: Int,
    val firstStartedAt: Long?,
    val lastPlayedAt: Long?,
    val recent: List<ListeningSession>,
)

data class RecommendationRow(val kind: Kind, val subject: String, val items: List<LibraryItem>) {
    enum class Kind {
        AUTHOR,
        NARRATOR,
        GENRE,
    }
}

data class SeriesDisplayData(
    val id: String,
    val name: String,
    val totalBooks: Int,
    val bookItems: List<LibraryItem>,
)

data class AudiobookshelfItemUiState(
    val isLoading: Boolean = false,
    val chapters: List<BookChapter> = emptyList(),
    val episodes: List<PodcastEpisode> = emptyList(),
    val seriesDetails: List<SeriesDisplayData> = emptyList(),
    val error: String? = null,
)
