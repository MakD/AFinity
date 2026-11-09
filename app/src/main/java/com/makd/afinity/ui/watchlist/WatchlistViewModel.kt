package com.makd.afinity.ui.watchlist

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.makd.afinity.R
import com.makd.afinity.data.manager.DownloadPermissions
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.jellyseerr.SearchResultItem
import com.makd.afinity.data.models.media.AfinityBoxSet
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.JellyseerrRepository
import com.makd.afinity.data.repository.download.DownloadRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.userdata.UserDataRepository
import com.makd.afinity.data.store.ItemStore
import com.makd.afinity.ui.components.EpisodeSelection
import com.makd.afinity.ui.item.delegates.ItemUserDataDelegate
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(FlowPreview::class)
@HiltViewModel
class WatchlistViewModel
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val userDataRepository: UserDataRepository,
    private val downloadRepository: DownloadRepository,
    private val appDataRepository: AppDataRepository,
    private val mediaRepository: MediaRepository,
    private val itemUserDataDelegate: ItemUserDataDelegate,
    private val downloadPermissions: DownloadPermissions,
    private val itemStore: ItemStore,
    private val jellyseerrRepository: JellyseerrRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WatchlistUiState())
    private var lastWatchlistLoadedAt = System.currentTimeMillis()

    private val _seerrItems = MutableStateFlow<List<SearchResultItem>>(emptyList())
    val seerrItems: StateFlow<List<SearchResultItem>> = _seerrItems.asStateFlow()
    private var seerrJob: Job? = null

    val isDownloadAllowedByServer: StateFlow<Boolean> = downloadPermissions.isAllowedByServer

    val canDownloadOnNetwork: StateFlow<Boolean> = downloadPermissions.isAllowedOnNetwork

    val uiState: StateFlow<WatchlistUiState> =
        itemStore.project(viewModelScope, _uiState) { state ->
            val boxSets = live(state.boxSets)
            val movies = live(state.movies)
            val shows = live(state.shows)
            val seasons = live(state.seasons)
            val episodes = live(state.episodes)
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

    private val episodeSelection =
        EpisodeSelection(
            scope = viewModelScope,
            mediaRepository = mediaRepository,
            userDataRepository = userDataRepository,
            itemUserDataDelegate = itemUserDataDelegate,
            downloadRepository = downloadRepository,
        )

    val selectedEpisode: StateFlow<AfinityEpisode?> = episodeSelection.selected

    val selectedEpisodeWatchlistStatus: StateFlow<Boolean> = episodeSelection.watchlistStatus

    val selectedEpisodeDownloadInfo: StateFlow<DownloadInfo?> = episodeSelection.downloadInfo

    init {
        viewModelScope.launch {
            combine(
                    appDataRepository.watchlistData,
                    appDataRepository.watchlistLoaded,
                    appDataRepository.watchlistLoadFailed,
                ) { data, loaded, failed ->
                    Triple(data, loaded, failed)
                }
                .collect { (data, loaded, failed) ->
                    _uiState.value =
                        WatchlistUiState(
                            boxSets = data.boxSets,
                            movies = data.movies,
                            shows = data.shows,
                            seasons = data.seasons,
                            episodes = data.episodes,
                            isLoading = !loaded && !failed,
                            error =
                                if (failed && !loaded) {
                                    context.getString(R.string.error_content_unavailable_server)
                                } else {
                                    null
                                },
                        )
                    if (loaded) lastWatchlistLoadedAt = System.currentTimeMillis()
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
            combine(jellyseerrRepository.currentSessionId, jellyseerrRepository.isAuthenticated) {
                    sessionId,
                    isAuth ->
                    sessionId != null && isAuth
                }
                .distinctUntilChanged()
                .collect { active ->
                    if (active) {
                        loadSeerrWatchlist()
                    } else {
                        seerrJob?.cancel()
                        _seerrItems.value = emptyList()
                    }
                }
        }

        viewModelScope.launch {
            jellyseerrRepository.watchlistEvents.collect {
                if (jellyseerrRepository.isAuthenticated.value) loadSeerrWatchlist()
            }
        }
    }

    private fun loadSeerrWatchlist() {
        seerrJob?.cancel()
        seerrJob = viewModelScope.launch {
            jellyseerrRepository.getWatchlist().onSuccess { result ->
                _seerrItems.value =
                    result.results.filter { it.mediaInfo?.getJellyfinItemId() == null }
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
        if (appDataRepository.watchlistLoaded.value) return
        viewModelScope.launch { appDataRepository.reloadWatchlist() }
    }

    fun retry() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            appDataRepository.reloadWatchlist()
        }
        if (jellyseerrRepository.isAuthenticated.value) loadSeerrWatchlist()
    }

    fun onItemClick(item: AfinityItem) {
        Timber.d("Watchlist item clicked: ${item.name} (${item.id})")
    }

    fun selectEpisode(episode: AfinityEpisode) = episodeSelection.select(episode)

    fun clearSelectedEpisode() = episodeSelection.clear()

    fun toggleEpisodeFavorite(episode: AfinityEpisode) = episodeSelection.toggleFavorite(episode)

    fun toggleEpisodeWatchlist(episode: AfinityEpisode) = episodeSelection.toggleWatchlist(episode)

    fun toggleEpisodeWatched(episode: AfinityEpisode) = episodeSelection.toggleWatched(episode)
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
