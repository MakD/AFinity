package com.makd.afinity.ui.audiobookshelf.libraries

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.audiobookshelf.AudiobookshelfSeries
import com.makd.afinity.data.models.audiobookshelf.Library
import com.makd.afinity.data.models.audiobookshelf.LibraryItem
import com.makd.afinity.data.models.audiobookshelf.MediaProgress
import com.makd.afinity.data.models.audiobookshelf.PersonalizedView
import com.makd.afinity.data.models.audiobookshelf.mediaProgressKey
import com.makd.afinity.data.repository.AudiobookshelfConfig
import com.makd.afinity.data.repository.AudiobookshelfRepository
import com.makd.afinity.data.websocket.AbsSocketEvent
import com.makd.afinity.data.websocket.AudiobookshelfSocketManager
import com.makd.afinity.data.websocket.WebSocketState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject

data class PersonalizedSection(
    val id: String,
    val label: String,
    val items: List<LibraryItem>,
    val series: List<AudiobookshelfSeries> = emptyList(),
)

private data class LibraryViews(val views: List<PersonalizedView>, val isFull: Boolean)

@HiltViewModel
class AudiobookshelfLibrariesViewModel
@Inject
constructor(
    private val audiobookshelfRepository: AudiobookshelfRepository,
    private val socketManager: AudiobookshelfSocketManager,
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(AudiobookshelfLibrariesUiState())
    val uiState: StateFlow<AudiobookshelfLibrariesUiState> = _uiState.asStateFlow()
    private var refreshJob: Job? = null
    private var shelfRefreshJob: Job? = null
    private var fullRefreshJob: Job? = null

    val libraries: StateFlow<List<Library>> =
        audiobookshelfRepository
            .getLibrariesFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentConfig: StateFlow<AudiobookshelfConfig?> = audiobookshelfRepository.currentConfig

    val isAuthenticated = audiobookshelfRepository.isAuthenticated

    private val _personalizedSections = MutableStateFlow<List<PersonalizedSection>>(emptyList())
    private val _genreSections = MutableStateFlow<List<PersonalizedSection>>(emptyList())

    private val progressMap: StateFlow<Map<String, MediaProgress>> =
        audiobookshelfRepository
            .getAllProgressFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val episodeProgressMap: StateFlow<Map<String, MediaProgress>> =
        audiobookshelfRepository
            .getEpisodeProgressFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val personalizedSections: StateFlow<List<PersonalizedSection>> =
        combine(_personalizedSections, _genreSections, progressMap, episodeProgressMap) {
                personalized,
                genres,
                progress,
                episodeProgress ->
                (personalized + genres).map { section ->
                    val enriched = enrichItems(section.items, progress, episodeProgress)
                    if (section.id == "continue-listening") {
                        section.copy(
                            items =
                                enriched.sortedByDescending {
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

    init {
        viewModelScope.launch {
            audiobookshelfRepository.currentSessionId.collect { sessionId ->
                if (sessionId != cachedSessionId) {
                    _personalizedSections.value = emptyList()
                    _genreSections.value = emptyList()
                    cachedGenreSections = emptyList()
                    lastFullRefreshAt = 0L
                    cachedSessionId = sessionId
                }

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

        viewModelScope.launch {
            socketManager.events.collect { event ->
                when (event) {
                    AbsSocketEvent.ProgressChanged -> scheduleShelfRefresh()
                    AbsSocketEvent.ItemsChanged,
                    AbsSocketEvent.SeriesChanged -> scheduleFullRefresh()
                }
            }
        }
    }

    private fun scheduleShelfRefresh() {
        shelfRefreshJob?.cancel()
        shelfRefreshJob = viewModelScope.launch {
            delay(2_000L)
            val activeLibraries = libraries.value
            if (activeLibraries.isNotEmpty()) {
                refreshContinueListening(activeLibraries)
            }
        }
    }

    private fun scheduleFullRefresh() {
        fullRefreshJob?.cancel()
        fullRefreshJob = viewModelScope.launch {
            delay(2_000L)
            refreshLibraries(force = true)
        }
    }

    fun refreshLibraries(force: Boolean = false) {
        if (refreshJob?.isActive == true) return

        refreshJob = viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            val cachedLibraries =
                withTimeoutOrNull(200) {
                    libraries.first { it.isNotEmpty() }
                } ?: libraries.value

            val refreshLibrariesDeferred =
                async(Dispatchers.IO) {
                    audiobookshelfRepository.refreshLibraries()
                }

            val activeLibraries = cachedLibraries.ifEmpty {
                refreshLibrariesDeferred.await().getOrNull() ?: emptyList()
            }

            if (activeLibraries.isNotEmpty()) {

                launch(Dispatchers.IO) { audiobookshelfRepository.refreshProgress() }

                val cachedViews = audiobookshelfRepository.personalizedCache.value
                val hasCachedSections = activeLibraries.all { cachedViews.containsKey(it.id) }
                if (hasCachedSections) {
                    _personalizedSections.value =
                        withContext(Dispatchers.Default) {
                            buildSectionsFromViews(cachedViews)
                        }
                    _uiState.update { it.copy(isRefreshing = false) }
                }

                if (cachedGenreSections.isNotEmpty()) {
                    _genreSections.value = cachedGenreSections
                }

                val cooldownMs =
                    if (socketManager.connectionState.value == WebSocketState.CONNECTED) {
                        SOCKET_CONNECTED_COOLDOWN_MS
                    } else {
                        FULL_REFRESH_COOLDOWN_MS
                    }
                val withinCooldown =
                    !force && System.currentTimeMillis() - lastFullRefreshAt < cooldownMs
                if (withinCooldown && hasCachedSections) {
                    refreshContinueListening(activeLibraries)
                } else {
                    lastFullRefreshAt = System.currentTimeMillis()
                    fetchPersonalizedIncrementally(
                        libraryList = activeLibraries,
                        quickFirst = !hasCachedSections,
                    )
                }
                _uiState.update { it.copy(isRefreshing = false) }

                if (cachedGenreSections.isEmpty()) {
                    launch(Dispatchers.IO) {
                        val genres = fetchGenreSections(activeLibraries)
                        cachedGenreSections = genres
                        _genreSections.value = genres
                    }
                }
            } else {
                _uiState.update { it.copy(isRefreshing = false, error = "No libraries found") }
            }
        }
    }

    private suspend fun fetchPersonalizedIncrementally(
        libraryList: List<Library>,
        quickFirst: Boolean,
    ) = coroutineScope {
        val stateMutex = Mutex()
        val viewsByLibrary = mutableMapOf<String, LibraryViews>()

        suspend fun record(libraryId: String, views: List<PersonalizedView>, isFull: Boolean) {
            stateMutex.withLock {
                val existing = viewsByLibrary[libraryId]
                if (!isFull && existing?.isFull == true) return@withLock
                viewsByLibrary[libraryId] = LibraryViews(views, isFull)
                val ordered = linkedMapOf<String, List<PersonalizedView>>()
                for (library in libraryList) {
                    viewsByLibrary[library.id]?.let { ordered[library.id] = it.views }
                }
                val sections = buildSectionsFromViews(ordered)
                if (sections.isNotEmpty()) {
                    _personalizedSections.value = sections
                    _uiState.update { it.copy(isRefreshing = false) }
                }
            }
        }

        if (quickFirst) {
            libraryList.forEach { library ->
                launch(Dispatchers.IO) {
                    audiobookshelfRepository
                        .getPersonalized(
                            libraryId = library.id,
                            shelves = listOf(CONTINUE_LISTENING_SHELF),
                            limit = 10,
                        )
                        .onSuccess { views -> record(library.id, views, isFull = false) }
                }
            }
        }

        libraryList.forEach { library ->
            launch(Dispatchers.IO) {
                audiobookshelfRepository
                    .getPersonalized(library.id)
                    .onSuccess { views -> record(library.id, views, isFull = true) }
                    .onFailure { error ->
                        Timber.e(
                            error,
                            "Failed to load personalized data for library ${library.id}",
                        )
                    }
            }
        }
    }

    private suspend fun refreshContinueListening(libraryList: List<Library>) = coroutineScope {
        val results =
            libraryList
                .map { library ->
                    async(Dispatchers.IO) {
                        audiobookshelfRepository
                            .getPersonalized(
                                libraryId = library.id,
                                shelves = listOf(CONTINUE_LISTENING_SHELF),
                                limit = 10,
                            )
                            .getOrNull()
                            ?.let { library.id to it }
                    }
                }
                .awaitAll()
                .filterNotNull()
                .toMap()
        if (results.isEmpty()) return@coroutineScope

        val freshSections = buildSectionsFromViews(results)
        if (freshSections.isEmpty()) return@coroutineScope
        val freshById = freshSections.associateBy { it.id }

        _personalizedSections.update { current ->
            if (current.isEmpty()) {
                freshSections
            } else {
                val existingIds = current.map { it.id }.toSet()
                val missing = freshSections.filterNot { it.id in existingIds }
                missing + current.map { section -> freshById[section.id] ?: section }
            }
        }
    }

    private fun buildSectionsFromViews(
        viewsByLibrary: Map<String, List<PersonalizedView>>
    ): List<PersonalizedSection> {
        val allSections = mutableMapOf<String, PersonalizedSection>()
        for ((_, views) in viewsByLibrary) {
            for (view in views) {
                if (view.type == "authors") continue
                if (view.type == "series") {
                    val series =
                        view.entities.mapNotNull { element ->
                            try {
                                json.decodeFromJsonElement(
                                    AudiobookshelfSeries.serializer(),
                                    element,
                                )
                            } catch (e: Exception) {
                                null
                            }
                        }
                    if (series.isEmpty()) continue
                    val existing = allSections[view.id]
                    allSections[view.id] =
                        existing?.copy(series = (existing.series + series).distinctBy { it.id })
                            ?: PersonalizedSection(
                                id = view.id,
                                label = view.label,
                                items = emptyList(),
                                series = series.distinctBy { it.id },
                            )
                    continue
                }
                val items =
                    view.entities.mapNotNull { element ->
                        try {
                            json.decodeFromJsonElement(LibraryItem.serializer(), element)
                        } catch (e: Exception) {
                            null
                        }
                    }
                if (items.isEmpty()) continue
                val existing = allSections[view.id]
                if (existing != null) {
                    allSections[view.id] =
                        existing.copy(
                            items =
                                (existing.items + items).distinctBy {
                                    it.id to it.recentEpisode?.id
                                }
                        )
                } else {
                    allSections[view.id] =
                        PersonalizedSection(
                            id = view.id,
                            label = view.label,
                            items = items.distinctBy { it.id to it.recentEpisode?.id },
                        )
                }
            }
        }
        return allSections.values.toList()
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

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    companion object {
        private const val FULL_REFRESH_COOLDOWN_MS = 30_000L
        private const val SOCKET_CONNECTED_COOLDOWN_MS = 5 * 60_000L
        private const val CONTINUE_LISTENING_SHELF = "continue-listening"
        @Volatile private var lastFullRefreshAt = 0L
        @Volatile private var cachedGenreSections: List<PersonalizedSection> = emptyList()
        @Volatile private var cachedSessionId: String? = null
    }

    private fun enrichItems(
        items: List<LibraryItem>,
        progressMap: Map<String, MediaProgress>,
        episodeProgressMap: Map<String, MediaProgress>,
    ): List<LibraryItem> {
        return items.map { item ->
            val episodeId = item.recentEpisode?.id
            if (episodeId != null) {
                val episodeProgress = episodeProgressMap[mediaProgressKey(item.id, episodeId)]
                if (item.userMediaProgress != episodeProgress) {
                    item.copy(userMediaProgress = episodeProgress)
                } else {
                    item
                }
            } else if (item.userMediaProgress != null) {
                item
            } else {
                progressMap[item.id]?.let { item.copy(userMediaProgress = it) } ?: item
            }
        }
    }
}

data class AudiobookshelfLibrariesUiState(
    val isRefreshing: Boolean = true,
    val error: String? = null,
)
