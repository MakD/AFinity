package com.makd.afinity.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.manager.MediaChangeManager
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
import com.makd.afinity.data.repository.userdata.UserDataRepository
import com.makd.afinity.data.repository.watchlist.WatchlistRepository
import com.makd.afinity.ui.item.delegates.ItemDownloadDelegate
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
    private val mediaChangeManager: MediaChangeManager,
    private val watchlistRepository: WatchlistRepository,
    private val downloadRepository: DownloadRepository,
    private val appDataRepository: AppDataRepository,
    private val itemUserDataDelegate: ItemUserDataDelegate,
    private val itemDownloadDelegate: ItemDownloadDelegate,
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
                _uiState.value =
                    FavoritesUiState(
                        movies = data.movies,
                        shows = data.shows,
                        seasons = data.seasons,
                        episodes = data.episodes,
                        boxSets = data.boxSets,
                        channels = data.channels,
                        people = data.people,
                        isLoading = false,
                        error = null,
                    )
                lastFavoritesLoadedAt = System.currentTimeMillis()
            }
        }

        viewModelScope.launch {
            mediaChangeManager.mediaChanges.collect { event ->
                val currentState = _uiState.value

                val currentMovieIds = currentState.movies.map { it.id }
                val currentShowIds = currentState.shows.map { it.id }
                val currentSeasonIds = currentState.seasons.map { it.id }
                val currentEpisodeIds = currentState.episodes.map { it.id }
                val targetIdsToFetch = mutableSetOf<java.util.UUID>()

                if (currentMovieIds.contains(event.itemId)) targetIdsToFetch.add(event.itemId)
                if (currentShowIds.contains(event.itemId)) targetIdsToFetch.add(event.itemId)
                if (currentSeasonIds.contains(event.itemId)) targetIdsToFetch.add(event.itemId)
                if (currentEpisodeIds.contains(event.itemId)) targetIdsToFetch.add(event.itemId)
                if (event.seriesId != null && currentShowIds.contains(event.seriesId)) {
                    targetIdsToFetch.add(event.seriesId)
                }
                if (event.seasonId != null && currentSeasonIds.contains(event.seasonId)) {
                    targetIdsToFetch.add(event.seasonId)
                }

                if (targetIdsToFetch.isNotEmpty()) {
                    try {
                        val newMovies = currentState.movies.toMutableList()
                        val newShows = currentState.shows.toMutableList()
                        val newSeasons = currentState.seasons.toMutableList()
                        val newEpisodes = currentState.episodes.toMutableList()
                        var hasChanges = false

                        for (id in targetIdsToFetch) {
                            val freshItem = mediaRepository.getItemById(id) ?: continue
                            hasChanges = true

                            when (freshItem) {
                                is AfinityMovie -> {
                                    val idx = newMovies.indexOfFirst { it.id == id }
                                    if (idx != -1) newMovies[idx] = freshItem
                                }
                                is AfinityShow -> {
                                    val idx = newShows.indexOfFirst { it.id == id }
                                    if (idx != -1) newShows[idx] = freshItem
                                }
                                is AfinitySeason -> {
                                    val idx = newSeasons.indexOfFirst { it.id == id }
                                    if (idx != -1) newSeasons[idx] = freshItem
                                }
                                is AfinityEpisode -> {
                                    val idx = newEpisodes.indexOfFirst { it.id == id }
                                    if (idx != -1) newEpisodes[idx] = freshItem
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
                    } catch (e: Exception) {
                        Timber.e(
                            e,
                            "Failed to resolve items for granular update in favorites: $targetIdsToFetch",
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

    fun onDownloadClick() {
        _selectedEpisode.value?.let { episode ->
            itemDownloadDelegate.onDownloadClick(viewModelScope, episode) {}
        }
    }

    fun pauseDownload() =
        itemDownloadDelegate.pauseDownload(viewModelScope, _selectedEpisodeDownloadInfo.value)

    fun resumeDownload() =
        itemDownloadDelegate.resumeDownload(viewModelScope, _selectedEpisodeDownloadInfo.value)

    fun cancelDownload() =
        itemDownloadDelegate.cancelDownload(viewModelScope, _selectedEpisodeDownloadInfo.value)
}

data class FavoritesUiState(
    val movies: List<AfinityMovie> = emptyList(),
    val shows: List<AfinityShow> = emptyList(),
    val seasons: List<AfinitySeason> = emptyList(),
    val episodes: List<AfinityEpisode> = emptyList(),
    val boxSets: List<AfinityBoxSet> = emptyList(),
    val people: List<com.makd.afinity.data.models.media.AfinityPersonDetail> = emptyList(),
    val channels: List<com.makd.afinity.data.models.livetv.AfinityChannel> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)
