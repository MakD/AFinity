package com.makd.afinity.data.repository.media

import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.media.LibraryIndexEntry
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.sdk.model.api.BaseItemKind

@Singleton
class LibraryIndexRepository
@Inject
constructor(
    private val mediaRepository: MediaRepository,
    private val sessionManager: SessionManager,
) {
    private val ttl = 24.hours.inWholeMilliseconds
    private val cache = ConcurrentHashMap<String, Pair<Long, List<LibraryIndexEntry>>>()
    private val locks = ConcurrentHashMap<BaseItemKind, Mutex>()

    suspend fun index(
        type: BaseItemKind,
        force: Boolean = false,
    ): Result<List<LibraryIndexEntry>> {
        val requestedAt = System.currentTimeMillis()
        val lock = locks.getOrPut(type) { Mutex() }
        return lock.withLock {
            val key = cacheKey(type)
            val cached =
                key?.let { cache[it] }
                    ?.takeIf { (fetchedAt, _) ->
                        System.currentTimeMillis() - fetchedAt < ttl &&
                            (!force || fetchedAt >= requestedAt)
                    }
            if (cached != null) {
                Result.success(cached.second)
            } else {
                mediaRepository.getLibraryIndexResult(type).onSuccess { entries ->
                    if (key != null) cache[key] = System.currentTimeMillis() to entries
                }
            }
        }
    }

    fun clear() {
        cache.clear()
    }

    private fun cacheKey(type: BaseItemKind): String? {
        val session = sessionManager.currentSession.value ?: return null
        if (session.serverId.isBlank()) return null
        return "${session.serverId}_${session.userId}_${type.name}"
    }
}
