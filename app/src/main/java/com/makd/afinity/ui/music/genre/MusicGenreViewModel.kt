package com.makd.afinity.ui.music.genre

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.music.AfinityAlbum
import com.makd.afinity.data.models.music.AfinityArtist
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.music.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

data class MusicGenreUiState(
    val albums: List<AfinityAlbum> = emptyList(),
    val artists: List<AfinityArtist> = emptyList(),
    val tracks: List<AfinityTrack> = emptyList(),
    val recentlyAdded: List<AfinityAlbum> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class MusicGenreViewModel
@Inject
constructor(
    private val musicRepository: MusicRepository,
    private val appDataRepository: AppDataRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val genreName: String = savedStateHandle.get<String>("genreName") ?: ""
    val genreImageUrl: String? = savedStateHandle.get<String>("imageUrl")
    val genreId: UUID? = savedStateHandle.get<String>("genreId")?.let {
        runCatching { UUID.fromString(it) }.getOrNull()
    }

    val userProfileImageUrl: StateFlow<String?> = appDataRepository.userProfileImageUrl

    private val _uiState = MutableStateFlow(MusicGenreUiState())
    val uiState: StateFlow<MusicGenreUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { loadGenreContent() }
    }

    private suspend fun loadGenreContent() {
        if (genreName.isBlank()) return
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        try {
            coroutineScope {
                val albumsJob = async { musicRepository.getAlbumsByGenre(genreName, limit = 50) }
                val artistsJob = async { musicRepository.getArtistsByGenre(genreName, limit = 50) }
                val tracksJob = async { musicRepository.getTracksByGenre(genreName, limit = 50) }
                val recentJob = async {
                    musicRepository.getRecentlyAddedAlbumsByGenre(genreName, limit = 20)
                }
                _uiState.value = _uiState.value.copy(
                    albums = albumsJob.await(),
                    artists = artistsJob.await(),
                    tracks = tracksJob.await(),
                    recentlyAdded = recentJob.await(),
                    isLoading = false,
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load genre content for: $genreName")
            _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
        }
    }

    fun refresh() {
        viewModelScope.launch { loadGenreContent() }
    }
}