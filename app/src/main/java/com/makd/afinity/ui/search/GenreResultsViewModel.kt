package com.makd.afinity.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.extensions.toAfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.repository.JellyfinRepository
import com.makd.afinity.data.repository.media.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class GenreResultsViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val jellyfinRepository: JellyfinRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GenreResultsUiState())
    val uiState: StateFlow<GenreResultsUiState> = _uiState.asStateFlow()

    fun loadGenreResults(genre: String) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)

                val results = mediaRepository.getItems(
                    genres = listOf(genre),
                    includeItemTypes = listOf("MOVIE", "SERIES"),
                    limit = 200
                )

                val allItems = results.items?.mapNotNull { baseItemDto ->
                    try {
                        baseItemDto.toAfinityItem(jellyfinRepository.getBaseUrl())
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to convert item to AfinityItem: ${baseItemDto.name}")
                        null
                    }
                } ?: emptyList()

                val movies = allItems.filterIsInstance<AfinityMovie>()
                val shows = allItems.filterIsInstance<AfinityShow>()

                _uiState.value = _uiState.value.copy(
                    movies = movies,
                    shows = shows,
                    isLoading = false
                )

                Timber.d("Genre results loaded: ${movies.size} movies, ${shows.size} shows for '$genre'")

            } catch (e: Exception) {
                Timber.e(e, "Failed to load genre results")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error occurred"
                )
            }
        }
    }
}

data class GenreResultsUiState(
    val movies: List<AfinityMovie> = emptyList(),
    val shows: List<AfinityShow> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)