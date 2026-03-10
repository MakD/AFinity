package com.makd.afinity.data.repository

import com.makd.afinity.data.database.AfinityDatabase
import com.makd.afinity.data.database.AfinityTypeConverters
import com.makd.afinity.data.database.entities.GenreCacheEntity
import com.makd.afinity.data.database.entities.GenreMovieCacheEntity
import com.makd.afinity.data.database.entities.GenreShowCacheEntity
import com.makd.afinity.data.database.entities.ShowGenreCacheEntity
import com.makd.afinity.data.models.GenreItem
import com.makd.afinity.data.models.GenreType
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityShow
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
constructor(private val jellyfinRepository: JellyfinRepository, database: AfinityDatabase) {
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

    suspend fun loadCombinedGenres() {
        withContext(Dispatchers.IO) {
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

    private suspend fun loadGenres() {
        try {
            val cachedGenreNames = genreCacheDao.getAllGenreNames()
            if (cachedGenreNames.isNotEmpty()) {
                val currentTime = System.currentTimeMillis()
                val oldestTimestamp = genreCacheDao.getOldestCacheTimestamp() ?: 0
                val isFresh = (currentTime - oldestTimestamp) < genreCacheTTL

                if (isFresh) return
            }

            val genres =
                jellyfinRepository.getGenres(
                    parentId = null,
                    limit = null,
                    includeItemTypes = listOf("MOVIE"),
                )

            val timestamp = System.currentTimeMillis()
            val genreEntities =
                genres.map { genreName ->
                    GenreCacheEntity(
                        genreName = genreName,
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
            val cachedGenreNames = genreCacheDao.getAllShowGenreNames()
            if (cachedGenreNames.isNotEmpty()) {
                val currentTime = System.currentTimeMillis()
                val oldestTimestamp = genreCacheDao.getOldestShowCacheTimestamp() ?: 0
                val isFresh = (currentTime - oldestTimestamp) < genreCacheTTL
                if (isFresh) return
            }

            val genres =
                jellyfinRepository.getGenres(
                    parentId = null,
                    limit = null,
                    includeItemTypes = listOf("SERIES"),
                )

            val timestamp = System.currentTimeMillis()
            val genreEntities =
                genres.map { genreName ->
                    ShowGenreCacheEntity(
                        genreName = genreName,
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
                _genreLoadingStates.update { it + (genre to true) }

                val cachedMovieEntities = genreCacheDao.getCachedMoviesForGenre(genre)
                if (cachedMovieEntities.isNotEmpty()) {
                    val cachedMovies =
                        cachedMovieEntities.mapNotNull { entity ->
                            afinityTypeConverters.toAfinityMovie(entity.movieData)
                        }

                    if (cachedMovies.isNotEmpty()) {
                        _genreMovies.update { it + (genre to cachedMovies) }
                        _genreLoadingStates.update { it + (genre to false) }

                        val currentTime = System.currentTimeMillis()
                        val isFresh =
                            genreCacheDao.isGenreCacheFresh(genre, genreCacheTTL, currentTime)

                        if (isFresh) return@withContext
                    }
                }

                val movies =
                    jellyfinRepository.getMoviesByGenre(
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
                                movieData = afinityTypeConverters.fromAfinityMovie(movie) ?: "",
                                position = index,
                                cachedTimestamp = timestamp,
                            )
                        }
                    genreCacheDao.cacheGenreWithMovies(genre, movieEntities, timestamp)
                }

                _genreMovies.update { it + (genre to movies) }
                _genreLoadingStates.update { it + (genre to false) }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load movies for genre: $genre")
                _genreLoadingStates.update { it + (genre to false) }

                try {
                    val fallbackEntities = genreCacheDao.getCachedMoviesForGenre(genre)
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
            _genreLoadingStates.update { it + (genre to true) }

            val cachedShowEntities = genreCacheDao.getCachedShowsForGenre(genre)
            if (cachedShowEntities.isNotEmpty()) {
                val cachedShows =
                    cachedShowEntities.mapNotNull { entity ->
                        afinityTypeConverters.toAfinityShow(entity.showData)
                    }

                if (cachedShows.isNotEmpty()) {
                    _genreShows.update { it + (genre to cachedShows) }
                    _genreLoadingStates.update { it + (genre to false) }

                    val currentTime = System.currentTimeMillis()
                    val isFresh =
                        genreCacheDao.isShowGenreCacheFresh(genre, genreCacheTTL, currentTime)
                    if (isFresh) return
                }
            }

            val shows =
                jellyfinRepository.getShowsByGenre(genre = genre, limit = limit, shuffle = true)

            if (shows.isNotEmpty()) {
                val timestamp = System.currentTimeMillis()
                val showEntities =
                    shows.mapIndexed { index, show ->
                        GenreShowCacheEntity(
                            genreName = genre,
                            showId = show.id.toString(),
                            showData = afinityTypeConverters.fromAfinityShow(show) ?: "",
                            position = index,
                            cachedTimestamp = timestamp,
                        )
                    }
                genreCacheDao.cacheGenreWithShows(genre, showEntities, timestamp)
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
                if (updatedItem is AfinityMovie) {
                    val newJson = afinityTypeConverters.fromAfinityMovie(updatedItem)
                    if (newJson != null) {
                        genreCacheDao.updateCachedMovieData(itemId.toString(), newJson)
                        Timber.d("Updated movie DB cache for: ${updatedItem.name}")
                    }
                } else if (updatedItem is AfinityShow) {
                    val newJson = afinityTypeConverters.fromAfinityShow(updatedItem)
                    if (newJson != null) {
                        genreCacheDao.updateCachedShowData(itemId.toString(), newJson)
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
