package com.makd.afinity.ui.music.artist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.manager.AdminChangeBroadcaster
import com.makd.afinity.data.manager.DownloadPermissions
import com.makd.afinity.data.manager.OfflineModeManager
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.music.AfinityAlbum
import com.makd.afinity.data.models.music.AfinityArtist
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.data.network.HotTracks
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.download.DownloadRepository
import com.makd.afinity.data.repository.lastfm.ArtistPopularity
import com.makd.afinity.data.repository.lastfm.LastFmRepository
import com.makd.afinity.data.repository.lastfm.PopularRanking
import com.makd.afinity.data.repository.music.MusicRepository
import com.makd.afinity.data.store.ItemStore
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

data class MusicArtistUiState(
    val artist: AfinityArtist? = null,
    val topTracks: List<AfinityTrack> = emptyList(),
    val albums: List<AfinityAlbum> = emptyList(),
    val appearsOn: List<AfinityAlbum> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val trackDownloadInfos: Map<UUID, DownloadInfo> = emptyMap(),
    val popularFromLastFm: Boolean = false,
    val hotTrackIds: Set<UUID> = emptySet(),
)

@HiltViewModel
class MusicArtistViewModel
@Inject
constructor(
    private val musicRepository: MusicRepository,
    private val downloadRepository: DownloadRepository,
    private val adminChangeBroadcaster: AdminChangeBroadcaster,
    private val appDataRepository: AppDataRepository,
    private val downloadPermissions: DownloadPermissions,
    private val itemStore: ItemStore,
    private val lastFmRepository: LastFmRepository,
    private val offlineModeManager: OfflineModeManager,
    private val preferencesRepository: PreferencesRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val artistId: UUID = UUID.fromString(savedStateHandle.get<String>("artistId")!!)

    val isDownloadAllowedByServer: StateFlow<Boolean> = downloadPermissions.isAllowedByServer

    val canDownloadOnNetwork: StateFlow<Boolean> = downloadPermissions.isAllowedOnNetwork

    private val _uiState = MutableStateFlow(MusicArtistUiState())
    val uiState: StateFlow<MusicArtistUiState> = _uiState.asStateFlow()

    private var popularityJob: Job? = null
    private var ranking: PopularRanking? = null
    private var playCountOrder: List<AfinityTrack> = emptyList()
    private var sortByLastFm = true

    init {
        load()
        observeDownloads()

        viewModelScope.launch {
            offlineModeManager.isOffline
                .drop(1)
                .filter { !it }
                .collect {
                    val state = _uiState.value
                    state.artist?.let { observeLastFm(it.name, state.albums) }
                }
        }

        viewModelScope.launch {
            preferencesRepository
                .getLastFmPopularSortFlow()
                .distinctUntilChanged()
                .drop(1)
                .collect { enabled ->
                    sortByLastFm = enabled
                    popularityJob?.cancel()
                    _uiState.update {
                        if (enabled) it.copy(hotTrackIds = emptySet())
                        else
                            it.copy(
                                topTracks = itemStore.mergeOwners(playCountOrder),
                                popularFromLastFm = false,
                            )
                    }
                    val state = _uiState.value
                    state.artist?.let { observeLastFm(it.name, state.albums) }
                }
        }

        viewModelScope.launch {
            itemStore.overlay.collect { overlay ->
                if (overlay.isEmpty()) return@collect
                _uiState.update { state ->
                    val topTracks = itemStore.mergeOwners(state.topTracks)
                    val albums = itemStore.mergeOwners(state.albums)
                    val appearsOn = itemStore.mergeOwners(state.appearsOn)
                    val artist = state.artist?.let { itemStore.mergeOwners(listOf(it)).first() }
                    if (
                        topTracks === state.topTracks &&
                            albums === state.albums &&
                            appearsOn === state.appearsOn &&
                            artist === state.artist
                    ) {
                        state
                    } else {
                        state.copy(
                            topTracks = topTracks,
                            albums = albums,
                            appearsOn = appearsOn,
                            artist = artist,
                        )
                    }
                }
            }
        }
    }

    private fun observeDownloads() {
        viewModelScope.launch {
            downloadRepository.getAllDownloadsFlow().collect { allDownloads ->
                _uiState.update {
                    it.copy(
                        trackDownloadInfos =
                            allDownloads
                                .filter { d -> d.itemType == "Audio" }
                                .associateBy { d -> d.itemId }
                    )
                }
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val artistDeferred = async { musicRepository.getArtistById(artistId) }
                val tracksDeferred = async {
                    musicRepository.getArtistTopTracks(artistId, limit = POPULAR_COUNT)
                }
                val albumsDeferred = async { musicRepository.getArtistAlbums(artistId) }
                val appearsDeferred = async { musicRepository.getArtistAppearsOn(artistId) }
                val sortDeferred = async {
                    preferencesRepository.getLastFmPopularSortFlow().first()
                }
                val rankingDeferred = async {
                    if (sortDeferred.await()) lastFmRepository.cachedRanking(artistId) else null
                }
                val rankedDeferred = async {
                    val cached = rankingDeferred.await() ?: return@async emptyList<AfinityTrack>()
                    val byId = musicRepository.getTracksByIds(cached.trackIds).associateBy { it.id }
                    cached.trackIds.mapNotNull { byId[it] }
                }

                val artist = artistDeferred.await()
                val topTracks = tracksDeferred.await()
                val albums = albumsDeferred.await()
                val appearsOn = appearsDeferred.await()
                val ranked = rankedDeferred.await()
                ranking = rankingDeferred.await()
                sortByLastFm = sortDeferred.await()
                playCountOrder = topTracks
                val popular =
                    if (ranked.isEmpty()) topTracks
                    else (ranked + topTracks).distinctBy { it.id }.take(POPULAR_COUNT)
                itemStore.putIfAbsent(popular + albums + appearsOn + listOfNotNull(artist))
                _uiState.update {
                    it.copy(
                        artist = artist?.let { a -> itemStore.mergeOwners(listOf(a)).first() },
                        topTracks = itemStore.mergeOwners(popular),
                        albums = itemStore.mergeOwners(albums),
                        appearsOn = itemStore.mergeOwners(appearsOn),
                        popularFromLastFm = ranked.isNotEmpty(),
                        isLoading = false,
                    )
                }
                artist?.let { observeLastFm(it.name, albums) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to load artist $artistId")
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun observeLastFm(artistName: String, albums: List<AfinityAlbum>) {
        if (sortByLastFm) observePopularity(artistName, albums) else observeHits(artistName, albums)
    }

    private fun observeHits(artistName: String, albums: List<AfinityAlbum>) {
        popularityJob?.cancel()
        popularityJob = viewModelScope.launch {
            lastFmRepository
                .popularity(
                    artistName,
                    allowNetwork = offlineModeManager.isInternetAvailable(),
                    freshForMs = LastFmRepository.freshnessFor(albums.map { it.productionYear }),
                )
                .collect { popularity ->
                    val shown = _uiState.value.topTracks
                    val hot =
                        withContext(Dispatchers.Default) {
                            HotTracks.hits(shown, { it.name }, popularity.index)
                                .map { it.id }
                                .toSet()
                        }
                    _uiState.update { it.copy(hotTrackIds = hot) }
                }
        }
    }

    private fun observePopularity(artistName: String, albums: List<AfinityAlbum>) {
        popularityJob?.cancel()
        popularityJob = viewModelScope.launch {
            var trackCount: Int? = null
            var countLoaded = false
            lastFmRepository
                .popularity(
                    artistName,
                    allowNetwork = offlineModeManager.isInternetAvailable(),
                    freshForMs = LastFmRepository.freshnessFor(albums.map { it.productionYear }),
                )
                .collect { popularity ->
                    if (!countLoaded) {
                        trackCount = musicRepository.getArtistTrackCount(artistId)
                        countLoaded = true
                    }
                    val current = ranking
                    val upToDate =
                        current != null &&
                            current.lastFmVersion == popularity.version &&
                            (trackCount == null || current.trackCount == trackCount)
                    if (upToDate && _uiState.value.popularFromLastFm) return@collect
                    rebuildPopular(popularity, trackCount)
                }
        }
    }

    private suspend fun rebuildPopular(popularity: ArtistPopularity, trackCount: Int?) {
        val pool = musicRepository.getArtistTopTracks(artistId, limit = POPULARITY_POOL_SIZE)
        val ranked =
            withContext(Dispatchers.Default) {
                HotTracks.rank(
                    pool.sortedWith(
                        compareByDescending<AfinityTrack> { it.playCount ?: 0 }
                            .thenBy { it.productionYear ?: Int.MAX_VALUE }
                    ),
                    { it.name },
                    popularity.index,
                )
            }
        if (ranked.isEmpty()) return
        val popular = (ranked + playCountOrder).distinctBy { it.id }.take(POPULAR_COUNT)
        itemStore.putIfAbsent(popular)
        _uiState.update {
            it.copy(topTracks = itemStore.mergeOwners(popular), popularFromLastFm = true)
        }
        if (trackCount != null) {
            val updated =
                PopularRanking(
                    trackIds = ranked.take(POPULAR_COUNT).map { it.id },
                    trackCount = trackCount,
                    lastFmVersion = popularity.version,
                )
            ranking = updated
            lastFmRepository.saveRanking(artistId, updated)
        }
    }

    fun toggleTrackFavorite(trackId: UUID) {
        val tracks = _uiState.value.topTracks
        val track = tracks.find { it.id == trackId } ?: return
        val newFavorite = !track.favorite
        _uiState.update {
            it.copy(
                topTracks =
                    tracks.map { t -> if (t.id == trackId) t.copy(favorite = newFavorite) else t }
            )
        }
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
                .onFailure {
                    _uiState.update { it.copy(artist = artist.copy(favorite = artist.favorite)) }
                }
        }
    }

    fun downloadArtist() {
        viewModelScope.launch {
            downloadRepository.startArtistDownload(artistId).onFailure {
                Timber.e(it, "Failed to initMonitoring artist download")
            }
        }
    }

    fun downloadAlbum(albumId: UUID) {
        viewModelScope.launch {
            downloadRepository.startAlbumDownload(albumId).onFailure {
                Timber.e(it, "Failed to initMonitoring album download for $albumId")
            }
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

    private companion object {
        const val POPULAR_COUNT = 10
        const val POPULARITY_POOL_SIZE = 500
    }
}
