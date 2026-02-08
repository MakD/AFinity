package com.makd.afinity.ui.audiobookshelf.item

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.audiobookshelf.BookChapter
import com.makd.afinity.data.models.audiobookshelf.LibraryItem
import com.makd.afinity.data.models.audiobookshelf.MediaProgress
import com.makd.afinity.data.models.audiobookshelf.PodcastEpisode
import com.makd.afinity.data.models.audiobookshelf.SeriesItem
import com.makd.afinity.data.repository.AudiobookshelfRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class AudiobookshelfItemViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val audiobookshelfRepository: AudiobookshelfRepository,
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
