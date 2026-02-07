package com.makd.afinity.ui.audiobookshelf.item.series

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.audiobookshelf.LibraryItem
import com.makd.afinity.data.repository.AudiobookshelfRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class AudiobookshelfSeriesViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val audiobookshelfRepository: AudiobookshelfRepository,
) : ViewModel() {

    val seriesId: String = savedStateHandle.get<String>("seriesId") ?: ""
    val seriesName: String = savedStateHandle.get<String>("seriesName") ?: ""
    val libraryId: String = savedStateHandle.get<String>("libraryId") ?: ""

    private val _uiState = MutableStateFlow(AudiobookshelfSeriesUiState())
    val uiState: StateFlow<AudiobookshelfSeriesUiState> = _uiState.asStateFlow()

    val currentConfig = audiobookshelfRepository.currentConfig

    init {
        loadSeriesItems()
    }

    private fun loadSeriesItems() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            audiobookshelfRepository
                .getSeriesItems(libraryId, seriesId, limit = 100)
                .fold(
                    onSuccess = { result ->
                        _uiState.value =
                            _uiState.value.copy(
                                isLoading = false,
                                books = result.items,
                                totalBooks = result.totalBooks,
                            )
                        Timber.d("Loaded ${result.totalBooks} books for series: $seriesName")
                    },
                    onFailure = { error ->
                        _uiState.value =
                            _uiState.value.copy(isLoading = false, error = error.message)
                        Timber.e(error, "Failed to load series items")
                    },
                )
        }
    }
}

data class AudiobookshelfSeriesUiState(
    val isLoading: Boolean = false,
    val books: List<LibraryItem> = emptyList(),
    val totalBooks: Int = 0,
    val error: String? = null,
)
