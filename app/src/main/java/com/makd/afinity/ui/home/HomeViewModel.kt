package com.makd.afinity.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.media.AfinityCollection
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.models.media.AfinityRecommendationCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val appDataRepository: AppDataRepository
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
            appDataRepository.getHomeSortByDateAddedFlow().collect { sortByDateAdded ->
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
            appDataRepository.userProfileImageUrl.collect { profileUrl ->
                _uiState.value = _uiState.value.copy(userProfileImageUrl = profileUrl)
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

    fun onPlayTrailerClick(item: AfinityItem) {
        Timber.d("Play trailer clicked: ${item.name}")
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
    val latestMedia: List<AfinityItem> = emptyList(),
    val continueWatching: List<AfinityItem> = emptyList(),
    val nextUp: List<AfinityEpisode> = emptyList(),
    val latestMovies: List<AfinityMovie> = emptyList(),
    val latestTvSeries: List<AfinityShow> = emptyList(),
    val highestRated: List<AfinityItem> = emptyList(),
    val recommendationCategories: List<AfinityRecommendationCategory> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val userProfileImageUrl: String? = null,
    val combineLibrarySections: Boolean = false,
    val separateMovieLibrarySections: List<Pair<AfinityCollection, List<AfinityMovie>>> = emptyList(),
    val separateTvLibrarySections: List<Pair<AfinityCollection, List<AfinityShow>>> = emptyList()
)