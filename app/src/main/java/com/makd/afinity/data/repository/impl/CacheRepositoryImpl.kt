package com.makd.afinity.data.repository.impl

import com.makd.afinity.data.database.dao.ListCacheDao
import com.makd.afinity.data.database.entities.ListCacheEntity
import com.makd.afinity.data.database.entities.ListType
import com.makd.afinity.data.database.entities.createCacheKey
import com.makd.afinity.data.database.entities.getItemTypeString
import com.makd.afinity.data.database.entities.toItemTypesJsonString
import com.makd.afinity.data.database.entities.toJsonString
import com.makd.afinity.data.database.entities.toUuidList
import com.makd.afinity.data.models.media.AfinityCollection
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.repository.CacheRepository
import com.makd.afinity.data.repository.DatabaseRepository
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CacheRepositoryImpl @Inject constructor(
    private val listCacheDao: ListCacheDao,
    private val databaseRepository: DatabaseRepository
) : CacheRepository {

    private fun calculateExpiryTime(ttlHours: Int): Long {
        return System.currentTimeMillis() + TimeUnit.HOURS.toMillis(ttlHours.toLong())
    }

    private suspend fun saveGenericListCache(
        userId: UUID,
        listType: String,
        items: List<Any>,
        ttlHours: Int
    ) {
        try {
            val itemIds = items.mapNotNull { item ->
                when (item) {
                    is AfinityItem -> item.id
                    is AfinityCollection -> item.id
                    else -> null
                }
            }

            val itemTypes = items.map { getItemTypeString(it) }

            val cacheEntity = ListCacheEntity(
                cacheKey = createCacheKey(listType, userId),
                userId = userId,
                listType = listType,
                itemIds = itemIds.toJsonString(),
                itemTypes = itemTypes.toItemTypesJsonString(),
                cachedAt = System.currentTimeMillis(),
                expiresAt = calculateExpiryTime(ttlHours)
            )

            listCacheDao.insertListCache(cacheEntity)
            Timber.d("Saved $listType cache with ${items.size} items")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save $listType cache")
        }
    }

    private suspend fun loadGenericItemCache(
        userId: UUID,
        listType: String
    ): List<AfinityItem>? {
        return try {
            val cacheEntity = listCacheDao.getListCacheByType(userId, listType) ?: return null

            if (cacheEntity.expiresAt < System.currentTimeMillis()) {
                Timber.d("$listType cache expired, deleting")
                listCacheDao.deleteListCache(cacheEntity.cacheKey)
                return null
            }

            val itemIds = cacheEntity.itemIds.toUuidList()
            val items = mutableListOf<AfinityItem>()

            for (itemId in itemIds) {
                val movie = databaseRepository.getMovie(itemId, userId)
                if (movie != null) {
                    items.add(movie)
                    continue
                }

                val show = databaseRepository.getShow(itemId, userId)
                if (show != null) {
                    items.add(show)
                    continue
                }

                val episode = databaseRepository.getEpisode(itemId, userId)
                if (episode != null) {
                    items.add(episode)
                }
            }

            Timber.d("Loaded $listType cache with ${items.size} items")
            items
        } catch (e: Exception) {
            Timber.e(e, "Failed to load $listType cache")
            null
        }
    }

    override suspend fun saveLatestMediaCache(userId: UUID, items: List<AfinityItem>, ttlHours: Int) {
        saveGenericListCache(userId, ListType.LATEST_MEDIA, items, ttlHours)
    }

    override suspend fun saveHeroCarouselCache(userId: UUID, items: List<AfinityItem>, ttlHours: Int) {
        saveGenericListCache(userId, ListType.HERO_CAROUSEL, items, ttlHours)
    }

    override suspend fun saveContinueWatchingCache(userId: UUID, items: List<AfinityItem>, ttlHours: Int) {
        saveGenericListCache(userId, ListType.CONTINUE_WATCHING, items, ttlHours)
    }

    override suspend fun saveNextUpCache(userId: UUID, episodes: List<AfinityEpisode>, ttlHours: Int) {
        saveGenericListCache(userId, ListType.NEXT_UP, episodes, ttlHours)
    }

    override suspend fun saveLibrariesCache(userId: UUID, libraries: List<AfinityCollection>, ttlHours: Int) {
        saveGenericListCache(userId, ListType.LIBRARIES, libraries, ttlHours)
    }

    override suspend fun saveLatestMoviesCache(userId: UUID, movies: List<AfinityMovie>, ttlHours: Int) {
        saveGenericListCache(userId, ListType.LATEST_MOVIES, movies, ttlHours)
    }

    override suspend fun saveLatestTvSeriesCache(userId: UUID, shows: List<AfinityShow>, ttlHours: Int) {
        saveGenericListCache(userId, ListType.LATEST_TV_SERIES, shows, ttlHours)
    }

    override suspend fun saveHighestRatedCache(userId: UUID, items: List<AfinityItem>, ttlHours: Int) {
        saveGenericListCache(userId, ListType.HIGHEST_RATED, items, ttlHours)
    }

    override suspend fun loadLatestMediaCache(userId: UUID): List<AfinityItem>? {
        return loadGenericItemCache(userId, ListType.LATEST_MEDIA)
    }

    override suspend fun loadHeroCarouselCache(userId: UUID): List<AfinityItem>? {
        return loadGenericItemCache(userId, ListType.HERO_CAROUSEL)
    }

    override suspend fun loadContinueWatchingCache(userId: UUID): List<AfinityItem>? {
        return loadGenericItemCache(userId, ListType.CONTINUE_WATCHING)
    }

    override suspend fun loadNextUpCache(userId: UUID): List<AfinityEpisode>? {
        val items = loadGenericItemCache(userId, ListType.NEXT_UP) ?: return null
        return items.filterIsInstance<AfinityEpisode>()
    }

    override suspend fun loadLibrariesCache(userId: UUID): List<AfinityCollection>? {
        return try {
            val cacheEntity = listCacheDao.getListCacheByType(userId, ListType.LIBRARIES) ?: return null

            if (cacheEntity.expiresAt < System.currentTimeMillis()) {
                listCacheDao.deleteListCache(cacheEntity.cacheKey)
                return null
            }

            null
        } catch (e: Exception) {
            Timber.e(e, "Failed to load libraries cache")
            null
        }
    }

    override suspend fun loadLatestMoviesCache(userId: UUID): List<AfinityMovie>? {
        val items = loadGenericItemCache(userId, ListType.LATEST_MOVIES) ?: return null
        return items.filterIsInstance<AfinityMovie>()
    }

    override suspend fun loadLatestTvSeriesCache(userId: UUID): List<AfinityShow>? {
        val items = loadGenericItemCache(userId, ListType.LATEST_TV_SERIES) ?: return null
        return items.filterIsInstance<AfinityShow>()
    }

    override suspend fun loadHighestRatedCache(userId: UUID): List<AfinityItem>? {
        return loadGenericItemCache(userId, ListType.HIGHEST_RATED)
    }

    override suspend fun clearListCache(userId: UUID, listType: String) {
        listCacheDao.deleteListCacheByType(userId, listType)
        Timber.d("Cleared $listType cache for user $userId")
    }

    override suspend fun clearAllUserCaches(userId: UUID) {
        listCacheDao.deleteAllUserListCaches(userId)
        Timber.d("Cleared all list caches for user $userId")
    }

    override suspend fun clearExpiredCaches() {
        listCacheDao.deleteExpiredListCaches(System.currentTimeMillis())
        Timber.d("Cleared expired list caches")
    }
}