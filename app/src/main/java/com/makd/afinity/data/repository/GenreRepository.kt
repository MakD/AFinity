package com.makd.afinity.data.repository

import com.makd.afinity.data.database.AfinityDatabase
import com.makd.afinity.data.database.AfinityTypeConverters
import com.makd.afinity.data.database.entities.GenreCacheEntity
import com.makd.afinity.data.database.entities.GenreMovieCacheEntity
import com.makd.afinity.data.database.entities.GenreShowCacheEntity
import com.makd.afinity.data.database.entities.ShowGenreCacheEntity
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.GenreItem
import com.makd.afinity.data.models.GenreType
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.media.withBaseUrl
import com.makd.afinity.data.repository.media.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.hours

@Singleton
class GenreRepository
@Inject
constructor(
    private val mediaRepository: MediaRepository,
    private val sessionManager: SessionManager,
    database: AfinityDatabase,
) {
    private val genreCacheTTL = 12.hours.inWholeMilliseconds
    private val genreCacheDao = database.genreCacheDao()
    private val afinityTypeConverters = AfinityTypeConverters()

    private val _combinedGenres = MutableStateFlow<List<GenreItem>>(emptyList())
    val combinedGenres: StateFlow<List<GenreItem>> = _combinedGenres.asStateFlow()

    private val _genreMovies = MutableStateFlow<Map<String, List<AfinityMovie>>>(emptyMap())
    val genreMovies: StateFlow<Map<String, List<AfinityMovie>>> = _genreMovies.asStateFlow()

    private val _genreShows = MutableStateFlow<Map<String, List<AfinityShow>>>(emptyMap())
    val genreShows: StateFlow<Map<String, List<AfinityShow>>> = _genreShows.asStateFlow()

    private val _genreLoadingStates = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val genreLoadingStates: StateFlow<Map<String, Boolean>> = _genreLoadingStates.asStateFlow()

    private fun currentServerId(): String = sessionManager.currentSession.value?.serverId ?: ""
    private fun currentUserId(): String = sessionManager.currentSession.value?.userId?.toString() ?: ""

    suspend fun loadCombinedGenres() {
        withContext(Dispatchers.IO) {
            try {
                coroutineScope {
                    val movieGenresTask = async { loadGenres() }
                    val showGenresTask = async { loadShowGenres() }
                    movieGenresTask.await()
                    showGenresTask.await()
                }

                val serverId = currentServerId()
                val userId = currentUserId()
                val movieGenreNames = genreCacheDao.getAllGenreNames(serverId, userId)
                val showGenreNames = genreCacheDao.getAllShowGenreNames(serverId, userId)

                val movieGenreItems = movieGenreNames.map { GenreItem(it, GenreType.MOVIE) }
                val showGenreItems = showGenreNames.map { GenreItem(it, GenreType.SHOW) }

                val combinedList = (movieGenreItems + showGenreItems).shuffled()
                _combinedGenres.value = combinedList
            } catch (e: Exception) {
                Timber.e(e, "Failed to load combined genres")
            }
        }
    }

    private suspend fun loadGenres() {
        try {
            val serverId = currentServerId()
            val userId = currentUserId()
            val cachedGenreNames = genreCacheDao.getAllGenreNames(serverId, userId)
            if (cachedGenreNames.isNotEmpty()) {
                val currentTime = System.currentTimeMillis()
                val oldestTimestamp = genreCacheDao.getOldestCacheTimestamp(serverId, userId) ?: 0
                val isFresh = (currentTime - oldestTimestamp) < genreCacheTTL

                if (isFresh) return
            }

            val genres =
                mediaRepository.getGenres(
                    parentId = null,
                    limit = null,
                    includeItemTypes = listOf("MOVIE"),
                )

            val timestamp = System.currentTimeMillis()
            val genreEntities =
                genres.map { genreName ->
                    GenreCacheEntity(
                        genreName = genreName,
                        serverId = serverId,
                        userId = userId,
                        lastFetchedTimestamp = timestamp,
                        movieCount = 0,
                    )
                }
            genreCacheDao.insertGenreCaches(genreEntities)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load genres")
        }
    }

    private suspend fun loadShowGenres() {
        try {
            val serverId = currentServerId()
            val userId = currentUserId()
            val cachedGenreNames = genreCacheDao.getAllShowGenreNames(serverId, userId)
            if (cachedGenreNames.isNotEmpty()) {
                val currentTime = System.currentTimeMillis()
                val oldestTimestamp = genreCacheDao.getOldestShowCacheTimestamp(serverId, userId) ?: 0
                val isFresh = (currentTime - oldestTimestamp) < genreCacheTTL
                if (isFresh) return
            }

            val genres =
                mediaRepository.getGenres(
                    parentId = null,
                    limit = null,
                    includeItemTypes = listOf("SERIES"),
                )

            val timestamp = System.currentTimeMillis()
            val genreEntities =
                genres.map { genreName ->
                    ShowGenreCacheEntity(
                        genreName = genreName,
                        serverId = serverId,
                        userId = userId,
                        lastFetchedTimestamp = timestamp,
                        showCount = 0,
                    )
                }
            genreCacheDao.insertShowGenreCaches(genreEntities)
        } catch (e: Exception) {
            Timber.e(e, "Failed to load show genres")
        }
    }

    suspend fun loadMoviesForGenre(genre: String, limit: Int = 20) {
        if (_genreMovies.value.containsKey(genre)) return

        withContext(Dispatchers.IO) {
            try {
                val serverId = currentServerId()
                val userId = currentUserId()
                _genreLoadingStates.update { it + (genre to true) }

                val cachedMovieEntities = genreCacheDao.getCachedMoviesForGenre(genre, serverId, userId)
                if (cachedMovieEntities.isNotEmpty()) {
                    val currentBaseUrl = mediaRepository.getBaseUrl()
                    val cachedMovies =
                        cachedMovieEntities.mapNotNull { entity ->
                            afinityTypeConverters.toAfinityMovie(entity.movieData)?.let { movie ->
                                movie.copy(images = movie.images.withBaseUrl(currentBaseUrl))
                            }
                        }

                    if (cachedMovies.isNotEmpty()) {
                        _genreMovies.update { it + (genre to cachedMovies) }
                        _genreLoadingStates.update { it + (genre to false) }

                        val currentTime = System.currentTimeMillis()
                        val isFresh =
                            genreCacheDao.isGenreCacheFresh(genre, serverId, userId, genreCacheTTL, currentTime)

                        if (isFresh) return@withContext
                    }
                }

                val movies =
                    mediaRepository.getMoviesByGenre(
                        genre = genre,
                        limit = limit,
                        shuffle = true,
                    )

                if (movies.isNotEmpty()) {
                    val timestamp = System.currentTimeMillis()
                    val movieEntities =
                        movies.mapIndexed { index, movie ->
                            GenreMovieCacheEntity(
                                genreName = genre,
                                movieId = movie.id.toString(),
                                serverId = serverId,
                                userId = userId,
                                movieData = afinityTypeConverters.fromAfinityMovie(movie) ?: "",
                                position = index,
                                cachedTimestamp = timestamp,
                            )
                        }
                    genreCacheDao.cacheGenreWithMovies(genre, serverId, userId, movieEntities, timestamp)
                }

                _genreMovies.update { it + (genre to movies) }
                _genreLoadingStates.update { it + (genre to false) }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load movies for genre: $genre")
                _genreLoadingStates.update { it + (genre to false) }

                try {
                    val fallbackEntities = genreCacheDao.getCachedMoviesForGenre(genre, currentServerId(), currentUserId())
                    val fallbackMovies =
                        fallbackEntities.mapNotNull { entity ->
                            afinityTypeConverters.toAfinityMovie(entity.movieData)
                        }
                    if (fallbackMovies.isNotEmpty()) {
                        _genreMovies.update { it + (genre to fallbackMovies) }
                    }
                } catch (cacheError: Exception) {
                    /* Ignore */
                }
            }
        }
    }

    suspend fun loadShowsForGenre(genre: String, limit: Int = 20) {
        if (_genreShows.value.containsKey(genre)) return

        try {
            val serverId = currentServerId()
            val userId = currentUserId()
            _genreLoadingStates.update { it + (genre to true) }

            val cachedShowEntities = genreCacheDao.getCachedShowsForGenre(genre, serverId, userId)
            if (cachedShowEntities.isNotEmpty()) {
                val currentBaseUrl = mediaRepository.getBaseUrl()
                val cachedShows =
                    cachedShowEntities.mapNotNull { entity ->
                        afinityTypeConverters.toAfinityShow(entity.showData)?.let { show ->
                            show.copy(images = show.images.withBaseUrl(currentBaseUrl))
                        }
                    }

                if (cachedShows.isNotEmpty()) {
                    _genreShows.update { it + (genre to cachedShows) }
                    _genreLoadingStates.update { it + (genre to false) }

                    val currentTime = System.currentTimeMillis()
                    val isFresh =
                        genreCacheDao.isShowGenreCacheFresh(genre, serverId, userId, genreCacheTTL, currentTime)
                    if (isFresh) return
                }
            }

            val shows =
                mediaRepository.getShowsByGenre(genre = genre, limit = limit, shuffle = true)

            if (shows.isNotEmpty()) {
                val timestamp = System.currentTimeMillis()
                val showEntities =
                    shows.mapIndexed { index, show ->
                        GenreShowCacheEntity(
                            genreName = genre,
                            showId = show.id.toString(),
                            serverId = serverId,
                            userId = userId,
                            showData = afinityTypeConverters.fromAfinityShow(show) ?: "",
                            position = index,
                            cachedTimestamp = timestamp,
                        )
                    }
                genreCacheDao.cacheGenreWithShows(genre, serverId, userId, showEntities, timestamp)
            }

            _genreShows.update { it + (genre to shows) }
            _genreLoadingStates.update { it + (genre to false) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load shows for genre: $genre")
            _genreLoadingStates.update { it + (genre to false) }
        }
    }

    suspend fun updateItemInCaches(updatedItem: AfinityItem) {
        val itemId = updatedItem.id

        if (updatedItem is AfinityMovie) {
            _genreMovies.update { currentMap ->
                val newMap = currentMap.toMutableMap()
                var changed = false
                newMap.forEach { (genre, movies) ->
                    val index = movies.indexOfFirst { it.id == itemId }
                    if (index != -1) {
                        val mut = movies.toMutableList()
                        mut[index] = updatedItem
                        newMap[genre] = mut
                        changed = true
                    }
                }
                if (changed) newMap else currentMap
            }
        }

        if (updatedItem is AfinityShow) {
            _genreShows.update { currentMap ->
                val newMap = currentMap.toMutableMap()
                var changed = false
                newMap.forEach { (genre, shows) ->
                    val index = shows.indexOfFirst { it.id == itemId }
                    if (index != -1) {
                        val mut = shows.toMutableList()
                        mut[index] = updatedItem
                        newMap[genre] = mut
                        changed = true
                    }
                }
                if (changed) newMap else currentMap
            }
        }

        withContext(Dispatchers.IO) {
            try {
                val serverId = currentServerId()
                val userId = currentUserId()
                if (updatedItem is AfinityMovie) {
                    val newJson = afinityTypeConverters.fromAfinityMovie(updatedItem)
                    if (newJson != null) {
                        genreCacheDao.updateCachedMovieData(itemId.toString(), serverId, userId, newJson)
                        Timber.d("Updated movie DB cache for: ${updatedItem.name}")
                    }
                } else if (updatedItem is AfinityShow) {
                    val newJson = afinityTypeConverters.fromAfinityShow(updatedItem)
                    if (newJson != null) {
                        genreCacheDao.updateCachedShowData(itemId.toString(), serverId, userId, newJson)
                        Timber.d("Updated show DB cache for: ${updatedItem.name}")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to update Genre DB caches")
            }
        }
    }

    suspend fun clearAllData() {
        try {
            genreCacheDao.clearAllCache()
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear genre database caches")
        }
        _combinedGenres.value = emptyList()
        _genreMovies.value = emptyMap()
        _genreShows.value = emptyMap()
        _genreLoadingStates.value = emptyMap()
    }
}