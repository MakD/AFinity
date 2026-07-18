package com.makd.afinity.ui.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import androidx.paging.map
import com.makd.afinity.data.manager.AdminChangeBroadcaster
import com.makd.afinity.data.manager.MediaChangeManager
import com.makd.afinity.data.manager.resolveChangedItems
import com.makd.afinity.data.models.extensions.toAfinityItem
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.ItemFilterCriteria
import com.makd.afinity.data.repository.AppDataRepository
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

@OptIn(FlowPreview::class)
@HiltViewModel
class GenreResultsViewModel
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val appDataRepository: AppDataRepository,
    private val adminChangeBroadcaster: AdminChangeBroadcaster,
    private val mediaChangeManager: MediaChangeManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GenreResultsUiState())
    val uiState: StateFlow<GenreResultsUiState> = _uiState.asStateFlow()

    private val _moviesPagingData = MutableStateFlow<Flow<PagingData<AfinityItem>>>(emptyFlow())
    val moviesPagingData: StateFlow<Flow<PagingData<AfinityItem>>> = _moviesPagingData.asStateFlow()

    private val _showsPagingData = MutableStateFlow<Flow<PagingData<AfinityItem>>>(emptyFlow())
    val showsPagingData: StateFlow<Flow<PagingData<AfinityItem>>> = _showsPagingData.asStateFlow()

    private var currentGenre: String? = null
    private var lastLoadedAt = 0L

    private val _itemUpdates = MutableStateFlow<Map<UUID, AfinityItem>>(emptyMap())

    private val pendingUpdates = mutableMapOf<UUID, AfinityItem>()
    private val genreUpdateTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private fun applyUpdatesToPagingFlow(
        baseFlow: Flow<PagingData<AfinityItem>>
    ): Flow<PagingData<AfinityItem>> {
        return baseFlow
            .cachedIn(viewModelScope)
            .combine(_itemUpdates) { pagingData, updates ->
                pagingData.map { item -> updates[item.id] ?: item }
            }
    }

    init {
        viewModelScope.launch {
            genreUpdateTrigger.debounce(300L).collect {
                if (pendingUpdates.isNotEmpty()) {
                    _itemUpdates.value += pendingUpdates
                    pendingUpdates.clear()
                    Timber.d("Applied batched PagingData updates to Genre Results")
                }
            }
        }

        viewModelScope.launch {
            adminChangeBroadcaster.itemChanged.collect {
                currentGenre?.let { reloadGenre(it) }
            }
        }

        viewModelScope.launch {
            mediaChangeManager.mediaChanges.collect { event ->
                val resolved = event.resolveChangedItems(mediaRepository)
                if (resolved.isNotEmpty()) {
                    resolved.forEach { pendingUpdates[it.id] = it }
                    genreUpdateTrigger.tryEmit(Unit)
                }
            }
        }
    }

    fun onScreenResumed() {
        if (appDataRepository.lastUserDataChangedAt.value > lastLoadedAt) {
            lastLoadedAt = System.currentTimeMillis()
        }
    }

    private fun reloadGenre(genre: String) {
        _itemUpdates.value = emptyMap()
        val moviesBaseFlow =
            Pager(PagingConfig(pageSize = 50)) {
                    GenrePagingSource(mediaRepository, genre, "MOVIE")
                }
                .flow
        _moviesPagingData.value = applyUpdatesToPagingFlow(moviesBaseFlow)
        val showsBaseFlow =
            Pager(PagingConfig(pageSize = 50)) {
                    GenrePagingSource(mediaRepository, genre, "SERIES")
                }
                .flow
        _showsPagingData.value = applyUpdatesToPagingFlow(showsBaseFlow)
        lastLoadedAt = System.currentTimeMillis()
    }

    fun loadGenreResults(genre: String) {
        if (currentGenre == genre) return

        currentGenre = genre
        _uiState.update { it.copy(isLoading = false, error = null) }
        reloadGenre(genre)
    }
}

data class GenreResultsUiState(val isLoading: Boolean = false, val error: String? = null)

class GenrePagingSource(
    private val mediaRepository: MediaRepository,
    private val genre: String,
    private val itemType: String,
) : PagingSource<Int, AfinityItem>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, AfinityItem> {
        val position = params.key ?: 0
        return try {
            val response =
                mediaRepository.getItems(
                    includeItemTypes = listOf(itemType),
                    startIndex = position,
                    limit = params.loadSize,
                    criteria = ItemFilterCriteria(genres = listOf(genre)),
                )

            val items =
                response.items?.mapNotNull { baseItemDto ->
                    try {
                        baseItemDto.toAfinityItem(mediaRepository.getBaseUrl())
                    } catch (e: Exception) {
                        null
                    }
                } ?: emptyList()

            LoadResult.Page(
                data = items,
                prevKey = if (position == 0) null else position - params.loadSize,
                nextKey = if (items.isEmpty()) null else position + items.size,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, AfinityItem>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(50) ?: anchorPage?.nextKey?.minus(50)
        }
    }
}
