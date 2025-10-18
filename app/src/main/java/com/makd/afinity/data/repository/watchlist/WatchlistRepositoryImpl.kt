package com.makd.afinity.data.repository.watchlist

import com.makd.afinity.data.database.dao.WatchlistDao
import com.makd.afinity.data.database.entities.WatchlistItemEntity
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinitySeason
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.repository.JellyfinRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchlistRepositoryImpl @Inject constructor(
    private val watchlistDao: WatchlistDao,
    private val jellyfinRepository: JellyfinRepository
) : WatchlistRepository {

    override suspend fun addToWatchlist(itemId: UUID, itemType: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val entity = WatchlistItemEntity(
                    itemId = itemId,
                    itemType = itemType
                )
                watchlistDao.addToWatchlist(entity)
                Timber.d("Added item $itemId to watchlist")
                true
            } catch (e: Exception) {
                Timber.e(e, "Failed to add item to watchlist: $itemId")
                false
            }
        }
    }

    override suspend fun removeFromWatchlist(itemId: UUID): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                watchlistDao.removeFromWatchlistById(itemId)
                Timber.d("Removed item $itemId from watchlist")
                true
            } catch (e: Exception) {
                Timber.e(e, "Failed to remove item from watchlist: $itemId")
                false
            }
        }
    }

    override suspend fun isInWatchlist(itemId: UUID): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                watchlistDao.isInWatchlist(itemId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to check if item is in watchlist: $itemId")
                false
            }
        }
    }

    override fun isInWatchlistFlow(itemId: UUID): Flow<Boolean> {
        return watchlistDao.isInWatchlistFlow(itemId)
    }

    override suspend fun getWatchlistMovies(): List<AfinityMovie> {
        return withContext(Dispatchers.IO) {
            try {
                val watchlistItems = watchlistDao.getWatchlistItemsByType("MOVIE")
                val movies = watchlistItems.mapNotNull { entity ->
                    try {
                        val item = jellyfinRepository.getItemById(entity.itemId)
                        item as? AfinityMovie
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to load movie ${entity.itemId} from watchlist")
                        null
                    }
                }
                Timber.d("Loaded ${movies.size} movies from watchlist")
                movies
            } catch (e: Exception) {
                Timber.e(e, "Failed to load watchlist movies")
                emptyList()
            }
        }
    }

    override suspend fun getWatchlistShows(): List<AfinityShow> {
        return withContext(Dispatchers.IO) {
            try {
                val watchlistItems = watchlistDao.getWatchlistItemsByType("SERIES")
                val shows = watchlistItems.mapNotNull { entity ->
                    try {
                        val item = jellyfinRepository.getItemById(entity.itemId)
                        item as? AfinityShow
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to load show ${entity.itemId} from watchlist")
                        null
                    }
                }
                Timber.d("Loaded ${shows.size} shows from watchlist")
                shows
            } catch (e: Exception) {
                Timber.e(e, "Failed to load watchlist shows")
                emptyList()
            }
        }
    }

    override suspend fun getWatchlistSeasons(): List<AfinitySeason> {
        return withContext(Dispatchers.IO) {
            try {

                val watchlistItems = watchlistDao.getWatchlistItemsByType("SEASON")

                val seasons = watchlistItems.mapNotNull { entity ->
                    try {
                        val item = jellyfinRepository.getItemById(entity.itemId)
                        item as? AfinitySeason
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to load season ${entity.itemId} from watchlist")
                        null
                    }
                }
                Timber.d("Loaded ${seasons.size} seasons from watchlist")
                seasons
            } catch (e: Exception) {
                Timber.e(e, "Failed to load watchlist seasons")
                emptyList()
            }
        }
    }

    override suspend fun getWatchlistEpisodes(): List<AfinityEpisode> {
        return withContext(Dispatchers.IO) {
            try {
                val watchlistItems = watchlistDao.getWatchlistItemsByType("EPISODE")
                val episodes = watchlistItems.mapNotNull { entity ->
                    try {
                        val item = jellyfinRepository.getItemById(entity.itemId)
                        item as? AfinityEpisode
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to load episode ${entity.itemId} from watchlist")
                        null
                    }
                }
                Timber.d("Loaded ${episodes.size} episodes from watchlist")
                episodes
            } catch (e: Exception) {
                Timber.e(e, "Failed to load watchlist episodes")
                emptyList()
            }
        }
    }

    override suspend fun getWatchlistCount(): Int {
        return withContext(Dispatchers.IO) {
            try {
                watchlistDao.getWatchlistCount()
            } catch (e: Exception) {
                Timber.e(e, "Failed to get watchlist count")
                0
            }
        }
    }

    override fun getWatchlistCountFlow(): Flow<Int> {
        return watchlistDao.getWatchlistCountFlow()
    }

    override suspend fun clearWatchlist() {
        return withContext(Dispatchers.IO) {
            try {
                watchlistDao.clearWatchlist()
                Timber.d("Cleared watchlist")
            } catch (e: Exception) {
                Timber.e(e, "Failed to clear watchlist")
            }
        }
    }
}