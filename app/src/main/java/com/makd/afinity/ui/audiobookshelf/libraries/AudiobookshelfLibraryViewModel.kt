package com.makd.afinity.ui.audiobookshelf.libraries

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.audiobookshelf.LibraryItem
import com.makd.afinity.data.models.audiobookshelf.MediaProgress
import com.makd.afinity.data.repository.AudiobookshelfConfig
import com.makd.afinity.data.repository.AudiobookshelfRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.websocket.AudiobookshelfSocketManager
import com.makd.afinity.data.websocket.WebSocketState
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.URLDecoder
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

private data class AbsItemQuery(
    val sort: AbsSortState<AbsItemSort>,
    val filters: AbsFilters,
    val ignorePrefix: Boolean,
    val orderVersion: Int,
)

@HiltViewModel
class AudiobookshelfLibraryViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val audiobookshelfRepository: AudiobookshelfRepository,
    private val socketManager: AudiobookshelfSocketManager,
    private val preferencesRepository: PreferencesRepository,
) : ViewModel() {

    val libraryId: String = checkNotNull(savedStateHandle["libraryId"])
    val libraryName: String =
        URLDecoder.decode(checkNotNull(savedStateHandle["libraryName"]), "UTF-8")

    val currentConfig: StateFlow<AudiobookshelfConfig?> = audiobookshelfRepository.currentConfig

    private val _isRefreshing = MutableStateFlow(true)

    private val _selectedLetter = MutableStateFlow<String?>(null)
    val selectedLetter: StateFlow<String?> = _selectedLetter.asStateFlow()

    private val _sort = MutableStateFlow(AbsSortState(AbsItemSort.TITLE))
    val sort: StateFlow<AbsSortState<AbsItemSort>> = _sort.asStateFlow()

    private val _filters = MutableStateFlow(AbsFilters())
    val filters: StateFlow<AbsFilters> = _filters.asStateFlow()

    private val ignorePrefix = MutableStateFlow(false)

    private val orderVersion = MutableStateFlow(0)

    private val items: StateFlow<List<LibraryItem>> =
        audiobookshelfRepository
            .getLibraryItemsFlow(libraryId)
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isPodcastLibrary: StateFlow<Boolean> =
        items
            .map { list -> list.firstOrNull()?.mediaType == "podcast" }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val filterOptions: StateFlow<AbsFilterOptions> =
        items
            .map(::buildAbsFilterOptions)
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AbsFilterOptions())

    val isLoading: StateFlow<Boolean> =
        combine(items, _isRefreshing) { loaded, refreshing -> refreshing && loaded.isEmpty() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val progressMap: StateFlow<Map<String, MediaProgress>> =
        audiobookshelfRepository
            .getAllProgressFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val query =
        combine(_sort, _filters, ignorePrefix, orderVersion) { sort, filters, prefix, version ->
            AbsItemQuery(sort, filters, prefix, version)
        }

    val display: StateFlow<AbsDisplay<LibraryItem>> =
        combine(items, _selectedLetter, progressMap, query) { loaded, letter, progress, query ->
                val byLetter =
                    if (letter == null) {
                        loaded
                    } else {
                        loaded.filter { item ->
                            val firstChar =
                                item.media.metadata.title?.firstOrNull()?.uppercase() ?: ""
                            if (letter == "#") {
                                firstChar.isNotEmpty() && !firstChar[0].isLetter()
                            } else {
                                firstChar == letter
                            }
                        }
                    }
                val isPodcast = loaded.firstOrNull()?.mediaType == "podcast"
                val sort =
                    if (query.sort.sort.availableFor(isPodcast)) query.sort
                    else AbsSortState(AbsItemSort.TITLE)
                AbsDisplay(
                    items =
                        enrichItems(byLetter, progress)
                            .filteredForAbs(query.filters, startedPodcastIds(progress))
                            .sortedForAbs(sort, query.ignorePrefix),
                    orderVersion = query.orderVersion,
                )
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AbsDisplay())

    init {
        viewModelScope.launch(Dispatchers.Default) {
            val socketLive = socketManager.connectionState.value == WebSocketState.CONNECTED
            audiobookshelfRepository
                .refreshLibraryItems(libraryId, force = !socketLive)
                .onFailure { error ->
                    Timber.e(error, "Failed to load items for library $libraryId")
                }
            _isRefreshing.value = false
        }
        viewModelScope.launch {
            try {
                _sort.value =
                    decodeAbsSort(preferencesRepository.getAbsSort(libraryId), AbsItemSort.TITLE)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to load Audiobookshelf sort for $libraryId")
            }
        }
        viewModelScope.launch {
            ignorePrefix.value = audiobookshelfRepository.getSortingIgnorePrefix()
        }
    }

    fun onLetterSelected(letter: String) {
        _selectedLetter.value = if (_selectedLetter.value == letter) null else letter
    }

    fun clearLetterFilter() {
        _selectedLetter.value = null
    }

    fun setSort(sort: AbsItemSort, descending: Boolean) {
        val current = _sort.value
        if (current.sort == sort && current.descending == descending) return
        val state = AbsSortState(sort, descending, randomSeed = Random.nextLong())
        _sort.value = state
        orderVersion.update { it + 1 }
        viewModelScope.launch {
            try {
                preferencesRepository.setAbsSort(libraryId, state.encode())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Failed to save Audiobookshelf sort for $libraryId")
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
