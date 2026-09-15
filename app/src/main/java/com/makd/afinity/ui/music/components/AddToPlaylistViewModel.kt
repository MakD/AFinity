package com.makd.afinity.ui.music.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.music.AfinityPlaylist
import com.makd.afinity.data.repository.music.MusicRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.MediaType
import timber.log.Timber

data class AddToPlaylistState(
    val playlists: List<AfinityPlaylist> = emptyList(),
    val isLoadingPlaylists: Boolean = false,
    val isSubmitting: Boolean = false,
    val result: AddToPlaylistResult? = null,
)

sealed interface AddToPlaylistResult {
    data class Added(val playlistName: String) : AddToPlaylistResult

    data class Created(val playlistName: String) : AddToPlaylistResult

    data class Error(val message: String) : AddToPlaylistResult
}

@HiltViewModel
class AddToPlaylistViewModel @Inject constructor(private val musicRepository: MusicRepository) :
    ViewModel() {

    private val _state = MutableStateFlow(AddToPlaylistState())
    val state: StateFlow<AddToPlaylistState> = _state.asStateFlow()

    fun loadPlaylists() {
        if (_state.value.playlists.isNotEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(isLoadingPlaylists = true) }
            try {
                val playlists = musicRepository.getPlaylists()
                _state.update { it.copy(playlists = playlists, isLoadingPlaylists = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to load playlists")
                _state.update { it.copy(isLoadingPlaylists = false) }
            }
        }
    }

    fun addToPlaylist(playlist: AfinityPlaylist, trackIds: List<UUID>) {
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true) }
            try {
                musicRepository.addTracksToPlaylist(playlist.id, trackIds)
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        result = AddToPlaylistResult.Added(playlist.name),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to add to playlist")
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        result = AddToPlaylistResult.Error(e.message ?: "Unknown error"),
                    )
                }
            }
        }
    }

    fun createPlaylist(
        name: String,
        trackIds: List<UUID>,
        isPublic: Boolean,
        mediaType: MediaType = MediaType.AUDIO,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true) }
            try {
                val created = musicRepository.createPlaylist(name, trackIds, isPublic, mediaType)
                if (created != null) {
                    _state.update { state ->
                        state.copy(
                            isSubmitting = false,
                            playlists = listOf(created) + state.playlists,
                            result = AddToPlaylistResult.Created(created.name),
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            result = AddToPlaylistResult.Error("Failed to create playlist"),
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to create playlist")
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        result = AddToPlaylistResult.Error(e.message ?: "Unknown error"),
                    )
                }
            }
        }
    }

    fun clearResult() {
        _state.update { it.copy(result = null) }
    }

    fun reset() {
        _state.value = AddToPlaylistState()
    }
}
