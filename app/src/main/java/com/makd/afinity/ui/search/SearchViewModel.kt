package com.makd.afinity.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.extensions.toAfinityItem
import com.makd.afinity.data.models.media.AfinityCollection
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.JellyfinRepository
import com.makd.afinity.data.repository.media.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val jellyfinRepository: JellyfinRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    fun loadLibraries() {
        viewModelScope.launch {
            try {
                val libraries = mediaRepository.getLibraries()
                _uiState.value = _uiState.value.copy(libraries = libraries)
                Timber.d("Loaded ${libraries.size} libraries")
            } catch (e: Exception) {
                Timber.e(e, "Failed to load libraries")
            }
        }
    }

    fun loadGenres() {
        viewModelScope.launch {
            try {
                val selectedLibraryId = _uiState.value.selectedLibrary?.id
                val genres = mediaRepository.getGenres(
                    parentId = selectedLibraryId,
                    limit = 100,
                    includeItemTypes = listOf("MOVIE", "SERIES", "BOXSET")
                )
                _uiState.value = _uiState.value.copy(genres = genres)
                Timber.d("Loaded ${genres.size} genres from API")
            } catch (e: Exception) {
                Timber.e(e, "Failed to load genres")
                _uiState.value = _uiState.value.copy(genres = emptyList())
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)

        if (query.length >= 2) {
            viewModelScope.launch {
                kotlinx.coroutines.delay(500)
                if (_uiState.value.searchQuery == query) {
                    performSearch()
                }
            }
        } else if (query.isEmpty()) {
            _uiState.value = _uiState.value.copy(searchResults = emptyList())
        }
    }

    fun selectLibrary(library: AfinityCollection?) {
        _uiState.value = _uiState.value.copy(selectedLibrary = library)

        loadGenres()

        if (_uiState.value.searchQuery.isNotEmpty()) {
            performSearch()
        }
    }

    fun performSearch() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isEmpty()) return

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isSearching = true)

                Timber.d("Performing search:")
                Timber.d("- Query: '$query'")
                Timber.d("- Selected library: ${_uiState.value.selectedLibrary?.name}")

                val results = mediaRepository.getItems(
                    parentId = _uiState.value.selectedLibrary?.id,
                    searchTerm = query,
                    includeItemTypes = listOf("MOVIE", "SERIES"),
                    limit = 50,
                    sortBy = SortBy.NAME,
                    sortDescending = false,
                    fields = FieldSets.SEARCH_RESULTS
                )

                val afinityItems = results.items?.mapNotNull { baseItemDto ->
                    try {
                        val item = baseItemDto.toAfinityItem(jellyfinRepository.getBaseUrl())
                        when (item) {
                            is AfinityMovie -> item
                            is AfinityShow -> item
                            else -> null
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to convert item: ${baseItemDto.name}")
                        null
                    }
                }?.let { items ->
                    items.sortedBy { item ->
                        val name = item.name.lowercase()
                        val queryLower = query.lowercase()
                        when {
                            name == queryLower -> 0
                            name.startsWith(queryLower) -> 1
                            name.contains(queryLower) -> 2
                            else -> 3
                        }
                    }
                } ?: emptyList()

                _uiState.value = _uiState.value.copy(
                    searchResults = afinityItems,
                    isSearching = false
                )

                Timber.d("Search completed: ${afinityItems.size} results for '$query'")

            } catch (e: Exception) {
                Timber.e(e, "Failed to perform search")
                _uiState.value = _uiState.value.copy(
                    searchResults = emptyList(),
                    isSearching = false
                )
            }
        }
    }

    fun clearSearch() {
        _uiState.value = _uiState.value.copy(
            searchQuery = "",
            searchResults = emptyList(),
            isSearching = false
        )
    }
}

data class SearchUiState(
    val searchQuery: String = "",
    val searchResults: List<AfinityItem> = emptyList(),
    val isSearching: Boolean = false,
    val libraries: List<AfinityCollection> = emptyList(),
    val selectedLibrary: AfinityCollection? = null,
    val genres: List<String> = emptyList()
)