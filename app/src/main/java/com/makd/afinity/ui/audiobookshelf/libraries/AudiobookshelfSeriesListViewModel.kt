package com.makd.afinity.ui.audiobookshelf.libraries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.audiobookshelf.AudiobookshelfSeries
import com.makd.afinity.data.models.audiobookshelf.MediaProgress
import com.makd.afinity.data.repository.AudiobookshelfConfig
import com.makd.afinity.data.repository.AudiobookshelfRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.websocket.AbsSocketEvent
import com.makd.afinity.data.websocket.AudiobookshelfSocketManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

private const val SERIES_SORT_KEY = "series"

@HiltViewModel
class AudiobookshelfSeriesListViewModel
@Inject
constructor(
    private val audiobookshelfRepository: AudiobookshelfRepository,
    private val socketManager: AudiobookshelfSocketManager,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    val currentConfig: StateFlow<AudiobookshelfConfig?> = audiobookshelfRepository.currentConfig

    private val _sort = MutableStateFlow(AbsSortState(AbsSeriesSort.NAME))
    val sort: StateFlow<AbsSortState<AbsSeriesSort>> = _sort.asStateFlow()

    private val _filters = MutableStateFlow(AbsFilters())
    val filters: StateFlow<AbsFilters> = _filters.asStateFlow()

    private val ignorePrefix = MutableStateFlow(false)

    private val orderVersion = MutableStateFlow(0)

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
                            },
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filterOptions: StateFlow<AbsFilterOptions> =
        allSeries
            .map(::buildAbsSeriesFilterOptions)
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AbsFilterOptions())

    val displaySeries: StateFlow<AbsDisplay<AudiobookshelfSeries>> =
        combine(allSeries, _sort, _filters, ignorePrefix, orderVersion) {
                series,
                sort,
                filters,
                prefix,
                version ->
                AbsDisplay(
                    items = series.filteredSeriesForAbs(filters).sortedSeriesForAbs(sort, prefix),
                    orderVersion = version,
                )
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AbsDisplay())

    private var loadJob: Job? = null
    private var seriesRefreshJob: Job? = null

    init {
        loadSeries()

        viewModelScope.launch {
            try {
                _sort.value =
                    decodeAbsSort(
                        preferencesRepository.getAbsSort(SERIES_SORT_KEY),
                        AbsSeriesSort.NAME,
                    )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to load Audiobookshelf series sort")
            }
        }
        viewModelScope.launch {
            ignorePrefix.value = audiobookshelfRepository.getSortingIgnorePrefix()
        }

        viewModelScope.launch {
            socketManager.events.collect { event ->
                if (event == AbsSocketEvent.SeriesChanged) {
                    seriesRefreshJob?.cancel()
                    seriesRefreshJob = viewModelScope.launch {
                        delay(2.seconds)
                        loadSeries()
                    }
                }
            }
        }
    }

    fun setSort(sort: AbsSeriesSort, descending: Boolean) {
        val current = _sort.value
        if (current.sort == sort && current.descending == descending) return
        val state = AbsSortState(sort, descending, randomSeed = Random.nextLong())
        _sort.value = state
        orderVersion.update { it + 1 }
        viewModelScope.launch {
            try {
                preferencesRepository.setAbsSort(SERIES_SORT_KEY, state.encode())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to save Audiobookshelf series sort")
            }
        }
    }

    fun setFilters(filters: AbsFilters) {
        if (_filters.value == filters) return
        _filters.value = filters
        orderVersion.update { it + 1 }
    }

    fun clearFilters() {
        setFilters(AbsFilters())
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
                                                (existing.books + series.books).distinctBy {
                                                    it.id
                                                },
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
