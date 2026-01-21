package com.makd.afinity.ui.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.manager.PlaybackEvent
import com.makd.afinity.data.manager.PlaybackStateManager
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.media.toAfinityEpisode
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.JellyfinRepository
import com.makd.afinity.data.repository.download.DownloadRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.userdata.UserDataRepository
import com.makd.afinity.data.repository.watchlist.WatchlistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class WatchlistViewModel @Inject constructor(
    private val jellyfinRepository: JellyfinRepository,
    private val watchlistRepository: WatchlistRepository,
    private val userDataRepository: UserDataRepository,
    private val downloadRepository: DownloadRepository,
    private val appDataRepository: AppDataRepository,
    private val mediaRepository: MediaRepository,
    private val playbackStateManager: PlaybackStateManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(WatchlistUiState())
    val uiState: StateFlow<WatchlistUiState> = _uiState.asStateFlow()

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
            appDataRepository.isInitialDataLoaded.collect { isLoaded ->
                if (isLoaded) {
                    loadWatchlist()
                } else {
                    _uiState.value = WatchlistUiState()
                    clearSelectedEpisode()
                }
            }
        }
        viewModelScope.launch {
            playbackStateManager.playbackEvents.collect { event ->
                if (event is PlaybackEvent.Synced) {
                    loadWatchlist()
                }
            }
        }
    }

    fun loadWatchlist() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)

                coroutineScope {
                    val moviesDeferred = async { watchlistRepository.getWatchlistMovies() }
                    val showsDeferred = async { watchlistRepository.getWatchlistShows() }
                    val seasonsDeferred = async { watchlistRepository.getWatchlistSeasons() }
                    val episodesDeferred = async { watchlistRepository.getWatchlistEpisodes() }

                    val movies = moviesDeferred.await()
                    val shows = showsDeferred.await()
                    val seasons = seasonsDeferred.await()
                    val episodes = episodesDeferred.await()

                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        movies = movies.sortedBy { it.name },
                        shows = shows.sortedBy { it.name },
                        seasons = seasons.sortedBy { it.name },
                        episodes = episodes.sortedBy { it.name },
                        error = null,
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load watchlist")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load watchlist: ${e.message}"
                )
            }
        }
    }


    fun onItemClick(item: AfinityItem) {
        Timber.d("Watchlist item clicked: ${item.name} (${item.id})")
    }

    fun selectEpisode(episode: AfinityEpisode) {
        viewModelScope.launch {
            try {
                _isLoadingEpisode.value = true

                val fullEpisode = jellyfinRepository.getItem(
                    episode.id,
                    fields = FieldSets.ITEM_DETAIL
                )?.toAfinityEpisode(jellyfinRepository, null)

                if (fullEpisode != null) {
                    _selectedEpisode.value = fullEpisode
                }

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
        viewModelScope.launch {
            try {
                val success = if (episode.favorite) {
                    userDataRepository.removeFromFavorites(episode.id)
                } else {
                    userDataRepository.addToFavorites(episode.id)
                }

                if (success) {
                    _selectedEpisode.value = episode.copy(favorite = !episode.favorite)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error toggling episode favorite")
            }
        }
    }

    fun toggleEpisodeWatchlist(episode: AfinityEpisode) {
        viewModelScope.launch {
            try {
                val isInWatchlist = _selectedEpisodeWatchlistStatus.value
                _selectedEpisodeWatchlistStatus.value = !isInWatchlist

                val success = if (isInWatchlist) {
                    watchlistRepository.removeFromWatchlist(episode.id)
                } else {
                    watchlistRepository.addToWatchlist(episode.id, "EPISODE")
                }

                if (!success) {
                    _selectedEpisodeWatchlistStatus.value = isInWatchlist
                    Timber.e("Failed to toggle watchlist for episode: ${episode.name}")
                } else {
                    loadWatchlist()
                }
            } catch (e: Exception) {
                Timber.e(e, "Error toggling episode watchlist")
                try {
                    val isInWatchlist = watchlistRepository.isInWatchlist(episode.id)
                    _selectedEpisodeWatchlistStatus.value = isInWatchlist
                } catch (e2: Exception) {
                    Timber.e(e2, "Failed to reload watchlist status")
                }
            }
        }
    }

    fun toggleEpisodeWatched(episode: AfinityEpisode) {
        viewModelScope.launch {
            try {
                val success = if (episode.played) {
                    userDataRepository.markUnwatched(episode.id)
                } else {
                    userDataRepository.markWatched(episode.id)
                }

                if (success) {
                    _selectedEpisode.value = episode.copy(played = !episode.played)
                    mediaRepository.refreshItemUserData(episode.id, FieldSets.REFRESH_USER_DATA)
                    playbackStateManager.notifyItemChanged(episode.id)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error toggling episode watched status")
            }
        }
    }

    fun onDownloadClick() {
        viewModelScope.launch {
            try {
                val episode = _selectedEpisode.value ?: return@launch
                val sources = episode.sources.filter {
                    it.type == com.makd.afinity.data.models.media.AfinitySourceType.REMOTE
                }

                if (sources.isEmpty()) {
                    Timber.w("No remote sources available for download for episode: ${episode.name}")
                    return@launch
                }

                if (sources.size == 1) {
                    val result = downloadRepository.startDownload(episode.id, sources.first().id)
                    result.onSuccess {
                        Timber.i("Download started successfully for episode: ${episode.name}")
                    }.onFailure { error ->
                        Timber.e(error, "Failed to start download for episode: ${episode.name}")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error starting download")
            }
        }
    }

    fun pauseDownload() {
        viewModelScope.launch {
            try {
                val downloadInfo = _selectedEpisodeDownloadInfo.value ?: return@launch
                val result = downloadRepository.pauseDownload(downloadInfo.id)
                result.onFailure { error ->
                    Timber.e(error, "Failed to pause download")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error pausing download")
            }
        }
    }

    fun resumeDownload() {
        viewModelScope.launch {
            try {
                val downloadInfo = _selectedEpisodeDownloadInfo.value ?: return@launch
                val result = downloadRepository.resumeDownload(downloadInfo.id)
                result.onFailure { error ->
                    Timber.e(error, "Failed to resume download")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error resuming download")
            }
        }
    }

    fun cancelDownload() {
        viewModelScope.launch {
            try {
                val downloadInfo = _selectedEpisodeDownloadInfo.value ?: return@launch
                val result = downloadRepository.cancelDownload(downloadInfo.id)
                result.onSuccess {
                    Timber.i("Download cancelled successfully")
                }.onFailure { error ->
                    Timber.e(error, "Failed to cancel download")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error cancelling download")
            }
        }
    }
}

data class WatchlistUiState(
    val movies: List<AfinityMovie> = emptyList(),
    val shows: List<AfinityShow> = emptyList(),
    val seasons: List<AfinitySeason> = emptyList(),
    val episodes: List<AfinityEpisode> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val userProfileImageUrl: String? = null
)