package com.makd.afinity.ui.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.download.DownloadStatus
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.PlaylistEntry
import com.makd.afinity.data.models.music.AfinityPlaylist
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.download.DownloadRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.music.MusicRepository
import com.makd.afinity.util.NetworkConnectivityMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

data class PlaylistArtistEntry(val name: String, val imageUrl: String?)

data class VideoPlaybackRequest(
    val itemId: UUID,
    val mediaSourceId: String,
    val startPositionMs: Long,
    val playlistId: UUID,
    val shuffle: Boolean,
)

private fun AfinityPlaylist.withEntryTotals(entries: List<PlaylistEntry>): AfinityPlaylist =
    copy(
        songCount = entries.size,
        runtimeTicks =
            entries.sumOf {
                when (it) {
                    is PlaylistEntry.Audio -> it.track.runtimeTicks
                    is PlaylistEntry.Video -> it.item.runtimeTicks
                }
            },
    )

private fun List<PlaylistEntry>.withFavorite(
    trackId: UUID,
    favorite: Boolean,
): List<PlaylistEntry> = map { entry ->
    if (entry is PlaylistEntry.Audio && entry.track.id == trackId)
        entry.copy(track = entry.track.copy(favorite = favorite))
    else entry
}

data class PlaylistUiState(
    val playlist: AfinityPlaylist? = null,
    val entries: List<PlaylistEntry> = emptyList(),
    val artistEntries: List<PlaylistArtistEntry> = emptyList(),
    val audioCount: Int = 0,
    val videoCount: Int = 0,
    val audioOnly: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val deleted: Boolean = false,
    val playlistDownloadInfo: DownloadInfo? = null,
    val trackDownloadInfos: Map<UUID, DownloadInfo> = emptyMap(),
) {
    val tracks: List<AfinityTrack>
        get() = entries.filterIsInstance<PlaylistEntry.Audio>().map { it.track }

    val hiddenItemCount: Int
        get() = if (audioOnly) videoCount else 0

    val isMixed: Boolean
        get() = !audioOnly && audioCount > 0 && videoCount > 0

    val canReorder: Boolean
        get() = entries.size == audioCount + videoCount && entries.all { it.playlistItemId != null }
}

