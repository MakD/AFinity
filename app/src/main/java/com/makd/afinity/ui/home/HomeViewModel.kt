package com.makd.afinity.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.makd.afinity.data.models.extensions.toAfinityEpisode
import com.makd.afinity.data.models.media.AfinityCollection
import com.makd.afinity.data.models.GenreItem
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.media.AfinityStudio
import com.makd.afinity.data.models.media.AfinityVideo
import com.makd.afinity.data.models.media.toAfinityEpisode
import com.makd.afinity.data.manager.OfflineModeManager
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.JellyfinRepository
import com.makd.afinity.data.repository.auth.AuthRepository
import com.makd.afinity.data.repository.download.DownloadRepository
import com.makd.afinity.data.repository.userdata.UserDataRepository
import com.makd.afinity.data.repository.watchlist.WatchlistRepository
import com.makd.afinity.navigation.Destination
import com.makd.afinity.ui.utils.IntentUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val appDataRepository: AppDataRepository,
    private val jellyfinRepository: JellyfinRepository,
    private val userDataRepository: UserDataRepository,
    private val watchlistRepository: WatchlistRepository,
    private val databaseRepository: DatabaseRepository,
    private val downloadRepository: DownloadRepository,
    private val offlineModeManager: OfflineModeManager,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {

        viewModelScope.launch {
            appDataRepository.isInitialDataLoaded.collect { isLoaded ->
                if (!isLoaded) {
                    Timber.d("Data cleared detected, resetting HomeViewModel UI state")
                    _uiState.value = HomeUiState()
                }
            }
        }

        viewModelScope.launch {
            appDataRepository.latestMedia.collect { latestMedia ->
                _uiState.value = _uiState.value.copy(latestMedia = latestMedia)
            }
        }

        viewModelScope.launch {
            appDataRepository.heroCarouselItems.collect { heroItems ->
                _uiState.value = _uiState.value.copy(heroCarouselItems = heroItems)
            }
        }

        viewModelScope.launch {
            appDataRepository.continueWatching.collect { continueWatching ->
                _uiState.value = _uiState.value.copy(continueWatching = continueWatching)
            }
        }

        viewModelScope.launch {
            appDataRepository.nextUp.collect { nextUp ->
                _uiState.value = _uiState.value.copy(nextUp = nextUp)
            }
        }

        viewModelScope.launch {
            appDataRepository.latestMovies.collect { latestMovies ->
                _uiState.value = _uiState.value.copy(latestMovies = latestMovies)
            }
        }

        viewModelScope.launch {
            appDataRepository.latestTvSeries.collect { latestTvSeries ->
                _uiState.value = _uiState.value.copy(latestTvSeries = latestTvSeries)
            }
        }

        viewModelScope.launch {
            appDataRepository.getCombineLibrarySectionsFlow().collect { combine ->
                _uiState.value = _uiState.value.copy(combineLibrarySections = combine)
            }
        }

        viewModelScope.launch {
            var isFirstEmission = true
            appDataRepository.getHomeSortByDateAddedFlow().collect { sortByDateAdded ->
                if (isFirstEmission) {
                    isFirstEmission = false
                    return@collect
                }
                appDataRepository.reloadHomeData()
            }
        }

        viewModelScope.launch {
            appDataRepository.separateMovieLibrarySections.collect { sections ->
                _uiState.value = _uiState.value.copy(separateMovieLibrarySections = sections)
            }
        }

        viewModelScope.launch {
            appDataRepository.separateTvLibrarySections.collect { sections ->
                _uiState.value = _uiState.value.copy(separateTvLibrarySections = sections)
            }
        }

        viewModelScope.launch {
            appDataRepository.highestRated.collect { highestRated ->
                _uiState.value = _uiState.value.copy(highestRated = highestRated)
            }
        }

        viewModelScope.launch {
            appDataRepository.combinedGenres.collect { combinedGenres ->
                _uiState.value = _uiState.value.copy(combinedGenres = combinedGenres)
            }
        }

        viewModelScope.launch {
            appDataRepository.genreMovies.collect { genreMovies ->
                _uiState.value = _uiState.value.copy(genreMovies = genreMovies)
            }
        }

        viewModelScope.launch {
            appDataRepository.genreShows.collect { genreShows ->
                _uiState.value = _uiState.value.copy(genreShows = genreShows)
            }
        }

        viewModelScope.launch {
            appDataRepository.genreLoadingStates.collect { loadingStates ->
                _uiState.value = _uiState.value.copy(genreLoadingStates = loadingStates)
            }
        }

        viewModelScope.launch {
            appDataRepository.studios.collect { studios ->
                _uiState.value = _uiState.value.copy(studios = studios)
            }
        }

        viewModelScope.launch {
            loadCombinedGenres()
        }

        viewModelScope.launch {
            loadStudios()
        }

        viewModelScope.launch {
            loadDownloadedContent()
        }

        viewModelScope.launch {
            offlineModeManager.isOffline.collect { isOffline ->
                Timber.d("Offline mode changed: $isOffline")
                _uiState.value = _uiState.value.copy(isOffline = isOffline)
                if (isOffline) {
                    loadDownloadedContent()
                }
            }
        }
    }

    private suspend fun loadDownloadedContent() {
        try {
            val userId = authRepository.currentUser.value?.id ?: return

            Timber.d("Loading downloaded content for user: $userId")

            val downloadedMovies = databaseRepository.getAllMovies(userId)
                .filter { movie -> movie.sources.any { it.type == com.makd.afinity.data.models.media.AfinitySourceType.LOCAL } }

            val allShows = databaseRepository.getAllShows(userId)
            val downloadedShows = allShows.filter { show ->
                show.seasons.any { season ->
                    season.episodes.any { episode ->
                        episode.sources.any { source -> source.type == com.makd.afinity.data.models.media.AfinitySourceType.LOCAL }
                    }
                }
            }

            Timber.d("Found ${downloadedMovies.size} movies and ${downloadedShows.size} shows with downloads")

            val offlineContinueWatching = mutableListOf<AfinityItem>()

            downloadedMovies.forEach { movie ->
                if (movie.playbackPositionTicks > 0 && !movie.played) {
                    offlineContinueWatching.add(movie)
                }
            }

            allShows.forEach { show ->
                show.seasons.forEach { season ->
                    season.episodes.forEach { episode ->
                        if (episode.playbackPositionTicks > 0 &&
                            !episode.played &&
                            episode.sources.any { it.type == com.makd.afinity.data.models.media.AfinitySourceType.LOCAL }
                        ) {
                            offlineContinueWatching.add(episode)
                        }
                    }
                }
            }

            val sortedOfflineContinueWatching = offlineContinueWatching.sortedByDescending { item ->
                when (item) {
                    is AfinityMovie -> item.playbackPositionTicks
                    is AfinityEpisode -> item.playbackPositionTicks
                    else -> 0L
                }
            }

            Timber.d("Found ${sortedOfflineContinueWatching.size} items to continue watching offline")

            _uiState.value = _uiState.value.copy(
                downloadedMovies = downloadedMovies,
                downloadedShows = downloadedShows,
                offlineContinueWatching = sortedOfflineContinueWatching
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to load downloaded content")
        }
    }

    private val _selectedEpisode = MutableStateFlow<AfinityEpisode?>(null)
    val selectedEpisode: StateFlow<AfinityEpisode?> = _selectedEpisode.asStateFlow()

    private val _selectedEpisodeWatchlistStatus = MutableStateFlow(false)
    val selectedEpisodeWatchlistStatus: StateFlow<Boolean> =
        _selectedEpisodeWatchlistStatus.asStateFlow()

    private val _isLoadingEpisode = MutableStateFlow(false)
    val isLoadingEpisode: StateFlow<Boolean> = _isLoadingEpisode.asStateFlow()

    private val _selectedEpisodeDownloadInfo = MutableStateFlow<DownloadInfo?>(null)
    val selectedEpisodeDownloadInfo: StateFlow<DownloadInfo?> =
        _selectedEpisodeDownloadInfo.asStateFlow()

    fun selectEpisode(episode: AfinityEpisode) {
        viewModelScope.launch {
            try {
                _isLoadingEpisode.value = true

                val fullEpisode = jellyfinRepository.getItem(
                    episode.id,
                    fields = FieldSets.ITEM_DETAIL
                )?.toAfinityEpisode(jellyfinRepository, null)

                if (fullEpisode != null) {
                    _selectedEpisode.value = episode
                }

                try {
                    val isInWatchlist = watchlistRepository.isInWatchlist(episode.id)
                    _selectedEpisodeWatchlistStatus.value = isInWatchlist
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load episode watchlist status")
                    _selectedEpisodeWatchlistStatus.value = false
                }

                try {
                    val episodeDownload = downloadRepository.getDownloadByItemId(episode.id)
                    _selectedEpisodeDownloadInfo.value = episodeDownload
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load episode download status")
                    _selectedEpisodeDownloadInfo.value = null
                }

                launch {
                    downloadRepository.getAllDownloadsFlow().collect { downloads ->
                        val currentEpisodeId = _selectedEpisode.value?.id
                        if (currentEpisodeId != null) {
                            val episodeDownload = downloads.find { it.itemId == currentEpisodeId }
                            _selectedEpisodeDownloadInfo.value = episodeDownload
                        }
                    }
                }

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
                    Timber.w("Failed to toggle watchlist status")
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
                    _selectedEpisode.value = episode.copy(
                        played = !episode.played,
                        playbackPositionTicks = if (!episode.played) episode.runtimeTicks else 0
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error toggling episode watched status")
            }
        }
    }

    fun onHeroItemClick(item: AfinityItem) {
        Timber.d("Hero item clicked: ${item.name}")
    }

    fun onMoreInformationClick(item: AfinityItem) {
        Timber.d("More information clicked: ${item.name}")
    }

    fun onWatchNowClick(item: AfinityItem) {
        Timber.d("Watch now clicked: ${item.name}")
    }

    fun onPlayTrailerClick(context: Context, item: AfinityItem) {
        Timber.d("Play trailer clicked: ${item.name}")
        val trailerUrl = when (item) {
            is AfinityMovie -> item.trailer
            is AfinityShow -> item.trailer
            is AfinityVideo -> item.trailer
            else -> null
        }
        IntentUtils.openYouTubeUrl(context, trailerUrl)
    }

    fun onContinueWatchingItemClick(item: AfinityItem) {
        Timber.d("Continue watching item clicked: ${item.name}")
    }

    fun onLatestMovieItemClick(movie: AfinityMovie) {
        Timber.d("Latest movie item clicked: ${movie.name}")
    }

    fun onLatestTvSeriesItemClick(series: AfinityShow) {
        Timber.d("Latest TV series item clicked: ${series.name}")
    }

    fun onHighestRatedItemClick(item: AfinityItem) {
        Timber.d("Highest rated item clicked: ${item.name}")
    }

    private fun loadCombinedGenres() {
        viewModelScope.launch {
            try {
                appDataRepository.loadCombinedGenres()
            } catch (e: Exception) {
                Timber.e(e, "Failed to load combined genres")
            }
        }
    }

    fun loadMoviesForGenre(genre: String) {
        viewModelScope.launch {
            try {
                appDataRepository.loadMoviesForGenre(genre)
            } catch (e: Exception) {
                Timber.e(e, "Failed to load movies for genre: $genre")
            }
        }
    }

    fun loadShowsForGenre(genre: String) {
        viewModelScope.launch {
            try {
                appDataRepository.loadShowsForGenre(genre)
            } catch (e: Exception) {
                Timber.e(e, "Failed to load shows for genre: $genre")
            }
        }
    }

    private fun loadStudios() {
        viewModelScope.launch {
            try {
                appDataRepository.loadStudios()
            } catch (e: Exception) {
                Timber.e(e, "Failed to load studios")
            }
        }
    }

    fun onStudioClick(studio: AfinityStudio, navController: NavController) {
        Timber.d("Studio clicked: ${studio.name}")
        val route = Destination.createStudioContentRoute(studio.name)
        navController.navigate(route)
    }

    fun onDownloadClick() {
        viewModelScope.launch {
            try {
                val episode = _selectedEpisode.value ?: return@launch
                val sources =
                    episode.sources.filter { it.type == com.makd.afinity.data.models.media.AfinitySourceType.REMOTE }

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
                } else {
                    _uiState.value = _uiState.value.copy(showQualityDialog = true)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error starting download")
            }
        }
    }

    fun onQualitySelected(sourceId: String) {
        viewModelScope.launch {
            try {
                val episode = _selectedEpisode.value ?: return@launch
                val result = downloadRepository.startDownload(episode.id, sourceId)
                result.onSuccess {
                    Timber.i("Download started successfully for episode: ${episode.name}")
                }.onFailure { error ->
                    Timber.e(error, "Failed to start download for episode: ${episode.name}")
                }
                _uiState.value = _uiState.value.copy(showQualityDialog = false)
            } catch (e: Exception) {
                Timber.e(e, "Error starting download with selected quality")
            }
        }
    }

    fun dismissQualityDialog() {
        _uiState.value = _uiState.value.copy(showQualityDialog = false)
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

data class HomeUiState(
    val heroCarouselItems: List<AfinityItem> = emptyList(),
    val latestMedia: List<AfinityItem> = emptyList(),
    val continueWatching: List<AfinityItem> = emptyList(),
    val offlineContinueWatching: List<AfinityItem> = emptyList(),
    val nextUp: List<AfinityEpisode> = emptyList(),
    val latestMovies: List<AfinityMovie> = emptyList(),
    val latestTvSeries: List<AfinityShow> = emptyList(),
    val highestRated: List<AfinityItem> = emptyList(),
    val studios: List<AfinityStudio> = emptyList(),
    val combinedGenres: List<GenreItem> = emptyList(),
    val genreMovies: Map<String, List<AfinityMovie>> = emptyMap(),
    val genreShows: Map<String, List<AfinityShow>> = emptyMap(),
    val genreLoadingStates: Map<String, Boolean> = emptyMap(),
    val downloadedMovies: List<AfinityMovie> = emptyList(),
    val downloadedShows: List<AfinityShow> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val combineLibrarySections: Boolean = false,
    val separateMovieLibrarySections: List<Pair<AfinityCollection, List<AfinityMovie>>> = emptyList(),
    val separateTvLibrarySections: List<Pair<AfinityCollection, List<AfinityShow>>> = emptyList(),
    val isOffline: Boolean = false,
    val showQualityDialog: Boolean = false
)