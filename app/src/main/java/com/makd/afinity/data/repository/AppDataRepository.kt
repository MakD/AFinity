package com.makd.afinity.data.repository

import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.media.AfinityCollection
import com.makd.afinity.data.models.media.AfinityRecommendationCategory
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.common.CollectionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppDataRepository @Inject constructor(
    private val jellyfinRepository: JellyfinRepository
) {
    private val _latestMedia = MutableStateFlow<List<AfinityItem>>(emptyList())
    val latestMedia: StateFlow<List<AfinityItem>> = _latestMedia.asStateFlow()

    private val _continueWatching = MutableStateFlow<List<AfinityItem>>(emptyList())
    val continueWatching: StateFlow<List<AfinityItem>> = _continueWatching.asStateFlow()

    private val _libraries = MutableStateFlow<List<AfinityCollection>>(emptyList())
    val libraries: StateFlow<List<AfinityCollection>> = _libraries.asStateFlow()

    private val _userProfileImageUrl = MutableStateFlow<String?>(null)
    val userProfileImageUrl: StateFlow<String?> = _userProfileImageUrl.asStateFlow()

    private val _latestMovies = MutableStateFlow<List<AfinityMovie>>(emptyList())
    val latestMovies: StateFlow<List<AfinityMovie>> = _latestMovies.asStateFlow()

    private val _latestTvSeries = MutableStateFlow<List<AfinityShow>>(emptyList())
    val latestTvSeries: StateFlow<List<AfinityShow>> = _latestTvSeries.asStateFlow()

    private val _highestRated = MutableStateFlow<List<AfinityItem>>(emptyList())
    val highestRated: StateFlow<List<AfinityItem>> = _highestRated.asStateFlow()

    private val _recommendationCategories = MutableStateFlow<List<AfinityRecommendationCategory>>(emptyList())
    val recommendationCategories: StateFlow<List<AfinityRecommendationCategory>> = _recommendationCategories.asStateFlow()

    private val _isInitialDataLoaded = MutableStateFlow(false)
    val isInitialDataLoaded: StateFlow<Boolean> = _isInitialDataLoaded.asStateFlow()

    private val _loadingProgress = MutableStateFlow(0f)
    val loadingProgress: StateFlow<Float> = _loadingProgress.asStateFlow()

    private val _loadingPhase = MutableStateFlow("")
    val loadingPhase: StateFlow<String> = _loadingPhase.asStateFlow()

    /**
     * Loads all essential app data in the correct order for optimal UX
     */
    suspend fun loadInitialData() {
        if (_isInitialDataLoaded.value) {
            Timber.d("Initial data already loaded, skipping...")
            return
        }

        try {
            coroutineScope {
                updateProgress(0.1f, "Getting latest content...")
                val latestMediaTask = async { loadLatestMedia() }

                updateProgress(0.25f, "Loading your watchlist...")
                val continueWatchingTask = async { loadContinueWatching() }

                updateProgress(0.4f, "Organizing libraries...")
                val librariesTask = async { loadLibraries() }

                updateProgress(0.55f, "Loading user profile...")
                val userProfileTask = async { loadUserProfileImage() }

                val latestMedia = latestMediaTask.await()
                _latestMedia.value = latestMedia
                updateProgress(0.65f, "Latest content loaded...")

                val continueWatching = continueWatchingTask.await()
                _continueWatching.value = continueWatching
                updateProgress(0.75f, "Watchlist loaded...")

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
                    try {
                        Timber.d("Loading recommendations in background...")
                        val recommendationCategories = loadRecommendations()
                        _recommendationCategories.value = recommendationCategories
                        Timber.d("Recommendations loaded successfully")
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to load recommendations in background")
                    }
                }

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

                Timber.d("All initial data loaded successfully")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load initial app data")
            throw e
        }
    }

    /**
     * Forces a refresh of all data
     */
    suspend fun refreshAllData() {
        _isInitialDataLoaded.value = false
        loadInitialData()
    }

    /**
     * Refreshes only specific data streams without full reload
     */
    suspend fun refreshContinueWatching() {
        try {
            val freshData = jellyfinRepository.getContinueWatching(limit = 12)
            _continueWatching.value = freshData
            Timber.d("Continue watching data refreshed")
        } catch (e: Exception) {
            Timber.e(e, "Failed to refresh continue watching")
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

    private suspend fun loadContinueWatching(): List<AfinityItem> {
        return try {
            jellyfinRepository.getContinueWatching(limit = 12)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load continue watching")
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

    private suspend fun loadHomeSpecificData(libraries: List<AfinityCollection>): Triple<List<AfinityMovie>, List<AfinityShow>, List<AfinityItem>> {
        return try {
            val movieLibraries = libraries.filter { it.type == CollectionType.Movies }
            val tvLibraries = libraries.filter { it.type == CollectionType.TvShows }

            val (movieResults, showResults) = coroutineScope {
                val movieTasks = movieLibraries.map { library ->
                    async {
                        try {
                            jellyfinRepository.getMovies(
                                parentId = library.id,
                                sortBy = SortBy.RELEASE_DATE,
                                sortDescending = true,
                                limit = 8,
                                isPlayed = false
                            )
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to load movies from library ${library.name}")
                            emptyList<AfinityMovie>()
                        }
                    }
                }

                val showTasks = tvLibraries.map { library ->
                    async {
                        try {
                            jellyfinRepository.getShows(
                                parentId = library.id,
                                sortBy = SortBy.RELEASE_DATE,
                                sortDescending = true,
                                limit = 8,
                                isPlayed = false
                            )
                        } catch (e: Exception) {
                            Timber.w(e, "Failed to load shows from library ${library.name}")
                            emptyList<AfinityShow>()
                        }
                    }
                }

                Pair(movieTasks.awaitAll(), showTasks.awaitAll())
            }

            val allLatestMovies = movieResults.flatten()
            val allLatestSeries = showResults.flatten()

            val latestMovies = allLatestMovies
                .sortedByDescending { it.premiereDate }
                .take(15)

            val latestTvSeries = allLatestSeries
                .sortedByDescending { it.premiereDate }
                .take(15)

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

    private fun updateProgress(progress: Float, phase: String) {
        _loadingProgress.value = progress
        _loadingPhase.value = phase
    }

    fun clearAllData() {
        Timber.d("Clearing all cached app data")
        _latestMedia.value = emptyList()
        _continueWatching.value = emptyList()
        _libraries.value = emptyList()
        _userProfileImageUrl.value = null
        _latestMovies.value = emptyList()
        _latestTvSeries.value = emptyList()
        _highestRated.value = emptyList()
        _recommendationCategories.value = emptyList()
        _isInitialDataLoaded.value = false
        _loadingProgress.value = 0f
        _loadingPhase.value = ""
    }
}