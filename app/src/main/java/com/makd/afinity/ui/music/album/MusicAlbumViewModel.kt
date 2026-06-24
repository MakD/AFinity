package com.makd.afinity.ui.music.album

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.manager.AdminChangeBroadcaster
import com.makd.afinity.data.models.music.AfinityAlbum
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.data.repository.music.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

data class MusicAlbumUiState(
    val album: AfinityAlbum? = null,
    val tracks: List<AfinityTrack> = emptyList(),
    val artistImageUrl: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class MusicAlbumViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val adminChangeBroadcaster: AdminChangeBroadcaster,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val albumId: UUID = UUID.fromString(savedStateHandle.get<String>("albumId")!!)

    private val _uiState = MutableStateFlow(MusicAlbumUiState())
    val uiState: StateFlow<MusicAlbumUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val albumDeferred = async { musicRepository.getAlbumById(albumId) }
                val tracksDeferred = async { musicRepository.getAlbumTracks(albumId) }
                val album = albumDeferred.await()
                val tracks = tracksDeferred.await()
                val artistImageUrl = album?.artistId?.let {
                    "${musicRepository.getBaseUrl()}/Items/$it/Images/Primary?fillHeight=128&quality=90"
                }
                _uiState.update {
                    it.copy(album = album, tracks = tracks, artistImageUrl = artistImageUrl, isLoading = false)
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load album $albumId")
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun toggleFavorite() {
        val album = _uiState.value.album ?: return
        val newFavorite = !album.favorite
        _uiState.update { it.copy(album = album.copy(favorite = newFavorite)) }
        viewModelScope.launch {
            runCatching { musicRepository.setFavorite(album.id, newFavorite) }
                .onSuccess { adminChangeBroadcaster.notifyItemChanged(album.id.toString()) }
                .onFailure { _uiState.update { it.copy(album = album.copy(favorite = album.favorite)) } }
        }
    }

    fun toggleTrackFavorite(trackId: UUID) {
        val tracks = _uiState.value.tracks
        val track = tracks.find { it.id == trackId } ?: return
        val newFavorite = !track.favorite
        _uiState.update { it.copy(tracks = tracks.map { t -> if (t.id == trackId) t.copy(favorite = newFavorite) else t }) }
        viewModelScope.launch {
            runCatching { musicRepository.setFavorite(trackId, newFavorite) }
                .onFailure { _uiState.update { it.copy(tracks = tracks) } }
        }
    }

    fun refresh() = load()
}