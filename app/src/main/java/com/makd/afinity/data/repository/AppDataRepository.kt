package com.makd.afinity.data.repository

import android.content.Context
import com.makd.afinity.R
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.GenreItem
import com.makd.afinity.data.models.PersonSection
import com.makd.afinity.data.models.PersonSectionType
import com.makd.afinity.data.models.PersonWithCount
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.extensions.toAfinityItem
import com.makd.afinity.data.models.livetv.AfinityChannel
import com.makd.afinity.data.models.media.AfinityBoxSet
import com.makd.afinity.data.models.media.AfinityCollection
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.withBaseUrl
import com.makd.afinity.data.models.media.AfinityPersonDetail
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.media.AfinityStudio
import com.makd.afinity.data.repository.livetv.LiveTvRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.server.ServerRepository
import com.makd.afinity.data.repository.watchlist.WatchlistRepository
import com.makd.afinity.util.JellyfinImageUrlBuilder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.PersonKind
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlin.time.Duration.Companion.hours

@Singleton
class AppDataRepository
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val preferencesRepository: PreferencesRepository,
    private val sessionManager: SessionManager,
    private val jellyfinImageUrlBuilder: JellyfinImageUrlBuilder,
    private val watchlistRepository: WatchlistRepository,
    private val liveTvRepository: LiveTvRepository,
    private val genreRepository: GenreRepository,
    private val peopleRepository: PeopleRepository,
    private val studioRepository: StudioRepository,
    private val serverRepository: ServerRepository,
) {
    private val recentCacheTTL = 6.hours.inWholeMilliseconds
    private var recentWatchedCache: Pair<Long, List<AfinityMovie>>? = null
    private val _latestMedia = MutableStateFlow<List<AfinityItem>>(emptyList())
    val latestMedia: StateFlow<List<AfinityItem>> = _latestMedia.asStateFlow()

    private val _heroCarouselItems = MutableStateFlow<List<AfinityItem>>(emptyList())
    val heroCarouselItems: StateFlow<List<AfinityItem>> = _heroCarouselItems.asStateFlow()

    private val _continueWatching = MutableStateFlow<List<AfinityItem>>(emptyList())
    val continueWatching: StateFlow<List<AfinityItem>> = _continueWatching.asStateFlow()

    private val _nextUp = MutableStateFlow<List<AfinityEpisode>>(emptyList())
    val nextUp: StateFlow<List<AfinityEpisode>> = _nextUp.asStateFlow()

    private val _libraries = MutableStateFlow<List<AfinityCollection>>(emptyList())
    val libraries: StateFlow<List<AfinityCollection>> = _libraries.asStateFlow()

    private val _separateMovieLibrarySections =
        MutableStateFlow<List<Pair<AfinityCollection, List<AfinityMovie>>>>(emptyList())
    val separateMovieLibrarySections: StateFlow<List<Pair<AfinityCollection, List<AfinityMovie>>>> =
        _separateMovieLibrarySections.asStateFlow()

    private val _separateTvLibrarySections =
        MutableStateFlow<List<Pair<AfinityCollection, List<AfinityShow>>>>(emptyList())
    val separateTvLibrarySections: StateFlow<List<Pair<AfinityCollection, List<AfinityShow>>>> =
        _separateTvLibrarySections.asStateFlow()

    val userProfileImageUrl: StateFlow<String?> =
        sessionManager.currentSession
            .map { session ->
                if (session?.user != null && !session.user.primaryImageTag.isNullOrBlank()) {
                    val url =
                        jellyfinImageUrlBuilder.buildUserPrimaryImageUrl(
                            baseUrl = session.serverUrl,
                            userId = session.user.id.toString(),
                            tag = session.user.primaryImageTag,
                        )

                    if (session.user.accessToken != null) {
                        val separator = if (url.contains("?")) "&" else "?"
                        "$url${separator}api_key=${session.user.accessToken}"
                    } else {
                        url
                    }
                } else {
                    null
                }
            }
            .stateIn(
                scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = null,
            )

    private val _latestMovies = MutableStateFlow<List<AfinityMovie>>(emptyList())
    val latestMovies: StateFlow<List<AfinityMovie>> = _latestMovies.asStateFlow()

    private val _latestTvSeries = MutableStateFlow<List<AfinityShow>>(emptyList())
    val latestTvSeries: StateFlow<List<AfinityShow>> = _latestTvSeries.asStateFlow()

    fun getCombineLibrarySectionsFlow(): Flow<Boolean> {
        return preferencesRepository.getCombineLibrarySectionsFlow()
    }

    fun getHomeSortByDateAddedFlow(): Flow<Boolean> {
        return preferencesRepository.getHomeSortByDateAddedFlow()
    }

    private val _highestRated = MutableStateFlow<List<AfinityItem>>(emptyList())
    val highestRated: StateFlow<List<AfinityItem>> = _highestRated.asStateFlow()

    val combinedGenres: StateFlow<List<GenreItem>> = genreRepository.combinedGenres
    val genreMovies: StateFlow<Map<String, List<AfinityMovie>>> = genreRepository.genreMovies
    val genreShows: StateFlow<Map<String, List<AfinityShow>>> = genreRepository.genreShows
    val genreLoadingStates: StateFlow<Map<String, Boolean>> = genreRepository.genreLoadingStates

    private val _favoritesData = MutableStateFlow(FavoritesData())
    val favoritesData: StateFlow<FavoritesData> = _favoritesData.asStateFlow()

    private val _watchlistData = MutableStateFlow(WatchlistData())
    val watchlistData: StateFlow<WatchlistData> = _watchlistData.asStateFlow()

    private val _isInitialDataLoaded = MutableStateFlow(false)
    val isInitialDataLoaded: StateFlow<Boolean> = _isInitialDataLoaded.asStateFlow()

    private val _loadingProgress = MutableStateFlow(0f)
    val loadingProgress: StateFlow<Float> = _loadingProgress.asStateFlow()

    private val _loadingPhase = MutableStateFlow("")
    val loadingPhase: StateFlow<String> = _loadingPhase.asStateFlow()

    private var currentSessionId: String? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var liveDataJob: Job? = null

    init {
        scope.launch {
            sessionManager.currentSession.collect { session ->
                val newSessionId = session?.let { "${it.serverId}_${it.userId}" }

                if (
                    currentSessionId != null &&
                        newSessionId != currentSessionId &&
                        newSessionId != null
                ) {
                    Timber.d(
                        "Session changed from $currentSessionId to $newSessionId - clearing and reloading data"
                    )
                    clearAllData()

                    kotlinx.coroutines.delay(300)

                    try {
                        loadInitialData()
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to reload data after session switch")
                    }
                }

                currentSessionId = newSessionId
            }
        }

        scope.launch {
            serverRepository.currentBaseUrl
                .filter { it.isNotBlank() }
                .distinctUntilChanged()
                .drop(1)
                .collect { newUrl ->
                    if (!_isInitialDataLoaded.value) return@collect
                    Timber.d(
                        "Server base URL changed to $newUrl — clearing all URL-dependent caches and reloading"
                    )
                    try {
                        clearAllData()
                        loadInitialData()
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to reload data after base URL change")
                    }
                }
        }
    }

    fun skipInitialDataLoad() {
        Timber.d("Skipping initial data load for offline mode")
        _loadingProgress.value = 1f
        _loadingPhase.value = context.getString(R.string.loading_phase_offline)
        _isInitialDataLoaded.value = true
    }

    suspend fun loadInitialData() {
        if (_isInitialDataLoaded.value) {
            Timber.d("Initial data already loaded, skipping...")
            return
        }

        try {
            coroutineScope {
                kotlinx.coroutines.delay(300)

                updateProgress(0.1f, context.getString(R.string.loading_phase_connecting))

                val latestMediaDeferred = async { loadLatestMedia() }
                val heroCarouselDeferred = async { loadHeroCarousel() }
                val continueWatchingDeferred = async { loadContinueWatching() }
                val nextUpDeferred = async { loadNextUp() }
                val librariesDeferred = async { loadLibraries() }
                val watchlistCountDeferred = async {
                    try {
                        watchlistRepository.refreshWatchlistCount()
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to load watchlist count on startup")
                    }
                }
                val favoritesDeferred = async { loadFavoritesData() }
                val watchlistDeferred = async { loadWatchlistData() }

                updateProgress(0.3f, context.getString(R.string.loading_phase_fetching))

                val libraries = librariesDeferred.await()
                _libraries.value = libraries

                val homeDataDeferred = async { loadHomeSpecificData(libraries) }

                updateProgress(0.5f, context.getString(R.string.loading_phase_processing))

                _latestMedia.value = latestMediaDeferred.await()
                _heroCarouselItems.value = heroCarouselDeferred.await()
                _continueWatching.value = continueWatchingDeferred.await()
                _nextUp.value = nextUpDeferred.await()
                watchlistCountDeferred.await()
                favoritesDeferred.await()
                watchlistDeferred.await()

                updateProgress(0.8f, context.getString(R.string.loading_phase_finalizing))

                val (latestMovies, latestTvSeries, highestRated) = homeDataDeferred.await()
                _latestMovies.value = latestMovies
                _latestTvSeries.value = latestTvSeries
                _highestRated.value = highestRated

                updateProgress(1f, context.getString(R.string.loading_phase_ready))
                _isInitialDataLoaded.value = true

                startLiveDataCollectors()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load initial app data")
            throw e
        }
    }

    private fun startLiveDataCollectors() {
        liveDataJob?.cancel()

        liveDataJob =
            scope.launch {
                launch {
                    mediaRepository.getContinueWatchingFlow().collect { liveData ->
                        _continueWatching.value = liveData
                    }
                }

                launch {
                    mediaRepository.getLatestMediaFlow().collect { liveData ->
                        val filteredData =
                            liveData.filter { item -> item is AfinityMovie || item is AfinityShow }
                        _latestMedia.value = filteredData
                    }
                }

                launch {
                    mediaRepository.getNextUpFlow().collect { liveData -> _nextUp.value = liveData }
                }
            }
    }

    suspend fun reloadHomeData() {
        if (_isInitialDataLoaded.value && _libraries.value.isEmpty()) {
            Timber.d("Libraries empty (offline start detected), forcing full initial load...")
            _isInitialDataLoaded.value = false
            loadInitialData()
            return
        }

        if (!_isInitialDataLoaded.value) return

        try {
            val libraries = _libraries.value
            if (libraries.isNotEmpty()) {
                Timber.d("Reloading home data...")
                val currentBaseUrl = mediaRepository.getBaseUrl()
                val patchedExisting = _highestRated.value
                    .map { item ->
                        when (item) {
                            is AfinityMovie -> item.copy(images = item.images.withBaseUrl(currentBaseUrl))
                            is AfinityShow -> item.copy(images = item.images.withBaseUrl(currentBaseUrl))
                            else -> item
                        }
                    }
                    .takeIf { it.isNotEmpty() }
                val (latestMovies, latestTvSeries, highestRated) =
                    loadHomeSpecificData(
                        libraries = libraries,
                        existingHighestRated = patchedExisting,
                    )
                _latestMovies.value = latestMovies
                _latestTvSeries.value = latestTvSeries
                _highestRated.value = highestRated
                Timber.d("Home data reloaded successfully")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to reload home data")
        }
    }

    private suspend fun reloadLatestMoviesData() {
        try {
            val libraries = _libraries.value
            val movieLibraries = libraries.filter { it.type == CollectionType.Movies }
            if (movieLibraries.isEmpty()) return

            val useJellyfinDefault = preferencesRepository.getHomeSortByDateAdded()

            val movieResults = coroutineScope {
                movieLibraries
                    .map { library ->
                        async {
                            try {
                                val items =
                                    if (useJellyfinDefault) {
                                        mediaRepository
                                            .getLatestMedia(parentId = library.id, limit = 30)
                                            .filterIsInstance<AfinityMovie>()
                                    } else {
                                        mediaRepository.getMovies(
                                            parentId = library.id,
                                            sortBy = SortBy.RELEASE_DATE,
                                            sortDescending = true,
                                            limit = 30,
                                            isPlayed = false,
                                        )
                                    }
                                library to items
                            } catch (e: Exception) {
                                library to emptyList()
                            }
                        }
                    }
                    .awaitAll()
            }

            _separateMovieLibrarySections.value =
                movieResults
                    .filter { it.second.isNotEmpty() }
                    .map { (library, movies) -> library to movies.filter { !it.played }.take(15) }

            val allMovies = movieResults.flatMap { it.second }.filter { !it.played }
            _latestMovies.value =
                if (useJellyfinDefault) allMovies.sortedByDescending { it.dateCreated }.take(15)
                else allMovies.sortedByDescending { it.premiereDate }.take(15)
        } catch (e: Exception) {
            Timber.e(e, "Failed to reload latest movies data")
        }
    }

    private suspend fun reloadLatestShowsData() {
        try {
            val libraries = _libraries.value
            val tvLibraries = libraries.filter { it.type == CollectionType.TvShows }
            if (tvLibraries.isEmpty()) return

            val useJellyfinDefault = preferencesRepository.getHomeSortByDateAdded()

            val showResults = coroutineScope {
                tvLibraries
                    .map { library ->
                        async {
                            try {
                                val items =
                                    if (useJellyfinDefault) {
                                        mediaRepository
                                            .getLatestMedia(parentId = library.id, limit = 30)
                                            .filterIsInstance<AfinityShow>()
                                    } else {
                                        mediaRepository.getShows(
                                            parentId = library.id,
                                            sortBy = SortBy.RELEASE_DATE,
                                            sortDescending = true,
                                            limit = 30,
                                            isPlayed = false,
                                        )
                                    }
                                library to items
                            } catch (e: Exception) {
                                library to emptyList()
                            }
                        }
                    }
                    .awaitAll()
            }

            _separateTvLibrarySections.value =
                showResults
                    .filter { it.second.isNotEmpty() }
                    .map { (library, shows) -> library to shows.filter { !it.played }.take(15) }

            val allShows = showResults.flatMap { it.second }.filter { !it.played }
            _latestTvSeries.value =
                if (useJellyfinDefault) allShows.sortedByDescending { it.dateCreated }.take(15)
                else allShows.sortedByDescending { it.premiereDate }.take(15)
        } catch (e: Exception) {
            Timber.e(e, "Failed to reload latest shows data")
        }
    }

    private suspend fun loadLatestMedia(): List<AfinityItem> {
        return try {
            val allLatestMedia = mediaRepository.getLatestMedia(limit = 15)
            allLatestMedia.filter { item -> item is AfinityMovie || item is AfinityShow }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load latest media")
            emptyList()
        }
    }

    private suspend fun loadHeroCarousel(): List<AfinityItem> {
        return try {
            val baseUrl = mediaRepository.getBaseUrl()
            val randomHeroItems =
                mediaRepository.getItems(
                    includeItemTypes = listOf("MOVIE", "SERIES"),
                    sortBy = SortBy.RANDOM,
                    sortDescending = false,
                    limit = 15,
                    isPlayed = false,
                    fields = FieldSets.HERO_CAROUSEL,
                    imageTypes = listOf("Logo", "Backdrop"),
                    hasOverview = true,
                )

            randomHeroItems.items?.mapNotNull { it.toAfinityItem(baseUrl) } ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Failed to load hero carousel items")
            emptyList()
        }
    }

    private suspend fun loadContinueWatching(): List<AfinityItem> {
        return try {
            mediaRepository.getContinueWatching(limit = 12)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load continue watching")
            emptyList()
        }
    }

    private suspend fun loadNextUp(): List<AfinityEpisode> {
        return try {
            mediaRepository.getNextUp(
                limit = 16,
                enableResumable = false,
                fields = FieldSets.NEXT_UP,
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to load next up")
            emptyList()
        }
    }

    private suspend fun loadLibraries(): List<AfinityCollection> {
        return try {
            mediaRepository.getLibraries()
        } catch (e: Exception) {
            Timber.e(e, "Failed to load libraries")
            emptyList()
        }
    }

    private suspend fun loadHomeSpecificData(
        libraries: List<AfinityCollection>,
        existingHighestRated: List<AfinityItem>? = null,
    ): Triple<List<AfinityMovie>, List<AfinityShow>, List<AfinityItem>> {
        return try {
            val movieLibraries = libraries.filter { it.type == CollectionType.Movies }
            val tvLibraries = libraries.filter { it.type == CollectionType.TvShows }

            val useJellyfinDefault = preferencesRepository.getHomeSortByDateAdded()

            val (movieResults, showResults) =
                coroutineScope {
                    val moviesDeferred = async {
                        if (useJellyfinDefault) {
                            movieLibraries
                                .map { library ->
                                    async {
                                        try {
                                            val items =
                                                mediaRepository
                                                    .getLatestMedia(
                                                        parentId = library.id,
                                                        limit = 30,
                                                    )
                                                    .filterIsInstance<AfinityMovie>()
                                            library to items
                                        } catch (e: Exception) {
                                            library to emptyList()
                                        }
                                    }
                                }
                                .awaitAll()
                        } else {
                            movieLibraries
                                .map { library ->
                                    async {
                                        try {
                                            library to
                                                mediaRepository.getMovies(
                                                    parentId = library.id,
                                                    sortBy = SortBy.RELEASE_DATE,
                                                    sortDescending = true,
                                                    limit = 30,
                                                    isPlayed = false,
                                                )
                                        } catch (e: Exception) {
                                            library to emptyList()
                                        }
                                    }
                                }
                                .awaitAll()
                        }
                    }

                    val showsDeferred = async {
                        if (useJellyfinDefault) {
                            tvLibraries
                                .map { library ->
                                    async {
                                        try {
                                            val items =
                                                mediaRepository
                                                    .getLatestMedia(
                                                        parentId = library.id,
                                                        limit = 30,
                                                    )
                                                    .filterIsInstance<AfinityShow>()
                                            library to items
                                        } catch (e: Exception) {
                                            library to emptyList()
                                        }
                                    }
                                }
                                .awaitAll()
                        } else {
                            tvLibraries
                                .map { library ->
                                    async {
                                        try {
                                            library to
                                                mediaRepository.getShows(
                                                    parentId = library.id,
                                                    sortBy = SortBy.RELEASE_DATE,
                                                    sortDescending = true,
                                                    limit = 30,
                                                    isPlayed = false,
                                                )
                                        } catch (e: Exception) {
                                            library to emptyList()
                                        }
                                    }
                                }
                                .awaitAll()
                        }
                    }

                    Pair(moviesDeferred.await(), showsDeferred.await())
                }

            _separateMovieLibrarySections.value =
                movieResults
                    .filter { it.second.isNotEmpty() }
                    .map { (library, movies) -> library to movies.filter { !it.played }.take(15) }

            _separateTvLibrarySections.value =
                showResults
                    .filter { it.second.isNotEmpty() }
                    .map { (library, shows) -> library to shows.filter { !it.played }.take(15) }

            val allMoviesIncludingWatched = movieResults.flatMap { it.second }
            val allSeriesIncludingWatched = showResults.flatMap { it.second }

            val allLatestMovies = allMoviesIncludingWatched.filter { !it.played }
            val allLatestSeries = allSeriesIncludingWatched.filter { !it.played }

            val latestMovies =
                if (useJellyfinDefault) {
                    allLatestMovies.sortedByDescending { it.dateCreated }.take(15)
                } else {
                    allLatestMovies.sortedByDescending { it.premiereDate }.take(15)
                }

            val latestTvSeries =
                if (useJellyfinDefault) {
                    allLatestSeries.sortedByDescending { it.dateCreated }.take(15)
                } else {
                    allLatestSeries.sortedByDescending { it.premiereDate }.take(15)
                }

            val highestRated =
                existingHighestRated
                    ?: run {
                        val highRatedMovies =
                            allMoviesIncludingWatched.filter { (it.communityRating ?: 0f) > 6.5f }
                        val highRatedShows =
                            allSeriesIncludingWatched.filter { (it.communityRating ?: 0f) > 6.5f }

                        (highRatedMovies + highRatedShows).shuffled().take(10).sortedByDescending {
                            when (it) {
                                is AfinityMovie -> it.communityRating ?: 0f
                                is AfinityShow -> it.communityRating ?: 0f
                                else -> 0f
                            }
                        }
                    }

            Triple(latestMovies, latestTvSeries, highestRated)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load home specific data")
            Triple(emptyList(), emptyList(), emptyList())
        }
    }

    suspend fun loadCombinedGenres() = genreRepository.loadCombinedGenres()

    suspend fun loadMoviesForGenre(genre: String, limit: Int = 20) =
        genreRepository.loadMoviesForGenre(genre, limit)

    suspend fun loadShowsForGenre(genre: String, limit: Int = 20) =
        genreRepository.loadShowsForGenre(genre, limit)

    val studios: StateFlow<List<AfinityStudio>> = studioRepository.studios

    suspend fun loadStudios() = studioRepository.loadStudios()

    suspend fun getTopPeople(
        type: PersonKind,
        limit: Int = 100,
        minAppearances: Int = 10,
    ): List<PersonWithCount> = peopleRepository.getTopPeople(type, limit, minAppearances)

    suspend fun getPersonSection(
        personWithCount: PersonWithCount,
        sectionType: PersonSectionType,
    ): PersonSection? = peopleRepository.getPersonSection(personWithCount, sectionType)

    suspend fun getRandomRecentlyWatchedMovie(excludedMovies: Set<UUID>): AfinityMovie? {
        try {
            val now = System.currentTimeMillis()
            val cached = recentWatchedCache

            val allRecentWatched =
                if (cached != null && now - cached.first < recentCacheTTL) {
                    cached.second
                } else {
                    val movies =
                        mediaRepository.getMovies(
                            sortBy = SortBy.DATE_PLAYED,
                            sortDescending = true,
                            limit = 10,
                            isPlayed = true,
                        )
                    recentWatchedCache = now to movies
                    movies
                }

            val recentWatched = allRecentWatched.filterNot { it.id in excludedMovies }

            if (recentWatched.isEmpty()) return null

            val random = Random.nextFloat()
            return if (random < 0.7f && recentWatched.size >= 5) {
                recentWatched.take(5).random()
            } else if (recentWatched.size > 5) {
                recentWatched.drop(5).take(5).randomOrNull() ?: recentWatched.random()
            } else {
                recentWatched.random()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to get random recently watched movie")
            return null
        }
    }

    private fun updateProgress(progress: Float, phase: String) {
        _loadingProgress.value = progress
        _loadingPhase.value = phase
    }

    suspend fun refreshLiveSections() {
        try {
            coroutineScope {
                launch { mediaRepository.invalidateContinueWatchingCache() }
                launch { mediaRepository.invalidateNextUpCache() }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to refresh live sections")
        }
    }

    suspend fun updateItemInCaches(updatedItem: AfinityItem) {
        val itemId = updatedItem.id
        genreRepository.updateItemInCaches(updatedItem)
        peopleRepository.updateItemInCaches(updatedItem)
        recentWatchedCache = null
        if (updatedItem is AfinityMovie) {
            val movieIsInLatest = _latestMovies.value.any { it.id == itemId }
            if (updatedItem.played) {
                _latestMovies.update { movies -> movies.filterNot { it.id == itemId } }
                _separateMovieLibrarySections.update { sections ->
                    sections.map { (library, movies) ->
                        library to movies.filterNot { it.id == itemId }
                    }
                }
            } else if (movieIsInLatest) {
                _latestMovies.update { movies ->
                    val index = movies.indexOfFirst { it.id == itemId }
                    if (index != -1) movies.toMutableList().apply { this[index] = updatedItem }
                    else movies
                }
                _separateMovieLibrarySections.update { sections ->
                    sections.map { (library, movies) ->
                        val index = movies.indexOfFirst { it.id == itemId }
                        library to
                            if (index != -1)
                                movies.toMutableList().apply { this[index] = updatedItem }
                            else movies
                    }
                }
            } else {
                reloadLatestMoviesData()
            }
        }
        if (updatedItem is AfinityShow) {
            val showIsInLatest = _latestTvSeries.value.any { it.id == itemId }
            if (updatedItem.played) {
                _latestTvSeries.update { shows -> shows.filterNot { it.id == itemId } }
                _separateTvLibrarySections.update { sections ->
                    sections.map { (library, shows) ->
                        library to shows.filterNot { it.id == itemId }
                    }
                }
            } else if (showIsInLatest) {
                _latestTvSeries.update { shows ->
                    val index = shows.indexOfFirst { it.id == itemId }
                    if (index != -1) shows.toMutableList().apply { this[index] = updatedItem }
                    else shows
                }
                _separateTvLibrarySections.update { sections ->
                    sections.map { (library, shows) ->
                        val index = shows.indexOfFirst { it.id == itemId }
                        library to
                            if (index != -1)
                                shows.toMutableList().apply { this[index] = updatedItem }
                            else shows
                    }
                }
            } else {
                reloadLatestShowsData()
            }
        }
        _heroCarouselItems.update { items ->
            val index = items.indexOfFirst { it.id == itemId }
            if (index != -1) items.toMutableList().apply { this[index] = updatedItem } else items
        }

        _latestMedia.update { items ->
            val index = items.indexOfFirst { it.id == itemId }
            if (index != -1) items.toMutableList().apply { this[index] = updatedItem } else items
        }

        _favoritesData.update { current ->
            current.copy(
                movies =
                    current.movies.map {
                        if (it.id == itemId && updatedItem is AfinityMovie) updatedItem else it
                    },
                shows =
                    current.shows.map {
                        if (it.id == itemId && updatedItem is AfinityShow) updatedItem else it
                    },
                seasons =
                    current.seasons.map {
                        if (it.id == itemId && updatedItem is AfinitySeason) updatedItem else it
                    },
                episodes =
                    current.episodes.map {
                        if (it.id == itemId && updatedItem is AfinityEpisode) updatedItem else it
                    },
            )
        }

        _watchlistData.update { current ->
            current.copy(
                movies =
                    current.movies.map {
                        if (it.id == itemId && updatedItem is AfinityMovie) updatedItem else it
                    },
                shows =
                    current.shows.map {
                        if (it.id == itemId && updatedItem is AfinityShow) updatedItem else it
                    },
                seasons =
                    current.seasons.map {
                        if (it.id == itemId && updatedItem is AfinitySeason) updatedItem else it
                    },
                episodes =
                    current.episodes.map {
                        if (it.id == itemId && updatedItem is AfinityEpisode) updatedItem else it
                    },
            )
        }
    }

    private suspend fun loadFavoritesData() {
        try {
            coroutineScope {
                val moviesDeferred = async { mediaRepository.getFavoriteMovies() }
                val showsDeferred = async { mediaRepository.getFavoriteShows() }
                val seasonsDeferred = async { mediaRepository.getFavoriteSeasons() }
                val episodesDeferred = async { mediaRepository.getFavoriteEpisodes() }
                val boxSetsDeferred = async { mediaRepository.getFavoriteBoxSets() }
                val peopleDeferred = async { mediaRepository.getFavoritePeople() }
                val channelsDeferred = async {
                    try {
                        liveTvRepository.getChannels(isFavorite = true)
                    } catch (_: Exception) {
                        emptyList()
                    }
                }

                _favoritesData.value =
                    FavoritesData(
                        movies = moviesDeferred.await().sortedBy { it.name },
                        shows = showsDeferred.await().sortedBy { it.name },
                        seasons = seasonsDeferred.await().sortedBy { it.name },
                        episodes = episodesDeferred.await().sortedBy { it.name },
                        boxSets = boxSetsDeferred.await().sortedBy { it.name },
                        people = peopleDeferred.await().sortedBy { it.name },
                        channels = channelsDeferred.await().sortedBy { it.channelNumber ?: it.name },
                    )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load favorites data")
        }
    }

    private suspend fun loadWatchlistData() {
        try {
            coroutineScope {
                val boxSetsDeferred = async { watchlistRepository.getWatchlistBoxSets() }
                val moviesDeferred = async { watchlistRepository.getWatchlistMovies() }
                val showsDeferred = async { watchlistRepository.getWatchlistShows() }
                val seasonsDeferred = async { watchlistRepository.getWatchlistSeasons() }
                val episodesDeferred = async { watchlistRepository.getWatchlistEpisodes() }

                _watchlistData.value =
                    WatchlistData(
                        boxSets = boxSetsDeferred.await().sortedBy { it.name },
                        movies = moviesDeferred.await().sortedBy { it.name },
                        shows = showsDeferred.await().sortedBy { it.name },
                        seasons = seasonsDeferred.await().sortedBy { it.name },
                        episodes = episodesDeferred.await().sortedBy { it.name },
                    )
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load watchlist data")
        }
    }

    fun updateFavoriteStatus(item: AfinityItem, isFavorite: Boolean) {
        _favoritesData.update { current ->
            if (isFavorite) {
                when (item) {
                    is AfinityMovie ->
                        current.copy(movies = (current.movies + item).sortedBy { it.name })
                    is AfinityShow ->
                        current.copy(shows = (current.shows + item).sortedBy { it.name })
                    is AfinitySeason ->
                        current.copy(seasons = (current.seasons + item).sortedBy { it.name })
                    is AfinityEpisode ->
                        current.copy(episodes = (current.episodes + item).sortedBy { it.name })
                    is AfinityBoxSet ->
                        current.copy(boxSets = (current.boxSets + item).sortedBy { it.name })
                    else -> current
                }
            } else {
                when (item) {
                    is AfinityMovie ->
                        current.copy(movies = current.movies.filterNot { it.id == item.id })
                    is AfinityShow ->
                        current.copy(shows = current.shows.filterNot { it.id == item.id })
                    is AfinitySeason ->
                        current.copy(seasons = current.seasons.filterNot { it.id == item.id })
                    is AfinityEpisode ->
                        current.copy(episodes = current.episodes.filterNot { it.id == item.id })
                    is AfinityBoxSet ->
                        current.copy(boxSets = current.boxSets.filterNot { it.id == item.id })
                    else -> current
                }
            }
        }
    }

    fun updateWatchlistStatus(item: AfinityItem, isOnWatchlist: Boolean) {
        _watchlistData.update { current ->
            if (isOnWatchlist) {
                when (item) {
                    is AfinityBoxSet ->
                        current.copy(boxSets = (current.boxSets + item).sortedBy { it.name })
                    is AfinityMovie ->
                        current.copy(movies = (current.movies + item).sortedBy { it.name })
                    is AfinityShow ->
                        current.copy(shows = (current.shows + item).sortedBy { it.name })
                    is AfinitySeason ->
                        current.copy(seasons = (current.seasons + item).sortedBy { it.name })
                    is AfinityEpisode ->
                        current.copy(episodes = (current.episodes + item).sortedBy { it.name })
                    else -> current
                }
            } else {
                when (item) {
                    is AfinityBoxSet ->
                        current.copy(boxSets = current.boxSets.filterNot { it.id == item.id })
                    is AfinityMovie ->
                        current.copy(movies = current.movies.filterNot { it.id == item.id })
                    is AfinityShow ->
                        current.copy(shows = current.shows.filterNot { it.id == item.id })
                    is AfinitySeason ->
                        current.copy(seasons = current.seasons.filterNot { it.id == item.id })
                    is AfinityEpisode ->
                        current.copy(episodes = current.episodes.filterNot { it.id == item.id })
                    else -> current
                }
            }
        }
    }

    suspend fun reloadFavorites() {
        loadFavoritesData()
    }

    suspend fun reloadWatchlist() {
        loadWatchlistData()
    }

    suspend fun clearAllData() {
        Timber.d("Clearing all cached app data")
        liveDataJob?.cancel()

        try {
            studioRepository.clearAllData()
            peopleRepository.clearAllData()
            genreRepository.clearAllData()
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear database caches")
        }

        recentWatchedCache = null
        _latestMedia.value = emptyList()
        _heroCarouselItems.value = emptyList()
        _continueWatching.value = emptyList()
        _nextUp.value = emptyList()
        _libraries.value = emptyList()
        _latestMovies.value = emptyList()
        _latestTvSeries.value = emptyList()
        _highestRated.value = emptyList()
        _isInitialDataLoaded.value = false
        _loadingProgress.value = 0f
        _loadingPhase.value = ""
        _separateMovieLibrarySections.value = emptyList()
        _separateTvLibrarySections.value = emptyList()
        _favoritesData.value = FavoritesData()
        _watchlistData.value = WatchlistData()
    }
}

data class FavoritesData(
    val movies: List<AfinityMovie> = emptyList(),
    val shows: List<AfinityShow> = emptyList(),
    val seasons: List<AfinitySeason> = emptyList(),
    val episodes: List<AfinityEpisode> = emptyList(),
    val boxSets: List<AfinityBoxSet> = emptyList(),
    val people: List<AfinityPersonDetail> = emptyList(),
    val channels: List<AfinityChannel> = emptyList(),
)

data class WatchlistData(
    val boxSets: List<AfinityBoxSet> = emptyList(),
    val movies: List<AfinityMovie> = emptyList(),
    val shows: List<AfinityShow> = emptyList(),
    val seasons: List<AfinitySeason> = emptyList(),
    val episodes: List<AfinityEpisode> = emptyList(),
)
