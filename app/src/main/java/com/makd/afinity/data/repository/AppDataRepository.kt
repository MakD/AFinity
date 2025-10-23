package com.makd.afinity.data.repository

import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.extensions.toAfinityItem
import com.makd.afinity.data.models.media.AfinityCollection
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityRecommendationCategory
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.repository.auth.AuthRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppDataRepository @Inject constructor(
    private val jellyfinRepository: JellyfinRepository,
    private val preferencesRepository: PreferencesRepository,
    private val cacheRepository: CacheRepository,
    private val authRepository: AuthRepository
) {
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

    private val _showOfflineModePrompt = MutableStateFlow(false)
    val showOfflineModePrompt: StateFlow<Boolean> = _showOfflineModePrompt.asStateFlow()

    fun getCombineLibrarySectionsFlow(): Flow<Boolean> {
        return preferencesRepository.getCombineLibrarySectionsFlow()
    }

    fun getHomeSortByDateAddedFlow(): Flow<Boolean> {
        return preferencesRepository.getHomeSortByDateAddedFlow()
    }

    private val _highestRated = MutableStateFlow<List<AfinityItem>>(emptyList())
    val highestRated: StateFlow<List<AfinityItem>> = _highestRated.asStateFlow()

    private val _recommendationCategories = MutableStateFlow<List<AfinityRecommendationCategory>>(emptyList())
    val recommendationCategories: StateFlow<List<AfinityRecommendationCategory>> = _recommendationCategories.asStateFlow()

    private val _isInitialDataLoaded = MutableStateFlow(false)
    val isInitialDataLoaded: StateFlow<Boolean> = _isInitialDataLoaded.asStateFlow()

    private val _isOfflineDataRefreshing = MutableStateFlow(false)
    val isOfflineDataRefreshing: StateFlow<Boolean> = _isOfflineDataRefreshing.asStateFlow()

    private val _loadingProgress = MutableStateFlow(0f)
    val loadingProgress: StateFlow<Float> = _loadingProgress.asStateFlow()

    private val _loadingPhase = MutableStateFlow("")
    val loadingPhase: StateFlow<String> = _loadingPhase.asStateFlow()

    suspend fun loadInitialData() {
        if (_isInitialDataLoaded.value) {
            Timber.d("Initial data already loaded, skipping...")
            return
        }

        try {
            val userId = authRepository.currentUser.value?.id
            if (userId == null) {
                Timber.e("No user ID available, cannot load data")
                return
            }

            if (preferencesRepository.getOfflineMode()) {
                Timber.d("Offline mode: Loading cached data only")
                loadOfflineData(userId)
                return
            }

            Timber.d("Online mode: Loading data with cache-first strategy")
            loadOnlineData(userId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load initial data")
        }
    }

    private suspend fun loadOfflineData(userId: UUID) {
        coroutineScope {
            updateProgress(0.1f, "Loading cached content...")

            val cachedContinueWatching = cacheRepository.loadContinueWatchingCache(userId)
            val cachedNextUp = cacheRepository.loadNextUpCache(userId)

            if (cachedContinueWatching != null) {
                _continueWatching.value = cachedContinueWatching
                updateProgress(0.5f, "Continue watching loaded from cache...")
            } else {
                val continueWatching = loadContinueWatching()
                _continueWatching.value = continueWatching
                updateProgress(0.5f, "Continue watching loaded...")
            }

            if (cachedNextUp != null) {
                _nextUp.value = cachedNextUp
                updateProgress(0.9f, "Next up loaded from cache...")
            } else {
                val nextUp = loadNextUp()
                _nextUp.value = nextUp
                updateProgress(0.9f, "Next up loaded...")
            }

            updateProgress(1f, "Offline mode ready")
            _isInitialDataLoaded.value = true
        }
    }

    private suspend fun loadOnlineData(userId: UUID) {
        coroutineScope {
            updateProgress(0.05f, "Checking cache...")

            launch {
                val cachedLatestMedia = cacheRepository.loadLatestMediaCache(userId)
                if (cachedLatestMedia != null) {
                    _latestMedia.value = cachedLatestMedia
                    Timber.d("Loaded latest media from cache")
                }
            }

            launch {
                val cachedHeroCarousel = cacheRepository.loadHeroCarouselCache(userId)
                if (cachedHeroCarousel != null) {
                    _heroCarouselItems.value = cachedHeroCarousel
                    Timber.d("Loaded hero carousel from cache")
                }
            }

            launch {
                val cachedContinueWatching = cacheRepository.loadContinueWatchingCache(userId)
                if (cachedContinueWatching != null) {
                    _continueWatching.value = cachedContinueWatching
                    Timber.d("Loaded continue watching from cache")
                }
            }

            launch {
                val cachedNextUp = cacheRepository.loadNextUpCache(userId)
                if (cachedNextUp != null) {
                    _nextUp.value = cachedNextUp
                    Timber.d("Loaded next up from cache")
                }
            }

            launch {
                val cachedLatestMovies = cacheRepository.loadLatestMoviesCache(userId)
                if (cachedLatestMovies != null) {
                    _latestMovies.value = cachedLatestMovies
                    Timber.d("Loaded latest movies from cache")
                }
            }

            launch {
                val cachedLatestTvSeries = cacheRepository.loadLatestTvSeriesCache(userId)
                if (cachedLatestTvSeries != null) {
                    _latestTvSeries.value = cachedLatestTvSeries
                    Timber.d("Loaded latest TV series from cache")
                }
            }

            launch {
                val cachedHighestRated = cacheRepository.loadHighestRatedCache(userId)
                if (cachedHighestRated != null) {
                    _highestRated.value = cachedHighestRated
                    Timber.d("Loaded highest rated from cache")
                }
            }

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
            cacheRepository.saveLatestMediaCache(userId, latestMedia, ttlHours = 6)

            val heroItems = heroCarouselTask.await()
            _heroCarouselItems.value = heroItems
            cacheRepository.saveHeroCarouselCache(userId, heroItems, ttlHours = 6)
            updateProgress(0.65f, "Latest content loaded...")

            val continueWatching = continueWatchingTask.await()
            _continueWatching.value = continueWatching
            cacheRepository.saveContinueWatchingCache(userId, continueWatching, ttlHours = 1)
            updateProgress(0.75f, "Watchlist loaded...")

            val nextUp = nextUpTask.await()
            _nextUp.value = nextUp
            cacheRepository.saveNextUpCache(userId, nextUp, ttlHours = 1)
            updateProgress(0.8f, "Next episodes loaded...")

            val libraries = librariesTask.await()
            _libraries.value = libraries
            cacheRepository.saveLibrariesCache(userId, libraries, ttlHours = 24)
            updateProgress(0.85f, "Libraries organized...")

            updateProgress(0.88f, "Loading recommendations...")
            val recommendationsTask = async { loadRecommendations() }

            val userProfileImageUrl = userProfileTask.await()
            _userProfileImageUrl.value = userProfileImageUrl

            updateProgress(0.9f, "Loading movies and shows...")
            val homeDataTask = async { loadHomeSpecificData(libraries) }

            val recommendations = recommendationsTask.await()
            _recommendationCategories.value = recommendations
            updateProgress(0.95f, "Recommendations loaded...")

            val (latestMovies, latestTvSeries, highestRated) = homeDataTask.await()
            _latestMovies.value = latestMovies
            cacheRepository.saveLatestMoviesCache(userId, latestMovies, ttlHours = 6)

            _latestTvSeries.value = latestTvSeries
            cacheRepository.saveLatestTvSeriesCache(userId, latestTvSeries, ttlHours = 6)

            _highestRated.value = highestRated
            cacheRepository.saveHighestRatedCache(userId, highestRated, ttlHours = 12)

            updateProgress(1f, "Ready!")
            _isInitialDataLoaded.value = true
        }
    }

    suspend fun refreshAllData() {
        _isInitialDataLoaded.value = false
        loadInitialData()
    }

    suspend fun reloadOnOfflineModeChange() {
        Timber.d("Reloading data due to offline mode change...")
        _isOfflineDataRefreshing.value = true
        try {
            val userId = authRepository.currentUser.value?.id
            if (userId == null) {
                Timber.e("No user ID available")
                _isOfflineDataRefreshing.value = false
                return
            }

            if (preferencesRepository.getOfflineMode()) {
                Timber.d("Offline mode enabled: Clearing online data and reloading offline content only")
                _latestMedia.value = emptyList()
                _heroCarouselItems.value = emptyList()
                _libraries.value = emptyList()
                _userProfileImageUrl.value = null
                _latestMovies.value = emptyList()
                _latestTvSeries.value = emptyList()
                _highestRated.value = emptyList()
                _recommendationCategories.value = emptyList()
                _separateMovieLibrarySections.value = emptyList()
                _separateTvLibrarySections.value = emptyList()

                loadOfflineData(userId)
            } else {
                Timber.d("Offline mode disabled: Reloading online content")
                _latestMedia.value = emptyList()
                _heroCarouselItems.value = emptyList()
                _libraries.value = emptyList()
                _userProfileImageUrl.value = null
                _latestMovies.value = emptyList()
                _latestTvSeries.value = emptyList()
                _highestRated.value = emptyList()
                _recommendationCategories.value = emptyList()
                _separateMovieLibrarySections.value = emptyList()
                _separateTvLibrarySections.value = emptyList()
                _continueWatching.value = emptyList()
                _nextUp.value = emptyList()

                loadOnlineData(userId)
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to handle offline mode change")
        } finally {
            _isOfflineDataRefreshing.value = false
        }
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
        try {
            val userId = authRepository.currentUser.value?.id ?: return

            coroutineScope {
                val latestMediaTask = async { loadLatestMedia() }
                val heroCarouselTask = async { loadHeroCarousel() }
                val continueWatchingTask = async { loadContinueWatching() }
                val nextUpTask = async { loadNextUp() }
                val librariesTask = async { loadLibraries() }

                val latestMedia = latestMediaTask.await()
                _latestMedia.value = latestMedia
                cacheRepository.saveLatestMediaCache(userId, latestMedia, ttlHours = 6)

                val heroItems = heroCarouselTask.await()
                _heroCarouselItems.value = heroItems
                cacheRepository.saveHeroCarouselCache(userId, heroItems, ttlHours = 6)

                val continueWatching = continueWatchingTask.await()
                _continueWatching.value = continueWatching
                cacheRepository.saveContinueWatchingCache(userId, continueWatching, ttlHours = 1)

                val nextUp = nextUpTask.await()
                _nextUp.value = nextUp
                cacheRepository.saveNextUpCache(userId, nextUp, ttlHours = 1)

                val libraries = librariesTask.await()
                _libraries.value = libraries
                cacheRepository.saveLibrariesCache(userId, libraries, ttlHours = 24)

                val homeDataTask = async { loadHomeSpecificData(libraries) }
                val (latestMovies, latestTvSeries, highestRated) = homeDataTask.await()

                _latestMovies.value = latestMovies
                cacheRepository.saveLatestMoviesCache(userId, latestMovies, ttlHours = 6)

                _latestTvSeries.value = latestTvSeries
                cacheRepository.saveLatestTvSeriesCache(userId, latestTvSeries, ttlHours = 6)

                _highestRated.value = highestRated
                cacheRepository.saveHighestRatedCache(userId, highestRated, ttlHours = 12)
            }

            Timber.d("Home data reload completed successfully")
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
                .map { (library, movies) -> library to movies.take(15) }

            _separateTvLibrarySections.value = showResults
                .filter { it.second.isNotEmpty() }
                .map { (library, shows) -> library to shows.take(15) }

            val allLatestMovies = movieResults.flatMap { it.second }
            val allLatestSeries = showResults.flatMap { it.second }

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

            val highRatedMovies = allLatestMovies.filter {
                (it.communityRating ?: 0f) > 6.5f
            }
            val highRatedShows = allLatestSeries.filter {
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

    private suspend fun loadRecommendations(): List<AfinityRecommendationCategory> {
        return try {
            jellyfinRepository.getRecommendationCategories(
                categoryLimit = 4,
                itemLimit = 6
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to load recommendations")
            emptyList()
        }
    }

    suspend fun clearAllCaches() {
        try {
            val userId = authRepository.currentUser.value?.id ?: return
            cacheRepository.clearAllUserCaches(userId)
            Timber.d("Cleared all caches for user")
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear caches")
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
        _recommendationCategories.value = emptyList()
        _isInitialDataLoaded.value = false
        _loadingProgress.value = 0f
        _loadingPhase.value = ""
        _separateMovieLibrarySections.value = emptyList()
        _separateTvLibrarySections.value = emptyList()
    }

    fun showOfflineModePrompt() {
        _showOfflineModePrompt.value = true
    }

    fun dismissOfflineModePrompt() {
        _showOfflineModePrompt.value = false
    }

    suspend fun enableOfflineMode() {
        try {
            preferencesRepository.setOfflineMode(true)
            dismissOfflineModePrompt()
            Timber.d("Offline mode enabled from prompt")
        } catch (e: Exception) {
            Timber.e(e, "Failed to enable offline mode")
        }
    }

    private fun isConnectionError(exception: Exception): Boolean {
        val message = exception.message?.lowercase() ?: ""
        return message.contains("unable to resolve host") ||
                message.contains("failed to connect") ||
                message.contains("network is unreachable") ||
                message.contains("timeout") ||
                message.contains("connection refused") ||
                exception is java.net.UnknownHostException ||
                exception is java.net.SocketTimeoutException ||
                exception is java.net.ConnectException ||
                exception is java.io.IOException
    }
}