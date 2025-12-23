package com.makd.afinity.data.repository

import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.extensions.toAfinityItem
import com.makd.afinity.data.models.media.AfinityCollection
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.database.AfinityDatabase
import com.makd.afinity.data.database.entities.GenreMovieCacheEntity
import com.makd.afinity.data.database.entities.GenreShowCacheEntity
import com.makd.afinity.data.models.GenreItem
import com.makd.afinity.data.models.GenreType
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.media.AfinityStudio
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.ImageType
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.hours

@Singleton
class AppDataRepository @Inject constructor(
    private val jellyfinRepository: JellyfinRepository,
    private val preferencesRepository: PreferencesRepository,
    private val database: AfinityDatabase
) {
    private val GENRE_CACHE_TTL = 12.hours.inWholeMilliseconds

    private val genreCacheDao = database.genreCacheDao()
    private val studioCacheDao = database.studioCacheDao()
    private val typeConverters = com.makd.afinity.data.database.TypeConverters()
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

    private val _separateMovieLibrarySections = MutableStateFlow<List<Pair<AfinityCollection, List<AfinityMovie>>>>(emptyList())
    val separateMovieLibrarySections: StateFlow<List<Pair<AfinityCollection, List<AfinityMovie>>>> = _separateMovieLibrarySections.asStateFlow()

    private val _separateTvLibrarySections = MutableStateFlow<List<Pair<AfinityCollection, List<AfinityShow>>>>(emptyList())
    val separateTvLibrarySections: StateFlow<List<Pair<AfinityCollection, List<AfinityShow>>>> = _separateTvLibrarySections.asStateFlow()

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
                updateProgress(0.1f, "Getting latest content...")
                val latestMediaTask = async { loadLatestMedia() }

                val heroCarouselTask = async { loadHeroCarousel() }
                updateProgress(0.25f, "Loading your watchlist...")
                val continueWatchingTask = async { loadContinueWatching() }

                updateProgress(0.35f, "Finding next episodes...")
                val nextUpTask = async { loadNextUp() }

                updateProgress(0.4f, "Organizing libraries...")
                val librariesTask = async { loadLibraries() }

                updateProgress(0.55f, "Loading user profile...")
                val userProfileTask = async { loadUserProfileImage() }

                val latestMedia = latestMediaTask.await()
                _latestMedia.value = latestMedia
                val heroItems = heroCarouselTask.await()
                _heroCarouselItems.value = heroItems
                updateProgress(0.65f, "Latest content loaded...")

                val continueWatching = continueWatchingTask.await()
                _continueWatching.value = continueWatching
                updateProgress(0.75f, "Watchlist loaded...")

                val nextUp = nextUpTask.await()
                _nextUp.value = nextUp
                updateProgress(0.8f, "Next episodes loaded...")

                val libraries = librariesTask.await()
                _libraries.value = libraries
                updateProgress(0.85f, "Libraries organized...")

                val userProfileImageUrl = userProfileTask.await()
                _userProfileImageUrl.value = userProfileImageUrl

                updateProgress(0.9f, "Loading movies and shows...")
                val homeDataTask = async { loadHomeSpecificData(libraries) }

                val (latestMovies, latestTvSeries, highestRated) = homeDataTask.await()
                _latestMovies.value = latestMovies
                _latestTvSeries.value = latestTvSeries
                _highestRated.value = highestRated

                updateProgress(1f, "Ready!")
                _isInitialDataLoaded.value = true

                launch {
                    jellyfinRepository.getContinueWatchingFlow().collect { liveData ->
                        _continueWatching.value = liveData
                    }
                }

                launch {
                    jellyfinRepository.getLatestMediaFlow().collect { liveData ->
                        val filteredData = liveData.filter { item ->
                            item is AfinityMovie || item is AfinityShow
                        }
                        _latestMedia.value = filteredData
                    }
                }

                launch {
                    jellyfinRepository.getNextUpFlow().collect { liveData ->
                        _nextUp.value = liveData
                    }
                }

                Timber.d("All initial data loaded successfully")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load initial app data")
            throw e
        }
    }

    suspend fun refreshAllData() {
        _isInitialDataLoaded.value = false
        loadInitialData()
    }

    suspend fun refreshContinueWatching() {
        try {
            val freshData = jellyfinRepository.getContinueWatching(limit = 12)
            _continueWatching.value = freshData
            Timber.d("Continue watching data refreshed")
        } catch (e: Exception) {
            Timber.e(e, "Failed to refresh continue watching")
        }
    }

    suspend fun reloadHomeData() {
        if (!_isInitialDataLoaded.value) return

        try {
            val libraries = _libraries.value
            if (libraries.isNotEmpty()) {
                Timber.d("Reloading home data...")
                val (latestMovies, latestTvSeries, highestRated) = loadHomeSpecificData(libraries)
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
            Timber.d("Loading random hero carousel items")

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
        return try {
            jellyfinRepository.getUserProfileImageUrl()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get user profile image URL")
            null
        }
    }

    private suspend fun loadHomeSpecificData(
        libraries: List<AfinityCollection>
    ): Triple<List<AfinityMovie>, List<AfinityShow>, List<AfinityItem>> {
        return try {
            val movieLibraries = libraries.filter { it.type == CollectionType.Movies }
            val tvLibraries = libraries.filter { it.type == CollectionType.TvShows }

            val useJellyfinDefault = preferencesRepository.getHomeSortByDateAdded()

            Timber.d("Loading home data with useJellyfinDefault=$useJellyfinDefault")

            val (movieResults, showResults) = if (useJellyfinDefault) {
                coroutineScope {
                    val movieTasks = movieLibraries.map { library ->
                        async {
                            try {
                                val items = jellyfinRepository.getLatestMedia(
                                    parentId = library.id,
                                    limit = 30
                                ).filterIsInstance<AfinityMovie>()
                                library to items
                            } catch (e: Exception) {
                                Timber.w(e, "Failed to load latest from ${library.name}")
                                library to emptyList<AfinityMovie>()
                            }
                        }
                    }

                    val showTasks = tvLibraries.map { library ->
                        async {
                            try {
                                val items = jellyfinRepository.getLatestMedia(
                                    parentId = library.id,
                                    limit = 30
                                ).filterIsInstance<AfinityShow>()
                                library to items
                            } catch (e: Exception) {
                                Timber.w(e, "Failed to load latest from ${library.name}")
                                library to emptyList<AfinityShow>()
                            }
                        }
                    }

                    Pair(movieTasks.awaitAll(), showTasks.awaitAll())
                }
            } else {
                coroutineScope {
                    val movieTasks = movieLibraries.map { library ->
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
                                Timber.w(e, "Failed to load movies from ${library.name}")
                                library to emptyList<AfinityMovie>()
                            }
                        }
                    }

                    val showTasks = tvLibraries.map { library ->
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
                                Timber.w(e, "Failed to load shows from ${library.name}")
                                library to emptyList<AfinityShow>()
                            }
                        }
                    }

                    Pair(movieTasks.awaitAll(), showTasks.awaitAll())
                }
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

            val highRatedMovies = allMoviesIncludingWatched.filter {
                (it.communityRating ?: 0f) > 6.5f
            }
            val highRatedShows = allSeriesIncludingWatched.filter {
                (it.communityRating ?: 0f) > 6.5f
            }

            val highestRated = (highRatedMovies + highRatedShows)
                .shuffled()
                .take(10)
                .sortedByDescending {
                    when (it) {
                        is AfinityMovie -> it.communityRating ?: 0f
                        is AfinityShow -> it.communityRating ?: 0f
                        else -> 0f
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
            Timber.d("Loading genres with cache-first strategy")

            val cachedGenreNames = genreCacheDao.getAllGenreNames()
            if (cachedGenreNames.isNotEmpty()) {
                Timber.d("Loaded ${cachedGenreNames.size} genres from cache")

                val currentTime = System.currentTimeMillis()
                val oldestTimestamp = genreCacheDao.getOldestCacheTimestamp() ?: 0
                val isFresh = (currentTime - oldestTimestamp) < GENRE_CACHE_TTL

                if (isFresh) {
                    Timber.d("Genre cache is fresh, skipping API call")
                    return
                }

                Timber.d("Genre cache is stale, refreshing in background...")
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

            Timber.d("Fetched and cached ${genres.size} genres from API")
        } catch (e: Exception) {
            Timber.e(e, "Failed to load genres")

            try {
                val fallbackGenres = genreCacheDao.getAllGenreNames()
                if (fallbackGenres.isNotEmpty()) {
                    Timber.d("Using stale cache as fallback (${fallbackGenres.size} genres)")
                }
            } catch (cacheError: Exception) {
                Timber.e(cacheError, "Failed to load fallback cache")
            }
        }
    }

    suspend fun loadMoviesForGenre(genre: String, limit: Int = 20) {
        if (_genreMovies.value.containsKey(genre)) {
            Timber.d("Genre '$genre' already in memory, skipping")
            return
        }

        try {
            _genreLoadingStates.value = _genreLoadingStates.value + (genre to true)

            val cachedMovieEntities = genreCacheDao.getCachedMoviesForGenre(genre)
            if (cachedMovieEntities.isNotEmpty()) {
                val cachedMovies = cachedMovieEntities.mapNotNull { entity ->
                    typeConverters.toAfinityMovie(entity.movieData)
                }

                if (cachedMovies.isNotEmpty()) {
                    _genreMovies.value = _genreMovies.value + (genre to cachedMovies)
                    _genreLoadingStates.value = _genreLoadingStates.value + (genre to false)
                    Timber.d("Loaded ${cachedMovies.size} movies for '$genre' from cache")

                    val currentTime = System.currentTimeMillis()
                    val isFresh = genreCacheDao.isGenreCacheFresh(genre, GENRE_CACHE_TTL, currentTime)

                    if (isFresh) {
                        Timber.d("Genre '$genre' cache is fresh (< 12 hours)")
                        return
                    }

                    Timber.d("Genre '$genre' cache is stale, refreshing in background...")
                }
            }

            Timber.d("Fetching movies for genre: $genre from API")
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
                        movieData = typeConverters.fromAfinityMovie(movie) ?: "",
                        position = index,
                        cachedTimestamp = timestamp
                    )
                }
                genreCacheDao.cacheGenreWithMovies(genre, movieEntities, timestamp)
            }

            _genreMovies.value = _genreMovies.value + (genre to movies)
            _genreLoadingStates.value = _genreLoadingStates.value + (genre to false)

            Timber.d("Fetched and cached ${movies.size} movies for genre: $genre")
        } catch (e: Exception) {
            Timber.e(e, "Failed to load movies for genre: $genre")
            _genreLoadingStates.value = _genreLoadingStates.value + (genre to false)

            try {
                val fallbackEntities = genreCacheDao.getCachedMoviesForGenre(genre)
                val fallbackMovies = fallbackEntities.mapNotNull { entity ->
                    typeConverters.toAfinityMovie(entity.movieData)
                }
                if (fallbackMovies.isNotEmpty()) {
                    _genreMovies.value = _genreMovies.value + (genre to fallbackMovies)
                    Timber.d("Using stale cache as fallback for '$genre' (${fallbackMovies.size} movies)")
                }
            } catch (cacheError: Exception) {
                Timber.e(cacheError, "Failed to load fallback cache for genre: $genre")
            }
        }
    }

    suspend fun loadCombinedGenres() {
        try {
            Timber.d("Loading and combining movie + show genres")

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
            Timber.d("Combined ${combinedList.size} genres (${movieGenreItems.size} movies, ${showGenreItems.size} shows) with random ordering")
        } catch (e: Exception) {
            Timber.e(e, "Failed to load combined genres")
        }
    }

    private suspend fun loadShowGenres() {
        try {
            Timber.d("Loading show genres with cache-first strategy")

            val cachedGenreNames = genreCacheDao.getAllShowGenreNames()
            if (cachedGenreNames.isNotEmpty()) {
                Timber.d("Loaded ${cachedGenreNames.size} show genres from cache")

                val currentTime = System.currentTimeMillis()
                val oldestTimestamp = genreCacheDao.getOldestShowCacheTimestamp() ?: 0
                val isFresh = (currentTime - oldestTimestamp) < GENRE_CACHE_TTL

                if (isFresh) {
                    Timber.d("Show genre cache is fresh, skipping API call")
                    return
                }

                Timber.d("Show genre cache is stale, refreshing in background...")
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

            Timber.d("Fetched and cached ${genres.size} show genres from API")
        } catch (e: Exception) {
            Timber.e(e, "Failed to load show genres")

            try {
                val fallbackGenres = genreCacheDao.getAllShowGenreNames()
                if (fallbackGenres.isNotEmpty()) {
                    Timber.d("Using stale cache as fallback (${fallbackGenres.size} show genres)")
                }
            } catch (cacheError: Exception) {
                Timber.e(cacheError, "Failed to load fallback cache")
            }
        }
    }

    suspend fun loadShowsForGenre(genre: String, limit: Int = 20) {
        if (_genreShows.value.containsKey(genre)) {
            Timber.d("Show genre '$genre' already in memory, skipping")
            return
        }

        try {
            _genreLoadingStates.value = _genreLoadingStates.value + (genre to true)

            val cachedShowEntities = genreCacheDao.getCachedShowsForGenre(genre)
            if (cachedShowEntities.isNotEmpty()) {
                val cachedShows = cachedShowEntities.mapNotNull { entity ->
                    typeConverters.toAfinityShow(entity.showData)
                }

                if (cachedShows.isNotEmpty()) {
                    _genreShows.value = _genreShows.value + (genre to cachedShows)
                    _genreLoadingStates.value = _genreLoadingStates.value + (genre to false)
                    Timber.d("Loaded ${cachedShows.size} shows for '$genre' from cache")

                    val currentTime = System.currentTimeMillis()
                    val isFresh = genreCacheDao.isShowGenreCacheFresh(genre, GENRE_CACHE_TTL, currentTime)

                    if (isFresh) {
                        Timber.d("Show genre '$genre' cache is fresh (< 12 hours)")
                        return
                    }

                    Timber.d("Show genre '$genre' cache is stale, refreshing in background...")
                }
            }

            Timber.d("Fetching shows for genre: $genre from API")
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
                        showData = typeConverters.fromAfinityShow(show) ?: "",
                        position = index,
                        cachedTimestamp = timestamp
                    )
                }
                genreCacheDao.cacheGenreWithShows(genre, showEntities, timestamp)
            }

            _genreShows.value = _genreShows.value + (genre to shows)
            _genreLoadingStates.value = _genreLoadingStates.value + (genre to false)

            Timber.d("Fetched and cached ${shows.size} shows for genre: $genre")
        } catch (e: Exception) {
            Timber.e(e, "Failed to load shows for genre: $genre")
            _genreLoadingStates.value = _genreLoadingStates.value + (genre to false)

            try {
                val fallbackEntities = genreCacheDao.getCachedShowsForGenre(genre)
                val fallbackShows = fallbackEntities.mapNotNull { entity ->
                    typeConverters.toAfinityShow(entity.showData)
                }
                if (fallbackShows.isNotEmpty()) {
                    _genreShows.value = _genreShows.value + (genre to fallbackShows)
                    Timber.d("Using stale cache as fallback for '$genre' (${fallbackShows.size} shows)")
                }
            } catch (cacheError: Exception) {
                Timber.e(cacheError, "Failed to load fallback cache for show genre: $genre")
            }
        }
    }

    suspend fun loadStudios() {
        try {
            Timber.d("Loading studios with cache-first strategy")

            val cachedStudios = studioCacheDao.getAllCachedStudios()
            if (cachedStudios.isNotEmpty()) {
                val studios = cachedStudios.mapNotNull { entity ->
                    typeConverters.toAfinityStudio(entity.studioData)
                }

                if (studios.isNotEmpty()) {
                    _studios.value = studios
                    Timber.d("Loaded ${studios.size} studios from cache")

                    val currentTime = System.currentTimeMillis()
                    val isFresh = studioCacheDao.isStudioCacheFresh(GENRE_CACHE_TTL, currentTime)

                    if (isFresh) {
                        Timber.d("Studio cache is fresh (< 12 hours)")
                        return
                    }

                    Timber.d("Studio cache is stale, refreshing in background...")
                }
            }

            Timber.d("Fetching studios from API")
            val studios: List<AfinityStudio> = jellyfinRepository.getStudios(
                parentId = null,
                limit = 15,
                includeItemTypes = listOf("MOVIE", "SERIES")
            )

            if (studios.isNotEmpty()) {
                val timestamp = System.currentTimeMillis()
                val studioEntities: List<com.makd.afinity.data.database.entities.StudioCacheEntity> = studios.mapIndexed { index: Int, studio: AfinityStudio ->
                    com.makd.afinity.data.database.entities.StudioCacheEntity(
                        studioId = studio.id.toString(),
                        studioData = typeConverters.fromAfinityStudio(studio) ?: "",
                        position = index,
                        cachedTimestamp = timestamp
                    )
                }
                studioCacheDao.replaceStudios(studioEntities)
            }

            _studios.value = studios
            Timber.d("Fetched and cached ${studios.size} studios")
        } catch (e: Exception) {
            Timber.e(e, "Failed to load studios")

            try {
                val fallbackEntities = studioCacheDao.getAllCachedStudios()
                val fallbackStudios = fallbackEntities.mapNotNull { entity ->
                    typeConverters.toAfinityStudio(entity.studioData)
                }
                if (fallbackStudios.isNotEmpty()) {
                    _studios.value = fallbackStudios
                    Timber.d("Using stale cache as fallback (${fallbackStudios.size} studios)")
                }
            } catch (cacheError: Exception) {
                Timber.e(cacheError, "Failed to load fallback cache for studios")
            }
        }
    }

    private fun updateProgress(progress: Float, phase: String) {
        _loadingProgress.value = progress
        _loadingPhase.value = phase
    }

    fun clearAllData() {
        Timber.d("Clearing all cached app data")
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