@HiltViewModel
class PlaylistViewModel
@Inject
constructor(
    private val musicRepository: MusicRepository,
    private val mediaRepository: MediaRepository,
    private val downloadRepository: DownloadRepository,
    private val appDataRepository: AppDataRepository,
    private val sessionManager: SessionManager,
    private val preferencesRepository: PreferencesRepository,
    private val networkMonitor: NetworkConnectivityMonitor,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val playlistId: UUID = UUID.fromString(savedStateHandle.get<String>("playlistId")!!)

    val isDownloadAllowedByServer: StateFlow<Boolean> =
        sessionManager.currentSession
            .map { it?.canDownload != false }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val canDownloadOnNetwork: StateFlow<Boolean> =
        combine(
                preferencesRepository.getDownloadWifiOnlyFlow(),
                networkMonitor.isOnWifiFlow,
            ) { wifiOnly, onWifi ->
                !wifiOnly || onWifi
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val audioOnly: Boolean = savedStateHandle.get<String>("audioOnly")?.toBoolean() == true

    private val _uiState = MutableStateFlow(PlaylistUiState(audioOnly = audioOnly))

    private val _videoPlaybackRequests = MutableSharedFlow<VideoPlaybackRequest>()
    val videoPlaybackRequests = _videoPlaybackRequests.asSharedFlow()
    val uiState: StateFlow<PlaylistUiState> = _uiState.asStateFlow()

    init {
        load()
        observeDownloads()
    }

    fun toggleTrackFavorite(trackId: UUID) {
        val currentEntries = _uiState.value.entries
        val track = _uiState.value.tracks.find { it.id == trackId } ?: return
        val newFavorite = !track.favorite
        _uiState.update { it.copy(entries = currentEntries.withFavorite(trackId, newFavorite)) }
        viewModelScope.launch {
            runCatching { musicRepository.setFavorite(trackId, newFavorite) }
                .onSuccess { appDataRepository.updateTrackFavoriteStatus(track, newFavorite) }
                .onFailure { _uiState.update { it.copy(entries = currentEntries) } }
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
                    _uiState.update {
                        it.copy(playlist = playlist.copy(favorite = playlist.favorite))
                    }
                }
        }
    }

    fun removeEntry(entry: PlaylistEntry) {
        val entryId = entry.playlistItemId ?: return
        val currentEntries = _uiState.value.entries
        val remaining = currentEntries.filterNot { it.playlistItemId == entryId }
        _uiState.update { state ->
            state.copy(
                entries = remaining,
                audioCount = remaining.count { it is PlaylistEntry.Audio },
                videoCount =
                    if (state.audioOnly) state.videoCount
                    else remaining.count { it is PlaylistEntry.Video },
                playlist = state.playlist?.withEntryTotals(remaining),
            )
        }
        viewModelScope.launch {
            runCatching { musicRepository.removeTracksFromPlaylist(playlistId, listOf(entryId)) }
                .onFailure {
                    _uiState.update { state ->
                        state.copy(
                            entries = currentEntries,
                            audioCount = currentEntries.count { it is PlaylistEntry.Audio },
                            videoCount =
                                if (state.audioOnly) state.videoCount
                                else currentEntries.count { it is PlaylistEntry.Video },
                            playlist = state.playlist?.withEntryTotals(currentEntries),
                        )
                    }
                }
        }
    }

    fun moveEntry(fromKey: String, toKey: String) {
        val current = _uiState.value.entries
        val from = current.indexOfFirst { it.playlistItemId == fromKey }
        val to = current.indexOfFirst { it.playlistItemId == toKey }
        if (from == -1 || to == -1 || from == to) return
        _uiState.update {
            it.copy(entries = current.toMutableList().apply { add(to, removeAt(from)) })
        }
    }

    fun commitEntryMove(movedKey: String) {
        val newIndex = _uiState.value.entries.indexOfFirst { it.playlistItemId == movedKey }
        if (newIndex == -1) return
        viewModelScope.launch {
            val moved = mediaRepository.movePlaylistItem(playlistId, movedKey, newIndex)
            if (!moved) {
                Timber.w("Move rejected by server, reloading playlist $playlistId")
                reload()
            }
        }
    }

    fun playVideoEntry(item: AfinityItem, shuffle: Boolean = false) {
        viewModelScope.launch {
            val sourceId =
                item.sources.firstOrNull()?.id
                    ?: mediaRepository
                        .getItem(item.id, FieldSets.PLAYABLE_EPISODE)
                        ?.mediaSources
                        ?.firstOrNull()
                        ?.id
            if (sourceId == null) {
                Timber.w("Playlist entry has no playable source: ${item.name}")
                return@launch
            }
            _videoPlaybackRequests.emit(
                VideoPlaybackRequest(
                    itemId = item.id,
                    mediaSourceId = sourceId,
                    startPositionMs =
                        if (item.playbackPositionTicks > 0) item.playbackPositionTicks / 10000
                        else 0L,
                    playlistId = playlistId,
                    shuffle = shuffle,
                )
            )
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
            downloadRepository.startPlaylistDownload(playlistId).onFailure {
                Timber.e(it, "Failed to start playlist download")
            }
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
        val entries = _uiState.value.entries
        val entryIds = entries.map { it.id }.toSet()
        val playlistDownloads = allDownloads.filter { it.itemId in entryIds }
        _uiState.update {
            it.copy(
                playlistDownloadInfo =
                    aggregatePlaylistDownloadInfo(playlistDownloads, entries.size),
                trackDownloadInfos = playlistDownloads.associateBy { info -> info.itemId },
            )
        }
    }

    private fun aggregatePlaylistDownloadInfo(
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
            downloadRepository.startDownload(trackId, "").onFailure {
                Timber.e(it, "Failed to download track $trackId")
            }
        }
    }

    fun reload() {
        viewModelScope.launch {
            try {
                val contentsDeferred = async { mediaRepository.getPlaylistEntries(playlistId) }
                val playlistDeferred = async { musicRepository.getPlaylistById(playlistId) }
                val contents = contentsDeferred.await()
                val entries = contents.entries.applyLens()
                _uiState.update {
                    it.copy(
                        playlist = playlistDeferred.await()?.withEntryTotals(entries),
                        entries = entries,
                        audioCount = contents.audioCount,
                        videoCount = contents.videoCount,
                        artistEntries = buildArtistEntries(entries),
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
                val contentsDeferred = async { mediaRepository.getPlaylistEntries(playlistId) }
                val contents = contentsDeferred.await()
                val entries = contents.entries.applyLens()
                _uiState.update {
                    it.copy(
                        playlist = playlistDeferred.await()?.withEntryTotals(entries),
                        entries = entries,
                        audioCount = contents.audioCount,
                        videoCount = contents.videoCount,
                        artistEntries = buildArtistEntries(entries),
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

    private fun List<PlaylistEntry>.applyLens(): List<PlaylistEntry> =
        if (audioOnly) filterIsInstance<PlaylistEntry.Audio>() else this

    private suspend fun buildArtistEntries(
        entries: List<PlaylistEntry>
    ): List<PlaylistArtistEntry> {
        val baseUrl = musicRepository.getBaseUrl()
        return entries
            .filterIsInstance<PlaylistEntry.Audio>()
            .map { it.track }
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
