package com.makd.afinity.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.makd.afinity.data.manager.OfflineModeManager
import com.makd.afinity.data.models.GenreItem
import com.makd.afinity.data.models.MovieSection
import com.makd.afinity.data.models.PersonFromMovieSection
import com.makd.afinity.data.models.PersonSection
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.extensions.toAfinityMovie
import com.makd.afinity.data.models.media.AfinityCollection
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.media.AfinityStudio
import com.makd.afinity.data.models.media.AfinityVideo
import com.makd.afinity.data.models.media.toAfinityEpisode
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.PersonKind.ACTOR
import org.jellyfin.sdk.model.api.PersonKind.DIRECTOR
import org.jellyfin.sdk.model.api.PersonKind.WRITER
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

    private val loadedRecommendationSections = mutableListOf<HomeSection>()

    private val recommendationMutex = Mutex()

    private val renderedPeopleNames = mutableSetOf<String>()
    private val renderedItemIds = mutableSetOf<java.util.UUID>()
    private val renderedWatchedMovies = mutableSetOf<java.util.UUID>()
    private val renderedStarringWatchedMovies = mutableSetOf<java.util.UUID>()
    private val renderedActorNames = mutableSetOf<String>()

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
                combinedGenreSections(combinedGenres)
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
                } else {
                    refresh()
                }
            }
        }


        viewModelScope.launch {
            loadNewHomescreenSections()
        }
    }

    private fun combinedGenreSections(genreItems: List<GenreItem>) {
        val allSections = mutableListOf<HomeSection>()

        allSections.addAll(loadedRecommendationSections)

        genreItems.forEach { genreItem ->
            allSections.add(HomeSection.Genre(genreItem))
        }

        val shuffledSections = allSections.shuffled()
        _uiState.value = _uiState.value.copy(combinedSections = shuffledSections)

        Timber.d("Combined ${loadedRecommendationSections.size} recommendation sections + ${genreItems.size} genres = ${shuffledSections.size} total sections (shuffled)")
    }

    private suspend fun loadNewHomescreenSections() {
        try {
            if (offlineModeManager.isOffline.first()) {
                Timber.d("Skipping new sections in offline mode")
                return
            }

            loadedRecommendationSections.clear()

            viewModelScope.launch(Dispatchers.IO) {
                try {
                    coroutineScope {
                        val actorTask = async { loadAllActorSections() }
                        val directorTask = async { loadAllDirectorSections() }
                        val writerTask = async { loadAllWriterSections() }
                        val becauseYouWatchedTask = async { loadAllBecauseYouWatchedSections() }
                        val actorFromRecentTask = async { loadAllActorFromRecentSections() }

                        awaitAll(
                            actorTask,
                            directorTask,
                            writerTask,
                            becauseYouWatchedTask,
                            actorFromRecentTask
                        )
                    }

                    Timber.d("Loaded ${loadedRecommendationSections.size} total recommendation sections")

                    withContext(Dispatchers.Main) {
                        appDataRepository.combinedGenres.value.let { genres ->
                            if (genres.isNotEmpty()) {
                                combinedGenreSections(genres)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load recommendation sections in background")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to start recommendation sections loading")
        }
    }

    private suspend fun loadAllActorSections() {
        try {
            val topActors = appDataRepository.getTopPeople(
                type = ACTOR,
                limit = 75,
                minAppearances = 5
            )

            val availableActors = topActors.filterNot { it.person.name in renderedPeopleNames }
            val maxActorSections = 15
            val selectedActors = availableActors.shuffled().take(maxActorSections)

            coroutineScope {
                selectedActors.map { actor ->
                    async {
                        try {
                            val section = appDataRepository.getPersonSection(
                                personWithCount = actor,
                                sectionType = com.makd.afinity.data.models.PersonSectionType.STARRING
                            )

                            if (section != null) {
                                recommendationMutex.withLock {
                                    renderedPeopleNames.add(actor.person.name)
                                    section.items.forEach { renderedItemIds.add(it.id) }
                                    loadedRecommendationSections.add(HomeSection.Person(section))
                                }
                                Timber.d("Loaded 'Starring ${section.person.name}' section (${section.items.size} items)")
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to load actor section for ${actor.person.name}")
                        }
                    }
                }.awaitAll()
            }

            Timber.d("Loaded ${loadedRecommendationSections.count { it is HomeSection.Person && it.section.sectionType == com.makd.afinity.data.models.PersonSectionType.STARRING }} actor sections (max: $maxActorSections)")
        } catch (e: Exception) {
            Timber.e(e, "Failed to load actor sections")
        }
    }

    private suspend fun loadAllDirectorSections() {
        try {
            val topDirectors = appDataRepository.getTopPeople(
                type = DIRECTOR,
                limit = 75,
                minAppearances = 5
            )

            val availableDirectors =
                topDirectors.filterNot { it.person.name in renderedPeopleNames }
            val maxDirectorSections = 8
            val selectedDirectors = availableDirectors.shuffled().take(maxDirectorSections)

            coroutineScope {
                selectedDirectors.map { director ->
                    async {
                        try {
                            val section = appDataRepository.getPersonSection(
                                personWithCount = director,
                                sectionType = com.makd.afinity.data.models.PersonSectionType.DIRECTED_BY
                            )

                            if (section != null) {
                                recommendationMutex.withLock {
                                    renderedPeopleNames.add(director.person.name)
                                    section.items.forEach { renderedItemIds.add(it.id) }
                                    loadedRecommendationSections.add(HomeSection.Person(section))
                                }
                                Timber.d("Loaded 'Directed by ${section.person.name}' section (${section.items.size} items)")
                            }
                        } catch (e: Exception) {
                            Timber.w(
                                e,
                                "Failed to load director section for ${director.person.name}"
                            )
                        }
                    }
                }.awaitAll()
            }

            Timber.d("Loaded ${loadedRecommendationSections.count { it is HomeSection.Person && it.section.sectionType == com.makd.afinity.data.models.PersonSectionType.DIRECTED_BY }} director sections (max: $maxDirectorSections)")
        } catch (e: Exception) {
            Timber.e(e, "Failed to load director sections")
        }
    }

    private suspend fun loadAllWriterSections() {
        try {
            val topWriters = appDataRepository.getTopPeople(
                type = WRITER,
                limit = 50,
                minAppearances = 3
            )

            val availableWriters = topWriters.filterNot { it.person.name in renderedPeopleNames }
            val maxWriterSections = 7
            val selectedWriters = availableWriters.shuffled().take(maxWriterSections)

            coroutineScope {
                selectedWriters.map { writer ->
                    async {
                        try {
                            val section = appDataRepository.getPersonSection(
                                personWithCount = writer,
                                sectionType = com.makd.afinity.data.models.PersonSectionType.WRITTEN_BY
                            )

                            if (section != null) {
                                recommendationMutex.withLock {
                                    renderedPeopleNames.add(writer.person.name)
                                    section.items.forEach { renderedItemIds.add(it.id) }
                                    loadedRecommendationSections.add(HomeSection.Person(section))
                                }
                                Timber.d("Loaded 'Written by ${section.person.name}' section (${section.items.size} items)")
                            }
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to load writer section for ${writer.person.name}")
                        }
                    }
                }.awaitAll()
            }

            Timber.d("Loaded ${loadedRecommendationSections.count { it is HomeSection.Person && it.section.sectionType == com.makd.afinity.data.models.PersonSectionType.WRITTEN_BY }} writer sections (max: $maxWriterSections)")
        } catch (e: Exception) {
            Timber.e(e, "Failed to load writer sections")
        }
    }

    private suspend fun loadAllBecauseYouWatchedSections() {
        try {
            val maxBecauseYouWatchedSections = 7
            var loadedCount = 0
            while (loadedCount < maxBecauseYouWatchedSections) {
                val referenceMovie = appDataRepository.getRandomRecentlyWatchedMovie(
                    excludedMovies = renderedWatchedMovies
                ) ?: break

                renderedWatchedMovies.add(referenceMovie.id)

                val similarMovies = jellyfinRepository.getSimilarMovies(
                    movieId = referenceMovie.id,
                    limit = 32
                )
                    .filterNot { it.id in renderedItemIds }
                    .shuffled()
                    .take(20)

                if (similarMovies.size >= 5) {
                    val section = MovieSection(
                        referenceMovie = referenceMovie,
                        recommendedItems = similarMovies,
                        sectionType = com.makd.afinity.data.models.MovieSectionType.BECAUSE_YOU_WATCHED
                    )

                    similarMovies.forEach { renderedItemIds.add(it.id) }
                    loadedRecommendationSections.add(HomeSection.Movie(section))
                    loadedCount++
                    Timber.d("Loaded 'Because you watched ${referenceMovie.name}' section (${similarMovies.size} items)")
                } else {
                    break
                }
            }

            Timber.d("Loaded $loadedCount 'Because you watched' sections (max: $maxBecauseYouWatchedSections)")
        } catch (e: Exception) {
            Timber.e(e, "Failed to load 'Because you watched' sections")
        }
    }

    private suspend fun loadAllActorFromRecentSections() {
        try {
            val maxActorFromRecentSections = 3
            var loadedCount = 0
            while (loadedCount < maxActorFromRecentSections) {
                val randomMovie = appDataRepository.getRandomRecentlyWatchedMovie(
                    excludedMovies = renderedStarringWatchedMovies
                ) ?: break

                renderedStarringWatchedMovies.add(randomMovie.id)

                val movieItem = jellyfinRepository.getItem(
                    itemId = randomMovie.id,
                    fields = listOf(org.jellyfin.sdk.model.api.ItemFields.PEOPLE)
                )

                val movieWithPeople = movieItem?.toAfinityMovie(jellyfinRepository.getBaseUrl())
                if (movieWithPeople == null) {
                    Timber.d("Failed to fetch or convert movie '${randomMovie.name}'")
                    continue
                }

                val availableActors = movieWithPeople.people
                    .filter { it.type == ACTOR }
                    .filterNot { it.name in renderedActorNames }

                if (availableActors.isEmpty()) {
                    Timber.d("No available actors in '${randomMovie.name}'")
                    continue
                }

                val selectedActor = availableActors.take(3).randomOrNull() ?: continue
                renderedActorNames.add(selectedActor.name)

                val allActorItems = jellyfinRepository.getPersonItems(
                    personId = selectedActor.id,
                    includeItemTypes = listOf("MOVIE"),
                    fields = listOf(org.jellyfin.sdk.model.api.ItemFields.PEOPLE)
                )

                val actorMovies = allActorItems
                    .filter { item ->
                        when (item) {
                            is AfinityMovie -> {
                                item.people.any { person ->
                                    person.id == selectedActor.id && person.type == ACTOR
                                }
                            }

                            else -> false
                        }
                    }
                    .filterIsInstance<AfinityMovie>()
                    .filterNot { it.id == randomMovie.id || it.id in renderedItemIds }
                    .shuffled()
                    .take(20)

                if (actorMovies.size >= 5) {
                    val section = PersonFromMovieSection(
                        person = selectedActor,
                        referenceMovie = movieWithPeople,
                        items = actorMovies
                    )

                    actorMovies.forEach { renderedItemIds.add(it.id) }
                    loadedRecommendationSections.add(HomeSection.PersonFromMovie(section))
                    loadedCount++
                    Timber.d("Loaded 'Starring ${selectedActor.name} because you watched ${randomMovie.name}' section (${actorMovies.size} items)")
                }
            }

            Timber.d("Loaded $loadedCount 'Starring actor from recent' sections (max: $maxActorFromRecentSections)")
        } catch (e: Exception) {
            Timber.e(e, "Failed to load actor from recent sections")
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
                    _selectedEpisode.value = fullEpisode
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

    fun refresh() {
        viewModelScope.launch {
            appDataRepository.reloadHomeData()
            loadStudios()
            loadCombinedGenres()
            loadNewHomescreenSections()
        }
    }

}

sealed interface HomeSection {
    data class Person(val section: PersonSection) : HomeSection
    data class Movie(val section: MovieSection) : HomeSection
    data class PersonFromMovie(val section: PersonFromMovieSection) : HomeSection
    data class Genre(val genreItem: GenreItem) : HomeSection
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
    val combinedSections: List<HomeSection> = emptyList(),
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