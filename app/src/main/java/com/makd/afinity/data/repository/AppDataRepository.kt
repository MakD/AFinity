package com.makd.afinity.data.repository

import androidx.core.net.toUri
import com.makd.afinity.data.database.AfinityDatabase
import com.makd.afinity.data.database.AfinityTypeConverters
import com.makd.afinity.data.database.entities.GenreMovieCacheEntity
import com.makd.afinity.data.database.entities.GenreShowCacheEntity
import com.makd.afinity.data.database.entities.PersonSectionCacheEntity
import com.makd.afinity.data.database.entities.TopPeopleCacheEntity
import com.makd.afinity.data.manager.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import com.makd.afinity.data.models.CachedPersonWithCount
import com.makd.afinity.data.models.GenreItem
import com.makd.afinity.data.models.GenreType
import com.makd.afinity.data.models.PersonSection
import com.makd.afinity.data.models.PersonSectionType
import com.makd.afinity.data.models.PersonWithCount
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.extensions.toAfinityItem
import com.makd.afinity.data.models.media.AfinityCollection
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityPerson
import com.makd.afinity.data.models.media.AfinityPersonImage
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.media.AfinityStudio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.PersonKind
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlin.time.Duration.Companion.hours

@Singleton
class AppDataRepository @Inject constructor(
    private val jellyfinRepository: JellyfinRepository,
    private val preferencesRepository: PreferencesRepository,
    private val database: AfinityDatabase,
    private val sessionManager: SessionManager
) {
    private val GENRE_CACHE_TTL = 12.hours.inWholeMilliseconds
    private val PERSON_SECTION_CACHE_TTL = 48.hours.inWholeMilliseconds
    private val PEOPLE_CACHE_TTL = 24.hours.inWholeMilliseconds
    private val RECENT_WATCHED_CACHE_TTL = 6.hours.inWholeMilliseconds

    private val genreCacheDao = database.genreCacheDao()
    private val studioCacheDao = database.studioCacheDao()
    private val topPeopleDao = database.topPeopleDao()
    private val personSectionDao = database.personSectionDao()
    private val afinityTypeConverters = AfinityTypeConverters()
    private val json = Json { ignoreUnknownKeys = true }

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

    private val _userProfileImageUrl = MutableStateFlow<String?>(null)
    val userProfileImageUrl: StateFlow<String?> = _userProfileImageUrl.asStateFlow()

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

    private val _studios = MutableStateFlow<List<AfinityStudio>>(emptyList())
    val studios: StateFlow<List<AfinityStudio>> = _studios.asStateFlow()

    private val _combinedGenres = MutableStateFlow<List<GenreItem>>(emptyList())
    val combinedGenres: StateFlow<List<GenreItem>> = _combinedGenres.asStateFlow()

    private val _genreMovies = MutableStateFlow<Map<String, List<AfinityMovie>>>(emptyMap())
    val genreMovies: StateFlow<Map<String, List<AfinityMovie>>> = _genreMovies.asStateFlow()

    private val _genreShows = MutableStateFlow<Map<String, List<AfinityShow>>>(emptyMap())
    val genreShows: StateFlow<Map<String, List<AfinityShow>>> = _genreShows.asStateFlow()

    private val _genreLoadingStates = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val genreLoadingStates: StateFlow<Map<String, Boolean>> = _genreLoadingStates.asStateFlow()

    private val _isInitialDataLoaded = MutableStateFlow(false)
    val isInitialDataLoaded: StateFlow<Boolean> = _isInitialDataLoaded.asStateFlow()

    private val _loadingProgress = MutableStateFlow(0f)
    val loadingProgress: StateFlow<Float> = _loadingProgress.asStateFlow()

    private val _loadingPhase = MutableStateFlow("")
    val loadingPhase: StateFlow<String> = _loadingPhase.asStateFlow()

    private var currentSessionId: String? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        scope.launch {
            sessionManager.currentSession.collect { session ->
                val newSessionId = session?.let { "${it.serverId}_${it.userId}" }

                if (currentSessionId != null && newSessionId != currentSessionId && newSessionId != null) {
                    Timber.d("Session changed from $currentSessionId to $newSessionId - clearing and reloading data")
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
    }

    fun skipInitialDataLoad() {
        Timber.d("Skipping initial data load for offline mode")
        _loadingProgress.value = 1f
        _loadingPhase.value = "Ready (Offline Mode)"
        _isInitialDataLoaded.value = true
    }

    suspend fun loadInitialData() {
        if (_isInitialDataLoaded.value) {
            Timber.d("Initial data already loaded, skipping...")
            return
        }

        try {
            coroutineScope {
                updateProgress(0.1f, "Connecting to server...")

                val latestMediaDeferred = async { loadLatestMedia() }
                val heroCarouselDeferred = async { loadHeroCarousel() }
                val continueWatchingDeferred = async { loadContinueWatching() }
                val nextUpDeferred = async { loadNextUp() }
                val librariesDeferred = async { loadLibraries() }
                val userProfileDeferred = async { loadUserProfileImage() }

                updateProgress(0.3f, "Fetching content...")

                val libraries = librariesDeferred.await()
                _libraries.value = libraries

                val homeDataDeferred = async { loadHomeSpecificData(libraries) }

                updateProgress(0.5f, "Processing libraries...")

                _latestMedia.value = latestMediaDeferred.await()
                _heroCarouselItems.value = heroCarouselDeferred.await()
                _continueWatching.value = continueWatchingDeferred.await()
                _nextUp.value = nextUpDeferred.await()
                _userProfileImageUrl.value = userProfileDeferred.await()

                updateProgress(0.8f, "Finalizing home screen...")

                val (latestMovies, latestTvSeries, highestRated) = homeDataDeferred.await()
                _latestMovies.value = latestMovies
                _latestTvSeries.value = latestTvSeries
                _highestRated.value = highestRated

                updateProgress(1f, "Ready!")
                _isInitialDataLoaded.value = true

                startLiveDataCollectors()

                Timber.d("All initial data loaded successfully in parallel")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load initial app data")
            throw e
        }
    }

    private fun startLiveDataCollectors() {
        val scope = CoroutineScope(Dispatchers.IO)

        scope.launch {
            jellyfinRepository.getContinueWatchingFlow().collect { liveData ->
                _continueWatching.value = liveData
            }
        }

        scope.launch {
            jellyfinRepository.getLatestMediaFlow().collect { liveData ->
                val filteredData = liveData.filter { item ->
                    item is AfinityMovie || item is AfinityShow
                }
                _latestMedia.value = filteredData
            }
        }

        scope.launch {
            jellyfinRepository.getNextUpFlow().collect { liveData ->
                _nextUp.value = liveData
            }
        }
    }

    suspend fun reloadHomeData() {
        if (_isInitialDataLoaded.value && _libraries.value.isEmpty()) {
            Timber.d("Libraries empty (Offline Start detected). Forcing full initial load...")
            _isInitialDataLoaded.value = false
            loadInitialData()
            return
        }

        if (!_isInitialDataLoaded.value) return

        try {
            val libraries = _libraries.value
            if (libraries.isNotEmpty()) {
                Timber.d("Reloading home data (preserving highest rated)...")
                val (latestMovies, latestTvSeries, highestRated) = loadHomeSpecificData(
                    libraries = libraries,
                    existingHighestRated = _highestRated.value
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

    private suspend fun loadLatestMedia(): List<AfinityItem> {
        return try {
            val allLatestMedia = jellyfinRepository.getLatestMedia(limit = 15)
            allLatestMedia.filter { item ->
                item is AfinityMovie || item is AfinityShow
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load latest media")
            emptyList()
        }
    }

    private suspend fun loadHeroCarousel(): List<AfinityItem> {
        return try {
            val baseUrl = jellyfinRepository.getBaseUrl()
            val randomHeroItems = jellyfinRepository.getItems(
                includeItemTypes = listOf("MOVIE", "SERIES"),
                sortBy = SortBy.RANDOM,
                sortDescending = false,
                limit = 15,
                isPlayed = false,
                fields = FieldSets.HERO_CAROUSEL,
                imageTypes = listOf("Logo", "Backdrop"),
                hasOverview = true,
            )

            randomHeroItems.items
                ?.mapNotNull { it.toAfinityItem(baseUrl) }
                ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "Failed to load hero carousel items")
            emptyList()
        }
    }

    private suspend fun loadContinueWatching(): List<AfinityItem> {
        return try {
            jellyfinRepository.getContinueWatching(limit = 12)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load continue watching")
            emptyList()
        }
    }

    private suspend fun loadNextUp(): List<AfinityEpisode> {
        return try {
            jellyfinRepository.getNextUp(limit = 16)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load next up")
            emptyList()
        }
    }

    private suspend fun loadLibraries(): List<AfinityCollection> {
        return try {
            jellyfinRepository.getLibraries()
        } catch (e: Exception) {
            Timber.e(e, "Failed to load libraries")
            emptyList()
        }
    }

    private suspend fun loadUserProfileImage(): String? {
        val url = try {
            jellyfinRepository.getUserProfileImageUrl()
        } catch (e: Exception) { null }
        return url
    }

    fun saveUserProfileImageUrl(url: String?) {
        _userProfileImageUrl.value = url
    }

    private suspend fun loadHomeSpecificData(
        libraries: List<AfinityCollection>,
        existingHighestRated: List<AfinityItem>? = null
    ): Triple<List<AfinityMovie>, List<AfinityShow>, List<AfinityItem>> {
        return try {
            val movieLibraries = libraries.filter { it.type == CollectionType.Movies }
            val tvLibraries = libraries.filter { it.type == CollectionType.TvShows }

            val useJellyfinDefault = preferencesRepository.getHomeSortByDateAdded()

            val (movieResults, showResults) = coroutineScope {
                val moviesDeferred = async {
                    if (useJellyfinDefault) {
                        movieLibraries.map { library ->
                            async {
                                try {
                                    val items = jellyfinRepository.getLatestMedia(
                                        parentId = library.id,
                                        limit = 30
                                    ).filterIsInstance<AfinityMovie>()
                                    library to items
                                } catch (e: Exception) {
                                    library to emptyList()
                                }
                            }
                        }.awaitAll()
                    } else {
                        movieLibraries.map { library ->
                            async {
                                try {
                                    library to jellyfinRepository.getMovies(
                                        parentId = library.id,
                                        sortBy = SortBy.RELEASE_DATE,
                                        sortDescending = true,
                                        limit = 30,
                                        isPlayed = false
                                    )
                                } catch (e: Exception) {
                                    library to emptyList()
                                }
                            }
                        }.awaitAll()
                    }
                }

                val showsDeferred = async {
                    if (useJellyfinDefault) {
                        tvLibraries.map { library ->
                            async {
                                try {
                                    val items = jellyfinRepository.getLatestMedia(
                                        parentId = library.id,
                                        limit = 30
                                    ).filterIsInstance<AfinityShow>()
                                    library to items
                                } catch (e: Exception) {
                                    library to emptyList()
                                }
                            }
                        }.awaitAll()
                    } else {
                        tvLibraries.map { library ->
                            async {
                                try {
                                    library to jellyfinRepository.getShows(
                                        parentId = library.id,
                                        sortBy = SortBy.RELEASE_DATE,
                                        sortDescending = true,
                                        limit = 30,
                                        isPlayed = false
                                    )
                                } catch (e: Exception) {
                                    library to emptyList()
                                }
                            }
                        }.awaitAll()
                    }
                }

                Pair(moviesDeferred.await(), showsDeferred.await())
            }

            _separateMovieLibrarySections.value = movieResults
                .filter { it.second.isNotEmpty() }
                .map { (library, movies) -> library to movies.filter { !it.played }.take(15) }

            _separateTvLibrarySections.value = showResults
                .filter { it.second.isNotEmpty() }
                .map { (library, shows) -> library to shows.filter { !it.played }.take(15) }

            val allMoviesIncludingWatched = movieResults.flatMap { it.second }
            val allSeriesIncludingWatched = showResults.flatMap { it.second }

            val allLatestMovies = allMoviesIncludingWatched.filter { !it.played }
            val allLatestSeries = allSeriesIncludingWatched.filter { !it.played }

            val latestMovies = if (useJellyfinDefault) {
                allLatestMovies.sortedByDescending { it.dateCreated }.take(15)
            } else {
                allLatestMovies.sortedByDescending { it.premiereDate }.take(15)
            }

            val latestTvSeries = if (useJellyfinDefault) {
                allLatestSeries.sortedByDescending { it.dateCreated }.take(15)
            } else {
                allLatestSeries.sortedByDescending { it.premiereDate }.take(15)
            }

            val highestRated = existingHighestRated ?: run {
                val highRatedMovies = allMoviesIncludingWatched.filter {
                    (it.communityRating ?: 0f) > 6.5f
                }
                val highRatedShows = allSeriesIncludingWatched.filter {
                    (it.communityRating ?: 0f) > 6.5f
                }

                (highRatedMovies + highRatedShows)
                    .shuffled()
                    .take(10)
                    .sortedByDescending {
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

    private suspend fun loadGenres() {
        try {
            val cachedGenreNames = genreCacheDao.getAllGenreNames()
            if (cachedGenreNames.isNotEmpty()) {
                val currentTime = System.currentTimeMillis()
                val oldestTimestamp = genreCacheDao.getOldestCacheTimestamp() ?: 0
                val isFresh = (currentTime - oldestTimestamp) < GENRE_CACHE_TTL

                if (isFresh) return
            }

            val genres = jellyfinRepository.getGenres(
                parentId = null,
                limit = null,
                includeItemTypes = listOf("MOVIE")
            )

            val timestamp = System.currentTimeMillis()
            val genreEntities = genres.map { genreName ->
                com.makd.afinity.data.database.entities.GenreCacheEntity(
                    genreName = genreName,
                    lastFetchedTimestamp = timestamp,
                    movieCount = 0
                )
            }
            genreCacheDao.insertGenreCaches(genreEntities)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load genres")
        }
    }

    suspend fun loadMoviesForGenre(genre: String, limit: Int = 20) {
        if (_genreMovies.value.containsKey(genre)) return

        kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                _genreLoadingStates.value += (genre to true)

                val cachedMovieEntities = genreCacheDao.getCachedMoviesForGenre(genre)
                if (cachedMovieEntities.isNotEmpty()) {
                    val cachedMovies = cachedMovieEntities.mapNotNull { entity ->
                        afinityTypeConverters.toAfinityMovie(entity.movieData)
                    }

                    if (cachedMovies.isNotEmpty()) {
                        _genreMovies.value += (genre to cachedMovies)
                        _genreLoadingStates.value += (genre to false)

                        val currentTime = System.currentTimeMillis()
                        val isFresh =
                            genreCacheDao.isGenreCacheFresh(genre, GENRE_CACHE_TTL, currentTime)

                        if (isFresh) return@withContext
                    }
                }

                val movies = jellyfinRepository.getMoviesByGenre(
                    genre = genre,
                    limit = limit,
                    shuffle = true
                )

                if (movies.isNotEmpty()) {
                    val timestamp = System.currentTimeMillis()
                    val movieEntities = movies.mapIndexed { index, movie ->
                        GenreMovieCacheEntity(
                            genreName = genre,
                            movieId = movie.id.toString(),
                            movieData = afinityTypeConverters.fromAfinityMovie(movie) ?: "",
                            position = index,
                            cachedTimestamp = timestamp
                        )
                    }
                    genreCacheDao.cacheGenreWithMovies(genre, movieEntities, timestamp)
                }

                _genreMovies.value += (genre to movies)
                _genreLoadingStates.value += (genre to false)
            } catch (e: Exception) {
                Timber.e(e, "Failed to load movies for genre: $genre")
                _genreLoadingStates.value += (genre to false)

                try {
                    val fallbackEntities = genreCacheDao.getCachedMoviesForGenre(genre)
                    val fallbackMovies = fallbackEntities.mapNotNull { entity ->
                        afinityTypeConverters.toAfinityMovie(entity.movieData)
                    }
                    if (fallbackMovies.isNotEmpty()) {
                        _genreMovies.value += (genre to fallbackMovies)
                    }
                } catch (cacheError: Exception) { /* Ignore */
                }
            }
        }
    }

    suspend fun loadCombinedGenres() {
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                coroutineScope {
                    val movieGenresTask = async { loadGenres() }
                    val showGenresTask = async { loadShowGenres() }
                    movieGenresTask.await()
                    showGenresTask.await()
                }

                val movieGenreNames = genreCacheDao.getAllGenreNames()
                val showGenreNames = genreCacheDao.getAllShowGenreNames()

                val movieGenreItems = movieGenreNames.map { GenreItem(it, GenreType.MOVIE) }
                val showGenreItems = showGenreNames.map { GenreItem(it, GenreType.SHOW) }

                val combinedList = (movieGenreItems + showGenreItems).shuffled()
                _combinedGenres.value = combinedList
            } catch (e: Exception) {
                Timber.e(e, "Failed to load combined genres")
            }
        }
    }

    private suspend fun loadShowGenres() {
        try {
            val cachedGenreNames = genreCacheDao.getAllShowGenreNames()
            if (cachedGenreNames.isNotEmpty()) {
                val currentTime = System.currentTimeMillis()
                val oldestTimestamp = genreCacheDao.getOldestShowCacheTimestamp() ?: 0
                val isFresh = (currentTime - oldestTimestamp) < GENRE_CACHE_TTL
                if (isFresh) return
            }

            val genres = jellyfinRepository.getGenres(
                parentId = null,
                limit = null,
                includeItemTypes = listOf("SERIES")
            )

            val timestamp = System.currentTimeMillis()
            val genreEntities = genres.map { genreName ->
                com.makd.afinity.data.database.entities.ShowGenreCacheEntity(
                    genreName = genreName,
                    lastFetchedTimestamp = timestamp,
                    showCount = 0
                )
            }
            genreCacheDao.insertShowGenreCaches(genreEntities)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load show genres")
        }
    }

    suspend fun loadShowsForGenre(genre: String, limit: Int = 20) {
        if (_genreShows.value.containsKey(genre)) return

        try {
            _genreLoadingStates.value += (genre to true)

            val cachedShowEntities = genreCacheDao.getCachedShowsForGenre(genre)
            if (cachedShowEntities.isNotEmpty()) {
                val cachedShows = cachedShowEntities.mapNotNull { entity ->
                    afinityTypeConverters.toAfinityShow(entity.showData)
                }

                if (cachedShows.isNotEmpty()) {
                    _genreShows.value += (genre to cachedShows)
                    _genreLoadingStates.value += (genre to false)

                    val currentTime = System.currentTimeMillis()
                    val isFresh =
                        genreCacheDao.isShowGenreCacheFresh(genre, GENRE_CACHE_TTL, currentTime)
                    if (isFresh) return
                }
            }

            val shows = jellyfinRepository.getShowsByGenre(
                genre = genre,
                limit = limit,
                shuffle = true
            )

            if (shows.isNotEmpty()) {
                val timestamp = System.currentTimeMillis()
                val showEntities = shows.mapIndexed { index, show ->
                    GenreShowCacheEntity(
                        genreName = genre,
                        showId = show.id.toString(),
                        showData = afinityTypeConverters.fromAfinityShow(show) ?: "",
                        position = index,
                        cachedTimestamp = timestamp
                    )
                }
                genreCacheDao.cacheGenreWithShows(genre, showEntities, timestamp)
            }

            _genreShows.value += (genre to shows)
            _genreLoadingStates.value += (genre to false)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load shows for genre: $genre")
            _genreLoadingStates.value += (genre to false)
        }
    }

    suspend fun loadStudios() {
        try {
            val cachedStudios = studioCacheDao.getAllCachedStudios()
            if (cachedStudios.isNotEmpty()) {
                val studios = cachedStudios.mapNotNull { entity ->
                    afinityTypeConverters.toAfinityStudio(entity.studioData)
                }

                if (studios.isNotEmpty()) {
                    _studios.value = studios
                    val currentTime = System.currentTimeMillis()
                    val isFresh = studioCacheDao.isStudioCacheFresh(GENRE_CACHE_TTL, currentTime)
                    if (isFresh) return
                }
            }

            val studios: List<AfinityStudio> = jellyfinRepository.getStudios(
                parentId = null,
                limit = 15,
                includeItemTypes = listOf("MOVIE", "SERIES")
            )

            if (studios.isNotEmpty()) {
                val timestamp = System.currentTimeMillis()
                val studioEntities = studios.mapIndexed { index: Int, studio: AfinityStudio ->
                    com.makd.afinity.data.database.entities.StudioCacheEntity(
                        studioId = studio.id.toString(),
                        studioData = afinityTypeConverters.fromAfinityStudio(studio) ?: "",
                        position = index,
                        cachedTimestamp = timestamp
                    )
                }
                studioCacheDao.replaceStudios(studioEntities)
            }

            _studios.value = studios
        } catch (e: Exception) {
            Timber.e(e, "Failed to load studios")
        }
    }

    private fun filterItemsByPersonRole(
        items: List<AfinityItem>,
        personId: UUID,
        personType: PersonKind
    ): List<AfinityItem> {
        return items.filter { item ->
            val people = when (item) {
                is AfinityMovie -> item.people
                is AfinityShow -> item.people
                is AfinityEpisode -> item.people
                else -> emptyList()
            }
            people.any { person ->
                person.id == personId && person.type == personType
            }
        }
    }

    suspend fun getTopPeople(
        type: PersonKind,
        limit: Int = 100,
        minAppearances: Int = 10
    ): List<PersonWithCount> {
        try {
            val cached = topPeopleDao.getCachedTopPeople(type.name)
            val currentTime = System.currentTimeMillis()

            if (cached != null && topPeopleDao.isTopPeopleCacheFresh(
                    type.name,
                    PEOPLE_CACHE_TTL,
                    currentTime
                )
            ) {
                val cachedData =
                    json.decodeFromString<List<CachedPersonWithCount>>(cached.peopleData)
                return cachedData.map { PersonWithCount.fromCached(it) }
            }

            Timber.d("Fetching top ${type.name}...")
            val baseUrl = jellyfinRepository.getBaseUrl()

            val scanLimit = 150
            val peopleFrequency = mutableMapOf<String, Pair<AfinityPerson, Int>>()

            val moviesResponse = jellyfinRepository.getItems(
                includeItemTypes = listOf("Movie"),
                fields = listOf(ItemFields.PEOPLE),
                limit = scanLimit,
                sortBy = SortBy.DATE_ADDED,
                sortDescending = true
            )

            val movies = moviesResponse.items ?: emptyList()

            movies.forEach { movieItem ->
                movieItem.people?.filter { it.type == type }?.forEach { personDto ->
                    val key = personDto.name ?: return@forEach

                    if (!peopleFrequency.containsKey(key)) {
                        val id = personDto.id
                        val primaryTag = personDto.primaryImageTag

                        val imageUri = primaryTag?.let { tag ->
                            baseUrl.toUri().buildUpon()
                                .appendEncodedPath("Items/$id/Images/Primary")
                                .appendQueryParameter("tag", tag)
                                .build()
                        }

                        val afinityPerson = AfinityPerson(
                            id = id,
                            name = key,
                            type = type,
                            role = personDto.role ?: type.name,
                            image = AfinityPersonImage(imageUri, null)
                        )
                        peopleFrequency[key] = afinityPerson to 1
                    } else {
                        val current = peopleFrequency[key]!!
                        peopleFrequency[key] = current.first to (current.second + 1)
                    }
                }
            }

            val mappedPeople = peopleFrequency.values
                .filter { it.second >= 2 }
                .sortedByDescending { it.second }
                .take(limit)
                .map { PersonWithCount(it.first, it.second) }

            Timber.d("Scan complete: Found ${mappedPeople.size} ${type.name}s")

            if (mappedPeople.isNotEmpty()) {
                val cachedData = mappedPeople.map { it.toCached() }
                val entity = TopPeopleCacheEntity(
                    personType = type.name,
                    peopleData = json.encodeToString(cachedData),
                    cachedTimestamp = System.currentTimeMillis()
                )
                topPeopleDao.insertTopPeople(entity)
            }

            return mappedPeople
        } catch (e: Exception) {
            Timber.e(e, "Failed to get top ${type.name}")
            return emptyList()
        }
    }

    suspend fun getPersonSection(
        personWithCount: PersonWithCount,
        sectionType: PersonSectionType
    ): PersonSection? {
        try {
            val person = personWithCount.person
            val cacheKey = "${person.name}_${sectionType.name}"

            val cached = personSectionDao.getCachedSection(cacheKey)
            val currentTime = System.currentTimeMillis()

            if (cached != null && personSectionDao.isSectionCacheFresh(
                    cacheKey,
                    PERSON_SECTION_CACHE_TTL,
                    currentTime
                )
            ) {
                val cachedPersonData = PersonWithCount.fromCached(
                    json.decodeFromString<CachedPersonWithCount>(cached.personData)
                )
                val cachedItems = json.decodeFromString<List<String>>(cached.itemsData)
                    .mapNotNull { afinityTypeConverters.toAfinityMovie(it) }
                return PersonSection(
                    person = cachedPersonData.person,
                    appearanceCount = cachedPersonData.appearanceCount,
                    items = cachedItems,
                    sectionType = sectionType
                )
            }

            val allPersonItems = jellyfinRepository.getPersonItems(
                personId = person.id,
                includeItemTypes = listOf("MOVIE", "SERIES"),
                fields = listOf(ItemFields.PEOPLE)
            )

            val filteredItems = filterItemsByPersonRole(
                items = allPersonItems,
                personId = person.id,
                personType = sectionType.toPersonKind()
            )

            if (filteredItems.size < 5) return null

            val selectedItems = filteredItems
                .filterIsInstance<AfinityMovie>()
                .shuffled()
                .take(20)

            val section = PersonSection(
                person = person,
                appearanceCount = personWithCount.appearanceCount,
                items = selectedItems,
                sectionType = sectionType
            )

            val movieJsonStrings = selectedItems.mapNotNull { afinityTypeConverters.fromAfinityMovie(it) }
            val entity = PersonSectionCacheEntity(
                cacheKey = cacheKey,
                personData = json.encodeToString(personWithCount.toCached()),
                itemsData = json.encodeToString(movieJsonStrings),
                sectionType = sectionType.name,
                cachedTimestamp = currentTime
            )
            personSectionDao.insertSection(entity)

            return section
        } catch (e: Exception) {
            Timber.e(e, "Failed to get person section for ${personWithCount.person.name}")
            return null
        }
    }

    suspend fun getRandomRecentlyWatchedMovie(
        excludedMovies: Set<UUID>
    ): AfinityMovie? {
        try {
            val now = System.currentTimeMillis()
            val cached = recentWatchedCache

            val allRecentWatched =
                if (cached != null && now - cached.first < RECENT_WATCHED_CACHE_TTL) {
                    cached.second
                } else {
                    val movies = jellyfinRepository.getMovies(
                        sortBy = SortBy.DATE_PLAYED,
                        sortDescending = true,
                        limit = 10,
                        isPlayed = true
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

    suspend fun clearAllData() {
        Timber.d("Clearing all cached app data")

        try {
            studioCacheDao.deleteAllStudios()
            genreCacheDao.clearAllCache()
            topPeopleDao.clearAllCache()
            personSectionDao.clearAllCache()
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear database caches")
        }

        recentWatchedCache = null
        _latestMedia.value = emptyList()
        _heroCarouselItems.value = emptyList()
        _continueWatching.value = emptyList()
        _nextUp.value = emptyList()
        _libraries.value = emptyList()
        _userProfileImageUrl.value = null
        _latestMovies.value = emptyList()
        _latestTvSeries.value = emptyList()
        _highestRated.value = emptyList()
        _studios.value = emptyList()
        _combinedGenres.value = emptyList()
        _genreMovies.value = emptyMap()
        _genreShows.value = emptyMap()
        _genreLoadingStates.value = emptyMap()
        _isInitialDataLoaded.value = false
        _loadingProgress.value = 0f
        _loadingPhase.value = ""
        _separateMovieLibrarySections.value = emptyList()
        _separateTvLibrarySections.value = emptyList()
    }
}