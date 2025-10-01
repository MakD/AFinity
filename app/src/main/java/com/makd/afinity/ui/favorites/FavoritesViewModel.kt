package com.makd.afinity.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.extensions.toAfinityItem
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.repository.JellyfinRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.userdata.UserDataRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val jellyfinRepository: JellyfinRepository,
    private val userDataRepository: UserDataRepository,
    private val mediaRepository: MediaRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoritesUiState())
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    init {
        loadFavorites()
    }

    fun loadFavorites() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)

                val movies = jellyfinRepository.getFavoriteMovies()
                val shows = jellyfinRepository.getFavoriteShows()
                val episodes = jellyfinRepository.getFavoriteEpisodes()

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    movies = movies.sortedBy { it.name },
                    shows = shows.sortedBy { it.name },
                    episodes = episodes.sortedBy { it.name },
                    people = emptyList(),
                    error = null
                )

                Timber.d("Loaded favorites: ${movies.size} movies, ${shows.size} shows, ${episodes.size} episodes")

            } catch (e: Exception) {
                Timber.e(e, "Failed to load favorites")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load favorites: ${e.message}"
                )
            }
        }
    }

    fun onItemClick(item: AfinityItem) {
        Timber.d("Favorite item clicked: ${item.name} (${item.id})")
    }

    fun onPersonClick(personId: String) {
        Timber.d("Favorite person clicked: $personId")
    }
}

data class FavoritesUiState(
    val movies: List<AfinityMovie> = emptyList(),
    val shows: List<AfinityShow> = emptyList(),
    val episodes: List<AfinityEpisode> = emptyList(),
    val people: List<com.makd.afinity.data.models.media.AfinityPersonDetail> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)