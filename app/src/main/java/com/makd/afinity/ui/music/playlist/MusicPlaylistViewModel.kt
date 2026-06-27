package com.makd.afinity.ui.music.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.music.AfinityPlaylist
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.data.repository.download.DownloadRepository
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

data class PlaylistArtistEntry(val name: String, val imageUrl: String?)

data class MusicPlaylistUiState(
    val playlist: AfinityPlaylist? = null,
    val tracks: List<AfinityTrack> = emptyList(),
    val artistEntries: List<PlaylistArtistEntry> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val deleted: Boolean = false,
)

@HiltViewModel
class MusicPlaylistViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val downloadRepository: DownloadRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val playlistId: UUID = UUID.fromString(savedStateHandle.get<String>("playlistId")!!)

    private val _uiState = MutableStateFlow(MusicPlaylistUiState())
    val uiState: StateFlow<MusicPlaylistUiState> = _uiState.asStateFlow()

    init {
        load()
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

    fun removeTrack(track: AfinityTrack) {
        val entryId = track.playlistItemId ?: return
        val currentTracks = _uiState.value.tracks
        _uiState.update { it.copy(tracks = currentTracks.filterNot { t -> t.id == track.id }) }
        viewModelScope.launch {
            runCatching { musicRepository.removeTracksFromPlaylist(playlistId, listOf(entryId)) }
                .onFailure { _uiState.update { it.copy(tracks = currentTracks) } }
        }
    }

    fun deletePlaylist() {
        viewModelScope.launch {
            runCatching { musicRepository.deletePlaylist(playlistId) }
                .onSuccess { _uiState.update { it.copy(deleted = true) } }
                .onFailure { Timber.e(it, "Failed to delete playlist $playlistId") }
        }
    }

    fun downloadPlaylist() {
        viewModelScope.launch {
            downloadRepository.startPlaylistDownload(playlistId)
                .onFailure { Timber.e(it, "Failed to start playlist download") }
        }
    }

    fun downloadTrack(trackId: UUID) {
        viewModelScope.launch {
            downloadRepository.startDownload(trackId, "")
                .onFailure { Timber.e(it, "Failed to download track $trackId") }
        }
    }

    fun reload() {
        viewModelScope.launch {
            try {
                val tracksDeferred = async { musicRepository.getPlaylistTracks(playlistId) }
                val playlistDeferred = async { musicRepository.getPlaylistById(playlistId) }
                val tracks = tracksDeferred.await()
                _uiState.update {
                    it.copy(
                        playlist = playlistDeferred.await(),
                        tracks = tracks,
                        artistEntries = buildArtistEntries(tracks),
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to reload playlist $playlistId")
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val playlistDeferred = async { musicRepository.getPlaylistById(playlistId) }
                val tracksDeferred = async { musicRepository.getPlaylistTracks(playlistId) }
                val tracks = tracksDeferred.await()
                _uiState.update {
                    it.copy(
                        playlist = playlistDeferred.await(),
                        tracks = tracks,
                        artistEntries = buildArtistEntries(tracks),
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load playlist $playlistId")
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private suspend fun buildArtistEntries(tracks: List<AfinityTrack>): List<PlaylistArtistEntry> {
        val baseUrl = musicRepository.getBaseUrl()
        return tracks
            .filter { it.artistId != null && it.artist != null }
            .groupBy { it.artistId }
            .entries
            .sortedByDescending { it.value.size }
            .mapNotNull { (artistId, group) ->
                val name = group.first().artist ?: return@mapNotNull null
                val imageUrl = "$baseUrl/Items/$artistId/Images/Primary?fillHeight=128&quality=90"
                PlaylistArtistEntry(name = name, imageUrl = imageUrl)
            }
    }
}