package com.makd.afinity.data.repository

import android.content.Context
import coil3.SingletonImageLoader
import com.makd.afinity.data.database.AfinityDatabase
import com.makd.afinity.data.repository.home.HomeCacheRepository
import com.makd.afinity.data.repository.home.HomeSectionsRepository
import com.makd.afinity.data.repository.media.BoxSetCache
import com.makd.afinity.data.repository.media.MediaRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

enum class CacheKind {
    HOME_ROWS,
    GENRES,
    PEOPLE,
    PERSON_DETAILS,
    BOX_SETS,
}

data class CacheUsage(val imageBytes: Long, val entries: Map<CacheKind, Int>) {
    val metadataEntries: Int
        get() = entries.values.sum()

    val isEmpty: Boolean
        get() = imageBytes <= 0L && metadataEntries == 0
}

@Singleton
class CacheMaintenance
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val database: AfinityDatabase,
    private val homeCacheRepository: HomeCacheRepository,
    private val homeSectionsRepository: HomeSectionsRepository,
    private val mediaRepository: MediaRepository,
    private val genreRepository: GenreRepository,
    private val peopleRepository: PeopleRepository,
    private val boxSetCache: BoxSetCache,
    private val appDataRepository: AppDataRepository,
) {
    suspend fun usage(): CacheUsage =
        withContext(Dispatchers.IO) {
            val imageBytes =
                runCatching { SingletonImageLoader.get(context).diskCache?.size ?: 0L }
                    .onFailure { Timber.w(it, "Failed to read image cache size") }
                    .getOrDefault(0L)

            val counters =
                mapOf<CacheKind, suspend () -> Int>(
                    CacheKind.HOME_ROWS to { database.homeCacheDao().cachedEntryCount() },
                    CacheKind.GENRES to { database.genreCacheDao().cachedEntryCount() },
                    CacheKind.PEOPLE to { database.topPeopleDao().cachedEntryCount() },
                    CacheKind.PERSON_DETAILS to { database.personSectionDao().cachedEntryCount() },
                    CacheKind.BOX_SETS to { database.boxSetCacheDao().cachedEntryCount() },
                )

            val entries =
                counters.mapValues { (kind, count) ->
                    runCatching { count() }
                        .onFailure { Timber.w(it, "Failed to count cached entries for $kind") }
                        .getOrDefault(0)
                }

            CacheUsage(imageBytes = imageBytes, entries = entries)
        }

    suspend fun clearCachedData() =
        withContext(Dispatchers.IO) {
            runCatching {
                    val loader = SingletonImageLoader.get(context)
                    loader.diskCache?.clear()
                    loader.memoryCache?.clear()
                }
                .onFailure { Timber.e(it, "Failed to clear image cache") }

            runCatching { homeCacheRepository.invalidateAll() }
                .onFailure { Timber.e(it, "Failed to clear home cache") }
            runCatching { genreRepository.clearAllData() }
                .onFailure { Timber.e(it, "Failed to clear genre cache") }
            runCatching { peopleRepository.clearAllData() }
                .onFailure { Timber.e(it, "Failed to clear people cache") }
            runCatching { boxSetCache.clear() }
                .onFailure { Timber.e(it, "Failed to clear boxset cache") }
            runCatching { mediaRepository.invalidateAllCaches() }
                .onFailure { Timber.e(it, "Failed to invalidate media caches") }
            runCatching { homeSectionsRepository.clearAllData() }
                .onFailure { Timber.e(it, "Failed to clear home sections") }
            runCatching { appDataRepository.reloadHomeData() }
                .onFailure { Timber.e(it, "Failed to reload home data") }

            homeSectionsRepository.ensureLayout(force = true)
        }
}