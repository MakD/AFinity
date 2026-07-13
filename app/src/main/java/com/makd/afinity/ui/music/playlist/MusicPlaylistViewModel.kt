package com.makd.afinity.ui.music.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.download.DownloadStatus
import com.makd.afinity.data.models.music.AfinityPlaylist
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

data class PlaylistArtistEntry(val name: String, val imageUrl: String?)

data class MusicPlaylistUiState(
    val playlist: AfinityPlaylist? = null,
    val tracks: List<AfinityTrack> = emptyList(),
    val artistEntries: List<PlaylistArtistEntry> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val deleted: Boolean = false,
    val playlistDownloadInfo: DownloadInfo? = null,
    val trackDownloadInfos: Map<UUID, DownloadInfo> = emptyMap(),
)

@HiltViewModel
class MusicPlaylistViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val downloadRepository: DownloadRepository,
    private val appDataRepository: AppDataRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val playlistId: UUID = UUID.fromString(savedStateHandle.get<String>("playlistId")!!)

    private val _uiState = MutableStateFlow(MusicPlaylistUiState())
    val uiState: StateFlow<MusicPlaylistUiState> = _uiState.asStateFlow()

    init {
        load()
        observeDownloads()
    }

    fun toggleTrackFavorite(trackId: UUID) {
        val tracks = _uiState.value.tracks
        val track = tracks.find { it.id == trackId } ?: return
        val newFavorite = !track.favorite
        _uiState.update { it.copy(tracks = tracks.map { t -> if (t.id == trackId) t.copy(favorite = newFavorite) else t }) }
        viewModelScope.launch {
            runCatching { musicRepository.setFavorite(trackId, newFavorite) }
                .onSuccess { appDataRepository.updateTrackFavoriteStatus(track, newFavorite) }
                .onFailure { _uiState.update { it.copy(tracks = tracks) } }
        }
    }

    fun toggleFavorite() {
        val playlist = _uiState.value.playlist ?: return
        val newFavorite = !playlist.favorite
        _uiState.update { it.copy(playlist = playlist.copy(favorite = newFavorite)) }
        viewModelScope.launch {
            runCatching { musicRepository.setFavorite(playlist.id, newFavorite) }
                .onSuccess { appDataRepository.updatePlaylistFavoriteStatus(playlist, newFavorite) }
                .onFailure {
                    _uiState.update { it.copy(playlist = playlist.copy(favorite = playlist.favorite)) }
                }
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

    fun cancelPlaylistDownload() {
        viewModelScope.launch {
            _uiState.value.trackDownloadInfos.values.forEach {
                downloadRepository.cancelDownload(it.id)
            }
        }
    }

    private var lastAllDownloads: List<DownloadInfo> = emptyList()

    private fun observeDownloads() {
        viewModelScope.launch {
            downloadRepository.getAllDownloadsFlow().collect { allDownloads ->
                lastAllDownloads = allDownloads
                updateDownloadState(allDownloads)
            }
        }
    }

    private fun updateDownloadState(allDownloads: List<DownloadInfo>) {
        val playlistTrackIds = _uiState.value.tracks.map { it.id }.toSet()
        val audioDownloads = allDownloads.filter { it.itemType == "Audio" }
        val playlistDownloads = audioDownloads.filter { it.itemId in playlistTrackIds }
        val totalTracks = _uiState.value.tracks.size
        _uiState.update {
            it.copy(
                playlistDownloadInfo = aggregatePlaylistDownloadInfo(playlistDownloads, totalTracks),
                trackDownloadInfos = audioDownloads.associateBy { it.itemId },
            )
        }
    }

    private fun aggregatePlaylistDownloadInfo(downloads: List<DownloadInfo>, totalTracks: Int): DownloadInfo? {
        if (downloads.isEmpty()) return null
        val hasActive = downloads.any {
            it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
        }
        val allComplete = downloads.all { it.status == DownloadStatus.COMPLETED }
        if (!hasActive && !(allComplete && totalTracks > 0 && downloads.size >= totalTracks)) return null
        val status =
            when {
                downloads.any { it.status == DownloadStatus.DOWNLOADING } ->
                    DownloadStatus.DOWNLOADING
                downloads.any { it.status == DownloadStatus.QUEUED } -> DownloadStatus.QUEUED
                downloads.all { it.status == DownloadStatus.COMPLETED } -> DownloadStatus.COMPLETED
                downloads.any { it.status == DownloadStatus.FAILED } -> DownloadStatus.FAILED
                else -> DownloadStatus.PAUSED
            }
        val first = downloads.first()
        return DownloadInfo(
            id = playlistId,
            itemId = playlistId,
            itemName = _uiState.value.playlist?.name ?: first.itemName,
            itemType = "Playlist",
            sourceId = "",
            sourceName = "",
            status = status,
            progress = downloads.map { it.progress }.average().toFloat(),
            bytesDownloaded = downloads.sumOf { it.bytesDownloaded },
            totalBytes = downloads.sumOf { it.totalBytes },
            filePath = null,
            error = null,
            createdAt = downloads.minOf { it.createdAt },
            updatedAt = downloads.maxOf { it.updatedAt },
            serverId = first.serverId,
            userId = first.userId,
        )
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
                updateDownloadState(lastAllDownloads)
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