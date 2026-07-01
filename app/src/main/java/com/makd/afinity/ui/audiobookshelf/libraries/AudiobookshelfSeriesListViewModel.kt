package com.makd.afinity.ui.audiobookshelf.libraries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.audiobookshelf.AudiobookshelfSeries
import com.makd.afinity.data.models.audiobookshelf.Library
import com.makd.afinity.data.models.audiobookshelf.MediaProgress
import com.makd.afinity.data.repository.AudiobookshelfConfig
import com.makd.afinity.data.repository.AudiobookshelfRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AudiobookshelfSeriesListViewModel
@Inject
constructor(private val audiobookshelfRepository: AudiobookshelfRepository) : ViewModel() {

    val currentConfig: StateFlow<AudiobookshelfConfig?> = audiobookshelfRepository.currentConfig

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _rawSeries = MutableStateFlow<List<AudiobookshelfSeries>>(emptyList())

    private val progressMap: StateFlow<Map<String, MediaProgress>> =
        audiobookshelfRepository
            .getAllProgressFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val allSeries: StateFlow<List<AudiobookshelfSeries>> =
        combine(_rawSeries, progressMap) { series, progress ->
                series.map { s ->
                    s.copy(
                        books =
                            s.books.map { item ->
                                if (item.userMediaProgress != null) item
                                else progress[item.id]?.let { item.copy(userMediaProgress = it) }
                                        ?: item
                            }
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            val libraries =
                audiobookshelfRepository.getLibrariesFlow().first { it.isNotEmpty() }
            _rawSeries.value = fetchAllSeries(libraries)
            _isLoading.value = false
        }
    }

    private suspend fun fetchAllSeries(libraryList: List<Library>): List<AudiobookshelfSeries> {
        val allSeriesMap = mutableMapOf<String, AudiobookshelfSeries>()
        val results = coroutineScope {
            libraryList
                .map { library ->
                    async { library.id to audiobookshelfRepository.getSeries(library.id) }
                }
                .awaitAll()
        }

        for ((libraryId, result) in results) {
            result.fold(
                onSuccess = { seriesList ->
                    for (series in seriesList) {
                        val existing = allSeriesMap[series.id]
                        if (existing != null) {
                            val mergedBooks = (existing.books + series.books).distinctBy { it.id }
                            allSeriesMap[series.id] = existing.copy(books = mergedBooks)
                        } else {
                            allSeriesMap[series.id] =
                                series.copy(books = series.books.distinctBy { it.id })
                        }
                    }
                },
                onFailure = { error ->
                    Timber.e(error, "Failed to load series for library $libraryId")
                },
            )
        }

        return allSeriesMap.values
            .filter { it.books.isNotEmpty() }
            .sortedBy { it.nameIgnorePrefix ?: it.name }
    }
}