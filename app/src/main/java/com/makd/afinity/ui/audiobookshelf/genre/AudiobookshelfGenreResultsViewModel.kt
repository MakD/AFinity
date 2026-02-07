package com.makd.afinity.ui.audiobookshelf.genre

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.audiobookshelf.LibraryItem
import com.makd.afinity.data.repository.AudiobookshelfRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

@HiltViewModel
class AudiobookshelfGenreResultsViewModel
@Inject
constructor(private val audiobookshelfRepository: AudiobookshelfRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AudiobookshelfGenreResultsUiState())
    val uiState: StateFlow<AudiobookshelfGenreResultsUiState> = _uiState.asStateFlow()

    private var currentGenre: String? = null

    fun loadGenreResults(genre: String) {
        if (genre == currentGenre && _uiState.value.audiobooks.isNotEmpty()) return
        currentGenre = genre

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)

                val serverUrl = audiobookshelfRepository.currentConfig.value?.serverUrl

                var libraries = audiobookshelfRepository.getLibrariesFlow().first()
                if (libraries.isEmpty()) {
                    libraries =
                        audiobookshelfRepository.refreshLibraries().getOrDefault(emptyList())
                }

                val results =
                    libraries
                        .map { library ->
                            async {
                                audiobookshelfRepository
                                    .getLibraryItemsByGenre(library.id, genre)
                                    .getOrNull()
                            }
                        }
                        .awaitAll()
                        .filterNotNull()
                        .flatten()
                        .distinctBy { it.id }

                val audiobooks = results.filter { it.mediaType == "book" }
                val podcasts = results.filter { it.mediaType == "podcast" }

                _uiState.value =
                    _uiState.value.copy(
                        audiobooks = audiobooks,
                        podcasts = podcasts,
                        serverUrl = serverUrl,
                        isLoading = false,
                    )

                Timber.d(
                    "Genre results loaded: ${audiobooks.size} audiobooks, ${podcasts.size} podcasts for '$genre'"
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to load genre results")
                _uiState.value =
                    _uiState.value.copy(
                        isLoading = false,
                        error = e.message ?: "An unknown error occurred",
                    )
            }
        }
    }
}

data class AudiobookshelfGenreResultsUiState(
    val audiobooks: List<LibraryItem> = emptyList(),
    val podcasts: List<LibraryItem> = emptyList(),
    val serverUrl: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)
