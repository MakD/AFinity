package com.makd.afinity.ui.audiobookshelf.libraries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.audiobookshelf.AudiobookshelfSeries
import com.makd.afinity.data.models.audiobookshelf.Library
import com.makd.afinity.data.models.audiobookshelf.LibraryItem
import com.makd.afinity.data.models.audiobookshelf.MediaProgress
import com.makd.afinity.data.repository.AudiobookshelfConfig
import com.makd.afinity.data.repository.AudiobookshelfRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject

data class PersonalizedSection(val id: String, val label: String, val items: List<LibraryItem>)

@HiltViewModel
class AudiobookshelfLibrariesViewModel
@Inject
constructor(private val audiobookshelfRepository: AudiobookshelfRepository) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(AudiobookshelfLibrariesUiState())
    val uiState: StateFlow<AudiobookshelfLibrariesUiState> = _uiState.asStateFlow()
    private var refreshJob: Job? = null

    val libraries: StateFlow<List<Library>> =
        audiobookshelfRepository
            .getLibrariesFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentConfig: StateFlow<AudiobookshelfConfig?> = audiobookshelfRepository.currentConfig

    val isAuthenticated = audiobookshelfRepository.isAuthenticated

    private val _personalizedSections = MutableStateFlow<List<PersonalizedSection>>(emptyList())
    private val _genreSections = MutableStateFlow<List<PersonalizedSection>>(emptyList())

    private val _libraryItems = MutableStateFlow<Map<String, List<LibraryItem>>>(emptyMap())

    private val _selectedLetter = MutableStateFlow<String?>(null)
    val selectedLetter: StateFlow<String?> = _selectedLetter.asStateFlow()

    private val _filteredLibraryItems = MutableStateFlow<Map<String, List<LibraryItem>>>(emptyMap())

    private val _allSeries = MutableStateFlow<List<AudiobookshelfSeries>>(emptyList())

    private val progressMap: StateFlow<Map<String, MediaProgress>> =
        audiobookshelfRepository
            .getAllProgressFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val personalizedSections: StateFlow<List<PersonalizedSection>> =
        combine(_personalizedSections, _genreSections, progressMap) { personalized, genres, progress
                ->
                (personalized + genres).map { section ->
                    val enriched = enrichItems(section.items, progress)
                    if (section.id == "continue-listening") {
                        section.copy(
                            items = enriched.sortedByDescending {
                                it.userMediaProgress?.lastUpdate ?: 0L
                            }
                        )
                    } else {
                        section.copy(items = enriched)
                    }
                }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val libraryItems: StateFlow<Map<String, List<LibraryItem>>> =
        combine(_libraryItems, progressMap) { items, progress ->
                items.mapValues { (_, list) -> enrichItems(list, progress) }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val filteredLibraryItems: StateFlow<Map<String, List<LibraryItem>>> =
        combine(_filteredLibraryItems, progressMap) { items, progress ->
                items.mapValues { (_, list) -> enrichItems(list, progress) }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val allSeries: StateFlow<List<AudiobookshelfSeries>> =
        combine(_allSeries, progressMap) { series, progress ->
                series.map { s -> s.copy(books = enrichItems(s.books, progress)) }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            audiobookshelfRepository.currentSessionId.collect { sessionId ->
                _libraryItems.value = emptyMap()
                _filteredLibraryItems.value = emptyMap()
                _personalizedSections.value = emptyList()
                _genreSections.value = emptyList()
                _allSeries.value = emptyList()
                _selectedLetter.value = null

                if (sessionId != null && audiobookshelfRepository.isAuthenticated.value) {
                    refreshLibraries()
                }
            }
        }

        viewModelScope.launch {
            var wasAuthenticated = false
            audiobookshelfRepository.isAuthenticated.collect { authenticated ->
                if (!wasAuthenticated && authenticated) {
                    refreshLibraries()
                }
                wasAuthenticated = authenticated
            }
        }
    }

    fun refreshLibraries() {
        if (refreshJob?.isActive == true) return

        refreshJob =
            viewModelScope.launch {
                _uiState.update { it.copy(isRefreshing = true, error = null) }

                val result = audiobookshelfRepository.refreshLibraries()
                val libraries = result.getOrNull()

                if (libraries != null && libraries.isNotEmpty()) {
                    audiobookshelfRepository.refreshProgress()
                    libraries.forEach { loadLibraryItems(it.id) }
                    val (personalized, series, genres) =
                        coroutineScope {
                            val personalizedDataResponse =
                                async(Dispatchers.Default) { fetchPersonalizedData(libraries) }
                            val seriesDataResponse =
                                async(Dispatchers.Default) { fetchAllSeries(libraries) }
                            val genreDataResponse =
                                async(Dispatchers.Default) { fetchGenreSections(libraries) }
                            Triple(
                                personalizedDataResponse.await(),
                                seriesDataResponse.await(),
                                genreDataResponse.await(),
                            )
                        }
                    _personalizedSections.value = personalized
                    _allSeries.value = series
                    _genreSections.value = genres
                    val expectedSections = personalized.size + genres.size
                    personalizedSections.first { it.size == expectedSections }
                    allSeries.first { it.size == series.size }
                    _uiState.update { it.copy(isRefreshing = false) }
                } else if (result.isFailure) {
                    _uiState.update {
                        it.copy(error = result.exceptionOrNull()?.message, isRefreshing = false)
                    }
                }
            }
    }

    private suspend fun fetchPersonalizedData(
        libraryList: List<Library>
    ): List<PersonalizedSection> {
        val allSections = mutableMapOf<String, PersonalizedSection>()
        val results = coroutineScope {
            libraryList
                .map { library ->
                    async { library.id to audiobookshelfRepository.getPersonalized(library.id) }
                }
                .awaitAll()
        }

        for ((libraryId, result) in results) {
            result.fold(
                onSuccess = { views ->
                    for (view in views) {
                        if (view.type == "authors") continue

                        val items =
                            view.entities.mapNotNull { element ->
                                try {
                                    json.decodeFromJsonElement(LibraryItem.serializer(), element)
                                } catch (e: Exception) {
                                    Timber.d(
                                        "Skipping non-LibraryItem entity in section ${view.id}"
                                    )
                                    null
                                }
                            }

                        if (items.isEmpty()) continue

                        val existing = allSections[view.id]
                        if (existing != null) {
                            val mergedItems = (existing.items + items).distinctBy { it.id }
                            allSections[view.id] = existing.copy(items = mergedItems)
                        } else {
                            allSections[view.id] =
                                PersonalizedSection(
                                    id = view.id,
                                    label = view.label,
                                    items = items.distinctBy { it.id },
                                )
                        }
                    }
                },
                onFailure = { error ->
                    Timber.e(error, "Failed to load personalized data for library $libraryId")
                },
            )
        }

        return allSections.values.toList()
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

    private suspend fun fetchGenreSections(libraryList: List<Library>): List<PersonalizedSection> =
        coroutineScope {
            try {
                val libraryIds = libraryList.map { it.id }
                val genresResult = audiobookshelfRepository.getGenres(libraryIds)
                val allGenres = genresResult.getOrNull() ?: emptyList()

                if (allGenres.isEmpty()) return@coroutineScope emptyList()

                val selectedGenres = allGenres.shuffled().take(15)

                val sections =
                    selectedGenres
                        .map { genre ->
                            async {
                                try {
                                    val items =
                                        libraryList
                                            .map { library ->
                                                async {
                                                    audiobookshelfRepository
                                                        .getGenreItemsLimited(library.id, genre, 15)
                                                        .getOrNull() ?: emptyList()
                                                }
                                            }
                                            .awaitAll()
                                            .flatten()
                                            .distinctBy { it.id }
                                            .shuffled()
                                            .take(15)

                                    if (items.isNotEmpty()) {
                                        PersonalizedSection(
                                            id = "genre_$genre",
                                            label = genre,
                                            items = items,
                                        )
                                    } else null
                                } catch (e: Exception) {
                                    Timber.e(e, "Failed to load items for genre '$genre'")
                                    null
                                }
                            }
                        }
                        .awaitAll()
                        .filterNotNull()

                return@coroutineScope sections
            } catch (e: Exception) {
                Timber.e(e, "Failed to load genre sections")
                return@coroutineScope emptyList()
            }
        }

    fun loadLibraryItems(libraryId: String) {
        if (_libraryItems.value.containsKey(libraryId)) return
        viewModelScope.launch(Dispatchers.Default) {
            val result = audiobookshelfRepository.refreshLibraryItems(libraryId)
            result.fold(
                onSuccess = { items ->
                    _libraryItems.update { currentMap -> currentMap + (libraryId to items) }
                },
                onFailure = { error ->
                    Timber.e(error, "Failed to load items for library $libraryId")
                },
            )
        }
    }

    fun onLetterSelected(letter: String) {
        val newLetter = if (_selectedLetter.value == letter) null else letter
        _selectedLetter.value = newLetter
        applyLetterFilter(newLetter)
    }

    private fun applyLetterFilter(letter: String?) {
        viewModelScope.launch(Dispatchers.Default) {
            val allItems = _libraryItems.value
            val filtered =
                if (letter == null) {
                    allItems
                } else {
                    allItems.mapValues { (_, items) ->
                        items.filter { item ->
                            val firstChar =
                                item.media.metadata.title?.firstOrNull()?.uppercase() ?: ""
                            if (letter == "#") {
                                firstChar.isNotEmpty() && !firstChar[0].isLetter()
                            } else {
                                firstChar == letter
                            }
                        }
                    }
                }
            _filteredLibraryItems.value = filtered
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun enrichItems(
        items: List<LibraryItem>,
        progressMap: Map<String, MediaProgress>,
    ): List<LibraryItem> {
        if (progressMap.isEmpty()) return items
        return items.map { item ->
            if (item.userMediaProgress != null) item
            else progressMap[item.id]?.let { item.copy(userMediaProgress = it) } ?: item
        }
    }
}

data class AudiobookshelfLibrariesUiState(
    val isRefreshing: Boolean = true,
    val error: String? = null,
)
