package com.makd.afinity.ui.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.repository.JellyfinRepository
import com.makd.afinity.data.repository.watchlist.WatchlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class WatchlistViewModel @Inject constructor(
    private val jellyfinRepository: JellyfinRepository,
    private val watchlistRepository: WatchlistRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(WatchlistUiState())
    val uiState: StateFlow<WatchlistUiState> = _uiState.asStateFlow()

    init {
        loadWatchlist()
    }

    fun loadWatchlist() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)

                val movies = watchlistRepository.getWatchlistMovies()
                val shows = watchlistRepository.getWatchlistShows()
                val episodes = watchlistRepository.getWatchlistEpisodes()

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    movies = movies.sortedBy { it.name },
                    shows = shows.sortedBy { it.name },
                    episodes = episodes.sortedBy { it.name },
                    error = null,
                )

                Timber.d("Loaded watchlist: ${movies.size} movies, ${shows.size} shows, ${episodes.size} episodes")

            } catch (e: Exception) {
                Timber.e(e, "Failed to load watchlist")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load watchlist: ${e.message}"
                )
            }
        }
    }


    fun onItemClick(item: AfinityItem) {
        Timber.d("Watchlist item clicked: ${item.name} (${item.id})")
    }
}

data class WatchlistUiState(
    val movies: List<AfinityMovie> = emptyList(),
    val shows: List<AfinityShow> = emptyList(),
    val episodes: List<AfinityEpisode> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val userProfileImageUrl: String? = null
)