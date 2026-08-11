package com.makd.afinity.data.repository

import android.content.Context
import coil3.SingletonImageLoader
import com.makd.afinity.data.database.AfinityDatabase
import com.makd.afinity.data.repository.home.HomeCacheRepository
import com.makd.afinity.data.repository.home.HomeSectionsRepository
import com.makd.afinity.data.repository.media.BoxSetCache
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.di.GitHubClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val EXO_CACHE_DIR = "exo_media_cache"
private const val HTTP_CACHE_DIR = "http_cache"
private const val MPV_CACHE_DIR = "mpv"
private const val FONTCONFIG_CACHE_DIR = "fontconfig"
private const val CRASHES_DIR = "crashes"
private const val AVATARS_DIR = "user_avatars"

private const val ITEM_METADATA_TTL_MS = 30L * 24 * 60 * 60 * 1000
private const val LIBRARY_CACHE_TTL_MS = 7L * 24 * 60 * 60 * 1000
private const val MAX_CRASH_FILES = 5

enum class CacheKind {
    HOME_ROWS,
    GENRES,
    PEOPLE,
    PERSON_DETAILS,
    BOX_SETS,
    ITEM_METADATA,
    JELLYFIN_STATS,
    JELLYSEERR_REQUESTS,
    AUDIOBOOKSHELF,
    AUDIBLE_RATINGS,
}

enum class CacheStore {
    IMAGES,
    VIDEO,
    NETWORK,
    PLAYER,
}

enum class CacheSection {
    IMAGES,
    VIDEO,
    NETWORK,
    PLAYER,
    JELLYFIN_METADATA,
    JELLYSEERR,
    AUDIOBOOKSHELF;

    val stores: Set<CacheStore>
        get() =
            when (this) {
                IMAGES -> setOf(CacheStore.IMAGES)
                VIDEO -> setOf(CacheStore.VIDEO)
                NETWORK -> setOf(CacheStore.NETWORK)
                PLAYER -> setOf(CacheStore.PLAYER)
                else -> emptySet()
            }

    val kinds: Set<CacheKind>
        get() =
            when (this) {
                JELLYFIN_METADATA ->
                    setOf(
                        CacheKind.HOME_ROWS,
                        CacheKind.GENRES,
                        CacheKind.PEOPLE,
                        CacheKind.PERSON_DETAILS,
                        CacheKind.BOX_SETS,
                        CacheKind.ITEM_METADATA,
                        CacheKind.JELLYFIN_STATS,
                    )
                JELLYSEERR -> setOf(CacheKind.JELLYSEERR_REQUESTS)
                AUDIOBOOKSHELF -> setOf(CacheKind.AUDIOBOOKSHELF, CacheKind.AUDIBLE_RATINGS)
                else -> emptySet()
            }
}

data class CacheUsage(
    val bytes: Map<CacheStore, Long>,
    val entries: Map<CacheKind, Int>,
) {
    val totalBytes: Long
        get() = bytes.values.sum()

    val metadataEntries: Int
        get() = entries.values.sum()

    val isEmpty: Boolean
        get() = totalBytes <= 0L && metadataEntries == 0
}

