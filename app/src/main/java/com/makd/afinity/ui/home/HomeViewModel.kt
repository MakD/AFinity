package com.makd.afinity.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.extensions.toAfinityEpisode
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
import com.makd.afinity.data.repository.userdata.UserDataRepository
import com.makd.afinity.data.repository.watchlist.WatchlistRepository
import com.makd.afinity.ui.utils.IntentUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val appDataRepository: AppDataRepository,
    private val jellyfinRepository: JellyfinRepository,
    private val userDataRepository: UserDataRepository,
    private val watchlistRepository: WatchlistRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {

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
    }

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

                // Optimistic update
                _selectedEpisodeWatchlistStatus.value = !isInWatchlist

                val success = if (isInWatchlist) {
                    watchlistRepository.removeFromWatchlist(episode.id)
                } else {
                    watchlistRepository.addToWatchlist(episode.id, "EPISODE")
                }

                if (!success) {
                    // Revert on failure
                    _selectedEpisodeWatchlistStatus.value = isInWatchlist
                    Timber.w("Failed to toggle watchlist status")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error toggling episode watchlist")
                // Revert on error
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