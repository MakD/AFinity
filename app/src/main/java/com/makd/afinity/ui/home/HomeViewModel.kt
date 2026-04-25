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
import com.makd.afinity.data.manager.OfflineModeManager
import com.makd.afinity.data.manager.PlaybackEvent
import com.makd.afinity.data.manager.PlaybackStateManager
import com.makd.afinity.data.models.GenreItem
import com.makd.afinity.data.models.GenreType
import com.makd.afinity.data.models.MovieSection
import com.makd.afinity.data.models.PersonFromMovieSection
import com.makd.afinity.data.models.PersonSection
import com.makd.afinity.data.models.audiobookshelf.AbsDownloadInfo
import com.makd.afinity.data.models.download.DownloadInfo
import com.makd.afinity.data.models.extensions.toAfinityMovie
import com.makd.afinity.data.models.media.AfinityCollection
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.media.AfinityStudio
import com.makd.afinity.data.models.media.AfinityVideo
import com.makd.afinity.data.models.media.toAfinityEpisode
import com.makd.afinity.data.repository.AppDataRepository
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.PreferencesRepository
import com.makd.afinity.data.repository.audiobookshelf.AbsDownloadRepository
import com.makd.afinity.data.repository.auth.AuthRepository
import com.makd.afinity.data.repository.download.DownloadRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.userdata.UserDataRepository
import com.makd.afinity.data.repository.watchlist.WatchlistRepository
import com.makd.afinity.data.workers.HomeDataReloadWorker
import com.makd.afinity.navigation.Destination
import com.makd.afinity.ui.item.delegates.ItemDownloadDelegate
import com.makd.afinity.ui.item.delegates.ItemUserDataDelegate
import com.makd.afinity.ui.utils.IntentUtils
import com.makd.afinity.util.NetworkConnectivityMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.model.api.PersonKind.ACTOR
import org.jellyfin.sdk.model.api.PersonKind.DIRECTOR
import org.jellyfin.sdk.model.api.PersonKind.WRITER
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

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
    private val offlineModeManager: OfflineModeManager,
    private val authRepository: AuthRepository,
    private val mediaRepository: MediaRepository,
    private val playbackStateManager: PlaybackStateManager,
    private val itemDownloadDelegate: ItemDownloadDelegate,
    private val itemUserDataDelegate: ItemUserDataDelegate,
    private val preferencesRepository: PreferencesRepository,
    private val networkMonitor: NetworkConnectivityMonitor,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())

    val canDownload: StateFlow<Boolean> =
        preferencesRepository
            .getDownloadWifiOnlyFlow()
            .combine(networkMonitor.isOnWifiFlow) { wifiOnly, onWifi -> !wifiOnly || onWifi }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val loadedRecommendationSections = mutableListOf<HomeSection>()
    private val loadedSpotlightSections = mutableListOf<HomeSection.Spotlight>()

    private var cachedShuffledGenres: List<HomeSection.Genre> = emptyList()

    private val recommendationMutex = Mutex()

    private var recommendationLoadingJob: kotlinx.coroutines.Job? = null

    private val renderedPeopleNames = mutableSetOf<String>()
    private val renderedItemIds = mutableSetOf<java.util.UUID>()
    private val renderedWatchedMovies = mutableSetOf<java.util.UUID>()
    private val renderedStarringWatchedMovies = mutableSetOf<java.util.UUID>()
    private val renderedActorNames = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            appDataRepository.isInitialDataLoaded.collect { isLoaded ->
                if (!isLoaded) {
                    Timber.d(
                        "Data cleared detected (Session Switch/Clear), resetting HomeViewModel UI state"
                    )
                    loadedRecommendationSections.clear()
                    loadedSpotlightSections.clear()
                    cachedShuffledGenres = emptyList()
                    renderedPeopleNames.clear()
                    renderedItemIds.clear()
                    renderedWatchedMovies.clear()
                    renderedStarringWatchedMovies.clear()
                    renderedActorNames.clear()

                    _uiState.value = HomeUiState()
                } else {
                    Timber.d(
                        "Initial Data Loaded: Triggering secondary content load (Studios, Genres, Recs)"
                    )
                    launch {
                        coroutineScope {
                            launch { loadStudios() }
                            launch { loadCombinedGenres() }
                            launch { loadUpcomingEpisodes() }
                        }
                        loadNewHomescreenSections()
                        loadDownloadedContent()
                    }
                }
            }
        }

        viewModelScope.launch {
            appDataRepository.latestMedia.collect { latestMedia ->
                _uiState.update { it.copy(latestMedia = latestMedia) }
            }
        }

        viewModelScope.launch {
            appDataRepository.heroCarouselItems.collect { heroItems ->
                _uiState.update { it.copy(heroCarouselItems = heroItems) }
            }
        }

        viewModelScope.launch {
            appDataRepository.continueWatching.collect { continueWatching ->
                _uiState.update { it.copy(continueWatching = continueWatching) }
            }
        }

        viewModelScope.launch {
            appDataRepository.nextUp.collect { nextUp ->
                _uiState.update { it.copy(nextUp = nextUp) }
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
            appDataRepository.combinedGenres.collect { combinedGenres ->
                updateCombinedSections(genres = combinedGenres)
            }
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
            offlineModeManager.isOffline.collect { isOffline ->
                Timber.d("Offline mode changed: $isOffline")
                _uiState.update { it.copy(isOffline = isOffline) }

                if (isOffline) {
                    loadDownloadedContent()
                } else {
                    scheduleHomeDataReload()
                }
            }
        }
        viewModelScope.launch {
            playbackStateManager.playbackEvents.collect { event ->
                when (event) {
                    is PlaybackEvent.Stopped -> {
                        Timber.d("HomeViewModel received fast Stopped event for ${event.itemId}")
                        _uiState.update { state ->
                            state.copy(
                                continueWatching =
                                    state.continueWatching.filterNot { it.id == event.itemId },
                                nextUp = state.nextUp.filterNot { it.id == event.itemId },
                            )
                        }
                    }

                    is PlaybackEvent.Synced -> {
                        Timber.d("HomeViewModel received sync for ${event.itemId}")
                        val syncedItem = mediaRepository.getItemById(event.itemId) ?: return@collect

                        val targetItem =
                            when (syncedItem) {
                                is AfinityEpisode ->
                                    mediaRepository.getItemById(syncedItem.seriesId)

                                is AfinitySeason -> mediaRepository.getItemById(syncedItem.seriesId)

                                else -> syncedItem
                            } ?: return@collect

                        appDataRepository.updateItemInCaches(targetItem)
                        updateItemInDynamicSections(targetItem)
                    }
                }
            }
        }
    }

    private fun updateCombinedSections(genres: List<GenreItem>) {
        if (cachedShuffledGenres.isEmpty() && genres.isNotEmpty()) {
            cachedShuffledGenres = genres.map { HomeSection.Genre(it) }.shuffled()
        } else if (genres.isNotEmpty() && cachedShuffledGenres.size != genres.size) {
            cachedShuffledGenres = genres.map { HomeSection.Genre(it) }.shuffled()
        }

        if (cachedShuffledGenres.isEmpty() && loadedRecommendationSections.isEmpty()) return

        val finalLayout = mutableListOf<HomeSection>()
        val genreIterator = cachedShuffledGenres.iterator()
        val recIterator = loadedRecommendationSections.iterator()

        var counter = 0

        while (genreIterator.hasNext()) {
            finalLayout.add(genreIterator.next())
            counter++

            if (counter % 2 == 0 && recIterator.hasNext()) {
                finalLayout.add(recIterator.next())
            }
        }

        while (recIterator.hasNext()) {
            finalLayout.add(recIterator.next())
        }

        if (loadedSpotlightSections.isNotEmpty()) {
            val positions =
                computeSpotlightPositions(finalLayout.size, loadedSpotlightSections.size)
            positions.sorted().forEachIndexed { offset, pos ->
                finalLayout.add(
                    (pos + offset).coerceAtMost(finalLayout.size),
                    loadedSpotlightSections[offset],
                )
            }
        }

        _uiState.update { it.copy(combinedSections = finalLayout) }
        Timber.d(
            "Updated home layout with ${finalLayout.size} sections (${loadedSpotlightSections.size} spotlights)"
        )
    }

    private fun loadNewHomescreenSections() {
        recommendationLoadingJob?.cancel()
        recommendationLoadingJob =
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    if (offlineModeManager.isOffline.first()) {
                        Timber.d("Skipping new sections in offline mode")
                        return@launch
                    }

                    loadedRecommendationSections.clear()
                    renderedPeopleNames.clear()
                    renderedItemIds.clear()
                    renderedWatchedMovies.clear()
                    renderedStarringWatchedMovies.clear()
                    renderedActorNames.clear()

                    coroutineScope {
                        val actorTask = async { loadAllActorSections() }
                        val directorTask = async { loadAllDirectorSections() }
                        val writerTask = async { loadAllWriterSections() }
                        val becauseYouWatchedTask = async { loadAllBecauseYouWatchedSections() }
                        val actorFromRecentTask = async { loadAllActorFromRecentSections() }
                        val spotlightTask = async { loadSpotlightSections() }

                        awaitAll(
                            actorTask,
                            directorTask,
                            writerTask,
                            becauseYouWatchedTask,
                            actorFromRecentTask,
                            spotlightTask,
                        )
                    }

                    Timber.d(
                        "Loaded ${loadedRecommendationSections.size} total recommendation sections"
                    )

                    withContext(Dispatchers.Main) {
                        appDataRepository.combinedGenres.value.let { genres ->
                            updateCombinedSections(genres)
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load recommendation sections")
                }
            }
    }

    private suspend fun loadAllActorSections() {
        try {
            val topActors =
                appDataRepository.getTopPeople(type = ACTOR, limit = 75, minAppearances = 5)

            val availableActors = topActors.filterNot { it.person.name in renderedPeopleNames }
            val maxActorSections = 15
            val selectedActors = availableActors.shuffled().take(maxActorSections)

            coroutineScope {
                selectedActors
                    .map { actor ->
                        async {
                            try {
                                val section =
                                    appDataRepository.getPersonSection(
                                        personWithCount = actor,
                                        sectionType =
                                            com.makd.afinity.data.models.PersonSectionType.STARRING,
                                    )

                                if (section != null) {
                                    recommendationMutex.withLock {
                                        renderedPeopleNames.add(actor.person.name)
                                        section.items.forEach { renderedItemIds.add(it.id) }
                                        loadedRecommendationSections.add(
                                            HomeSection.Person(section)
                                        )
                                    }
                                    Timber.d(
                                        "Loaded 'Starring ${section.person.name}' section (${section.items.size} items)"
                                    )
                                }
                            } catch (e: Exception) {
                                Timber.w(e, "Failed to load actor section for ${actor.person.name}")
                            }
                        }
                    }
                    .awaitAll()
            }
            Timber.d(
                "Loaded ${loadedRecommendationSections.count { it is HomeSection.Person && it.section.sectionType == com.makd.afinity.data.models.PersonSectionType.STARRING }} actor sections (max: $maxActorSections)"
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to load actor sections")
        }
    }

    private suspend fun loadAllDirectorSections() {
        try {
            val topDirectors =
                appDataRepository.getTopPeople(type = DIRECTOR, limit = 75, minAppearances = 5)

            val availableDirectors = topDirectors.filterNot {
                it.person.name in renderedPeopleNames
            }
            val maxDirectorSections = 8
            val selectedDirectors = availableDirectors.shuffled().take(maxDirectorSections)

            coroutineScope {
                selectedDirectors
                    .map { director ->
                        async {
                            try {
                                val section =
                                    appDataRepository.getPersonSection(
                                        personWithCount = director,
                                        sectionType =
                                            com.makd.afinity.data.models.PersonSectionType
                                                .DIRECTED_BY,
                                    )

                                if (section != null) {
                                    recommendationMutex.withLock {
                                        renderedPeopleNames.add(director.person.name)
                                        section.items.forEach { renderedItemIds.add(it.id) }
                                        loadedRecommendationSections.add(
                                            HomeSection.Person(section)
                                        )
                                    }
                                    Timber.d(
                                        "Loaded 'Directed by ${section.person.name}' section (${section.items.size} items)"
                                    )
                                }
                            } catch (e: Exception) {
                                Timber.w(
                                    e,
                                    "Failed to load director section for ${director.person.name}",
                                )
                            }
                        }
                    }
                    .awaitAll()
            }
            Timber.d(
                "Loaded ${loadedRecommendationSections.count { it is HomeSection.Person && it.section.sectionType == com.makd.afinity.data.models.PersonSectionType.DIRECTED_BY }} director sections (max: $maxDirectorSections)"
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to load director sections")
        }
    }

    private suspend fun loadAllWriterSections() {
        try {
            val topWriters =
                appDataRepository.getTopPeople(type = WRITER, limit = 50, minAppearances = 3)

            val availableWriters = topWriters.filterNot { it.person.name in renderedPeopleNames }
            val maxWriterSections = 7
            val selectedWriters = availableWriters.shuffled().take(maxWriterSections)

            coroutineScope {
                selectedWriters
                    .map { writer ->
                        async {
                            try {
                                val section =
                                    appDataRepository.getPersonSection(
                                        personWithCount = writer,
                                        sectionType =
                                            com.makd.afinity.data.models.PersonSectionType
                                                .WRITTEN_BY,
                                    )

                                if (section != null) {
                                    recommendationMutex.withLock {
                                        renderedPeopleNames.add(writer.person.name)
                                        section.items.forEach { renderedItemIds.add(it.id) }
                                        loadedRecommendationSections.add(
                                            HomeSection.Person(section)
                                        )
                                    }
                                    Timber.d(
                                        "Loaded 'Written by ${section.person.name}' section (${section.items.size} items)"
                                    )
                                }
                            } catch (e: Exception) {
                                Timber.w(
                                    e,
                                    "Failed to load writer section for ${writer.person.name}",
                                )
                            }
                        }
                    }
                    .awaitAll()
            }
            Timber.d(
                "Loaded ${loadedRecommendationSections.count { it is HomeSection.Person && it.section.sectionType == com.makd.afinity.data.models.PersonSectionType.WRITTEN_BY }} writer sections (max: $maxWriterSections)"
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to load writer sections")
        }
    }

    private suspend fun loadAllBecauseYouWatchedSections() {
        try {
            val maxBecauseYouWatchedSections = 7
            var loadedCount = 0
            while (loadedCount < maxBecauseYouWatchedSections) {
                val referenceMovie =
                    appDataRepository.getRandomRecentlyWatchedMovie(
                        excludedMovies = renderedWatchedMovies
                    ) ?: break

                renderedWatchedMovies.add(referenceMovie.id)

                val similarMovies =
                    mediaRepository
                        .getSimilarMovies(movieId = referenceMovie.id, limit = 32)
                        .filterNot { it.id in renderedItemIds }
                        .shuffled()
                        .take(20)

                if (similarMovies.size >= 5) {
                    val section =
                        MovieSection(
                            referenceMovie = referenceMovie,
                            recommendedItems = similarMovies,
                            sectionType =
                                com.makd.afinity.data.models.MovieSectionType.BECAUSE_YOU_WATCHED,
                        )

                    similarMovies.forEach { renderedItemIds.add(it.id) }
                    loadedRecommendationSections.add(HomeSection.Movie(section))
                    loadedCount++
                    Timber.d(
                        "Loaded 'Because you watched ${referenceMovie.name}' section (${similarMovies.size} items)"
                    )
                } else {
                    break
                }
            }
            Timber.d(
                "Loaded $loadedCount 'Because you watched' sections (max: $maxBecauseYouWatchedSections)"
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to load 'Because you watched' sections")
        }
    }

    private suspend fun loadAllActorFromRecentSections() {
        try {
            val maxActorFromRecentSections = 3
            var loadedCount = 0
            while (loadedCount < maxActorFromRecentSections) {
                val randomMovie =
                    appDataRepository.getRandomRecentlyWatchedMovie(
                        excludedMovies = renderedStarringWatchedMovies
                    ) ?: break

                renderedStarringWatchedMovies.add(randomMovie.id)

                val movieItem =
                    mediaRepository.getItem(
                        itemId = randomMovie.id,
                        fields = listOf(org.jellyfin.sdk.model.api.ItemFields.PEOPLE),
                    )

                val movieWithPeople = movieItem?.toAfinityMovie(mediaRepository.getBaseUrl())
                if (movieWithPeople == null) {
                    Timber.d("Failed to fetch or convert movie '${randomMovie.name}'")
                    continue
                }

                val availableActors =
                    movieWithPeople.people
                        .filter { it.type == ACTOR }
                        .filterNot { it.name in renderedActorNames }

                if (availableActors.isEmpty()) {
                    Timber.d("No available actors in '${randomMovie.name}'")
                    continue
                }

                val selectedActor = availableActors.take(3).randomOrNull() ?: continue
                renderedActorNames.add(selectedActor.name)

                val allActorItems =
                    mediaRepository.getPersonItems(
                        personId = selectedActor.id,
                        includeItemTypes = listOf("MOVIE"),
                        fields = listOf(org.jellyfin.sdk.model.api.ItemFields.PEOPLE),
                    )

                val actorMovies =
                    allActorItems
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
                    val section =
                        PersonFromMovieSection(
                            person = selectedActor,
                            referenceMovie = movieWithPeople,
                            items = actorMovies,
                        )

                    actorMovies.forEach { renderedItemIds.add(it.id) }
                    loadedRecommendationSections.add(HomeSection.PersonFromMovie(section))
                    loadedCount++
                    Timber.d(
                        "Loaded 'Starring ${selectedActor.name} because you watched ${randomMovie.name}' section (${actorMovies.size} items)"
                    )
                }
            }
            Timber.d(
                "Loaded $loadedCount 'Starring actor from recent' sections (max: $maxActorFromRecentSections)"
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to load actor from recent sections")
        }
    }

    private suspend fun loadSpotlightSections() {
        try {
            loadedSpotlightSections.clear()
            val genres = appDataRepository.combinedGenres.value
            val movieGenres = genres.filter { it.type == GenreType.MOVIE }.shuffled().take(7)
            val showGenres = genres.filter { it.type == GenreType.SHOW }.shuffled().take(7)
            val studios =
                try {
                    mediaRepository.getStudios(limit = 50)
                } catch (e: Exception) {
                    emptyList()
                }
            val selectedStudios = studios.shuffled().take(10)

            coroutineScope {
                val tasks = buildList {
                    movieGenres.forEach { genre ->
                        add(
                            async {
                                try {
                                    val items =
                                        mediaRepository.getTopRatedByGenre(
                                            genre.name,
                                            GenreType.MOVIE,
                                            limit = 20,
                                        )
                                    if (items.size >= 3) {
                                        recommendationMutex.withLock {
                                            loadedSpotlightSections.add(
                                                HomeSection.Spotlight(
                                                    title = "Top ${genre.name} Movies",
                                                    type = SpotlightType.GENRE_MOVIE,
                                                    items = items,
                                                )
                                            )
                                        }
                                        Timber.d(
                                            "Loaded genre spotlight: ${genre.name} Movies (${items.size} items)"
                                        )
                                    }
                                } catch (e: Exception) {
                                    Timber.w(e, "Failed spotlight for movie genre: ${genre.name}")
                                }
                            }
                        )
                    }
                    showGenres.forEach { genre ->
                        add(
                            async {
                                try {
                                    val items =
                                        mediaRepository.getTopRatedByGenre(
                                            genre.name,
                                            GenreType.SHOW,
                                            limit = 20,
                                        )
                                    if (items.size >= 3) {
                                        recommendationMutex.withLock {
                                            loadedSpotlightSections.add(
                                                HomeSection.Spotlight(
                                                    title = "Top ${genre.name} Series",
                                                    type = SpotlightType.GENRE_SHOW,
                                                    items = items,
                                                )
                                            )
                                        }
                                        Timber.d(
                                            "Loaded genre spotlight: ${genre.name} Series (${items.size} items)"
                                        )
                                    }
                                } catch (e: Exception) {
                                    Timber.w(e, "Failed spotlight for show genre: ${genre.name}")
                                }
                            }
                        )
                    }
                    selectedStudios.forEach { studio ->
                        add(
                            async {
                                try {
                                    val items =
                                        mediaRepository.getTopRatedByStudio(studio.name, limit = 20)
                                    if (items.size >= 3) {
                                        recommendationMutex.withLock {
                                            loadedSpotlightSections.add(
                                                HomeSection.Spotlight(
                                                    title = "Best of ${studio.name}",
                                                    type = SpotlightType.STUDIO,
                                                    items = items,
                                                )
                                            )
                                        }
                                        Timber.d(
                                            "Loaded studio spotlight: ${studio.name} (${items.size} items)"
                                        )
                                    }
                                } catch (e: Exception) {
                                    Timber.w(e, "Failed studio spotlight")
                                }
                            }
                        )
                    }

                    add(
                        async {
                            try {
                                val boxSets =
                                    mediaRepository.getBoxSetsForSpotlight(
                                        minChildCount = 3,
                                        maxBoxSets = 15,
                                    )
                                val selected = boxSets.shuffled().take(8)
                                selected.forEach { (boxSet, children) ->
                                    recommendationMutex.withLock {
                                        loadedSpotlightSections.add(
                                            HomeSection.Spotlight(
                                                title = boxSet.name,
                                                type = SpotlightType.BOXSET,
                                                items = children,
                                            )
                                        )
                                    }
                                }
                                Timber.d("Loaded ${selected.size} boxset spotlight sections")
                            } catch (e: Exception) {
                                Timber.w(e, "Failed to load boxset spotlights")
                            }
                        }
                    )
                }
                tasks.awaitAll()
            }
            val seenIds = mutableSetOf<java.util.UUID>()
            val deduplicated =
                loadedSpotlightSections.shuffled().mapNotNull { section ->
                    val uniqueItems = section.items.filter { seenIds.add(it.id) }.take(10)
                    if (uniqueItems.size < 3) null else section.copy(items = uniqueItems)
                }
            loadedSpotlightSections.clear()
            loadedSpotlightSections.addAll(deduplicated)

            if (loadedSpotlightSections.size > 20) {
                loadedSpotlightSections.subList(20, loadedSpotlightSections.size).clear()
            }
            Timber.d("Loaded ${loadedSpotlightSections.size} spotlight sections total")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to load spotlight sections")
        }
    }

    private fun computeSpotlightPositions(listSize: Int, count: Int): List<Int> {
        if (listSize == 0 || count == 0) return emptyList()
        val chunkSize = (listSize / (count + 1)).coerceAtLeast(1)

        val rawPositions =
            (1..count)
                .map { i -> (i * chunkSize + (-2..2).random()).coerceIn(1, listSize) }
                .sorted()

        val adjustedPositions = mutableListOf<Int>()
        var lastPos = -2

        for (pos in rawPositions) {
            var newPos = if (pos <= lastPos + 1) lastPos + 2 else pos
            newPos = newPos.coerceAtMost(listSize)
            adjustedPositions.add(newPos)
            lastPos = newPos
        }

        return adjustedPositions.distinct()
    }

    private suspend fun loadDownloadedContent() {
        try {
            val userId = authRepository.currentUser.value?.id ?: return

            Timber.d("Loading downloaded content for user: $userId")

            val completedDownloads = downloadRepository.getCompletedDownloadsFlow().first()
            val downloadedItemIds = completedDownloads.map { it.itemId }.toSet()

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

            _uiState.update {
                it.copy(
                    downloadedMovies = downloadedMovies,
                    downloadedShows = downloadedShows,
                    offlineContinueWatching = sortedOfflineContinueWatching,
                    downloadedAudiobooks = downloadedAudiobooks,
                    downloadedPodcastEpisodes = downloadedPodcastEpisodes,
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

    private val _selectedEpisodeDownloadInfo = MutableStateFlow<DownloadInfo?>(null)
    val selectedEpisodeDownloadInfo: StateFlow<DownloadInfo?> =
        _selectedEpisodeDownloadInfo.asStateFlow()

    fun selectEpisode(episode: AfinityEpisode) {
        viewModelScope.launch {
            try {
                _isLoadingEpisode.value = true

                val fullEpisode =
                    mediaRepository
                        .getItem(episode.id, fields = FieldSets.ITEM_DETAIL)
                        ?.toAfinityEpisode(mediaRepository.getBaseUrl(), null)

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
        itemUserDataDelegate.toggleEpisodeFavorite(viewModelScope, episode) {
            _selectedEpisode.value = episode.copy(favorite = !episode.favorite)
        }
    }

    fun onDownloadClick() {
        itemDownloadDelegate.onDownloadClick(
            scope = viewModelScope,
            item = _selectedEpisode.value,
            showQualityDialog = { _uiState.update { it.copy(showQualityDialog = true) } },
        )
    }

    fun onQualitySelected(sourceId: String) {
        itemDownloadDelegate.onQualitySelected(
            scope = viewModelScope,
            item = _selectedEpisode.value,
            sourceId = sourceId,
            hideQualityDialog = { dismissQualityDialog() },
        )
    }

    fun dismissQualityDialog() {
        _uiState.update { it.copy(showQualityDialog = false) }
    }

    fun pauseDownload() =
        itemDownloadDelegate.pauseDownload(viewModelScope, _selectedEpisodeDownloadInfo.value)

    fun resumeDownload() =
        itemDownloadDelegate.resumeDownload(viewModelScope, _selectedEpisodeDownloadInfo.value)

    fun cancelDownload() =
        itemDownloadDelegate.cancelDownload(viewModelScope, _selectedEpisodeDownloadInfo.value)

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
                    mediaRepository.refreshItemUserData(episode.id, FieldSets.REFRESH_USER_DATA)
                    playbackStateManager.notifyItemChanged(episode.id)
                    if (updatedEpisode.played) {
                        mediaRepository.invalidateNextUpCache()
                    }
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

    fun refresh() {
        viewModelScope.launch {
            appDataRepository.reloadHomeData()

            coroutineScope {
                launch { loadStudios() }
                launch { loadCombinedGenres() }
                launch { loadUpcomingEpisodes() }
            }

            loadNewHomescreenSections()
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

    private suspend fun updateItemInDynamicSections(updatedItem: AfinityItem) {
        val targetId = updatedItem.id

        var highestRatedChanged = false
        val currentHighestRated = _uiState.value.highestRated.toMutableList()
        val hrIndex = currentHighestRated.indexOfFirst { it.id == targetId }
        if (hrIndex != -1) {
            currentHighestRated[hrIndex] = updatedItem
            highestRatedChanged = true
        }
        if (highestRatedChanged) {
            _uiState.update { it.copy(highestRated = currentHighestRated) }
        }

        var sectionsChanged = false
        val updatedSections = loadedRecommendationSections.map { section ->
            when (section) {
                is HomeSection.Movie -> {
                    val items = section.section.recommendedItems
                    val index = items.indexOfFirst { it.id == targetId }
                    if (index != -1 && updatedItem is AfinityMovie) {
                        sectionsChanged = true
                        val newItems = items.toMutableList().apply { this[index] = updatedItem }
                        HomeSection.Movie(section.section.copy(recommendedItems = newItems))
                    } else section
                }

                is HomeSection.Person -> {
                    val items = section.section.items
                    val index = items.indexOfFirst { it.id == targetId }
                    if (
                        index != -1 && (updatedItem is AfinityMovie || updatedItem is AfinityShow)
                    ) {
                        sectionsChanged = true
                        val newItems = items.toMutableList().apply { this[index] = updatedItem }
                        HomeSection.Person(section.section.copy(items = newItems))
                    } else section
                }

                is HomeSection.PersonFromMovie -> {
                    val items = section.section.items
                    val index = items.indexOfFirst { it.id == targetId }
                    if (
                        index != -1 && (updatedItem is AfinityMovie || updatedItem is AfinityShow)
                    ) {
                        sectionsChanged = true
                        val newItems = items.toMutableList().apply { this[index] = updatedItem }
                        HomeSection.PersonFromMovie(section.section.copy(items = newItems))
                    } else section
                }

                is HomeSection.Genre -> section

                is HomeSection.Spotlight -> section
            }
        }

        if (sectionsChanged) {
            loadedRecommendationSections.clear()
            loadedRecommendationSections.addAll(updatedSections)
            withContext(Dispatchers.Main) {
                updateCombinedSections(appDataRepository.combinedGenres.value)
            }
        }
    }

    companion object {
        private const val WORK_HOME_RELOAD = "home_data_reload"
    }
}

sealed interface HomeSection {
    data class Person(val section: PersonSection) : HomeSection

    data class Movie(val section: MovieSection) : HomeSection

    data class PersonFromMovie(val section: PersonFromMovieSection) : HomeSection

    data class Genre(val genreItem: GenreItem) : HomeSection

    data class Spotlight(val title: String, val type: SpotlightType, val items: List<AfinityItem>) :
        HomeSection
}

enum class SpotlightType {
    GENRE_MOVIE,
    GENRE_SHOW,
    STUDIO,
    BOXSET,
}

data class HomeUiState(
    val heroCarouselItems: List<AfinityItem> = emptyList(),
    val latestMedia: List<AfinityItem> = emptyList(),
    val continueWatching: List<AfinityItem> = emptyList(),
    val offlineContinueWatching: List<AfinityItem> = emptyList(),
    val nextUp: List<AfinityEpisode> = emptyList(),
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
    val isLoading: Boolean = false,
    val error: String? = null,
    val combineLibrarySections: Boolean = false,
    val libraries: List<AfinityCollection> = emptyList(),
    val separateMovieLibrarySections: List<Pair<AfinityCollection, List<AfinityMovie>>> =
        emptyList(),
    val separateTvLibrarySections: List<Pair<AfinityCollection, List<AfinityShow>>> = emptyList(),
    val isOffline: Boolean = false,
    val showQualityDialog: Boolean = false,
)
