package com.makd.afinity.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.media.AfinityCollection
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityRecommendationCategory
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.media.AfinityVideo
import com.makd.afinity.data.models.media.toAfinityEpisode
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.JellyfinRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.userdata.UserDataRepository
import com.makd.afinity.data.repository.watchlist.WatchlistRepository
import com.makd.afinity.data.websocket.WebSocketEvent
import com.makd.afinity.data.websocket.WebSocketEventBus
import com.makd.afinity.ui.utils.IntentUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.UUID
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val appDataRepository: AppDataRepository,
    private val jellyfinRepository: JellyfinRepository,
    private val userDataRepository: UserDataRepository,
    private val watchlistRepository: WatchlistRepository,
    private val preferencesRepository: PreferencesRepository,
    private val eventBus: WebSocketEventBus
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.getOfflineModeFlow().collect { isOffline ->
                val isOfflineChanged = _uiState.value.offlineMode != isOffline
                _uiState.value = _uiState.value.copy(offlineMode = isOffline)

                if (isOfflineChanged) {
                    Timber.d("Offline mode changed to: $isOffline - reloading data")
                    appDataRepository.reloadOnOfflineModeChange()
                }
            }
        }

        viewModelScope.launch {
            appDataRepository.isInitialDataLoaded.collect { isLoaded ->
                if (!isLoaded) {
                    Timber.d("Data cleared detected, resetting HomeViewModel UI state")
                    _uiState.value = HomeUiState()
                }
            }
        }

        viewModelScope.launch {
            appDataRepository.latestMedia.collect { latestMedia ->
                _uiState.value = _uiState.value.copy(latestMedia = latestMedia)
            }
        }

        viewModelScope.launch {
            appDataRepository.heroCarouselItems.collect { heroItems ->
                _uiState.value = _uiState.value.copy(heroCarouselItems = heroItems)
            }
        }

        viewModelScope.launch {
            appDataRepository.continueWatching.collect { continueWatching ->
                _uiState.value = _uiState.value.copy(continueWatching = continueWatching)
            }
        }

        viewModelScope.launch {
            appDataRepository.nextUp.collect { nextUp ->
                _uiState.value = _uiState.value.copy(nextUp = nextUp)
            }
        }

        viewModelScope.launch {
            appDataRepository.latestMovies.collect { latestMovies ->
                _uiState.value = _uiState.value.copy(latestMovies = latestMovies)
            }
        }

        viewModelScope.launch {
            appDataRepository.latestTvSeries.collect { latestTvSeries ->
                _uiState.value = _uiState.value.copy(latestTvSeries = latestTvSeries)
            }
        }

        viewModelScope.launch {
            appDataRepository.getCombineLibrarySectionsFlow().collect { combine ->
                _uiState.value = _uiState.value.copy(combineLibrarySections = combine)
            }
        }

        viewModelScope.launch {
            var isFirstEmission = true
            appDataRepository.getHomeSortByDateAddedFlow().collect { sortByDateAdded ->
                if (isFirstEmission) {
                    isFirstEmission = false
                    return@collect
                }
                appDataRepository.reloadHomeData()
            }
        }

        viewModelScope.launch {
            appDataRepository.separateMovieLibrarySections.collect { sections ->
                _uiState.value = _uiState.value.copy(separateMovieLibrarySections = sections)
            }
        }

        viewModelScope.launch {
            appDataRepository.separateTvLibrarySections.collect { sections ->
                _uiState.value = _uiState.value.copy(separateTvLibrarySections = sections)
            }
        }

        viewModelScope.launch {
            appDataRepository.highestRated.collect { highestRated ->
                _uiState.value = _uiState.value.copy(highestRated = highestRated)
            }
        }

        viewModelScope.launch {
            appDataRepository.recommendationCategories.collect { recommendations ->
                _uiState.value = _uiState.value.copy(recommendationCategories = recommendations)
            }
        }

        viewModelScope.launch {
            try {
                eventBus.events.collect { event ->
                    try {
                        handleWebSocketEvent(event)
                    } catch (e: Exception) {
                        Timber.e(e, "Error handling WebSocket event in Home (recovered)")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "WebSocket event collection failed in Home (recovered)")
            }
        }
    }

    private suspend fun handleWebSocketEvent(event: WebSocketEvent) {
        when (event) {
            is WebSocketEvent.UserDataChanged -> {
                val itemId = event.itemId
                val isVisible = isItemVisibleInHome(itemId)

                if (isVisible) {
                    Timber.d("WebSocket: Visible item updated in home screen: $itemId")
                }
            }

            is WebSocketEvent.BatchUserDataChanged -> {
                val visibleItems = event.itemIds.count { isItemVisibleInHome(it) }

                if (visibleItems > 0) {
                    Timber.d("WebSocket: $visibleItems visible items updated in batch")
                }
            }

            is WebSocketEvent.LibraryChanged -> {
                val totalChanges = event.itemsAdded.size +
                        event.itemsUpdated.size +
                        event.itemsRemoved.size
                Timber.d("WebSocket: Library changed ($totalChanges items)")
            }

            is WebSocketEvent.ServerRestarting,
            is WebSocketEvent.ServerShuttingDown -> {
                Timber.w("Server event in home screen: ${event::class.simpleName}")
            }
        }
    }

    private fun isItemVisibleInHome(itemId: UUID): Boolean {
        val currentState = _uiState.value
        return currentState.continueWatching.any { it.id == itemId } ||
                currentState.nextUp.any { it.id == itemId } ||
                currentState.latestMovies.any { it.id == itemId } ||
                currentState.latestTvSeries.any { it.id == itemId } ||
                currentState.heroCarouselItems.any { it.id == itemId }
    }

    fun refreshOnResume() {
        viewModelScope.launch {
            try {
                Timber.d("Refreshing home screen on resume")
                appDataRepository.refreshContinueWatching()
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh on resume")
            }
        }
    }


    private suspend fun handleUserDataChange(itemId: UUID) {
        val isInContinueWatching = _uiState.value.continueWatching.any { it.id == itemId }
        val isInNextUp = _uiState.value.nextUp.any { it.id == itemId }

        if (isInContinueWatching || isInNextUp) {
            Timber.d("Changed item is visible in home screen, UI will auto-update from flows")
        }
    }


    fun dismissOfflineModePrompt() {
        appDataRepository.dismissOfflineModePrompt()
    }

    fun enableOfflineModeFromPrompt() {
        viewModelScope.launch {
            appDataRepository.enableOfflineMode()
        }
    }

    val showOfflineModePrompt = appDataRepository.showOfflineModePrompt

    private val _selectedEpisode = MutableStateFlow<AfinityEpisode?>(null)
    val selectedEpisode: StateFlow<AfinityEpisode?> = _selectedEpisode.asStateFlow()

    private val _selectedEpisodeWatchlistStatus = MutableStateFlow(false)
    val selectedEpisodeWatchlistStatus: StateFlow<Boolean> = _selectedEpisodeWatchlistStatus.asStateFlow()

    private val _isLoadingEpisode = MutableStateFlow(false)
    val isLoadingEpisode: StateFlow<Boolean> = _isLoadingEpisode.asStateFlow()

    fun selectEpisode(episode: AfinityEpisode) {
        viewModelScope.launch {
            try {
                _isLoadingEpisode.value = true

                val fullEpisode = jellyfinRepository.getItem(
                    episode.id,
                    fields = FieldSets.ITEM_DETAIL
                )?.toAfinityEpisode(jellyfinRepository, null)

                if (fullEpisode != null) {
                    _selectedEpisode.value = episode
                }

                try {
                    val isInWatchlist = watchlistRepository.isInWatchlist(episode.id)
                    _selectedEpisodeWatchlistStatus.value = isInWatchlist
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load episode watchlist status")
                    _selectedEpisodeWatchlistStatus.value = false
                }

                _isLoadingEpisode.value = false
            } catch (e: Exception) {
                Timber.e(e, "Failed to load full episode details")
                _selectedEpisode.value = episode
                _isLoadingEpisode.value = false
            }
        }
    }

    fun clearSelectedEpisode() {
        _selectedEpisode.value = null
        _selectedEpisodeWatchlistStatus.value = false
    }

    fun toggleEpisodeFavorite(episode: AfinityEpisode) {
        viewModelScope.launch {
            try {
                val success = if (episode.favorite) {
                    userDataRepository.removeFromFavorites(episode.id)
                } else {
                    userDataRepository.addToFavorites(episode.id)
                }

                if (success) {
                    _selectedEpisode.value = episode.copy(favorite = !episode.favorite)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error toggling episode favorite")
            }
        }
    }

    fun toggleEpisodeWatchlist(episode: AfinityEpisode) {
        viewModelScope.launch {
            try {
                val isInWatchlist = _selectedEpisodeWatchlistStatus.value

                _selectedEpisodeWatchlistStatus.value = !isInWatchlist

                val success = if (isInWatchlist) {
                    watchlistRepository.removeFromWatchlist(episode.id)
                } else {
                    watchlistRepository.addToWatchlist(episode.id, "EPISODE")
                }

                if (!success) {
                    _selectedEpisodeWatchlistStatus.value = isInWatchlist
                    Timber.w("Failed to toggle watchlist status")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error toggling episode watchlist")
                try {
                    val isInWatchlist = watchlistRepository.isInWatchlist(episode.id)
                    _selectedEpisodeWatchlistStatus.value = isInWatchlist
                } catch (e2: Exception) {
                    Timber.e(e2, "Failed to reload watchlist status")
                }
            }
        }
    }

    fun toggleEpisodeWatched(episode: AfinityEpisode) {
        viewModelScope.launch {
            try {
                val success = if (episode.played) {
                    userDataRepository.markUnwatched(episode.id)
                } else {
                    userDataRepository.markWatched(episode.id)
                }

                if (success) {
                    _selectedEpisode.value = episode.copy(
                        played = !episode.played,
                        playbackPositionTicks = if (!episode.played) episode.runtimeTicks else 0
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error toggling episode watched status")
            }
        }
    }

    fun onHeroItemClick(item: AfinityItem) {
        Timber.d("Hero item clicked: ${item.name}")
    }

    fun onMoreInformationClick(item: AfinityItem) {
        Timber.d("More information clicked: ${item.name}")
    }

    fun onWatchNowClick(item: AfinityItem) {
        Timber.d("Watch now clicked: ${item.name}")
    }

    fun onPlayTrailerClick(context: Context, item: AfinityItem) {
        Timber.d("Play trailer clicked: ${item.name}")
        val trailerUrl = when (item) {
            is AfinityMovie -> item.trailer
            is AfinityShow -> item.trailer
            is AfinityVideo -> item.trailer
            else -> null
        }
        IntentUtils.openYouTubeUrl(context, trailerUrl)
    }

    fun onContinueWatchingItemClick(item: AfinityItem) {
        Timber.d("Continue watching item clicked: ${item.name}")
    }

    fun onLatestMovieItemClick(movie: AfinityMovie) {
        Timber.d("Latest movie item clicked: ${movie.name}")
    }

    fun onLatestTvSeriesItemClick(series: AfinityShow) {
        Timber.d("Latest TV series item clicked: ${series.name}")
    }

    fun onHighestRatedItemClick(item: AfinityItem) {
        Timber.d("Highest rated item clicked: ${item.name}")
    }

    fun onRecommendationItemClick(item: AfinityItem) {
        Timber.d("Recommendation item clicked: ${item.name}")
    }
}

data class HomeUiState(
    val offlineMode: Boolean = false,
    val heroCarouselItems: List<AfinityItem> = emptyList(),
    val latestMedia: List<AfinityItem> = emptyList(),
    val continueWatching: List<AfinityItem> = emptyList(),
    val nextUp: List<AfinityEpisode> = emptyList(),
    val latestMovies: List<AfinityMovie> = emptyList(),
    val latestTvSeries: List<AfinityShow> = emptyList(),
    val highestRated: List<AfinityItem> = emptyList(),
    val recommendationCategories: List<AfinityRecommendationCategory> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val combineLibrarySections: Boolean = false,
    val separateMovieLibrarySections: List<Pair<AfinityCollection, List<AfinityMovie>>> = emptyList(),
    val separateTvLibrarySections: List<Pair<AfinityCollection, List<AfinityShow>>> = emptyList()
)