package com.makd.afinity.data.repository

import com.makd.afinity.data.models.media.AfinityCollection
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityShow
import java.util.UUID

interface CacheRepository {

    suspend fun saveLatestMediaCache(userId: UUID, items: List<AfinityItem>, ttlHours: Int = 6)
    suspend fun saveHeroCarouselCache(userId: UUID, items: List<AfinityItem>, ttlHours: Int = 6)
    suspend fun saveContinueWatchingCache(userId: UUID, items: List<AfinityItem>, ttlHours: Int = 1)
    suspend fun saveNextUpCache(userId: UUID, episodes: List<AfinityEpisode>, ttlHours: Int = 1)
    suspend fun saveLibrariesCache(userId: UUID, libraries: List<AfinityCollection>, ttlHours: Int = 24)
    suspend fun saveLatestMoviesCache(userId: UUID, movies: List<AfinityMovie>, ttlHours: Int = 6)
    suspend fun saveLatestTvSeriesCache(userId: UUID, shows: List<AfinityShow>, ttlHours: Int = 6)
    suspend fun saveHighestRatedCache(userId: UUID, items: List<AfinityItem>, ttlHours: Int = 12)

    suspend fun loadLatestMediaCache(userId: UUID): List<AfinityItem>?
    suspend fun loadHeroCarouselCache(userId: UUID): List<AfinityItem>?
    suspend fun loadContinueWatchingCache(userId: UUID): List<AfinityItem>?
    suspend fun loadNextUpCache(userId: UUID): List<AfinityEpisode>?
    suspend fun loadLibrariesCache(userId: UUID): List<AfinityCollection>?
    suspend fun loadLatestMoviesCache(userId: UUID): List<AfinityMovie>?
    suspend fun loadLatestTvSeriesCache(userId: UUID): List<AfinityShow>?
    suspend fun loadHighestRatedCache(userId: UUID): List<AfinityItem>?

    suspend fun clearListCache(userId: UUID, listType: String)
    suspend fun clearAllUserCaches(userId: UUID)
    suspend fun clearExpiredCaches()
}