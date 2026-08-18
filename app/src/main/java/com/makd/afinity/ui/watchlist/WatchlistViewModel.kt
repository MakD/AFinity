package com.makd.afinity.ui.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.data.manager.AdminChangeBroadcaster
import com.makd.afinity.data.manager.DownloadPermissions
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
import com.makd.afinity.data.repository.download.DownloadRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.userdata.UserDataRepository
import com.makd.afinity.data.repository.watchlist.WatchlistRepository
import com.makd.afinity.data.store.ItemStore
import com.makd.afinity.ui.item.delegates.ItemUserDataDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(FlowPreview::class)
@HiltViewModel
class WatchlistViewModel
@Inject
constructor(
    private val watchlistRepository: WatchlistRepository,
    private val userDataRepository: UserDataRepository,
    private val downloadRepository: DownloadRepository,
    private val appDataRepository: AppDataRepository,
    private val mediaRepository: MediaRepository,
    private val adminChangeBroadcaster: AdminChangeBroadcaster,
    private val mediaChangeManager: MediaChangeManager,
    private val itemUserDataDelegate: ItemUserDataDelegate,
    private val downloadPermissions: DownloadPermissions,
    private val itemStore: ItemStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WatchlistUiState())
    private var lastWatchlistLoadedAt = 0L

    val isDownloadAllowedByServer: StateFlow<Boolean> = downloadPermissions.isAllowedByServer

    val canDownloadOnNetwork: StateFlow<Boolean> = downloadPermissions.isAllowedOnNetwork

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
        observeSelectedEpisodeDownload()

        viewModelScope.launch { adminChangeBroadcaster.itemChanged.collect { loadWatchlist() } }

        viewModelScope.launch {
            appDataRepository.watchlistData.collect { data ->
                itemStore.putIfAbsent(
                    data.boxSets + data.movies + data.shows + data.seasons + data.episodes
                )
                _uiState.value =
                    WatchlistUiState(
                        boxSets = itemStore.merge(data.boxSets),
                        movies = itemStore.merge(data.movies),
                        shows = itemStore.merge(data.shows),
                        seasons = itemStore.merge(data.seasons),
                        episodes = itemStore.merge(data.episodes),
                        isLoading = false,
                        error = null,
                    )
                lastWatchlistLoadedAt = System.currentTimeMillis()
            }
        }

        viewModelScope.launch {
            appDataRepository.lastResyncAt.collect { at ->
                if (at <= lastWatchlistLoadedAt) return@collect
                lastWatchlistLoadedAt = System.currentTimeMillis()
                appDataRepository.reloadWatchlist()
            }
        }

        viewModelScope.launch {
            itemStore.overlay.collect { overlay ->
                if (overlay.isEmpty()) return@collect
                _uiState.update { state ->
                    val boxSets = itemStore.merge(state.boxSets)
                    val movies = itemStore.merge(state.movies)
                    val shows = itemStore.merge(state.shows)
                    val seasons = itemStore.merge(state.seasons)
                    val episodes = itemStore.merge(state.episodes)
                    if (
                        boxSets === state.boxSets &&
                            movies === state.movies &&
                            shows === state.shows &&
                            seasons === state.seasons &&
                            episodes === state.episodes
                    ) {
                        state
                    } else {
                        state.copy(
                            boxSets = boxSets,
                            movies = movies,
                            shows = shows,
                            seasons = seasons,
                            episodes = episodes,
                        )
                    }
                }
            }
        }
    }

    fun onScreenResumed() {
        if (appDataRepository.lastUserDataChangedAt.value > lastWatchlistLoadedAt) {
            viewModelScope.launch {
                appDataRepository.reloadWatchlist()
                lastWatchlistLoadedAt = System.currentTimeMillis()
            }
        }
    }

    fun loadWatchlist() {
        viewModelScope.launch {
            val currentData = _uiState.value
            val hasData =
                currentData.movies.isNotEmpty() ||
                    currentData.shows.isNotEmpty() ||
                    currentData.episodes.isNotEmpty() ||
                    currentData.boxSets.isNotEmpty()
            if (!hasData) {
                appDataRepository.reloadWatchlist()
            }
        }
    }

    fun onItemClick(item: AfinityItem) {
        Timber.d("Watchlist item clicked: ${item.name} (${item.id})")
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeSelectedEpisodeDownload() {
        _selectedEpisode
            .map { it?.id }
            .distinctUntilChanged()
            .flatMapLatest { id ->
                if (id == null) flowOf(null)
                else
                    downloadRepository.getAllDownloadsFlow().map { downloads ->
                        downloads.find { it.itemId == id }
                    }
            }
            .onEach { _selectedEpisodeDownloadInfo.value = it }
            .launchIn(viewModelScope)
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
}

data class WatchlistUiState(
    val boxSets: List<AfinityBoxSet> = emptyList(),
    val movies: List<AfinityMovie> = emptyList(),
    val shows: List<AfinityShow> = emptyList(),
    val seasons: List<AfinitySeason> = emptyList(),
    val episodes: List<AfinityEpisode> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val userProfileImageUrl: String? = null,
)
