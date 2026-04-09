package com.makd.afinity.ui.audiobookshelf.item

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.manager.OfflineModeManager
import com.makd.afinity.data.models.audiobookshelf.AbsDownloadInfo
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.util.NetworkConnectivityMonitor
import com.makd.afinity.data.models.audiobookshelf.BookChapter
import com.makd.afinity.data.models.audiobookshelf.LibraryItem
import com.makd.afinity.data.models.audiobookshelf.MediaProgress
import com.makd.afinity.data.models.audiobookshelf.PodcastEpisode
import com.makd.afinity.data.models.audiobookshelf.SeriesItem
import com.makd.afinity.data.repository.AudiobookshelfRepository
import com.makd.afinity.data.repository.audiobookshelf.AbsDownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
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
) : ViewModel() {

    val itemId: String = savedStateHandle.get<String>("itemId") ?: ""

    private val _uiState = MutableStateFlow(AudiobookshelfItemUiState())
    val uiState: StateFlow<AudiobookshelfItemUiState> = _uiState.asStateFlow()

    private val _item = MutableStateFlow<LibraryItem?>(null)
    val item: StateFlow<LibraryItem?> = _item.asStateFlow()

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
        offlineModeManager.isOffline
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val canDownload: StateFlow<Boolean> =
        preferencesRepository.getDownloadWifiOnlyFlow()
            .combine(networkMonitor.isOnWifiFlow) { wifiOnly, onWifi -> !wifiOnly || onWifi }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val downloadInfo: StateFlow<AbsDownloadInfo?> =
        absDownloadRepository.getActiveDownloadsFlow()
            .combine(absDownloadRepository.getCompletedDownloadsFlow()) { active, completed ->
                (active + completed).find { it.libraryItemId == itemId && it.episodeId == null }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val episodeDownloadMap: StateFlow<Map<String, AbsDownloadInfo>> =
        absDownloadRepository.getActiveDownloadsFlow()
            .combine(absDownloadRepository.getCompletedDownloadsFlow()) { active, completed ->
                (active + completed)
                    .filter { it.libraryItemId == itemId && it.episodeId != null }
                    .associateBy { it.episodeId!! }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun startDownload(episodeId: String? = null) {
        viewModelScope.launch {
            absDownloadRepository.startDownload(itemId, episodeId)
                .onFailure { Timber.e(it, "Failed to start download") }
        }
    }

    fun cancelDownload(episodeId: String? = null) {
        viewModelScope.launch {
            val info = if (episodeId == null) downloadInfo.value
            else episodeDownloadMap.value[episodeId]
            info?.let {
                absDownloadRepository.cancelDownload(it.id)
                    .onFailure { e -> Timber.e(e, "Failed to cancel download") }
            }
        }
    }

    fun deleteDownload(episodeId: String? = null) {
        viewModelScope.launch {
            val info = if (episodeId == null) downloadInfo.value
            else episodeDownloadMap.value[episodeId]
            info?.let {
                absDownloadRepository.deleteDownload(it.id)
                    .onFailure { e -> Timber.e(e, "Failed to delete download") }
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
                        audiobookshelfRepository.refreshProgress()
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

    private fun loadSeriesDetails(libraryId: String, seriesItems: List<SeriesItem>) {
        viewModelScope.launch {
            val loadedSeries = mutableListOf<SeriesDisplayData>()

            for (seriesItem in seriesItems) {
                audiobookshelfRepository
                    .getSeriesItems(libraryId, seriesItem.id, limit = 4)
                    .fold(
                        onSuccess = { result ->
                            loadedSeries.add(
                                SeriesDisplayData(
                                    id = seriesItem.id,
                                    name = seriesItem.name,
                                    totalBooks = result.totalBooks,
                                    bookItems = result.items,
                                )
                            )
                        },
                        onFailure = { e ->
                            Timber.w(e, "Failed to load series items: ${seriesItem.name}")
                        },
                    )
            }

            _uiState.value = _uiState.value.copy(seriesDetails = loadedSeries)
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
