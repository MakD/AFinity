package com.makd.afinity.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.repository.auth.AuthRepository
import com.makd.afinity.data.repository.DatabaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val databaseRepository: DatabaseRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    init {
        loadDownloads()
    }

    fun loadDownloads() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                val userId = authRepository.currentUser.value?.id

                if (userId == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "No user logged in"
                    )
                    return@launch
                }

                val movies = databaseRepository.getDownloadedMovies(userId)
                val episodes = databaseRepository.getDownloadedEpisodes(userId)

                _uiState.value = _uiState.value.copy(
                    movies = movies,
                    episodes = episodes,
                    isLoading = false,
                    error = null
                )

                Timber.d("Loaded ${movies.size} downloaded movies and ${episodes.size} downloaded episodes")
            } catch (e: Exception) {
                Timber.e(e, "Failed to load downloads")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load downloads: ${e.message}"
                )
            }
        }
    }

    fun refresh() {
        loadDownloads()
    }
}

data class DownloadsUiState(
    val movies: List<AfinityMovie> = emptyList(),
    val episodes: List<AfinityEpisode> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)