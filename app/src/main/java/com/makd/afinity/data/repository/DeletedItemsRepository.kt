package com.makd.afinity.data.repository

import com.makd.afinity.data.database.dao.DeletedItemDao
import com.makd.afinity.data.database.entities.DeletedItemEntity
import com.makd.afinity.util.ItemIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.days

@Singleton
class DeletedItemsRepository @Inject constructor(private val dao: DeletedItemDao) {

    private val mutex = Mutex()

    @Volatile private var cachedIds: Set<String>? = null

    suspend fun deletedIds(): Set<String> {
        cachedIds?.let { return it }
        return mutex.withLock {
            cachedIds
                ?: withContext(Dispatchers.IO) {
                        runCatching { dao.getAllIds().toSet() }
                            .onFailure { Timber.e(it, "Failed to load deleted item tombstones") }
                            .getOrDefault(emptySet())
                    }
                    .also { cachedIds = it }
        }
    }

    suspend fun mark(itemIds: Collection<String>, serverId: String) {
        val normalized = itemIds.mapNotNull { normalize(it) }.distinct()
        if (normalized.isEmpty()) return
        val now = System.currentTimeMillis()
        mutex.withLock {
            runCatching {
                    withContext(Dispatchers.IO) {
                        dao.insertAll(
                            normalized.map { DeletedItemEntity(it, serverId, now) }
                        )
                    }
                }
                .onFailure { Timber.e(it, "Failed to persist deleted item tombstones") }
            cachedIds = cachedIds?.plus(normalized)
        }
        Timber.d("Marked ${normalized.size} item(s) as deleted")
    }

    suspend fun unmark(itemIds: Collection<String>) {
        val normalized = itemIds.mapNotNull { normalize(it) }.distinct()
        if (normalized.isEmpty()) return
        val known = deletedIds()
        val stale = normalized.filter { it in known }
        if (stale.isEmpty()) return
        runCatching { withContext(Dispatchers.IO) { dao.deleteByIds(stale) } }
            .onFailure { Timber.w(it, "Failed to drop deleted item tombstones") }
        mutex.withLock { cachedIds = cachedIds?.minus(stale.toSet()) }
        Timber.d("Dropped ${stale.size} deleted-item tombstone(s) after re-add")
    }

    suspend fun <T> retainAlive(items: List<T>, idOf: (T) -> String): List<T> {
        if (items.isEmpty()) return items
        val deleted = deletedIds()
        if (deleted.isEmpty()) return items
        return items.filterNot { normalize(idOf(it)) in deleted }
    }

    suspend fun prune(maxAgeMs: Long = DEFAULT_MAX_AGE_MS) {
        runCatching {
                withContext(Dispatchers.IO) {
                    dao.deleteOlderThan(System.currentTimeMillis() - maxAgeMs)
                }
            }
            .onFailure { Timber.w(it, "Failed to prune deleted item tombstones") }
        mutex.withLock { cachedIds = null }
    }

    suspend fun clear() {
        runCatching { withContext(Dispatchers.IO) { dao.deleteAll() } }
            .onFailure { Timber.w(it, "Failed to clear deleted item tombstones") }
        mutex.withLock { cachedIds = emptySet() }
    }

    private fun normalize(raw: String): String? = ItemIds.normalize(raw)

    companion object {
        val DEFAULT_MAX_AGE_MS = 7.days.inWholeMilliseconds
    }
}