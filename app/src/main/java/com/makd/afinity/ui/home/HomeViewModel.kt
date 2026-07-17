package com.makd.afinity.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.makd.afinity.R
import com.makd.afinity.data.manager.AdminChangeBroadcaster
import com.makd.afinity.data.manager.MediaChangeManager
import com.makd.afinity.data.manager.OfflineModeManager
import com.makd.afinity.data.manager.PlaybackStateManager
import com.makd.afinity.data.models.GenreItem
import com.makd.afinity.data.models.GenreType
import com.makd.afinity.data.models.HomeSectionContent
import com.makd.afinity.data.models.HomeSectionDescriptor
import com.makd.afinity.data.models.HomeSectionType
import com.makd.afinity.data.models.MovieSection
import com.makd.afinity.data.models.PersonFromMovieSection
import com.makd.afinity.data.models.PersonSection
import com.makd.afinity.data.models.audiobookshelf.AbsDownloadInfo
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.extensions.toAfinityItem
import com.makd.afinity.data.models.media.AfinityCollection
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.media.AfinityStudio
import com.makd.afinity.data.models.media.AfinityVideo
import com.makd.afinity.data.models.media.toAfinityEpisode
import com.makd.afinity.data.models.music.AfinityAlbum
import com.makd.afinity.data.models.music.AfinityTrack
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.audiobookshelf.AbsDownloadRepository
import com.makd.afinity.data.repository.auth.AuthRepository
import com.makd.afinity.data.repository.download.DownloadRepository
import com.makd.afinity.data.repository.home.HomeSectionsRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.userdata.UserDataRepository
import com.makd.afinity.data.repository.watchlist.WatchlistRepository
import com.makd.afinity.data.storage.StorageLocationProvider
import com.makd.afinity.data.workers.HomeDataReloadWorker
import com.makd.afinity.navigation.Destination
import com.makd.afinity.ui.item.delegates.ItemUserDataDelegate
import com.makd.afinity.ui.utils.IntentUtils
import com.makd.afinity.util.NetworkConnectivityMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class HomeViewModel
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val appDataRepository: AppDataRepository,
    private val userDataRepository: UserDataRepository,
    private val watchlistRepository: WatchlistRepository,
    private val databaseRepository: DatabaseRepository,
    private val downloadRepository: DownloadRepository,
    private val absDownloadRepository: AbsDownloadRepository,
    private val storageLocationProvider: StorageLocationProvider,
    private val offlineModeManager: OfflineModeManager,
    private val authRepository: AuthRepository,
    private val mediaRepository: MediaRepository,
    private val playbackStateManager: PlaybackStateManager,
    private val adminChangeBroadcaster: AdminChangeBroadcaster,
    private val mediaChangeManager: MediaChangeManager,
    private val itemUserDataDelegate: ItemUserDataDelegate,
    private val preferencesRepository: PreferencesRepository,
    private val networkMonitor: NetworkConnectivityMonitor,
    private val homeSectionsRepository: HomeSectionsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())

    val canDownload: StateFlow<Boolean> =
        preferencesRepository
            .getDownloadWifiOnlyFlow()
            .combine(networkMonitor.isOnWifiFlow) { wifiOnly, onWifi -> !wifiOnly || onWifi }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private val _isFetchingRandomItem = MutableStateFlow(false)
    val isFetchingRandomItem: StateFlow<Boolean> = _isFetchingRandomItem.asStateFlow()

    private var libraryContentReloadJob: Job? = null

    private var lastHomeRefreshedAt = 0L

    init {
        viewModelScope.launch {
            appDataRepository.isInitialDataLoaded.collect { isLoaded ->
                if (!isLoaded) {
                    Timber.d(
                        "Data cleared detected (Session Switch/Clear), resetting HomeViewModel UI state"
                    )
                    _uiState.value = HomeUiState()
                } else {
                    Timber.d(
                        "Initial Data Loaded: Triggering secondary content load (Studios, Genres, Recs)"
                    )
                    _uiState.update { it.copy(isLoading = false) }
                    launch {
                        coroutineScope {
                            launch { loadStudios() }
                            launch { loadCombinedGenres() }
                            launch { loadUpcomingEpisodes() }
                        }
                        if (!offlineModeManager.isOffline.first()) {
                            homeSectionsRepository.ensureLayout()
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            mediaRepository.getLatestMediaFlow().collect { items ->
                _uiState.update {
                    it.copy(
                        latestMedia =
                            items.filter { item -> item is AfinityMovie || item is AfinityShow }
                    )
                }
            }
        }

        viewModelScope.launch {
            appDataRepository.heroCarouselItems.collect { heroItems ->
                _uiState.update { it.copy(heroCarouselItems = heroItems) }
            }
        }

        viewModelScope.launch {
            mediaRepository.getContinueWatchingFlow().collect { items ->
                lastHomeRefreshedAt = System.currentTimeMillis()
                _uiState.update { it.copy(continueWatching = items) }
            }
        }

        viewModelScope.launch {
            mediaRepository.getNextUpFlow().collect { items ->
                _uiState.update { it.copy(nextUp = items) }
            }
        }

        viewModelScope.launch {
            appDataRepository.latestMovies.collect { latestMovies ->
                _uiState.update { it.copy(latestMovies = latestMovies) }
            }
        }

        viewModelScope.launch {
            appDataRepository.latestTvSeries.collect { latestTvSeries ->
                _uiState.update { it.copy(latestTvSeries = latestTvSeries) }
            }
        }

        viewModelScope.launch {
            appDataRepository.getCombineLibrarySectionsFlow().collect { combine ->
                _uiState.update { it.copy(combineLibrarySections = combine) }
            }
        }

        viewModelScope.launch {
            appDataRepository.getHomeSortByDateAddedFlow().distinctUntilChanged().drop(1).collect {
                appDataRepository.reloadHomeData()
            }
        }

        viewModelScope.launch {
            adminChangeBroadcaster.itemChanged.collect {
                appDataRepository.refreshPlaybackSections()
            }
        }

        viewModelScope.launch {
            appDataRepository.separateMovieLibrarySections.collect { sections ->
                _uiState.update { it.copy(separateMovieLibrarySections = sections) }
            }
        }

        viewModelScope.launch {
            appDataRepository.libraries.collect { libs ->
                _uiState.update { it.copy(libraries = libs) }
            }
        }

        viewModelScope.launch {
            appDataRepository.separateTvLibrarySections.collect { sections ->
                _uiState.update { it.copy(separateTvLibrarySections = sections) }
            }
        }

        viewModelScope.launch {
            appDataRepository.highestRated.collect { highestRated ->
                _uiState.update { it.copy(highestRated = highestRated) }
            }
        }

        viewModelScope.launch {
            combine(homeSectionsRepository.layout, homeSectionsRepository.content) { layout, content
                    ->
                    layout.mapNotNull { descriptor ->
                        descriptor.toHomeSection(content[descriptor.key])
                    }
                }
                .distinctUntilChanged()
                .collect { sections -> _uiState.update { it.copy(combinedSections = sections) } }
        }

        viewModelScope.launch {
            appDataRepository.genreMovies.collect { genreMovies ->
                _uiState.update { it.copy(genreMovies = genreMovies) }
            }
        }

        viewModelScope.launch {
            appDataRepository.genreShows.collect { genreShows ->
                _uiState.update { it.copy(genreShows = genreShows) }
            }
        }

        viewModelScope.launch {
            appDataRepository.genreLoadingStates.collect { loadingStates ->
                _uiState.update { it.copy(genreLoadingStates = loadingStates) }
            }
        }

        viewModelScope.launch {
            appDataRepository.studios.collect { studios ->
                _uiState.update { it.copy(studios = studios) }
            }
        }

        viewModelScope.launch {
            combine(
                    appDataRepository.initialLoadFailed,
                    appDataRepository.libraries,
                    offlineModeManager.isOffline,
                ) { failed, libs, offline ->
                    failed && libs.isEmpty() && !offline
                }
                .distinctUntilChanged()
                .collect { showError ->
                    _uiState.update {
                        it.copy(
                            error =
                                if (showError) context.getString(R.string.home_load_failed)
                                else null,
                            isLoading = if (showError) false else it.isLoading,
                        )
                    }
                }
        }

        viewModelScope.launch {
            var previousIsOffline: Boolean? = null
            offlineModeManager.isOffline.collect { isOffline ->
                Timber.d("Offline mode changed: $isOffline")
                _uiState.update {
                    it.copy(
                        isOffline = isOffline,
                        offlineContentLoaded = if (isOffline) it.offlineContentLoaded else false,
                    )
                }

                if (previousIsOffline == true && !isOffline) {
                    scheduleHomeDataReload()
                }
                previousIsOffline = isOffline
            }
        }
        viewModelScope.launch {
            combine(offlineModeManager.isOffline, authRepository.currentUser) { isOffline, user ->
                    isOffline to user?.id
                }
                .distinctUntilChanged()
                .flatMapLatest { (isOffline, userId) ->
                    if (isOffline && userId != null) {
                        combine(
                                downloadRepository.getCompletedDownloadsFlow(),
                                absDownloadRepository.getCompletedDownloadsFlow(),
                                databaseRepository.getAllMusicTracksFlowByUser(userId),
                                databaseRepository.getAllUserDataFlow(userId),
                            ) { _, _, _, _ ->
                                userId
                            }
                            .debounce(300L)
                    } else {
                        flowOf()
                    }
                }
                .collect { userId -> loadDownloadedContent(userId) }
        }

        viewModelScope.launch {
            mediaChangeManager.mediaChanges.collect { event ->
                var targetItem = event.updatedItem ?: event.parentItem ?: event.seasonItem
                if (targetItem == null) {
                    try {
                        targetItem = mediaRepository.getItemById(event.itemId)
                    } catch (e: Exception) {
                        Timber.e(
                            e,
                            "Failed to resolve item for granular home patch: ${event.itemId}",
                        )
                    }
                }

                var parentShowItem: AfinityItem? = null
                val trueSeriesId =
                    event.seriesId
                        ?: (targetItem as? AfinityEpisode)?.seriesId
                        ?: (targetItem as? AfinitySeason)?.seriesId
                if (trueSeriesId != null && trueSeriesId != targetItem?.id) {
                    parentShowItem = event.parentItem?.takeIf { it.id == trueSeriesId }
                    if (parentShowItem == null) {
                        try {
                            parentShowItem = mediaRepository.getItemById(trueSeriesId)
                        } catch (e: Exception) {
                            Timber.e(
                                e,
                                "Failed to resolve parent show for home patch: $trueSeriesId",
                            )
                        }
                    }
                }

                targetItem?.let { item ->
                    homeSectionsRepository.updateItem(item)
                    patchUiStateItem(item)
                }

                val isPlayed = (targetItem?.played == true) || (event.userData?.played == true)
                val idToRemove = targetItem?.id ?: event.itemId
                if (isPlayed) {
                    _uiState.update { state ->
                        state.copy(
                            continueWatching =
                                state.continueWatching.filter { it.id != idToRemove },
                            nextUp = state.nextUp.filter { it.id != idToRemove },
                            latestMovies = state.latestMovies.filter { it.id != idToRemove },
                            latestMedia = state.latestMedia.filter { it.id != idToRemove },
                        )
                    }
                }

                parentShowItem?.let { show ->
                    homeSectionsRepository.updateItem(show)
                    patchUiStateItem(show)
                }
            }
        }

        viewModelScope.launch {
            mediaChangeManager.libraryContentChanges.collect { event ->
                Timber.d("HomeViewModel received library content change: ${event.reason}")
                libraryContentReloadJob?.cancel()
                libraryContentReloadJob = launch {
                    delay(2_000L)
                    coroutineScope {
                        launch { loadStudios() }
                        launch { loadCombinedGenres() }
                        launch { loadUpcomingEpisodes() }
                    }
                    if (!offlineModeManager.isOffline.first()) {
                        homeSectionsRepository.ensureLayout(force = true)
                    }
                }
            }
        }
    }

    private fun patchUiStateItem(updatedItem: AfinityItem) {
        _uiState.update { state ->
            fun <T : AfinityItem> patchItem(list: List<T>, replacement: T?): List<T> =
                if (replacement == null || list.none { it.id == updatedItem.id }) list
                else list.map { if (it.id == updatedItem.id) replacement else it }

            fun <T : AfinityItem> patchMap(
                map: Map<String, List<T>>,
                replacement: T?,
            ): Map<String, List<T>> =
                if (
                    replacement == null ||
                        map.values.none { list -> list.any { it.id == updatedItem.id } }
                )
                    map
                else map.mapValues { (_, items) -> patchItem(items, replacement) }

            state.copy(
                heroCarouselItems = patchItem(state.heroCarouselItems, updatedItem),
                highestRated = patchItem(state.highestRated, updatedItem),
                latestMovies = patchItem(state.latestMovies, updatedItem as? AfinityMovie),
                latestTvSeries = patchItem(state.latestTvSeries, updatedItem as? AfinityShow),
                genreMovies = patchMap(state.genreMovies, updatedItem as? AfinityMovie),
                genreShows = patchMap(state.genreShows, updatedItem as? AfinityShow),
            )
        }
    }

    fun hydrateSection(key: String) {
        homeSectionsRepository.hydrate(key)
    }

    fun retryInitialLoad() {
        appDataRepository.retryInitialLoad()
    }

    private suspend fun loadDownloadedContent(userId: UUID) {
        try {
            Timber.d("Loading downloaded content for user: $userId")

            val completedDownloads = downloadRepository.getCompletedDownloadsFlow().first()
            val downloadedItemIds = completedDownloads.map { it.itemId }.toSet()
            val mountedVolumeIds = storageLocationProvider.mountedVolumeIds()
            val volumeByItemId = completedDownloads.associate { it.itemId to it.storageVolumeId }
            fun isItemUnavailable(itemId: UUID): Boolean {
                val volumeId = volumeByItemId[itemId] ?: return false
                return volumeId !in mountedVolumeIds
            }

            val downloadedMovies =
                databaseRepository.getAllMovies(userId).filter { movie ->
                    movie.id in downloadedItemIds
                }

            val allShows = databaseRepository.getAllShows(userId)
            val downloadedShows = allShows.filter { show ->
                show.seasons.any { season ->
                    season.episodes.any { episode -> episode.id in downloadedItemIds }
                }
            }

            Timber.d(
                "Found ${downloadedMovies.size} movies and ${downloadedShows.size} shows with downloads"
            )

            val offlineContinueWatching = mutableListOf<AfinityItem>()

            downloadedMovies.forEach { movie ->
                if (movie.playbackPositionTicks > 0 && !movie.played) {
                    offlineContinueWatching.add(movie)
                }
            }

            allShows.forEach { show ->
                show.seasons.forEach { season ->
                    season.episodes.forEach { episode ->
                        if (
                            episode.playbackPositionTicks > 0 &&
                                !episode.played &&
                                episode.id in downloadedItemIds
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

            Timber.d(
                "Found ${sortedOfflineContinueWatching.size} items to continue watching offline"
            )

            val offlineNextUp = mutableListOf<AfinityEpisode>()
            downloadedShows.forEach { show ->
                val downloadedEpisodes =
                    show.seasons
                        .flatMap { season -> season.episodes }
                        .filter { it.id in downloadedItemIds && !isItemUnavailable(it.id) }
                        .sortedWith(compareBy({ it.parentIndexNumber }, { it.indexNumber }))
                val hasInProgress = downloadedEpisodes.any {
                    it.playbackPositionTicks > 0 && !it.played
                }
                if (!hasInProgress) {
                    downloadedEpisodes
                        .firstOrNull { !it.played && it.playbackPositionTicks == 0L }
                        ?.let { offlineNextUp.add(it) }
                }
            }

            Timber.d("Found ${offlineNextUp.size} next up episodes offline")

            val absCompleted = absDownloadRepository.getCompletedDownloadsFlow().first()
            val downloadedAudiobooks = absCompleted.filter { it.mediaType == "book" }
            val downloadedPodcastEpisodes =
                absCompleted
                    .filter { it.mediaType == "podcast" }
                    .groupBy { it.libraryItemId }
                    .map { (_, episodes) ->
                        val rep = episodes.maxByOrNull { it.updatedAt }!!
                        val count = episodes.size
                        rep.copy(
                            title = rep.authorName?.takeIf { it.isNotBlank() } ?: rep.title,
                            authorName = "$count episode${if (count > 1) "s" else ""} downloaded",
                        )
                    }

            val unavailableMovieIds =
                downloadedMovies.map { it.id }.filter { isItemUnavailable(it) }.toSet()
            val unavailableShowIds =
                downloadedShows
                    .filter { show ->
                        val downloadedEpisodeIds =
                            show.seasons
                                .flatMap { season -> season.episodes }
                                .map { it.id }
                                .filter { it in downloadedItemIds }
                        downloadedEpisodeIds.isNotEmpty() &&
                            downloadedEpisodeIds.all { isItemUnavailable(it) }
                    }
                    .map { it.id }
                    .toSet()
            val unavailableDownloadIds = unavailableMovieIds + unavailableShowIds

            val downloadedMusicTracks =
                databaseRepository.getAllMusicTracksByUser(userId).filter {
                    it.localFilePath != null
                }
            val downloadedTrackAlbumIds = downloadedMusicTracks.mapNotNull { it.albumId }.toSet()
            val downloadedMusicAlbums =
                databaseRepository.getAllMusicAlbumsByUser(userId).filter {
                    it.id in downloadedTrackAlbumIds
                }

            _uiState.update {
                it.copy(
                    downloadedMovies = downloadedMovies,
                    downloadedShows = downloadedShows,
                    offlineContinueWatching = sortedOfflineContinueWatching,
                    offlineNextUp = offlineNextUp,
                    downloadedAudiobooks = downloadedAudiobooks,
                    downloadedPodcastEpisodes = downloadedPodcastEpisodes,
                    downloadedMusicAlbums = downloadedMusicAlbums,
                    downloadedMusicTracks = downloadedMusicTracks,
                    unavailableDownloadIds = unavailableDownloadIds,
                    offlineContentLoaded = true,
                )
            }
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

    val selectedEpisodeDownloadInfo: StateFlow<DownloadInfo?> =
        _selectedEpisode
            .flatMapLatest { episode ->
                if (episode == null) {
                    flowOf(null)
                } else {
                    downloadRepository.getAllDownloadsFlow().map { downloads ->
                        downloads.find { it.itemId == episode.id }
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun selectEpisode(episode: AfinityEpisode) {
        viewModelScope.launch {
            try {
                _isLoadingEpisode.value = true

                if (offlineModeManager.isOffline.first()) {
                    _selectedEpisode.value = episode
                    _selectedEpisodeWatchlistStatus.value = false
                    _isLoadingEpisode.value = false
                    return@launch
                }

                val fullEpisode =
                    mediaRepository
                        .getItem(episode.id, fields = FieldSets.ITEM_DETAIL)
                        ?.toAfinityEpisode(mediaRepository.getBaseUrl(), null)

                _selectedEpisode.value = fullEpisode ?: episode

                try {
                    val isInWatchlist = watchlistRepository.isInWatchlist(episode.id)
                    _selectedEpisodeWatchlistStatus.value = isInWatchlist
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load episode watchlist status")
                    _selectedEpisodeWatchlistStatus.value = false
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
    }

    suspend fun getRandomUnwatchedItem(): AfinityItem? {
        if (!_isFetchingRandomItem.compareAndSet(expect = false, update = true)) return null

        return try {
            mediaRepository
                .getItems(
                    includeItemTypes = listOf("Movie", "Series"),
                    isPlayed = false,
                    sortBy = SortBy.RANDOM,
                    limit = 1,
                )
                .items
                .firstOrNull()
                ?.toAfinityItem(mediaRepository.getBaseUrl())
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch random unwatched item")
            null
        } finally {
            _isFetchingRandomItem.value = false
        }
    }

    fun toggleEpisodeFavorite(episode: AfinityEpisode) {
        itemUserDataDelegate.toggleEpisodeFavorite(viewModelScope, episode) {
            _selectedEpisode.value = episode.copy(favorite = !episode.favorite)
        }
    }

    fun toggleEpisodeWatchlist(episode: AfinityEpisode) {
        viewModelScope.launch {
            try {
                val isInWatchlist = _selectedEpisodeWatchlistStatus.value

                _selectedEpisodeWatchlistStatus.value = !isInWatchlist

                val success =
                    if (isInWatchlist) {
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
                val isNowPlayed = !episode.played
                val updatedEpisode =
                    episode.copy(
                        played = isNowPlayed,
                        playbackPositionTicks = if (!isNowPlayed) episode.runtimeTicks else 0,
                    )
                _selectedEpisode.value = updatedEpisode

                val success =
                    if (episode.played) {
                        userDataRepository.markUnwatched(episode.id)
                    } else {
                        userDataRepository.markWatched(episode.id)
                    }

                if (success) {
                    mediaChangeManager.notifyItemChanged(
                        episode.id,
                        episode.seriesId,
                        episode.seasonId,
                    )
                } else {
                    _selectedEpisode.value = episode
                }
            } catch (e: Exception) {
                Timber.e(e, "Error toggling episode watched status")
                _selectedEpisode.value = episode
            }
        }
    }

    fun onPlayTrailerClick(context: Context, item: AfinityItem) {
        Timber.d("Play trailer clicked: ${item.name}")
        val trailerUrl =
            when (item) {
                is AfinityMovie -> item.trailer
                is AfinityShow -> item.trailer
                is AfinityVideo -> item.trailer
                else -> null
            }
        IntentUtils.openYouTubeUrl(context, trailerUrl)
    }

    private suspend fun loadCombinedGenres() {
        if (offlineModeManager.isOffline.first()) return
        try {
            appDataRepository.loadCombinedGenres()
        } catch (e: Exception) {
            Timber.e(e, "Failed to load combined genres")
        }
    }

    fun loadMoviesForGenre(genre: String) {
        if (_uiState.value.genreLoadingStates[genre] == true) return

        viewModelScope.launch {
            try {
                appDataRepository.loadMoviesForGenre(genre)
            } catch (e: Exception) {
                Timber.e(e, "Failed to load movies for genre: $genre")
            }
        }
    }

    fun loadShowsForGenre(genre: String) {
        if (_uiState.value.genreLoadingStates[genre] == true) return

        viewModelScope.launch {
            try {
                appDataRepository.loadShowsForGenre(genre)
            } catch (e: Exception) {
                Timber.e(e, "Failed to load shows for genre: $genre")
            }
        }
    }

    private suspend fun loadStudios() {
        if (offlineModeManager.isOffline.first()) return
        try {
            appDataRepository.loadStudios()
        } catch (e: Exception) {
            Timber.e(e, "Failed to load studios")
        }
    }

    private suspend fun loadUpcomingEpisodes() {
        try {
            if (offlineModeManager.isOffline.first()) return
            val upcoming = mediaRepository.getUpcomingEpisodes(limit = 24)
            _uiState.update { it.copy(upcomingEpisodes = upcoming) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load upcoming episodes")
        }
    }

    fun onStudioClick(studio: AfinityStudio, navController: NavController) {
        Timber.d("Studio clicked: ${studio.name}")
        val route = Destination.createStudioContentRoute(studio.name)
        navController.navigate(route)
    }

    fun onScreenResumed() {
        if (appDataRepository.lastUserDataChangedAt.value > lastHomeRefreshedAt) {
            viewModelScope.launch {
                appDataRepository.refreshPlaybackSections()
                lastHomeRefreshedAt = System.currentTimeMillis()
            }
        }
    }

    private fun scheduleHomeDataReload() {
        val request =
            OneTimeWorkRequestBuilder<HomeDataReloadWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
                .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_HOME_RELOAD, ExistingWorkPolicy.REPLACE, request)

        Timber.d("HomeDataReloadWorker scheduled")
    }

    companion object {
        private const val WORK_HOME_RELOAD = "home_data_reload"
    }
}

sealed interface HomeSection {
    val key: String

    data class Pending(override val key: String, val title: String, val isSpotlight: Boolean) :
        HomeSection

    data class Person(override val key: String, val section: PersonSection) : HomeSection

    data class Movie(override val key: String, val section: MovieSection) : HomeSection

    data class PersonFromMovie(override val key: String, val section: PersonFromMovieSection) :
        HomeSection

    data class Genre(override val key: String, val genreItem: GenreItem) : HomeSection

    data class Spotlight(
        override val key: String,
        val title: String,
        val items: List<AfinityItem>,
    ) : HomeSection
}

private fun HomeSectionDescriptor.toHomeSection(content: HomeSectionContent?): HomeSection? =
    when (type) {
        HomeSectionType.GENRE_MOVIE ->
            HomeSection.Genre(key, GenreItem(genreName ?: title, GenreType.MOVIE))
        HomeSectionType.GENRE_SHOW ->
            HomeSection.Genre(key, GenreItem(genreName ?: title, GenreType.SHOW))
        else ->
            when (content) {
                null ->
                    HomeSection.Pending(
                        key = key,
                        title = title,
                        isSpotlight =
                            type == HomeSectionType.SPOTLIGHT_GENRE_MOVIE ||
                                type == HomeSectionType.SPOTLIGHT_GENRE_SHOW ||
                                type == HomeSectionType.SPOTLIGHT_STUDIO ||
                                type == HomeSectionType.SPOTLIGHT_BOXSET,
                    )
                HomeSectionContent.Empty -> null
                is HomeSectionContent.Person -> HomeSection.Person(key, content.section)
                is HomeSectionContent.Movie -> HomeSection.Movie(key, content.section)
                is HomeSectionContent.PersonFromMovie ->
                    HomeSection.PersonFromMovie(key, content.section)
                is HomeSectionContent.Spotlight -> HomeSection.Spotlight(key, title, content.items)
            }
    }

data class HomeUiState(
    val heroCarouselItems: List<AfinityItem> = emptyList(),
    val latestMedia: List<AfinityItem> = emptyList(),
    val continueWatching: List<AfinityItem> = emptyList(),
    val offlineContinueWatching: List<AfinityItem> = emptyList(),
    val nextUp: List<AfinityEpisode> = emptyList(),
    val offlineNextUp: List<AfinityEpisode> = emptyList(),
    val upcomingEpisodes: List<AfinityEpisode> = emptyList(),
    val latestMovies: List<AfinityMovie> = emptyList(),
    val latestTvSeries: List<AfinityShow> = emptyList(),
    val highestRated: List<AfinityItem> = emptyList(),
    val studios: List<AfinityStudio> = emptyList(),
    val combinedSections: List<HomeSection> = emptyList(),
    val genreMovies: Map<String, List<AfinityMovie>> = emptyMap(),
    val genreShows: Map<String, List<AfinityShow>> = emptyMap(),
    val genreLoadingStates: Map<String, Boolean> = emptyMap(),
    val downloadedMovies: List<AfinityMovie> = emptyList(),
    val downloadedShows: List<AfinityShow> = emptyList(),
    val downloadedAudiobooks: List<AbsDownloadInfo> = emptyList(),
    val downloadedPodcastEpisodes: List<AbsDownloadInfo> = emptyList(),
    val downloadedMusicAlbums: List<AfinityAlbum> = emptyList(),
    val downloadedMusicTracks: List<AfinityTrack> = emptyList(),
    val unavailableDownloadIds: Set<UUID> = emptySet(),
    val offlineContentLoaded: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val combineLibrarySections: Boolean = false,
    val libraries: List<AfinityCollection> = emptyList(),
    val separateMovieLibrarySections: List<Pair<AfinityCollection, List<AfinityMovie>>> =
        emptyList(),
    val separateTvLibrarySections: List<Pair<AfinityCollection, List<AfinityShow>>> = emptyList(),
    val isOffline: Boolean = false,
)
