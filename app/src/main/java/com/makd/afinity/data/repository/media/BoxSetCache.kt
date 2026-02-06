package com.makd.afinity.data.repository.media

import com.makd.afinity.data.database.dao.BoxSetCacheDao
import com.makd.afinity.data.database.entities.BoxSetCacheEntity
import com.makd.afinity.data.database.entities.BoxSetCacheMetadata
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import timber.log.Timber

@Singleton
class BoxSetCache @Inject constructor(private val cacheDao: BoxSetCacheDao) {

    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    private var itemToBoxSetsMap: Map<UUID, List<UUID>> = emptyMap()

    private var lastBuiltTimestamp: Long = 0

    private val cacheLifetimeMs = 60 * 60 * 1000L

    private val currentCacheVersion = 1

    private var isInitialized = false

    private suspend fun ensureInitialized() =
        mutex.withLock {
            if (isInitialized) return@withLock

            try {
                Timber.d("Loading BoxSet cache from database...")
                val startTime = System.currentTimeMillis()

                val metadata = cacheDao.getMetadata()
                if (metadata != null) {
                    lastBuiltTimestamp = metadata.lastFullBuild
                    Timber.d(
                        "Found cache metadata: lastBuild=${lastBuiltTimestamp}, version=${metadata.cacheVersion}"
                    )

                    if (metadata.cacheVersion != currentCacheVersion) {
                        Timber.w(
                            "Cache version mismatch (${metadata.cacheVersion} != $currentCacheVersion), clearing cache"
                        )
                        clearDatabase()
                        isInitialized = true
                        return@withLock
                    }
                }

                val entries = cacheDao.getAllCacheEntries()
                val loadedMap = mutableMapOf<UUID, List<UUID>>()

                entries.forEach { entry ->
                    try {
                        val itemId = UUID.fromString(entry.itemId)
                        val boxSetIds =
                            json.decodeFromString<List<String>>(entry.boxSetIds).mapNotNull {
                                try {
                                    UUID.fromString(it)
                                } catch (e: Exception) {
                                    null
                                }
                            }
                        loadedMap[itemId] = boxSetIds
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to parse cache entry for item ${entry.itemId}")
                    }
                }

                itemToBoxSetsMap = loadedMap
                val duration = System.currentTimeMillis() - startTime
                Timber.i(
                    "Loaded BoxSet cache from database in ${duration}ms: ${loadedMap.size} items"
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to load cache from database, starting with empty cache")
                itemToBoxSetsMap = emptyMap()
                lastBuiltTimestamp = 0
            } finally {
                isInitialized = true
            }
        }

    suspend fun isStale(): Boolean {
        ensureInitialized()
        val currentTime = System.currentTimeMillis()
        return (currentTime - lastBuiltTimestamp) > cacheLifetimeMs
    }

    suspend fun isEmpty(): Boolean {
        ensureInitialized()
        return itemToBoxSetsMap.isEmpty()
    }

    suspend fun getBoxSetIdsForItem(itemId: UUID): List<UUID> {
        ensureInitialized()
        return itemToBoxSetsMap[itemId] ?: emptyList()
    }

    suspend fun buildCache(fetchAllBoxSets: suspend () -> List<BoxSetWithChildren>) =
        mutex.withLock {
            try {
                Timber.d("Building BoxSet cache...")
                val startTime = System.currentTimeMillis()

                val boxSetsWithChildren = fetchAllBoxSets()

                val newMap = mutableMapOf<UUID, MutableList<UUID>>()

                boxSetsWithChildren.forEach { boxSetData ->
                    boxSetData.childItemIds.forEach { childItemId ->
                        newMap.getOrPut(childItemId) { mutableListOf() }.add(boxSetData.boxSetId)
                    }
                }

                itemToBoxSetsMap = newMap.mapValues { it.value.toList() }
                lastBuiltTimestamp = System.currentTimeMillis()

                withContext(Dispatchers.IO) { saveToDatabase(newMap) }

                val duration = System.currentTimeMillis() - startTime
                Timber.i(
                    "BoxSet cache built and saved in ${duration}ms. Cached ${itemToBoxSetsMap.size} items in ${boxSetsWithChildren.size} BoxSets"
                )
            } catch (e: Exception) {
                Timber.e(e, "Failed to build BoxSet cache")
            }
        }

    private suspend fun saveToDatabase(cacheMap: Map<UUID, List<UUID>>) {
        try {
            Timber.d("Saving BoxSet cache to database...")

            val entities =
                cacheMap.map { (itemId, boxSetIds) ->
                    BoxSetCacheEntity(
                        itemId = itemId.toString(),
                        boxSetIds = json.encodeToString(boxSetIds.map { it.toString() }),
                        lastUpdated = System.currentTimeMillis(),
                    )
                }

            cacheDao.clearAllCacheEntries()
            cacheDao.insertCacheEntries(entities)

            val metadata =
                BoxSetCacheMetadata(
                    id = 1,
                    lastFullBuild = lastBuiltTimestamp,
                    cacheVersion = currentCacheVersion,
                )
            cacheDao.insertMetadata(metadata)

            Timber.d("Saved ${entities.size} cache entries to database")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save cache to database")
        }
    }

    suspend fun clear() =
        mutex.withLock {
            itemToBoxSetsMap = emptyMap()
            lastBuiltTimestamp = 0
            clearDatabase()
            Timber.d("BoxSet cache cleared from memory and database")
        }

    private suspend fun clearDatabase() {
        try {
            cacheDao.clearAllCache()
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear database cache")
        }
    }

    suspend fun getStats(): CacheStats {
        ensureInitialized()
        val ageMs = System.currentTimeMillis() - lastBuiltTimestamp
        return CacheStats(
            itemCount = itemToBoxSetsMap.size,
            ageMs = ageMs,
            isStale = isStale(),
            isEmpty = isEmpty(),
        )
    }
}

data class BoxSetWithChildren(val boxSetId: UUID, val childItemIds: List<UUID>)

data class CacheStats(
    val itemCount: Int,
    val ageMs: Long,
    val isStale: Boolean,
    val isEmpty: Boolean,
)
