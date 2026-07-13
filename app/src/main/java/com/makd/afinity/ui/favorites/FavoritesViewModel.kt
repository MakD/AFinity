package com.makd.afinity.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.manager.AdminChangeBroadcaster
import com.makd.afinity.data.manager.MediaChangeManager
import com.makd.afinity.data.manager.resolveChangedItems
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.media.AfinityBoxSet
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.media.toAfinityEpisode
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.download.DownloadRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.music.MusicRepository
import com.makd.afinity.data.repository.userdata.UserDataRepository
import com.makd.afinity.data.repository.watchlist.WatchlistRepository
import com.makd.afinity.ui.item.delegates.ItemUserDataDelegate
import com.makd.afinity.util.NetworkConnectivityMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class FavoritesViewModel
@Inject
constructor(
    private val userDataRepository: UserDataRepository,
    private val mediaRepository: MediaRepository,
    private val musicRepository: MusicRepository,
    private val adminChangeBroadcaster: AdminChangeBroadcaster,
    private val mediaChangeManager: MediaChangeManager,
    private val watchlistRepository: WatchlistRepository,
    private val downloadRepository: DownloadRepository,
    private val appDataRepository: AppDataRepository,
    private val itemUserDataDelegate: ItemUserDataDelegate,
    private val preferencesRepository: PreferencesRepository,
    private val networkMonitor: NetworkConnectivityMonitor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoritesUiState())
    private var lastFavoritesLoadedAt = 0L

    val canDownload: StateFlow<Boolean> =
        preferencesRepository
            .getDownloadWifiOnlyFlow()
            .combine(networkMonitor.isOnWifiFlow) { wifiOnly, onWifi -> !wifiOnly || onWifi }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val uiState: StateFlow<FavoritesUiState> = _uiState.asStateFlow()

    private val _selectedEpisode = MutableStateFlow<AfinityEpisode?>(null)
    val selectedEpisode: StateFlow<AfinityEpisode?> = _selectedEpisode.asStateFlow()

    private val _isLoadingEpisode = MutableStateFlow(false)
    val isLoadingEpisode: StateFlow<Boolean> = _isLoadingEpisode.asStateFlow()

    private val _selectedEpisodeWatchlistStatus = MutableStateFlow(false)
    val selectedEpisodeWatchlistStatus: StateFlow<Boolean> =
        _selectedEpisodeWatchlistStatus.asStateFlow()

    private val _selectedEpisodeDownloadInfo = MutableStateFlow<DownloadInfo?>(null)
    val selectedEpisodeDownloadInfo: StateFlow<DownloadInfo?> =
        _selectedEpisodeDownloadInfo.asStateFlow()

    init {
        viewModelScope.launch {
            appDataRepository.favoritesData.collect { data ->
                _uiState.update {
                    it.copy(
                        movies = data.movies,
                        shows = data.shows,
                        seasons = data.seasons,
                        episodes = data.episodes,
                        boxSets = data.boxSets,
                        channels = data.channels,
                        people = data.people,
                        favoriteAlbums = data.favoriteAlbums,
                        favoriteArtists = data.favoriteArtists,
                        favoriteTracks = data.favoriteTracks,
                        favoritePlaylists = data.favoritePlaylists,
                        isLoading = false,
                        error = null,
                    )
                }
                lastFavoritesLoadedAt = System.currentTimeMillis()
            }
        }

        viewModelScope.launch {
            adminChangeBroadcaster.itemChanged.collect { loadFavorites() }
        }

        viewModelScope.launch {
            mediaChangeManager.mediaChanges.collect { event ->
                val currentState = _uiState.value
                val displayedIds = buildSet {
                    currentState.movies.forEach { add(it.id) }
                    currentState.shows.forEach { add(it.id) }
                    currentState.seasons.forEach { add(it.id) }
                    currentState.episodes.forEach { add(it.id) }
                }

                val resolved = event.resolveChangedItems(mediaRepository, displayedIds)
                if (resolved.isEmpty()) return@collect

                val newMovies = currentState.movies.toMutableList()
                val newShows = currentState.shows.toMutableList()
                val newSeasons = currentState.seasons.toMutableList()
                val newEpisodes = currentState.episodes.toMutableList()
                var hasChanges = false

                for (freshItem in resolved) {
                    when (freshItem) {
                        is AfinityMovie -> {
                            val idx = newMovies.indexOfFirst { it.id == freshItem.id }
                            if (idx != -1) {
                                newMovies[idx] = freshItem
                                hasChanges = true
                            }
                        }
                        is AfinityShow -> {
                            val idx = newShows.indexOfFirst { it.id == freshItem.id }
                            if (idx != -1) {
                                newShows[idx] = freshItem
                                hasChanges = true
                            }
                        }
                        is AfinitySeason -> {
                            val idx = newSeasons.indexOfFirst { it.id == freshItem.id }
                            if (idx != -1) {
                                newSeasons[idx] = freshItem
                                hasChanges = true
                            }
                        }
                        is AfinityEpisode -> {
                            val idx = newEpisodes.indexOfFirst { it.id == freshItem.id }
                            if (idx != -1) {
                                newEpisodes[idx] = freshItem
                                hasChanges = true
                            }
                        }
                        else -> {}
                    }
                }

                if (hasChanges) {
                    _uiState.update {
                        it.copy(
                            movies = newMovies,
                            shows = newShows,
                            seasons = newSeasons,
                            episodes = newEpisodes,
                        )
                    }
                }
            }
        }
    }

    fun onScreenResumed() {
        if (appDataRepository.lastUserDataChangedAt.value > lastFavoritesLoadedAt) {
            lastFavoritesLoadedAt = System.currentTimeMillis()
        }
    }

    fun loadFavorites() {
        viewModelScope.launch {
            val currentData = _uiState.value
            val hasData =
                currentData.movies.isNotEmpty() ||
                    currentData.shows.isNotEmpty() ||
                    currentData.episodes.isNotEmpty()
            if (!hasData) {
                appDataRepository.reloadFavorites()
            }
        }
    }

    fun onItemClick(item: AfinityItem) {
        Timber.d("Favorite item clicked: ${item.name} (${item.id})")
    }

    fun selectEpisode(episode: AfinityEpisode) {
        viewModelScope.launch {
            try {
                _isLoadingEpisode.value = true

                val fullEpisode =
                    mediaRepository
                        .getItem(episode.id, fields = FieldSets.ITEM_DETAIL)
                        ?.toAfinityEpisode(mediaRepository.getBaseUrl(), null)

                _selectedEpisode.value = fullEpisode ?: episode

                val isInWatchlist = watchlistRepository.isInWatchlist(episode.id)
                _selectedEpisodeWatchlistStatus.value = isInWatchlist

                val episodeDownload = downloadRepository.getDownloadByItemId(episode.id)
                _selectedEpisodeDownloadInfo.value = episodeDownload

                _isLoadingEpisode.value = false
            } catch (e: Exception) {
                Timber.e(e, "Failed to load full episode details")
                _selectedEpisode.value = episode
                _isLoadingEpisode.value = false
            }
        }
    }

    fun clearSelectedEpisode() {
        _selectedEpisode.value = null
        _selectedEpisodeWatchlistStatus.value = false
        _selectedEpisodeDownloadInfo.value = null
    }

    fun toggleTrackFavorite(track: com.makd.afinity.data.models.music.AfinityTrack) {
        val tracks = _uiState.value.favoriteTracks
        val newFavorite = !track.favorite
        _uiState.update { state ->
            if (newFavorite) {
                state.copy(favoriteTracks = (tracks.filterNot { it.id == track.id } + track.copy(favorite = true)).sortedBy { it.name })
            } else {
                state.copy(favoriteTracks = tracks.filterNot { it.id == track.id })
            }
        }
        viewModelScope.launch {
            runCatching { musicRepository.setFavorite(track.id, newFavorite) }
                .onSuccess { appDataRepository.updateTrackFavoriteStatus(track, newFavorite) }
                .onFailure { _uiState.update { it.copy(favoriteTracks = tracks) } }
        }
    }

    fun toggleEpisodeFavorite(episode: AfinityEpisode) {
        itemUserDataDelegate.toggleEpisodeFavorite(viewModelScope, episode) {
            _selectedEpisode.value = episode.copy(favorite = !episode.favorite)
        }
    }

    fun toggleEpisodeWatchlist(episode: AfinityEpisode) {
        val isLiked = _selectedEpisodeWatchlistStatus.value
        itemUserDataDelegate.toggleWatchlist(
            scope = viewModelScope,
            item = episode,
            updateOptimisticUI = {
                _selectedEpisodeWatchlistStatus.value = !isLiked
                _selectedEpisode.value = _selectedEpisode.value?.copy(liked = !isLiked)
            },
            revertUI = {
                _selectedEpisodeWatchlistStatus.value = isLiked
                _selectedEpisode.value = _selectedEpisode.value?.copy(liked = isLiked)
            },
        )
    }

    fun toggleEpisodeWatched(episode: AfinityEpisode) {
        viewModelScope.launch {
            val isNowPlayed = !episode.played
            _selectedEpisode.value = episode.copy(played = isNowPlayed, playbackPositionTicks = 0)

            val success =
                if (episode.played) {
                    userDataRepository.markUnwatched(episode.id)
                } else {
                    userDataRepository.markWatched(episode.id)
                }

            if (!success) {
                _selectedEpisode.value = episode
            }
        }
    }
}

data class FavoritesUiState(
    val movies: List<AfinityMovie> = emptyList(),
    val shows: List<AfinityShow> = emptyList(),
    val seasons: List<AfinitySeason> = emptyList(),
    val episodes: List<AfinityEpisode> = emptyList(),
    val boxSets: List<AfinityBoxSet> = emptyList(),
    val people: List<com.makd.afinity.data.models.media.AfinityPersonDetail> = emptyList(),
    val channels: List<com.makd.afinity.data.models.livetv.AfinityChannel> = emptyList(),
    val favoriteAlbums: List<com.makd.afinity.data.models.music.AfinityAlbum> = emptyList(),
    val favoriteArtists: List<com.makd.afinity.data.models.music.AfinityArtist> = emptyList(),
    val favoriteTracks: List<com.makd.afinity.data.models.music.AfinityTrack> = emptyList(),
    val favoritePlaylists: List<com.makd.afinity.data.models.music.AfinityPlaylist> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)
