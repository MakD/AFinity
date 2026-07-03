package com.makd.afinity.ui.audiobookshelf.item

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.manager.OfflineModeManager
import com.makd.afinity.data.models.audiobookshelf.AbsDownloadInfo
import com.makd.afinity.data.models.audiobookshelf.AudibleRating
import com.makd.afinity.data.models.audiobookshelf.BookChapter
import com.makd.afinity.data.models.audiobookshelf.LibraryItem
import com.makd.afinity.data.models.audiobookshelf.MediaProgress
import com.makd.afinity.data.models.audiobookshelf.PodcastEpisode
import com.makd.afinity.data.models.audiobookshelf.SeriesItem
import com.makd.afinity.data.repository.AudiobookshelfRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.audiobookshelf.AbsDownloadRepository
import com.makd.afinity.data.websocket.AudiobookshelfSocketManager
import com.makd.afinity.data.websocket.WebSocketState
import com.makd.afinity.player.audiobookshelf.AudiobookshelfPlaybackManager
import com.makd.afinity.util.NetworkConnectivityMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

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
