package com.makd.afinity.ui.music.album

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.manager.AdminChangeBroadcaster
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.download.DownloadStatus
import com.makd.afinity.data.models.music.AfinityAlbum
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

data class MusicAlbumUiState(
    val album: AfinityAlbum? = null,
    val tracks: List<AfinityTrack> = emptyList(),
    val artistImageUrl: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val albumDownloadInfo: DownloadInfo? = null,
    val trackDownloadInfos: Map<UUID, DownloadInfo> = emptyMap(),
)

@HiltViewModel
class MusicAlbumViewModel
@Inject
constructor(
    private val musicRepository: MusicRepository,
    private val downloadRepository: DownloadRepository,
    private val adminChangeBroadcaster: AdminChangeBroadcaster,
    private val appDataRepository: AppDataRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val albumId: UUID = UUID.fromString(savedStateHandle.get<String>("albumId")!!)

    private val _uiState = MutableStateFlow(MusicAlbumUiState())
    val uiState: StateFlow<MusicAlbumUiState> = _uiState.asStateFlow()

    init {
        load()
        observeDownloads()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val albumDeferred = async { musicRepository.getAlbumById(albumId) }
                val tracksDeferred = async { musicRepository.getAlbumTracks(albumId) }
                val album = albumDeferred.await()
                val tracks = tracksDeferred.await()
                val artistImageUrl =
                    album?.artistId?.let {
                        "${musicRepository.getBaseUrl()}/Items/$it/Images/Primary?fillHeight=128&quality=90"
                    }
                _uiState.update {
                    it.copy(
                        album = album,
                        tracks = tracks,
                        artistImageUrl = artistImageUrl,
                        isLoading = false,
                    )
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
                .onSuccess {
                    appDataRepository.updateAlbumFavoriteStatus(album, newFavorite)
                    adminChangeBroadcaster.notifyItemChanged(album.id.toString())
                }
                .onFailure {
                    _uiState.update { it.copy(album = album.copy(favorite = album.favorite)) }
                }
        }
    }

    fun toggleTrackFavorite(trackId: UUID) {
        val tracks = _uiState.value.tracks
        val track = tracks.find { it.id == trackId } ?: return
        val newFavorite = !track.favorite
        _uiState.update {
            it.copy(
                tracks =
                    tracks.map { t -> if (t.id == trackId) t.copy(favorite = newFavorite) else t }
            )
        }
        viewModelScope.launch {
            runCatching { musicRepository.setFavorite(trackId, newFavorite) }
                .onSuccess { appDataRepository.updateTrackFavoriteStatus(track, newFavorite) }
                .onFailure { _uiState.update { it.copy(tracks = tracks) } }
        }
    }

    private fun observeDownloads() {
        viewModelScope.launch {
            downloadRepository.getAllDownloadsFlow().collect { allDownloads ->
                val albumTracks = allDownloads.filter { it.seriesId == albumId.toString() }
                val totalTracks = _uiState.value.tracks.size
                val albumInfo = aggregateAlbumDownloadInfo(albumTracks, totalTracks)
                val trackMap =
                    allDownloads.filter { it.itemType == "Audio" }.associateBy { it.itemId }
                _uiState.update {
                    it.copy(albumDownloadInfo = albumInfo, trackDownloadInfos = trackMap)
                }
            }
        }
    }

    private fun aggregateAlbumDownloadInfo(
        downloads: List<DownloadInfo>,
        totalTracks: Int,
    ): DownloadInfo? {
        if (downloads.isEmpty()) return null
        val hasActive = downloads.any {
            it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
        }
        val allComplete = downloads.all { it.status == DownloadStatus.COMPLETED }
        if (!hasActive && !(allComplete && totalTracks > 0 && downloads.size >= totalTracks))
            return null
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
            id = albumId,
            itemId = albumId,
            itemName = first.seriesName ?: first.itemName,
            itemType = "Album",
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

    fun downloadAlbum() {
        viewModelScope.launch {
            downloadRepository.startAlbumDownload(albumId).onFailure {
                Timber.e(it, "Failed to start album download")
            }
        }
    }

    fun cancelAlbumDownload() {
        val info = _uiState.value.albumDownloadInfo ?: return
        viewModelScope.launch {
            _uiState.value.trackDownloadInfos.values
                .filter { it.seriesId == albumId.toString() }
                .forEach { downloadRepository.cancelDownload(it.id) }
        }
    }

    fun downloadTrack(trackId: UUID) {
        viewModelScope.launch {
            downloadRepository.startDownload(trackId, "").onFailure {
                Timber.e(it, "Failed to download track $trackId")
            }
        }
    }

    fun cancelTrackDownload(trackId: UUID) {
        val info = _uiState.value.trackDownloadInfos[trackId] ?: return
        viewModelScope.launch { downloadRepository.cancelDownload(info.id) }
    }

    fun refresh() = load()
}
