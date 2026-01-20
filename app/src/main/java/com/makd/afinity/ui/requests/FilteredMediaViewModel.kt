package com.makd.afinity.ui.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.jellyseerr.SearchResultItem
import com.makd.afinity.data.repository.JellyseerrRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class FilteredMediaViewModel @Inject constructor(
    private val jellyseerrRepository: JellyseerrRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FilteredMediaUiState())
    val uiState: StateFlow<FilteredMediaUiState> = _uiState.asStateFlow()

    private var currentFilterParams: FilterParams? = null
    private var currentPage = 1

    init {
        viewModelScope.launch {
            jellyseerrRepository.requestEvents.collect { event ->
                val updatedRequest = event.request

                _uiState.update { state ->
                    state.copy(
                        items = state.items.map { item ->
                            if (item.id == updatedRequest.media.tmdbId) {
                                item.copy(mediaInfo = updatedRequest.media)
                            } else {
                                item
                            }
                        }
                    )
                }
                Timber.d("Updated media item ${updatedRequest.media.tmdbId} with new request status")
            }
        }

        viewModelScope.launch {
            jellyseerrRepository.currentSessionId.collect { sessionId ->
                if (sessionId != null && currentFilterParams != null) {
                    Timber.d("Session switched to $sessionId. Reloading filtered media...")
                    reloadCurrentContent()
                } else if (sessionId == null) {
                    _uiState.update { it.copy(items = emptyList()) }
                }
            }
        }
    }

    fun loadContent(filterParams: FilterParams) {
        if (currentFilterParams == filterParams && _uiState.value.items.isNotEmpty()) {
            return
        }

        currentFilterParams = filterParams
        reloadCurrentContent()
    }

    private fun reloadCurrentContent() {
        currentPage = 1
        _uiState.update { it.copy(items = emptyList(), hasReachedEnd = false) }
        loadPage()
    }

    fun loadNextPage() {
        if (_uiState.value.isLoading || _uiState.value.hasReachedEnd) {
            return
        }

        currentPage++
        loadPage()
    }

    private fun loadPage() {
        val params = currentFilterParams ?: return
        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                val result = when (params.type) {
                    FilterType.GENRE_MOVIE -> jellyseerrRepository.getMoviesByGenre(
                        params.id,
                        currentPage
                    )

                    FilterType.GENRE_TV -> jellyseerrRepository.getTvByGenre(params.id, currentPage)
                    FilterType.STUDIO -> jellyseerrRepository.getMoviesByStudio(
                        params.id,
                        currentPage
                    )

                    FilterType.NETWORK -> jellyseerrRepository.getTvByNetwork(
                        params.id,
                        currentPage
                    )

                    FilterType.TRENDING -> jellyseerrRepository.getTrending(currentPage)
                    FilterType.POPULAR_MOVIES -> jellyseerrRepository.getDiscoverMovies(currentPage)
                    FilterType.UPCOMING_MOVIES -> jellyseerrRepository.getUpcomingMovies(currentPage)
                    FilterType.POPULAR_TV -> jellyseerrRepository.getDiscoverTv(currentPage)
                    FilterType.UPCOMING_TV -> jellyseerrRepository.getUpcomingTv(currentPage)
                }

                result.fold(
                    onSuccess = { searchResult ->
                        val newItems = searchResult.results
                        val hasReachedEnd = newItems.isEmpty() || newItems.size < 20

                        _uiState.update { state ->
                            val combinedList =
                                if (currentPage == 1) newItems else state.items + newItems
                            val uniqueList = combinedList.distinctBy { it.id }

                            state.copy(
                                items = uniqueList,
                                isLoading = false,
                                hasReachedEnd = hasReachedEnd
                            )
                        }
                        Timber.d("Loaded page $currentPage for ${params.name}")
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = error.message ?: "Failed to load content"
                            )
                        }
                        Timber.e(error, "Failed to load content for ${params.name}")
                    }
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Unknown error"
                    )
                }
                Timber.e(e, "Error loading content")
            }
        }
    }
}

data class FilteredMediaUiState(
    val items: List<SearchResultItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasReachedEnd: Boolean = false
)