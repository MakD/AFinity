package com.makd.afinity.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.manager.PlaybackEvent
import com.makd.afinity.data.manager.PlaybackStateManager
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
import com.makd.afinity.data.repository.JellyfinRepository
import com.makd.afinity.data.repository.download.DownloadRepository
import com.makd.afinity.data.repository.livetv.LiveTvRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.userdata.UserDataRepository
import com.makd.afinity.data.repository.watchlist.WatchlistRepository
import com.makd.afinity.ui.item.delegates.ItemDownloadDelegate
import com.makd.afinity.ui.item.delegates.ItemUserDataDelegate
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
class FavoritesViewModel
@Inject
constructor(
    private val jellyfinRepository: JellyfinRepository,
    private val userDataRepository: UserDataRepository,
    private val mediaRepository: MediaRepository,
    private val playbackStateManager: PlaybackStateManager,
    private val watchlistRepository: WatchlistRepository,
    private val downloadRepository: DownloadRepository,
    private val appDataRepository: AppDataRepository,
    private val liveTvRepository: LiveTvRepository,
    private val itemUserDataDelegate: ItemUserDataDelegate,
    private val itemDownloadDelegate: ItemDownloadDelegate,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FavoritesUiState())
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
            appDataRepository.isInitialDataLoaded.collect { isLoaded ->
                if (isLoaded) {
                    loadFavorites()
                } else {
                    _uiState.value = FavoritesUiState()
                    clearSelectedEpisode()
                }
            }
        }
        viewModelScope.launch {
            playbackStateManager.playbackEvents.collect { event ->
                if (event is PlaybackEvent.Synced) {
                    loadFavorites()
                    _selectedEpisode.value?.let { ep ->
                        if (ep.id == event.itemId) {
                            val refreshedEp =
                                jellyfinRepository.getItemById(event.itemId) as? AfinityEpisode
                            refreshedEp?.let { _selectedEpisode.value = it }
                        }
                    }
                }
            }
        }
    }

    fun loadFavorites() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)

                coroutineScope {
                    val moviesDeferred = async { jellyfinRepository.getFavoriteMovies() }
                    val showsDeferred = async { jellyfinRepository.getFavoriteShows() }
                    val seasonsDeferred = async { jellyfinRepository.getFavoriteSeasons() }
                    val episodesDeferred = async { jellyfinRepository.getFavoriteEpisodes() }
                    val boxSetsDeferred = async { jellyfinRepository.getFavoriteBoxSets() }
                    val peopleDeferred = async { jellyfinRepository.getFavoritePeople() }
                    val channelsDeferred = async {
                        try {
                            liveTvRepository.getChannels(isFavorite = true)
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }

                    val movies = moviesDeferred.await()
                    val shows = showsDeferred.await()
                    val seasons = seasonsDeferred.await()
                    val episodes = episodesDeferred.await()
                    val boxSets = boxSetsDeferred.await()
                    val people = peopleDeferred.await()
                    val channels = channelsDeferred.await()

                    _uiState.value =
                        _uiState.value.copy(
                            isLoading = false,
                            movies = movies.sortedBy { it.name },
                            shows = shows.sortedBy { it.name },
                            seasons = seasons.sortedBy { it.name },
                            episodes = episodes.sortedBy { it.name },
                            boxSets = boxSets.sortedBy { it.name },
                            channels = channels.sortedBy { it.channelNumber ?: it.name },
                            people = people.sortedBy { it.name },
                            error = null,
                        )
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load favorites")
                _uiState.value =
                    _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to load favorites: ${e.message}",
                    )
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
                    jellyfinRepository
                        .getItem(episode.id, fields = FieldSets.ITEM_DETAIL)
                        ?.toAfinityEpisode(jellyfinRepository, null)

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
            loadFavorites()
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
            try {
                val isNowPlayed = !episode.played
                _selectedEpisode.value =
                    episode.copy(played = isNowPlayed, playbackPositionTicks = 0)

                val success =
                    if (episode.played) {
                        userDataRepository.markUnwatched(episode.id)
                    } else {
                        userDataRepository.markWatched(episode.id)
                    }

                if (success) {
                    mediaRepository.refreshItemUserData(episode.id, FieldSets.REFRESH_USER_DATA)
                    playbackStateManager.notifyItemChanged(episode.id)
                } else {
                    _selectedEpisode.value = episode
                }
            } catch (e: Exception) {
                Timber.e(e, "Error toggling episode watched status")
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
