package com.makd.afinity.ui.requests

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.R
import com.makd.afinity.data.models.jellyseerr.DiscoverFilterOptions
import com.makd.afinity.data.models.jellyseerr.Genre
import com.makd.afinity.data.models.jellyseerr.MovieSortField
import com.makd.afinity.data.models.jellyseerr.SearchResultItem
import com.makd.afinity.data.models.jellyseerr.TmdbKeyword
import com.makd.afinity.data.models.jellyseerr.TvSortField
import com.makd.afinity.data.models.jellyseerr.WatchProviderDetails
import com.makd.afinity.data.models.jellyseerr.WatchProviderRegion
import com.makd.afinity.data.repository.JellyseerrRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class FilteredMediaViewModel
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val jellyseerrRepository: JellyseerrRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FilteredMediaUiState())
    val uiState: StateFlow<FilteredMediaUiState> = _uiState.asStateFlow()

    private var currentFilterParams: FilterParams? = null
    private var currentContextKey: String? = null
    private var currentPage = 1
    private var loadedWatchProvidersKey: Pair<Boolean, String>? = null
    private var keywordSearchJob: Job? = null

    init {
        viewModelScope.launch {
            jellyseerrRepository.requestEvents.collect { event ->
                val updatedRequest = event.request

                _uiState.update { state ->
                    state.copy(
                        items =
                            state.items.map { item ->
                                if (item.id == updatedRequest.media.tmdbId) {
                                    item.copy(mediaInfo = updatedRequest.media)
                                } else {
                                    item
                                }
                            }
                    )
                }
                Timber.d(
                    "Updated media item ${updatedRequest.media.tmdbId} with new request status"
                )
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
        val contextKey = contextKeyFor(filterParams)
        currentContextKey = contextKey

        if (contextKey != null) {
            viewModelScope.launch {
                applyPersistedFilterState(filterParams, contextKey)
                reloadCurrentContent()
            }
        } else {
            reloadCurrentContent()
        }
    }

    private fun isTvType(type: FilterType): Boolean =
        type == FilterType.GENRE_TV || type == FilterType.POPULAR_TV || type == FilterType.UPCOMING_TV

    private fun contextKeyFor(params: FilterParams): String? =
        when (params.type) {
            FilterType.GENRE_MOVIE,
            FilterType.GENRE_TV -> "${params.type.name}_${params.id}"
            FilterType.POPULAR_MOVIES,
            FilterType.POPULAR_TV,
            FilterType.UPCOMING_MOVIES,
            FilterType.UPCOMING_TV -> params.type.name
            else -> null
        }

    private suspend fun applyPersistedFilterState(params: FilterParams, contextKey: String) {
        val isTv = isTvType(params.type)
        val persisted = jellyseerrRepository.getDiscoverFilterState(contextKey)

        if (persisted == null) {
            val releaseDateGte =
                if (params.type == FilterType.UPCOMING_MOVIES || params.type == FilterType.UPCOMING_TV) {
                    java.time.LocalDate.now().toString()
                } else null
            val defaultRegion =
                jellyseerrRepository.getPublicSettings().getOrNull()?.discoverRegion?.takeIf {
                    it.isNotBlank()
                }

            if (releaseDateGte != null || defaultRegion != null) {
                _uiState.update {
                    it.copy(
                        filterOptions =
                            DiscoverFilterOptions(
                                releaseDateGte = releaseDateGte,
                                watchRegion = defaultRegion,
                            )
                    )
                }
            }
            return
        }

        val (sortBy, options) = persisted
        val separatorIndex = sortBy.lastIndexOf('.')
        val apiKey = if (separatorIndex < 0) sortBy else sortBy.substring(0, separatorIndex)
        val descending = separatorIndex < 0 || sortBy.substring(separatorIndex + 1) != "asc"

        _uiState.update { state ->
            if (isTv) {
                val field = TvSortField.entries.find { it.apiKey == apiKey } ?: state.tvSortField
                state.copy(
                    tvSortField = field,
                    tvSortDescending = descending,
                    filterOptions = options,
                )
            } else {
                val field = MovieSortField.entries.find { it.apiKey == apiKey } ?: state.movieSortField
                state.copy(
                    movieSortField = field,
                    movieSortDescending = descending,
                    filterOptions = options,
                )
            }
        }
    }

    private fun persistFilterState() {
        val contextKey = currentContextKey ?: return
        val params = currentFilterParams ?: return
        val state = _uiState.value
        val isTv = isTvType(params.type)
        val sortBy =
            if (isTv) {
                "${state.tvSortField.apiKey}.${if (state.tvSortDescending) "desc" else "asc"}"
            } else {
                "${state.movieSortField.apiKey}.${if (state.movieSortDescending) "desc" else "asc"}"
            }

        viewModelScope.launch {
            jellyseerrRepository.saveDiscoverFilterState(contextKey, sortBy, state.filterOptions)
        }
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

    fun setMovieSort(field: MovieSortField, descending: Boolean) {
        _uiState.update { it.copy(movieSortField = field, movieSortDescending = descending) }
        persistFilterState()
        reloadCurrentContent()
    }

    fun setTvSort(field: TvSortField, descending: Boolean) {
        _uiState.update { it.copy(tvSortField = field, tvSortDescending = descending) }
        persistFilterState()
        reloadCurrentContent()
    }

    fun updateFilterOptions(
        options: DiscoverFilterOptions,
        selectedKeywords: List<TmdbKeyword> = _uiState.value.selectedKeywords,
        selectedExcludeKeywords: List<TmdbKeyword> = _uiState.value.selectedExcludeKeywords,
    ) {
        _uiState.update {
            it.copy(
                filterOptions = options,
                selectedKeywords = selectedKeywords,
                selectedExcludeKeywords = selectedExcludeKeywords,
            )
        }
        persistFilterState()
        reloadCurrentContent()
    }

    fun ensureGenresLoaded(isTv: Boolean) {
        val state = _uiState.value
        if (isTv && state.tvGenres.isNotEmpty()) return
        if (!isTv && state.movieGenres.isNotEmpty()) return

        viewModelScope.launch {
            val result = if (isTv) jellyseerrRepository.getTvGenres() else jellyseerrRepository.getMovieGenres()

            result.onSuccess { genres ->
                _uiState.update {
                    if (isTv) it.copy(tvGenres = genres) else it.copy(movieGenres = genres)
                }
            }
        }
    }

    fun ensureWatchProviderRegionsLoaded() {
        if (_uiState.value.watchProviderRegions.isNotEmpty()) return

        viewModelScope.launch {
            jellyseerrRepository.getWatchProviderRegions().onSuccess { regions ->
                _uiState.update { it.copy(watchProviderRegions = regions) }
            }
        }
    }

    fun loadWatchProviders(isTv: Boolean, region: String) {
        val key = isTv to region
        if (loadedWatchProvidersKey == key) return
        loadedWatchProvidersKey = key

        viewModelScope.launch {
            val result =
                if (isTv) jellyseerrRepository.getTvWatchProviders(region)
                else jellyseerrRepository.getMovieWatchProviders(region)

            result.onSuccess { providers ->
                _uiState.update {
                    if (isTv) it.copy(tvWatchProviders = providers)
                    else it.copy(movieWatchProviders = providers)
                }
            }
        }
    }

    fun searchKeywords(query: String) {
        keywordSearchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(keywordSearchResults = emptyList()) }
            return
        }

        keywordSearchJob =
            viewModelScope.launch {
                delay(300)
                jellyseerrRepository.searchKeywords(query).onSuccess { response ->
                    _uiState.update { it.copy(keywordSearchResults = response.results) }
                }
            }
    }

    private fun loadPage() {
        val params = currentFilterParams ?: return
        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                val result =
                    when (params.type) {
                        FilterType.GENRE_MOVIE -> {
                            val state = _uiState.value
                            val direction = if (state.movieSortDescending) "desc" else "asc"
                            jellyseerrRepository.getMoviesByGenre(
                                genreId = params.id,
                                page = currentPage,
                                sortBy = "${state.movieSortField.apiKey}.$direction",
                                filterOptions = state.filterOptions,
                            )
                        }

                        FilterType.GENRE_TV -> {
                            val state = _uiState.value
                            val direction = if (state.tvSortDescending) "desc" else "asc"
                            jellyseerrRepository.getTvByGenre(
                                genreId = params.id,
                                page = currentPage,
                                sortBy = "${state.tvSortField.apiKey}.$direction",
                                filterOptions = state.filterOptions,
                            )
                        }
                        FilterType.STUDIO ->
                            jellyseerrRepository.getMoviesByStudio(params.id, currentPage)

                        FilterType.NETWORK ->
                            jellyseerrRepository.getTvByNetwork(params.id, currentPage)

                        FilterType.TRENDING -> jellyseerrRepository.getTrending(currentPage)
                        FilterType.PERSON ->
                            jellyseerrRepository.getPersonCombinedCredits(params.id).map { credits
                                ->
                                val combined =
                                    if (currentPage == 1) {
                                        (credits.cast + credits.crew)
                                            .filter { it.getMediaType() != null }
                                            .distinctBy { it.id }
                                            .sortedByDescending { it.popularity ?: 0.0 }
                                    } else emptyList()
                                com.makd.afinity.data.models.jellyseerr.JellyseerrSearchResult(
                                    results = combined
                                )
                            }
                        FilterType.POPULAR_MOVIES,
                        FilterType.UPCOMING_MOVIES -> {
                            val state = _uiState.value
                            val direction = if (state.movieSortDescending) "desc" else "asc"
                            jellyseerrRepository.getDiscoverMovies(
                                page = currentPage,
                                sortBy = "${state.movieSortField.apiKey}.$direction",
                                filterOptions = state.filterOptions,
                            )
                        }
                        FilterType.POPULAR_TV,
                        FilterType.UPCOMING_TV -> {
                            val state = _uiState.value
                            val direction = if (state.tvSortDescending) "desc" else "asc"
                            jellyseerrRepository.getDiscoverTv(
                                page = currentPage,
                                sortBy = "${state.tvSortField.apiKey}.$direction",
                                filterOptions = state.filterOptions,
                            )
                        }
                    }

                result.fold(
                    onSuccess = { searchResult ->
                        val rawItems = searchResult.results
                        val hasReachedEnd = rawItems.isEmpty() || rawItems.size < 20

                        val hideAvailable =
                            params.type != FilterType.PERSON &&
                                jellyseerrRepository.getPublicSettings().getOrNull()?.hideAvailable ==
                                    true
                        val newItems =
                            if (hideAvailable) rawItems.filterNot { it.isAvailableOrPartial() }
                            else rawItems

                        _uiState.update { state ->
                            val combinedList =
                                if (currentPage == 1) newItems else state.items + newItems
                            val uniqueList = combinedList.distinctBy { it.id }

                            state.copy(
                                items = uniqueList,
                                isLoading = false,
                                hasReachedEnd = hasReachedEnd,
                            )
                        }
                        Timber.d("Loaded page $currentPage for ${params.name}")
                    },
                    onFailure = { error ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error =
                                    error.message
                                        ?: context.getString(R.string.error_load_content_generic),
                            )
                        }
                        Timber.e(error, "Failed to load content for ${params.name}")
                    },
                )
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: context.getString(R.string.error_unknown),
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
    val hasReachedEnd: Boolean = false,
    val movieSortField: MovieSortField = MovieSortField.POPULARITY,
    val movieSortDescending: Boolean = true,
    val tvSortField: TvSortField = TvSortField.POPULARITY,
    val tvSortDescending: Boolean = true,
    val filterOptions: DiscoverFilterOptions = DiscoverFilterOptions(),
    val movieGenres: List<Genre> = emptyList(),
    val tvGenres: List<Genre> = emptyList(),
    val watchProviderRegions: List<WatchProviderRegion> = emptyList(),
    val movieWatchProviders: List<WatchProviderDetails> = emptyList(),
    val tvWatchProviders: List<WatchProviderDetails> = emptyList(),
    val keywordSearchResults: List<TmdbKeyword> = emptyList(),
    val selectedKeywords: List<TmdbKeyword> = emptyList(),
    val selectedExcludeKeywords: List<TmdbKeyword> = emptyList(),
)
