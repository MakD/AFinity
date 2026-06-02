package com.makd.afinity.ui.library

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import com.makd.afinity.R
import com.makd.afinity.data.manager.AdminChangeBroadcaster
import com.makd.afinity.data.manager.MediaChangeManager
import com.makd.afinity.data.manager.MediaChangeSource
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.media.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

enum class FilterType {
    ALL,
    WATCHED,
    UNWATCHED,
    WATCHLIST,
    FAVORITES,
}

@OptIn(FlowPreview::class)
@HiltViewModel
class LibraryContentViewModel
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val appDataRepository: AppDataRepository,
    private val adminChangeBroadcaster: AdminChangeBroadcaster,
    private val mediaChangeManager: MediaChangeManager,
    private val preferencesRepository: PreferencesRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val libraryId: String? = savedStateHandle["libraryId"]
    private val libraryName: String? = savedStateHandle["libraryName"]
    private val studioName: String? = savedStateHandle["studioName"]

    private val _uiState =
        MutableStateFlow(
            LibraryContentUiState(
                libraryId = libraryId?.let { UUID.fromString(it) },
                libraryName = (libraryName ?: studioName ?: "Content").replace("%2F", "/"),
                isStudioMode = studioName != null,
            )
        )
    val uiState: StateFlow<LibraryContentUiState> = _uiState.asStateFlow()

    private val _itemUpdates = MutableStateFlow<Map<UUID, AfinityItem>>(emptyMap())
    private val pendingUpdates = mutableMapOf<UUID, AfinityItem>()
    private val libraryUpdateTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var lastLoadedAt = 0L

    private fun applyUpdatesToPagingFlow(
        baseFlow: Flow<PagingData<AfinityItem>>
    ): Flow<PagingData<AfinityItem>> {
        return baseFlow
            .cachedIn(viewModelScope)
            .combine(_itemUpdates) { pagingData, updates ->
                pagingData
                    .map { item -> updates[item.id] ?: item }
                    .filter { item ->
                        when (currentFilter) {
                            FilterType.ALL -> true
                            FilterType.WATCHED -> item.played
                            FilterType.UNWATCHED -> !item.played
                            FilterType.WATCHLIST -> item.liked
                            FilterType.FAVORITES -> item.favorite
                        }
                    }
            }
            .cachedIn(viewModelScope)
    }

    private val _pagingData = MutableStateFlow<Flow<PagingData<AfinityItem>>>(emptyFlow())
    val pagingData: StateFlow<Flow<PagingData<AfinityItem>>> = _pagingData.asStateFlow()

    private var currentSortBy = SortBy.NAME

    private var currentSortDescending = false

    private val _scrollToIndex = MutableStateFlow(-1)
    val scrollToIndex: StateFlow<Int> = _scrollToIndex.asStateFlow()

    private var libraryType: CollectionType? = null

    private var currentFilter = FilterType.ALL

    init {
        viewModelScope.launch {
            appDataRepository.isInitialDataLoaded.collect { isLoaded ->
                if (isLoaded) {
                    loadLibraryContent()
                } else {
                    _uiState.update {
                        it.copy(isLoading = true, error = null, userProfileImageUrl = null)
                    }
                    _pagingData.value = emptyFlow()
                }
            }
        }
        viewModelScope.launch {
            appDataRepository.userProfileImageUrl.collect { url ->
                _uiState.update { it.copy(userProfileImageUrl = url) }
            }
        }
        viewModelScope.launch {
            libraryUpdateTrigger.debounce(300L).collect {
                if (pendingUpdates.isNotEmpty()) {
                    _itemUpdates.value += pendingUpdates
                    pendingUpdates.clear()
                    Timber.d("Applied batched PagingData updates to Library")
                }
            }
        }

        viewModelScope.launch {
            adminChangeBroadcaster.itemChanged.collect { loadItems() }
        }

        viewModelScope.launch {
            mediaChangeManager.mediaChanges.collect { event ->
                event.updatedItem?.let { pendingUpdates[it.id] = it }
                event.parentItem?.let { pendingUpdates[it.id] = it }
                event.seasonItem?.let { pendingUpdates[it.id] = it }

                if (event.source == MediaChangeSource.WEBSOCKET) {
                    try {
                        val directItem = mediaRepository.getItemById(event.itemId)
                        directItem?.let { pendingUpdates[it.id] = it }

                        if (event.seriesId != null && event.seriesId != event.itemId) {
                            val seriesItem = mediaRepository.getItemById(event.seriesId)
                            seriesItem?.let { pendingUpdates[it.id] = it }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to resolve items for granular update: ${event.itemId}")
                    }
                }

                if (pendingUpdates.isNotEmpty()) {
                    libraryUpdateTrigger.tryEmit(Unit)
                }
            }
        }
    }

    private suspend fun determineLibraryType(): CollectionType {
        if (studioName != null) {
            Timber.d("Studio mode: using Mixed collection type")
            return CollectionType.Mixed
        }

        return try {
            val libraries = mediaRepository.getLibraries()
            val library = libraries.find { it.id.toString() == libraryId }
            Timber.d("Library '$libraryName' has type: ${library?.type}")
            library?.type ?: CollectionType.Mixed
        } catch (e: Exception) {
            Timber.w(e, "Failed to determine library type, falling back to name detection")
            val name = libraryName ?: ""
            when {
                name.contains("TV", ignoreCase = true) ||
                    name.contains("Shows", ignoreCase = true) ||
                    name.contains("Series", ignoreCase = true) -> CollectionType.TvShows

                name.contains("Movie", ignoreCase = true) -> CollectionType.Movies
                else -> CollectionType.Mixed
            }
        }
    }

    private fun loadItems() {
        val type = libraryType ?: return
        _itemUpdates.value = emptyMap()

        val baseFlow =
            mediaRepository.getItemsPaging(
                parentId = libraryId?.let { UUID.fromString(it) },
                libraryType = type,
                sortBy = currentSortBy,
                sortDescending = currentSortDescending,
                filter = currentFilter,
                nameStartsWith = null,
                studioName = studioName,
            )
        _pagingData.value = applyUpdatesToPagingFlow(baseFlow)
    }

    private fun loadLibraryContent() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val type = determineLibraryType()
                libraryType = type

                currentSortBy = preferencesRepository.getDefaultSortBy()
                currentSortDescending = preferencesRepository.getSortDescending()

                _uiState.value =
                    _uiState.value.copy(
                        libraryType = type,
                        currentSortBy = currentSortBy,
                        currentSortDescending = currentSortDescending,
                        currentFilter = currentFilter,
                        isLoading = false,
                    )

                loadItems()
                lastLoadedAt = System.currentTimeMillis()
            } catch (e: Exception) {
                Timber.e(e, "Failed to load library content")
                _uiState.value =
                    _uiState.value.copy(
                        isLoading = false,
                        error = context.getString(R.string.error_content_unavailable_server),
                    )
            }
        }
    }

    fun onScreenResumed() {
        if (appDataRepository.lastUserDataChangedAt.value > lastLoadedAt) {
            lastLoadedAt = System.currentTimeMillis()
        }
    }

    fun updateFilter(filterType: FilterType) {
        if (currentFilter != filterType) {
            currentFilter = filterType
            _uiState.value = _uiState.value.copy(currentFilter = currentFilter)
            loadItems()
        }
    }

    fun updateSort(sortBy: SortBy, descending: Boolean) {
        if (currentSortBy != sortBy || currentSortDescending != descending) {
            currentSortBy = sortBy
            currentSortDescending = descending
            viewModelScope.launch {
                preferencesRepository.setDefaultSortBy(sortBy)
                preferencesRepository.setSortDescending(descending)
            }
            _uiState.value =
                _uiState.value.copy(
                    currentSortBy = currentSortBy,
                    currentSortDescending = currentSortDescending,
                )
            loadItems()
        }
    }

    fun onItemClick(item: AfinityItem) {
        Timber.d("Item clicked: ${item.name} (${item.id})")
        // TODO: Navigate to item detail screen
    }

    fun resetScrollIndex() {
        _scrollToIndex.value = -1
    }

    fun scrollToLetter(letter: String) {
        if (_uiState.value.selectedLetter == letter) {
            clearLetterFilter()
            return
        }

        viewModelScope.launch {
            try {
                val type = libraryType ?: return@launch

                val letterFilter =
                    when (letter) {
                        "#" -> "0"
                        else -> letter
                    }

                _uiState.value = _uiState.value.copy(selectedLetter = letter)
                _itemUpdates.value = emptyMap()

                val baseFlow =
                    mediaRepository.getItemsPaging(
                        parentId = libraryId?.let { UUID.fromString(it) },
                        libraryType = type,
                        sortBy = currentSortBy,
                        sortDescending = currentSortDescending,
                        filter = currentFilter,
                        nameStartsWith = letterFilter,
                        studioName = studioName,
                    )
                _pagingData.value = applyUpdatesToPagingFlow(baseFlow)

                Timber.d("Alphabet scroll: Created new paging source for letter '$letter'")
            } catch (e: Exception) {
                Timber.e(e, "Failed to scroll to letter $letter")
            }
        }
    }

    fun clearLetterFilter() {
        _uiState.value = _uiState.value.copy(selectedLetter = null)
        loadItems()
    }
}

data class LibraryContentUiState(
    val libraryId: UUID?,
    val libraryName: String,
    val libraryType: CollectionType? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val userProfileImageUrl: String? = null,
    val currentSortBy: SortBy = SortBy.NAME,
    val currentSortDescending: Boolean = false,
    val currentFilter: FilterType = FilterType.ALL,
    val isStudioMode: Boolean = false,
    val selectedLetter: String? = null,
)
