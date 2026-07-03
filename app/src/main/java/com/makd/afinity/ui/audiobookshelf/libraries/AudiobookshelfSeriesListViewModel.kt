package com.makd.afinity.ui.audiobookshelf.libraries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.audiobookshelf.AudiobookshelfSeries
import com.makd.afinity.data.models.audiobookshelf.MediaProgress
import com.makd.afinity.data.repository.AudiobookshelfConfig
import com.makd.afinity.data.repository.AudiobookshelfRepository
import com.makd.afinity.data.websocket.AbsSocketEvent
import com.makd.afinity.data.websocket.AudiobookshelfSocketManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

@HiltViewModel
class AudiobookshelfSeriesListViewModel
@Inject
constructor(
    private val audiobookshelfRepository: AudiobookshelfRepository,
    private val socketManager: AudiobookshelfSocketManager,
) : ViewModel() {

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
                                else
                                    progress[item.id]?.let { item.copy(userMediaProgress = it) }
                                        ?: item
                            }
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var loadJob: Job? = null
    private var seriesRefreshJob: Job? = null

    init {
        loadSeries()

        viewModelScope.launch {
            socketManager.events.collect { event ->
                if (event == AbsSocketEvent.SeriesChanged) {
                    seriesRefreshJob?.cancel()
                    seriesRefreshJob = viewModelScope.launch {
                        delay(2_000L)
                        loadSeries()
                    }
                }
            }
        }
    }

    private fun loadSeries() {
        if (loadJob?.isActive == true) return
        loadJob = viewModelScope.launch {
            val libraries = audiobookshelfRepository.getLibrariesFlow().first { it.isNotEmpty() }

            val seriesById = mutableMapOf<String, AudiobookshelfSeries>()
            val mergeMutex = Mutex()

            coroutineScope {
                libraries.forEach { library ->
                    launch {
                        audiobookshelfRepository.getSeriesPages(library.id).collect { page ->
                            val snapshot = mergeMutex.withLock {
                                for (series in page) {
                                    val existing = seriesById[series.id]
                                    seriesById[series.id] =
                                        existing?.copy(
                                            books =
                                                (existing.books + series.books).distinctBy { it.id }
                                        ) ?: series.copy(books = series.books.distinctBy { it.id })
                                }
                                seriesById.values
                                    .filter { it.books.isNotEmpty() }
                                    .sortedBy { it.nameIgnorePrefix ?: it.name }
                            }
                            _rawSeries.value = snapshot
                            _isLoading.value = false
                        }
                    }
                }
            }
            _isLoading.value = false
        }
    }
}