fun interface VideoCacheCleaner {
    fun clear()
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
    private val videoCacheCleaner: VideoCacheCleaner,
    private val okHttpClient: OkHttpClient,
    @param:GitHubClient private val gitHubOkHttpClient: OkHttpClient,
    private val jellyseerrRepository: JellyseerrRepository,
    private val audiobookshelfRepository: AudiobookshelfRepository,
    private val deletedItemsRepository: DeletedItemsRepository,
) {
    suspend fun usage(): CacheUsage =
        withContext(Dispatchers.IO) {
            val imageBytes = runCatching {
                SingletonImageLoader.get(context).diskCache?.size ?: 0L
            }.onFailure { Timber.w(it, "Failed to read image cache size") }.getOrDefault(0L)

            val bytes =
                mapOf(
                    CacheStore.IMAGES to imageBytes,
                    CacheStore.VIDEO to directorySize(cacheDir(EXO_CACHE_DIR)),
                    CacheStore.NETWORK to directorySize(cacheDir(HTTP_CACHE_DIR)),
                    CacheStore.PLAYER to
                        directorySize(cacheDir(MPV_CACHE_DIR)) +
                            directorySize(cacheDir(FONTCONFIG_CACHE_DIR)),
                )

            val counters =
                mapOf<CacheKind, suspend () -> Int>(
                    CacheKind.HOME_ROWS to { database.homeCacheDao().cachedEntryCount() },
                    CacheKind.GENRES to { database.genreCacheDao().cachedEntryCount() },
                    CacheKind.PEOPLE to { database.topPeopleDao().cachedEntryCount() },
                    CacheKind.PERSON_DETAILS to { database.personSectionDao().cachedEntryCount() },
                    CacheKind.BOX_SETS to { database.boxSetCacheDao().cachedEntryCount() },
                    CacheKind.JELLYSEERR_REQUESTS to
                        {
                            database.jellyseerrDao().cachedEntryCount()
                        },
                    CacheKind.AUDIOBOOKSHELF to
                        {
                            database.audiobookshelfDao().cachedEntryCount()
                        },
                    CacheKind.ITEM_METADATA to
                        {
                            database.itemMetadataCacheDao().cachedEntryCount()
                        },
                    CacheKind.JELLYFIN_STATS to { database.jellyfinStatsDao().cachedEntryCount() },
                    CacheKind.AUDIBLE_RATINGS to { database.audibleRatingDao().cachedEntryCount() },
                )

            val entries = counters.mapValues { (kind, count) ->
                runCatching { count() }
                    .onFailure { Timber.w(it, "Failed to count cached entries for $kind") }
                    .getOrDefault(0)
            }

            CacheUsage(bytes = bytes, entries = entries)
        }

    suspend fun clearCachedData(sections: Set<CacheSection> = CacheSection.entries.toSet()) =
        withContext(Dispatchers.IO) {
            if (CacheSection.IMAGES in sections) {
                runCatching {
                    val loader = SingletonImageLoader.get(context)
                    loader.diskCache?.clear()
                    loader.memoryCache?.clear()
                }
                    .onFailure { Timber.e(it, "Failed to clear image cache") }
            }

            if (CacheSection.VIDEO in sections) {
                runCatching { videoCacheCleaner.clear() }
                    .onFailure { Timber.e(it, "Failed to clear video cache") }
            }

            if (CacheSection.NETWORK in sections) {
                runCatching { okHttpClient.cache?.evictAll() }
                    .onFailure { Timber.e(it, "Failed to clear network cache") }
                runCatching { gitHubOkHttpClient.cache?.evictAll() }
                    .onFailure { Timber.e(it, "Failed to clear GitHub network cache") }
            }

            if (CacheSection.PLAYER in sections) {
                runCatching {
                    cacheDir(MPV_CACHE_DIR).deleteRecursively()
                    cacheDir(FONTCONFIG_CACHE_DIR).deleteRecursively()
                }
                    .onFailure { Timber.e(it, "Failed to clear player cache") }
            }

            if (CacheSection.JELLYFIN_METADATA in sections) {
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
                runCatching { database.itemMetadataCacheDao().clearAll() }
                    .onFailure { Timber.e(it, "Failed to clear item metadata cache") }
                runCatching { database.jellyfinStatsDao().clearAll() }
                    .onFailure { Timber.e(it, "Failed to clear Jellyfin stats cache") }
                runCatching { deletedItemsRepository.clear() }
                    .onFailure { Timber.e(it, "Failed to clear deleted item tombstones") }
            }

            if (CacheSection.JELLYSEERR in sections) {
                runCatching { database.jellyseerrDao().deleteAllRequests() }
                    .onFailure { Timber.e(it, "Failed to clear Jellyseerr requests") }
                runCatching { jellyseerrRepository.clearCachedSettings() }
                    .onFailure { Timber.e(it, "Failed to clear Jellyseerr settings cache") }
            }

            if (CacheSection.AUDIOBOOKSHELF in sections) {
                runCatching {
                    database.audiobookshelfDao().deleteAllEpisodes()
                    database.audiobookshelfDao().deleteAllItems()
                    database.audiobookshelfDao().deleteAllLibraries()
                }
                    .onFailure { Timber.e(it, "Failed to clear Audiobookshelf cache") }
                runCatching { database.audibleRatingDao().clearAll() }
                    .onFailure { Timber.e(it, "Failed to clear Audible ratings cache") }
                runCatching { audiobookshelfRepository.clearPersonalizedCache() }
                    .onFailure { Timber.e(it, "Failed to clear Audiobookshelf shelves") }
            }

            vacuum()

            if (CacheSection.JELLYFIN_METADATA in sections) {
                runCatching { appDataRepository.reloadHomeData() }
                    .onFailure { Timber.e(it, "Failed to reload home data") }
            }

            homeSectionsRepository.ensureLayout(force = true)
        }

    suspend fun pruneExpiredData() =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()

            runCatching {
                database.itemMetadataCacheDao().clearOldCache(now - ITEM_METADATA_TTL_MS)
            }
                .onFailure { Timber.w(it, "Failed to prune item metadata cache") }

            runCatching {
                database.libraryCacheDao().clearExpiredCache(now - LIBRARY_CACHE_TTL_MS)
            }
                .onFailure { Timber.w(it, "Failed to prune library cache") }

            runCatching { deletedItemsRepository.prune() }
                .onFailure { Timber.w(it, "Failed to prune deleted item tombstones") }

            runCatching { pruneCrashFiles() }
                .onFailure { Timber.w(it, "Failed to prune crash files") }

            runCatching { pruneOrphanedAvatars() }
                .onFailure { Timber.w(it, "Failed to prune user avatars") }
        }

    private fun pruneCrashFiles() {
        val files =
            File(context.filesDir, CRASHES_DIR).listFiles()?.takeIf { it.isNotEmpty() } ?: return
        files
            .sortedByDescending { it.lastModified() }
            .drop(MAX_CRASH_FILES)
            .forEach { stale ->
                if (stale.delete()) Timber.d("Pruned crash file ${stale.name}")
            }
    }

    private suspend fun pruneOrphanedAvatars() {
        val dir = File(context.filesDir, AVATARS_DIR)
        val files = dir.listFiles()?.takeIf { it.isNotEmpty() } ?: return
        val knownUserIds = database.userDao().getAllUsers().map { it.id.toString() }.toSet()

        files.forEach { file ->
            val isTemp = file.name.endsWith(".tmp")
            val ownerId = file.name.substringBefore('_', missingDelimiterValue = "")
            val isOrphan = ownerId.isNotEmpty() && ownerId !in knownUserIds
            if ((isTemp || isOrphan) && file.delete()) {
                Timber.d("Pruned avatar ${file.name}")
            }
        }
    }

    private fun vacuum() {
        runCatching { database.openHelper.writableDatabase.execSQL("VACUUM") }
            .onFailure { Timber.w(it, "Failed to vacuum database") }
    }

    private fun cacheDir(name: String): File = File(context.cacheDir, name)

    private fun directorySize(dir: File): Long = runCatching {
        if (!dir.exists()) 0L else dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
    }
        .onFailure { Timber.w(it, "Failed to size ${dir.name}") }
        .getOrDefault(0L)
}
