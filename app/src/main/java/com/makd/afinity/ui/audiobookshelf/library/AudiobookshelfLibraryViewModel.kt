package com.makd.afinity.ui.audiobookshelf.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.audiobookshelf.Library
import com.makd.afinity.data.models.audiobookshelf.LibraryItem
import com.makd.afinity.data.repository.AudiobookshelfConfig
import com.makd.afinity.data.repository.AudiobookshelfRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AudiobookshelfLibraryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val audiobookshelfRepository: AudiobookshelfRepository
) : ViewModel() {

    private val libraryId: String = savedStateHandle.get<String>("libraryId") ?: ""

    private val _uiState = MutableStateFlow(AudiobookshelfLibraryUiState())
    val uiState: StateFlow<AudiobookshelfLibraryUiState> = _uiState.asStateFlow()

    private val _library = MutableStateFlow<Library?>(null)
    val library: StateFlow<Library?> = _library.asStateFlow()

    val items: StateFlow<List<LibraryItem>> =
        audiobookshelfRepository.getLibraryItemsFlow(libraryId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentConfig: StateFlow<AudiobookshelfConfig?> = audiobookshelfRepository.currentConfig

    init {
        loadLibrary()
        refreshItems()
    }

    private fun loadLibrary() {
        viewModelScope.launch {
            val result = audiobookshelfRepository.getLibrary(libraryId)
            result.fold(
                onSuccess = { library ->
                    _library.value = library
                },
                onFailure = { error ->
                    Timber.e(error, "Failed to load library")
                }
            )
        }
    }

    fun refreshItems() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)

            val result = audiobookshelfRepository.refreshLibraryItems(libraryId)

            result.fold(
                onSuccess = { items ->
                    _uiState.value = _uiState.value.copy(
                        isRefreshing = false,
                        totalItems = items.size
                    )
                    Timber.d("Refreshed ${items.size} items for library $libraryId")
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isRefreshing = false,
                        error = error.message
                    )
                    Timber.e(error, "Failed to refresh items")
                }
            )
        }
    }

    fun search(query: String) {
        if (query.isBlank()) {
            _uiState.value = _uiState.value.copy(searchQuery = "", searchResults = null)
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                searchQuery = query,
                isSearching = true
            )

            val result = audiobookshelfRepository.searchLibrary(libraryId, query)

            result.fold(
                onSuccess = { searchResponse ->
                    val searchItems = (searchResponse.book?.map { it.libraryItem } ?: emptyList()) +
                            (searchResponse.podcast?.map { it.libraryItem } ?: emptyList())

                    _uiState.value = _uiState.value.copy(
                        isSearching = false,
                        searchResults = searchItems
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isSearching = false,
                        error = error.message
                    )
                }
            )
        }
    }

    fun clearSearch() {
        _uiState.value = _uiState.value.copy(
            searchQuery = "",
            searchResults = null
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

data class AudiobookshelfLibraryUiState(
    val isRefreshing: Boolean = false,
    val isSearching: Boolean = false,
    val searchQuery: String = "",
    val searchResults: List<LibraryItem>? = null,
    val totalItems: Int = 0,
    val error: String? = null
)
