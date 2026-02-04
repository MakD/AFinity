package com.makd.afinity.ui.audiobookshelf.libraries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.audiobookshelf.AudiobookshelfSeries
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
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject

data class PersonalizedSection(
    val id: String,
    val label: String,
    val items: List<LibraryItem>
)

@HiltViewModel
class AudiobookshelfLibrariesViewModel @Inject constructor(
    private val audiobookshelfRepository: AudiobookshelfRepository
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(AudiobookshelfLibrariesUiState())
    val uiState: StateFlow<AudiobookshelfLibrariesUiState> = _uiState.asStateFlow()

    val libraries: StateFlow<List<Library>> = audiobookshelfRepository.getLibrariesFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentConfig: StateFlow<AudiobookshelfConfig?> = audiobookshelfRepository.currentConfig

    val isAuthenticated = audiobookshelfRepository.isAuthenticated

    private val _personalizedSections = MutableStateFlow<List<PersonalizedSection>>(emptyList())
    val personalizedSections: StateFlow<List<PersonalizedSection>> =
        _personalizedSections.asStateFlow()

    private val _libraryItems = MutableStateFlow<Map<String, List<LibraryItem>>>(emptyMap())
    val libraryItems: StateFlow<Map<String, List<LibraryItem>>> = _libraryItems.asStateFlow()

    private val _allSeries = MutableStateFlow<List<AudiobookshelfSeries>>(emptyList())
    val allSeries: StateFlow<List<AudiobookshelfSeries>> = _allSeries.asStateFlow()

    init {
        refreshLibraries()
    }

    fun refreshLibraries() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)

            val result = audiobookshelfRepository.refreshLibraries()

            result.fold(
                onSuccess = { libraries ->
                    _uiState.value = _uiState.value.copy(isRefreshing = false)
                    Timber.d("Refreshed ${libraries.size} libraries")

                    audiobookshelfRepository.refreshProgress()
                    loadPersonalizedData(libraries)
                    loadAllSeries(libraries)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isRefreshing = false,
                        error = error.message
                    )
                    Timber.e(error, "Failed to refresh libraries")
                }
            )
        }
    }

    private fun loadPersonalizedData(libraryList: List<Library>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingPersonalized = true)

            val allSections = mutableMapOf<String, PersonalizedSection>()

            for (library in libraryList) {
                try {
                    val result = audiobookshelfRepository.getPersonalized(library.id)
                    result.fold(
                        onSuccess = { views ->
                            for (view in views) {
                                if (view.type == "authors") continue

                                val items = view.entities.mapNotNull { element ->
                                    try {
                                        json.decodeFromJsonElement(
                                            LibraryItem.serializer(),
                                            element
                                        )
                                    } catch (e: Exception) {
                                        Timber.d("Skipping non-LibraryItem entity in section ${view.id}")
                                        null
                                    }
                                }

                                if (items.isEmpty()) continue

                                val existing = allSections[view.id]
                                if (existing != null) {
                                    val mergedItems = (existing.items + items)
                                        .distinctBy { it.id }
                                    allSections[view.id] = existing.copy(items = mergedItems)
                                } else {
                                    allSections[view.id] = PersonalizedSection(
                                        id = view.id,
                                        label = view.label,
                                        items = items
                                    )
                                }
                            }
                        },
                        onFailure = { error ->
                            Timber.e(
                                error,
                                "Failed to load personalized data for library ${library.id}"
                            )
                        }
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Error loading personalized data for library ${library.id}")
                }
            }

            _personalizedSections.value = allSections.values.toList()
            _uiState.value = _uiState.value.copy(isLoadingPersonalized = false)
        }
    }

    private fun loadAllSeries(libraryList: List<Library>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingSeries = true)

            val allSeriesMap = mutableMapOf<String, AudiobookshelfSeries>()

            for (library in libraryList) {
                try {
                    val result = audiobookshelfRepository.getSeries(library.id)
                    result.fold(
                        onSuccess = { seriesList ->
                            for (series in seriesList) {
                                val existing = allSeriesMap[series.id]
                                if (existing != null) {
                                    val mergedBooks = (existing.books + series.books)
                                        .distinctBy { it.id }
                                    allSeriesMap[series.id] = existing.copy(books = mergedBooks)
                                } else {
                                    allSeriesMap[series.id] = series
                                }
                            }
                        },
                        onFailure = { error ->
                            Timber.e(error, "Failed to load series for library ${library.id}")
                        }
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Error loading series for library ${library.id}")
                }
            }

            _allSeries.value = allSeriesMap.values.sortedBy { it.nameIgnorePrefix ?: it.name }
            _uiState.value = _uiState.value.copy(isLoadingSeries = false)
        }
    }

    fun loadLibraryItems(libraryId: String) {
        if (_libraryItems.value.containsKey(libraryId)) return

        viewModelScope.launch {
            val result = audiobookshelfRepository.refreshLibraryItems(libraryId)
            result.fold(
                onSuccess = { items ->
                    _libraryItems.value = _libraryItems.value + (libraryId to items)
                },
                onFailure = { error ->
                    Timber.e(error, "Failed to load items for library $libraryId")
                }
            )
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

data class AudiobookshelfLibrariesUiState(
    val isRefreshing: Boolean = false,
    val isLoadingPersonalized: Boolean = false,
    val isLoadingSeries: Boolean = false,
    val error: String? = null
)
