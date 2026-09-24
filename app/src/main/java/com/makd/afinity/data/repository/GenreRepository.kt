package com.makd.afinity.data.repository

import com.makd.afinity.data.database.AfinityDatabase
import com.makd.afinity.data.database.AfinityTypeConverters
import com.makd.afinity.data.database.entities.GenreCacheEntity
import com.makd.afinity.data.database.entities.GenreMovieCacheEntity
import com.makd.afinity.data.database.entities.GenreShowCacheEntity
import com.makd.afinity.data.database.entities.ShowGenreCacheEntity
import com.makd.afinity.data.manager.BackgroundWorkQueue
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.GenreItem
import com.makd.afinity.data.models.GenreType
import com.makd.afinity.data.models.common.CollectionType
import com.makd.afinity.data.models.media.AfinityCollection
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.media.withBaseUrl
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.di.ApplicationScope
import com.makd.afinity.util.ItemIds
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

@Singleton
class GenreRepository
@Inject
constructor(
    private val mediaRepository: MediaRepository,
    private val sessionManager: SessionManager,
    private val deletedItemsRepository: DeletedItemsRepository,
    private val backgroundWorkQueue: BackgroundWorkQueue,
    @ApplicationScope private val scope: CoroutineScope,
    database: AfinityDatabase,
) {
    private val genreCacheTTL = 24.hours.inWholeMilliseconds
    private val genreFailureBackoff = 10.minutes.inWholeMilliseconds
    private val genreRefreshMutex = Mutex()
    private var genreRefreshJob: Job? = null
    private var lastGenreFailure: Pair<String, Long>? = null
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

    private fun currentUserId(): String =
        sessionManager.currentSession.value?.userId?.toString() ?: ""

    private suspend fun videoLibraries(): List<AfinityCollection> =
        mediaRepository.libraries.first().ifEmpty { mediaRepository.getLibraries() }

    private suspend fun fetchGenreNames(
        type: CollectionType,
        libraries: List<AfinityCollection>,
    ): List<String>? {
        val itemTypes =
            when (type) {
                CollectionType.Movies -> listOf("MOVIE")
                CollectionType.TvShows -> listOf("SERIES")
                else -> return emptyList()
            }
        val names = sortedSetOf<String>()
        for (library in libraries.filter { it.type == type }) {
            val genres =
                mediaRepository
                    .getGenresResult(parentId = library.id, includeItemTypes = itemTypes)
                    .getOrElse { e ->
                        if (e is CancellationException) throw e
                        Timber.w(e, "Failed to load ${type.name} genres for ${library.name}")
                        return null
                    }
            names.addAll(genres)
        }
        return names.toList()
    }

    suspend fun loadCombinedGenres() {
        withContext(Dispatchers.IO) {
            try {
                val serverId = currentServerId()
                val userId = currentUserId()
                var movieGenreNames = genreCacheDao.getAllGenreNames(serverId, userId)
                var showGenreNames = genreCacheDao.getAllShowGenreNames(serverId, userId)

                if (movieGenreNames.isEmpty() && showGenreNames.isEmpty()) {
                    if (refreshGenreLists()) {
                        movieGenreNames = genreCacheDao.getAllGenreNames(serverId, userId)
                        showGenreNames = genreCacheDao.getAllShowGenreNames(serverId, userId)
                    }
                } else if (isGenreListStale(serverId, userId)) {
                    refreshGenreListsInBackground()
                }

                val movieGenreItems = movieGenreNames.map { GenreItem(it, GenreType.MOVIE) }
                val showGenreItems = showGenreNames.map { GenreItem(it, GenreType.SHOW) }

                val combinedList = (movieGenreItems + showGenreItems).shuffled()
                _combinedGenres.value = combinedList
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to load combined genres")
            }
        }
    }

    private suspend fun isGenreListStale(serverId: String, userId: String): Boolean {
        val oldest =
            listOfNotNull(
                    genreCacheDao.getOldestCacheTimestamp(serverId, userId),
                    genreCacheDao.getOldestShowCacheTimestamp(serverId, userId),
                )
                .minOrNull() ?: return true
        return System.currentTimeMillis() - oldest >= genreCacheTTL
    }

    private fun refreshGenreListsInBackground() {
        if (genreRefreshJob?.isActive == true) return
        genreRefreshJob =
            scope.launch(Dispatchers.IO) {
                try {
                    refreshGenreLists()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Background genre refresh failed")
                }
            }
    }

    private suspend fun refreshGenreLists(): Boolean = genreRefreshMutex.withLock {
        val serverId = currentServerId()
        val userId = currentUserId()
        val sessionKey = "${serverId}_$userId"
        val now = System.currentTimeMillis()
        lastGenreFailure?.let { (key, at) ->
            if (key == sessionKey && now - at < genreFailureBackoff) return false
        }

        val libraries = videoLibraries()
        if (libraries.isEmpty()) {
            lastGenreFailure = sessionKey to now
            return false
        }

        val (movieGenres, showGenres) =
            backgroundWorkQueue.run("genre lists") {
                fetchGenreNames(CollectionType.Movies, libraries) to
                    fetchGenreNames(CollectionType.TvShows, libraries)
            }

        val timestamp = System.currentTimeMillis()
        movieGenres?.let { names ->
            val removed = genreCacheDao.getAllGenreNames(serverId, userId) - names.toSet()
            removed.forEach {
                genreCacheDao.deleteGenreCache(it, serverId, userId)
                genreCacheDao.deleteMoviesForGenre(it, serverId, userId)
            }
            genreCacheDao.insertGenreCaches(
                names.map {
                    GenreCacheEntity(
                        genreName = it,
                        serverId = serverId,
                        userId = userId,
                        lastFetchedTimestamp = timestamp,
                        movieCount = 0,
                    )
                }
            )
        }
        showGenres?.let { names ->
            val removed = genreCacheDao.getAllShowGenreNames(serverId, userId) - names.toSet()
            removed.forEach {
                genreCacheDao.deleteShowGenreCache(it, serverId, userId)
                genreCacheDao.deleteShowsForGenre(it, serverId, userId)
            }
            genreCacheDao.insertShowGenreCaches(
                names.map {
                    ShowGenreCacheEntity(
                        genreName = it,
                        serverId = serverId,
                        userId = userId,
                        lastFetchedTimestamp = timestamp,
                        showCount = 0,
                    )
                }
            )
        }

        val succeeded = movieGenres != null && showGenres != null
        lastGenreFailure = if (succeeded) null else sessionKey to now
        succeeded
    }

    suspend fun loadMoviesForGenre(genre: String, limit: Int = 20) {
        if (_genreMovies.value.containsKey(genre)) return

        withContext(Dispatchers.IO) {
            try {
                val serverId = currentServerId()
                val userId = currentUserId()
                _genreLoadingStates.update { it + (genre to true) }

                val cachedMovieEntities =
                    genreCacheDao.getCachedMoviesForGenre(genre, serverId, userId)
                if (cachedMovieEntities.isNotEmpty()) {
                    val currentBaseUrl = mediaRepository.getBaseUrl()
                    val decodedMovies = cachedMovieEntities.mapNotNull { entity ->
                        afinityTypeConverters.toAfinityMovie(entity.movieData)?.let { movie ->
                            movie.copy(images = movie.images.withBaseUrl(currentBaseUrl))
                        }
                    }
                    val cachedMovies =
                        deletedItemsRepository.retainAlive(decodedMovies) { it.id.toString() }

                    if (cachedMovies.isNotEmpty()) {
                        _genreMovies.update { it + (genre to cachedMovies) }
                        _genreLoadingStates.update { it + (genre to false) }

                        val currentTime = System.currentTimeMillis()
                        val isFresh =
                            genreCacheDao.isGenreCacheFresh(
                                genre,
                                serverId,
                                userId,
                                genreCacheTTL,
                                currentTime,
                            )

                        if (isFresh) return@withContext
                    }
                }

                val movies =
                    mediaRepository.getMoviesByGenre(genre = genre, limit = limit, shuffle = true)

                if (movies.isNotEmpty()) {
                    deletedItemsRepository.unmark(movies.map { it.id.toString() })
                    val timestamp = System.currentTimeMillis()
                    val movieEntities = movies.mapIndexed { index, movie ->
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
                    genreCacheDao.cacheGenreWithMovies(
                        genre,
                        serverId,
                        userId,
                        movieEntities,
                        timestamp,
                    )

                    _genreMovies.update { it + (genre to movies) }
                } else {
                    if (_genreMovies.value[genre].isNullOrEmpty()) {
                        _genreMovies.update { it + (genre to emptyList()) }
                    }
                }

                _genreLoadingStates.update { it + (genre to false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to load movies for genre: $genre")
                _genreLoadingStates.update { it + (genre to false) }

                try {
                    val fallbackEntities =
                        genreCacheDao.getCachedMoviesForGenre(
                            genre,
                            currentServerId(),
                            currentUserId(),
                        )
                    val currentBaseUrl = mediaRepository.getBaseUrl()

                    val decodedFallback = fallbackEntities.mapNotNull { entity ->
                        afinityTypeConverters.toAfinityMovie(entity.movieData)?.let { movie ->
                            movie.copy(images = movie.images.withBaseUrl(currentBaseUrl))
                        }
                    }
                    val fallbackMovies =
                        deletedItemsRepository.retainAlive(decodedFallback) { it.id.toString() }
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
                val decodedShows = cachedShowEntities.mapNotNull { entity ->
                    afinityTypeConverters.toAfinityShow(entity.showData)?.let { show ->
                        show.copy(images = show.images.withBaseUrl(currentBaseUrl))
                    }
                }
                val cachedShows =
                    deletedItemsRepository.retainAlive(decodedShows) { it.id.toString() }

                if (cachedShows.isNotEmpty()) {
                    _genreShows.update { it + (genre to cachedShows) }
                    _genreLoadingStates.update { it + (genre to false) }

                    val currentTime = System.currentTimeMillis()
                    val isFresh =
                        genreCacheDao.isShowGenreCacheFresh(
                            genre,
                            serverId,
                            userId,
                            genreCacheTTL,
                            currentTime,
                        )
                    if (isFresh) return
                }
            }

            val shows =
                mediaRepository.getShowsByGenre(genre = genre, limit = limit, shuffle = true)

            if (shows.isNotEmpty()) {
                deletedItemsRepository.unmark(shows.map { it.id.toString() })
                val timestamp = System.currentTimeMillis()
                val showEntities = shows.mapIndexed { index, show ->
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

                _genreShows.update { it + (genre to shows) }
            } else {
                if (_genreShows.value[genre].isNullOrEmpty()) {
                    _genreShows.update { it + (genre to emptyList()) }
                }
            }

            _genreLoadingStates.update { it + (genre to false) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to load shows for genre: $genre")
            _genreLoadingStates.update { it + (genre to false) }

            try {
                val fallbackEntities =
                    genreCacheDao.getCachedShowsForGenre(genre, currentServerId(), currentUserId())
                val currentBaseUrl = mediaRepository.getBaseUrl()

                val decodedFallback = fallbackEntities.mapNotNull { entity ->
                    afinityTypeConverters.toAfinityShow(entity.showData)?.let { show ->
                        show.copy(images = show.images.withBaseUrl(currentBaseUrl))
                    }
                }
                val fallbackShows =
                    deletedItemsRepository.retainAlive(decodedFallback) { it.id.toString() }
                if (fallbackShows.isNotEmpty()) {
                    _genreShows.update { it + (genre to fallbackShows) }
                }
            } catch (cacheError: Exception) {
                /* Ignore */
            }
        }
    }

    suspend fun removeItem(itemId: String) {
        val normalized = ItemIds.normalize(itemId) ?: return
        val canonical = ItemIds.canonical(itemId) ?: return
        val matches: (UUID) -> Boolean = { id -> ItemIds.normalize(id.toString()) == normalized }

        _genreMovies.update { currentMap ->
            if (currentMap.values.none { list -> list.any { matches(it.id) } }) currentMap
            else currentMap.mapValues { (_, movies) -> movies.filterNot { matches(it.id) } }
        }
        _genreShows.update { currentMap ->
            if (currentMap.values.none { list -> list.any { matches(it.id) } }) currentMap
            else currentMap.mapValues { (_, shows) -> shows.filterNot { matches(it.id) } }
        }

        withContext(Dispatchers.IO) {
            try {
                val serverId = currentServerId()
                val userId = currentUserId()
                genreCacheDao.deleteCachedMovie(canonical, serverId, userId)
                genreCacheDao.deleteCachedShow(canonical, serverId, userId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to remove $itemId from genre caches")
            }
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

        if (updatedItem is AfinityEpisode) {
            _genreShows.update { currentMap ->
                val newMap = currentMap.toMutableMap()
                var changed = false
                newMap.forEach { (genre, shows) ->
                    val index = shows.indexOfFirst { it.id == updatedItem.seriesId }
                    if (index != -1) {
                        val show = shows[index]
                        val currentCount = show.unplayedItemCount ?: 0
                        val newCount =
                            if (updatedItem.played) {
                                (currentCount - 1).coerceAtLeast(0)
                            } else {
                                currentCount + 1
                            }
                        val mut = shows.toMutableList()
                        mut[index] = show.copy(unplayedItemCount = newCount)
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
                        genreCacheDao.updateCachedMovieData(
                            itemId.toString(),
                            serverId,
                            userId,
                            newJson,
                        )
                        Timber.d("Updated movie DB cache for: ${updatedItem.name}")
                    }
                } else if (updatedItem is AfinityShow) {
                    val newJson = afinityTypeConverters.fromAfinityShow(updatedItem)
                    if (newJson != null) {
                        genreCacheDao.updateCachedShowData(
                            itemId.toString(),
                            serverId,
                            userId,
                            newJson,
                        )
                        Timber.d("Updated show DB cache for: ${updatedItem.name}")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to update Genre DB caches")
            }
        }
    }

    suspend fun clearAllData() {
        genreRefreshJob?.cancel()
        genreRefreshMutex.withLock { lastGenreFailure = null }
        try {
            genreCacheDao.clearAllCache()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear genre database caches")
        }
        _combinedGenres.value = emptyList()
        _genreMovies.value = emptyMap()
        _genreShows.value = emptyMap()
        _genreLoadingStates.value = emptyMap()
    }
}
