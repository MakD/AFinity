package com.makd.afinity.ui.music.artist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.manager.AdminChangeBroadcaster
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.music.AfinityAlbum
import com.makd.afinity.data.models.music.AfinityArtist
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.data.repository.AppDataRepository
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

data class MusicArtistUiState(
    val artist: AfinityArtist? = null,
    val topTracks: List<AfinityTrack> = emptyList(),
    val albums: List<AfinityAlbum> = emptyList(),
    val appearsOn: List<AfinityAlbum> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val trackDownloadInfos: Map<UUID, DownloadInfo> = emptyMap(),
)

@HiltViewModel
class MusicArtistViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val downloadRepository: DownloadRepository,
    private val adminChangeBroadcaster: AdminChangeBroadcaster,
    private val appDataRepository: AppDataRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val artistId: UUID = UUID.fromString(savedStateHandle.get<String>("artistId")!!)

    private val _uiState = MutableStateFlow(MusicArtistUiState())
    val uiState: StateFlow<MusicArtistUiState> = _uiState.asStateFlow()

    init {
        load()
        observeDownloads()
    }

    private fun observeDownloads() {
        viewModelScope.launch {
            downloadRepository.getAllDownloadsFlow().collect { allDownloads ->
                _uiState.update {
                    it.copy(trackDownloadInfos = allDownloads.filter { d -> d.itemType == "Audio" }.associateBy { d -> d.itemId })
                }
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val artistDeferred = async { musicRepository.getArtistById(artistId) }
                val tracksDeferred = async { musicRepository.getArtistTopTracks(artistId, limit = 10) }
                val albumsDeferred = async { musicRepository.getArtistAlbums(artistId) }
                val appearsDeferred = async { musicRepository.getArtistAppearsOn(artistId) }

                _uiState.update {
                    it.copy(
                        artist = artistDeferred.await(),
                        topTracks = tracksDeferred.await(),
                        albums = albumsDeferred.await(),
                        appearsOn = appearsDeferred.await(),
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load artist $artistId")
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun toggleTrackFavorite(trackId: UUID) {
        val tracks = _uiState.value.topTracks
        val track = tracks.find { it.id == trackId } ?: return
        val newFavorite = !track.favorite
        _uiState.update { it.copy(topTracks = tracks.map { t -> if (t.id == trackId) t.copy(favorite = newFavorite) else t }) }
        viewModelScope.launch {
            runCatching { musicRepository.setFavorite(trackId, newFavorite) }
                .onSuccess { appDataRepository.updateTrackFavoriteStatus(track, newFavorite) }
                .onFailure { _uiState.update { it.copy(topTracks = tracks) } }
        }
    }

    fun toggleFavorite() {
        val artist = _uiState.value.artist ?: return
        val newFavorite = !artist.favorite
        _uiState.update { it.copy(artist = artist.copy(favorite = newFavorite)) }
        viewModelScope.launch {
            runCatching { musicRepository.setFavorite(artist.id, newFavorite) }
                .onSuccess {
                    appDataRepository.updateArtistFavoriteStatus(artist, newFavorite)
                    adminChangeBroadcaster.notifyItemChanged(artist.id.toString())
                }
                .onFailure { _uiState.update { it.copy(artist = artist.copy(favorite = artist.favorite)) } }
        }
    }

    fun downloadArtist() {
        viewModelScope.launch {
            downloadRepository.startArtistDownload(artistId)
                .onFailure { Timber.e(it, "Failed to start artist download") }
        }
    }

    fun downloadAlbum(albumId: UUID) {
        viewModelScope.launch {
            downloadRepository.startAlbumDownload(albumId)
                .onFailure { Timber.e(it, "Failed to start album download for $albumId") }
        }
    }

    fun downloadTrack(trackId: UUID) {
        viewModelScope.launch {
            downloadRepository.startDownload(trackId, "")
                .onFailure { Timber.e(it, "Failed to download track $trackId") }
        }
    }
}