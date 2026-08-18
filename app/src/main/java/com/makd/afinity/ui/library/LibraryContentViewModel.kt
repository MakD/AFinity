package com.makd.afinity.ui.library

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.cachedIn
import androidx.paging.filter
import androidx.paging.map
import com.makd.afinity.R
import com.makd.afinity.data.manager.AdminChangeBroadcaster
import com.makd.afinity.data.manager.DownloadPermissions
import com.makd.afinity.data.manager.MediaChangeManager
import com.makd.afinity.data.manager.resolveChangedItems
import com.makd.afinity.data.models.CustomHomeSection
import com.makd.afinity.data.models.CustomSectionItemType
import com.makd.afinity.data.models.CustomSectionSourceType
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.LibraryFilterOptions
import com.makd.afinity.data.models.media.LibraryFilters
import com.makd.afinity.data.models.media.toAfinityEpisode
import com.makd.afinity.data.models.media.withUserDataFrom
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.download.DownloadRepository
import com.makd.afinity.data.repository.home.CustomHomeSectionsRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.userdata.UserDataRepository
import com.makd.afinity.data.repository.watchlist.WatchlistRepository
import com.makd.afinity.data.store.ItemStore
import com.makd.afinity.ui.item.delegates.ItemUserDataDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

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
    private val customHomeSectionsRepository: CustomHomeSectionsRepository,
    private val watchlistRepository: WatchlistRepository,
    private val downloadRepository: DownloadRepository,
    private val userDataRepository: UserDataRepository,
    private val itemUserDataDelegate: ItemUserDataDelegate,
    private val downloadPermissions: DownloadPermissions,
    private val itemStore: ItemStore,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val isDownloadAllowedByServer: StateFlow<Boolean> = downloadPermissions.isAllowedByServer

    val canDownloadOnNetwork: StateFlow<Boolean> = downloadPermissions.isAllowedOnNetwork

    private val _selectedEpisode = MutableStateFlow<AfinityEpisode?>(null)
    val selectedEpisode: StateFlow<AfinityEpisode?> = _selectedEpisode.asStateFlow()

    private val _selectedEpisodeWatchlistStatus = MutableStateFlow(false)
    val selectedEpisodeWatchlistStatus: StateFlow<Boolean> =
        _selectedEpisodeWatchlistStatus.asStateFlow()

    private val _selectedEpisodeDownloadInfo = MutableStateFlow<DownloadInfo?>(null)
    val selectedEpisodeDownloadInfo: StateFlow<DownloadInfo?> =
        _selectedEpisodeDownloadInfo.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeSelectedEpisodeDownload() {
        _selectedEpisode
            .map { it?.id }
            .distinctUntilChanged()
            .flatMapLatest { id ->
                if (id == null) flowOf(null)
                else
                    downloadRepository.getAllDownloadsFlow().map { downloads ->
                        downloads.find { it.itemId == id }
                    }
            }
            .onEach { _selectedEpisodeDownloadInfo.value = it }
            .launchIn(viewModelScope)
    }

    fun selectEpisode(episode: AfinityEpisode) {
        viewModelScope.launch {
            try {
                val fullEpisode =
                    mediaRepository
                        .getItem(episode.id, fields = FieldSets.ITEM_DETAIL)
                        ?.toAfinityEpisode(mediaRepository.getBaseUrl(), null)
                _selectedEpisode.value = fullEpisode ?: episode
                _selectedEpisodeWatchlistStatus.value =
                    watchlistRepository.isInWatchlist(episode.id)
            } catch (e: Exception) {
                Timber.e(e, "Failed to load full episode details")
                _selectedEpisode.value = episode
            }
        }
    }

    fun clearSelectedEpisode() {
        _selectedEpisode.value = null
        _selectedEpisodeWatchlistStatus.value = false
        _selectedEpisodeDownloadInfo.value = null
    }

    fun toggleEpisodeFavorite(episode: AfinityEpisode) {
        itemUserDataDelegate.toggleEpisodeFavorite(viewModelScope, episode) {
            _selectedEpisode.value = episode.copy(favorite = !episode.favorite)
        }
    }

    fun toggleEpisodeWatchlist(episode: AfinityEpisode) {
        val isLiked = _selectedEpisodeWatchlistStatus.value
        itemUserDataDelegate.toggleWatchlist(
            scope = viewModelScope,
            item = episode,
            updateOptimisticUI = {
                _selectedEpisodeWatchlistStatus.value = !isLiked
                _selectedEpisode.value = _selectedEpisode.value?.copy(liked = !isLiked)
            },
            revertUI = {
                _selectedEpisodeWatchlistStatus.value = isLiked
                _selectedEpisode.value = _selectedEpisode.value?.copy(liked = isLiked)
            },
        )
    }

    fun toggleEpisodeWatched(episode: AfinityEpisode) {
        viewModelScope.launch {
            val isNowPlayed = !episode.played
            _selectedEpisode.value = episode.copy(played = isNowPlayed, playbackPositionTicks = 0)
            val success =
                if (episode.played) userDataRepository.markUnwatched(episode.id)
                else userDataRepository.markWatched(episode.id)
            if (!success) _selectedEpisode.value = episode
        }
    }

    private val libraryId: String? = savedStateHandle["libraryId"]
    private val libraryName: String? = savedStateHandle["libraryName"]
    private val studioName: String? = savedStateHandle["studioName"]
    private val sectionId: String? = savedStateHandle["sectionId"]

    private var customSection: CustomHomeSection? = null
    private var sectionParentId: UUID? = null
    private var sectionStudios: List<String> = emptyList()
    private var sectionItemTypes: List<String>? = null
    private var currentLibraryPagingSource: PagingSource<Int, AfinityItem>? = null

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val filtersKey = "library_filters_${libraryId ?: studioName ?: "content"}"

    private val _uiState =
        MutableStateFlow(
            LibraryContentUiState(
                libraryId = libraryId?.let { UUID.fromString(it) },
                libraryName = (libraryName ?: studioName ?: "Content").replace("%2F", "/"),
                isStudioMode = studioName != null,
                filtersLocked = sectionId != null,
            )
        )
    val uiState: StateFlow<LibraryContentUiState> = _uiState.asStateFlow()

    private var lastLoadedAt = 0L

    private fun applyUpdatesToPagingFlow(
        baseFlow: Flow<PagingData<AfinityItem>>
    ): Flow<PagingData<AfinityItem>> {
        return baseFlow.cachedIn(viewModelScope).combine(itemStore.overlay) { pagingData, updates
            ->
            pagingData
                .map { item ->
                    val source = updates[item.id] ?: return@map item
                    source as? AfinityItem ?: item.withUserDataFrom(source)
                }
                .filter { item -> matchesStatusFilters(item, currentFilters) }
        }
    }

    private fun matchesStatusFilters(item: AfinityItem, filters: LibraryFilters): Boolean {
        val requiredPlayed =
            when {
                filters.played && !filters.unplayed -> true
                filters.unplayed && !filters.played -> false
                else -> null
            }
        if (requiredPlayed != null && item.played != requiredPlayed) return false
        if (filters.favorites && !item.favorite) return false
        if (filters.watchlist && !item.liked) return false
        return true
    }

    private val _pagingData = MutableStateFlow<Flow<PagingData<AfinityItem>>>(emptyFlow())
    val pagingData: StateFlow<Flow<PagingData<AfinityItem>>> = _pagingData.asStateFlow()

    private var currentSortBy = SortBy.NAME

    private var currentSortDescending = false

    private val _scrollToIndex = MutableStateFlow(-1)
    val scrollToIndex: StateFlow<Int> = _scrollToIndex.asStateFlow()

    private var libraryType: CollectionType? = null

    private var currentFilters = LibraryFilters()

    init {
        observeSelectedEpisodeDownload()

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
        viewModelScope.launch { adminChangeBroadcaster.itemChanged.collect { loadItems() } }

        viewModelScope.launch {
            mediaChangeManager.libraryContentChanges.collect { event ->
                Timber.d("Library content changed (${event.reason}) — refreshing ${libraryName}")
                currentLibraryPagingSource?.invalidate() ?: loadItems()
            }
        }


        viewModelScope.launch {
            mediaChangeManager.mediaChanges.collect { event ->
                val resolved =
                    event.resolveChangedItems(
                        mediaRepository = mediaRepository,
                        heldItem = { id -> itemStore.get(id) as? AfinityItem },
                    )
                if (resolved.isNotEmpty()) {
                    itemStore.put(resolved)
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

        val baseFlow =
            mediaRepository.getItemsPaging(
                parentId = sectionParentId ?: libraryId?.let { UUID.fromString(it) },
                libraryType = type,
                sortBy = currentSortBy,
                sortDescending = currentSortDescending,
                filters = currentFilters,
                nameStartsWith = null,
                studioNames = sectionStudios.ifEmpty { listOfNotNull(studioName) },
                includeItemTypes = sectionItemTypes,
                onSourceCreated = { source -> currentLibraryPagingSource = source },
            )
        _pagingData.value = applyUpdatesToPagingFlow(baseFlow)
    }

    private fun sectionLibraryType(): CollectionType {
        val types = customSection?.itemTypes.orEmpty()
        return when (types.singleOrNull()) {
            CustomSectionItemType.MOVIE -> CollectionType.Movies
            CustomSectionItemType.SERIES -> CollectionType.TvShows
            CustomSectionItemType.BOX_SET -> CollectionType.BoxSets
            else -> CollectionType.Mixed
        }
    }

    private suspend fun resolveCustomSection(id: String): CustomHomeSection? {
        val section = customHomeSectionsRepository.get(id)
        if (section == null) {
            Timber.w("Custom section $id no longer exists")
            return null
        }
        customSection = section

        val sourceId =
            if (section.sourceType.usesItemIds) {
                runCatching { UUID.fromString(section.primarySourceValue.orEmpty()) }.getOrNull()
            } else null

        sectionParentId = sourceId
        sectionStudios =
            if (section.sourceType == CustomSectionSourceType.STUDIO) section.sourceValues
            else emptyList()
        sectionItemTypes = section.includeItemTypes.takeIf { it.isNotEmpty() }

        currentFilters =
            when (section.sourceType) {
                CustomSectionSourceType.GENRE ->
                    section.filters.copy(genres = section.filters.genres + section.sourceValues)
                CustomSectionSourceType.TAG ->
                    section.filters.copy(tags = section.filters.tags + section.sourceValues)
                else -> section.filters
            }
        currentSortBy = section.sortBy
        currentSortDescending = section.sortDescending
        return section
    }

    private fun loadLibraryContent() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val section = sectionId?.let { resolveCustomSection(it) }
                if (sectionId != null && section == null) {
                    _uiState.value =
                        _uiState.value.copy(
                            isLoading = false,
                            error = context.getString(R.string.error_content_unavailable_server),
                        )
                    return@launch
                }

                val type = if (section != null) sectionLibraryType() else determineLibraryType()
                libraryType = type

                if (section == null) {
                    currentSortBy = preferencesRepository.getDefaultSortBy()
                    currentSortDescending = preferencesRepository.getSortDescending()
                    currentFilters = loadPersistedFilters()
                }

                _uiState.value =
                    _uiState.value.copy(
                        libraryName = section?.title ?: _uiState.value.libraryName,
                        libraryType = type,
                        currentSortBy = currentSortBy,
                        currentSortDescending = currentSortDescending,
                        currentFilters = currentFilters,
                        isLoading = false,
                    )

                loadItems()
                if (section == null) loadFilterOptions(type)
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

    private suspend fun loadPersistedFilters(): LibraryFilters =
        preferencesRepository.getStringPreference(filtersKey)?.let { stored ->
            runCatching { json.decodeFromString(LibraryFilters.serializer(), stored) }.getOrNull()
        } ?: LibraryFilters()

    private fun loadFilterOptions(type: CollectionType) {
        viewModelScope.launch {
            try {
                val options =
                    mediaRepository.getFilterOptions(
                        parentId = libraryId?.let { UUID.fromString(it) },
                        libraryType = type,
                    )
                _uiState.value = _uiState.value.copy(filterOptions = options)
            } catch (e: Exception) {
                Timber.e(e, "Failed to load filter options")
            }
        }
    }

    fun updateFilters(filters: LibraryFilters) {
        if (sectionId != null) return
        if (currentFilters != filters) {
            currentFilters = filters
            _uiState.value = _uiState.value.copy(currentFilters = currentFilters)
            viewModelScope.launch {
                preferencesRepository.setStringPreference(
                    filtersKey,
                    if (filters.isEmpty) null
                    else json.encodeToString(LibraryFilters.serializer(), filters),
                )
            }
            if (_uiState.value.selectedLetter != null) {
                scrollToLetterInternal(_uiState.value.selectedLetter!!)
            } else {
                loadItems()
            }
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
        scrollToLetterInternal(letter)
    }

    private fun scrollToLetterInternal(letter: String) {
        viewModelScope.launch {
            try {
                val type = libraryType ?: return@launch

                val letterFilter =
                    when (letter) {
                        "#" -> "0"
                        else -> letter
                    }

                _uiState.value = _uiState.value.copy(selectedLetter = letter)

                val baseFlow =
                    mediaRepository.getItemsPaging(
                        parentId = sectionParentId ?: libraryId?.let { UUID.fromString(it) },
                        libraryType = type,
                        sortBy = currentSortBy,
                        sortDescending = currentSortDescending,
                        filters = currentFilters,
                        nameStartsWith = letterFilter,
                        studioNames = sectionStudios.ifEmpty { listOfNotNull(studioName) },
                        includeItemTypes = sectionItemTypes,
                        onSourceCreated = { source -> currentLibraryPagingSource = source },
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
    val currentFilters: LibraryFilters = LibraryFilters(),
    val filterOptions: LibraryFilterOptions = LibraryFilterOptions(),
    val isStudioMode: Boolean = false,
    val selectedLetter: String? = null,
    val filtersLocked: Boolean = false,
)
