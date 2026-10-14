package com.makd.afinity.data.repository.lastfm

import com.makd.afinity.data.database.entities.LastFmArtistCacheEntity
import com.makd.afinity.data.database.entities.LastFmPopularCacheEntity
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.lastfm.LastFmTopTrack
import com.makd.afinity.data.models.lastfm.LastFmTrackPopularity
import com.makd.afinity.data.network.HotTracks
import com.makd.afinity.data.network.LastFmApiService
import com.makd.afinity.data.repository.DatabaseRepository
import com.makd.afinity.data.repository.SecurePreferencesRepository
import java.time.Year
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import timber.log.Timber

data class ArtistPopularity(val index: Map<String, Long>, val version: Int)

data class PopularRanking(val trackIds: List<UUID>, val trackCount: Int, val lastFmVersion: Int)

@Singleton
class LastFmRepository
@Inject
constructor(
    private val lastFmApiService: LastFmApiService,
    private val databaseRepository: DatabaseRepository,
    private val securePreferencesRepository: SecurePreferencesRepository,
    private val sessionManager: SessionManager,
) {

    private class CachedIndex(
        val index: Map<String, Long>,
        val version: Int,
        val fetchedAt: Long,
        val hasTracks: Boolean,
        val confirmed: Boolean,
    )

    private class Fetched(val entity: LastFmArtistCacheEntity, val persist: Boolean)

    private val inFlight = mutableMapOf<String, Mutex>()
    private val inFlightLock = Mutex()
    private val failedAt = ConcurrentHashMap<String, Long>()
    private val memory =
        object : LinkedHashMap<String, CachedIndex>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedIndex>) =
                size > MEMORY_CACHE_SIZE
        }

    suspend fun isEnabled(): Boolean = apiKey() != null

    fun popularity(
        artistName: String?,
        allowNetwork: Boolean,
        freshForMs: Long,
    ): Flow<ArtistPopularity> = flow {
        val name = artistName?.trim()?.takeIf { it.isNotEmpty() } ?: return@flow
        if (apiKey() == null) return@flow
        val key = name.lowercase()

        val stored = fromMemory(key) ?: fromDatabase(key)
        if (stored != null && stored.index.isNotEmpty()) {
            emit(ArtistPopularity(stored.index, stored.version))
        }

        if (!allowNetwork) return@flow
        if (stored != null && !stored.needsRefresh(freshForMs)) return@flow
        if (failedAt[key]?.let { System.currentTimeMillis() - it < RETRY_TTL_MS } == true) {
            return@flow
        }

        val refreshed = refresh(key, name, freshForMs) ?: return@flow
        if (refreshed.index.isNotEmpty() && refreshed.version != stored?.version) {
            emit(ArtistPopularity(refreshed.index, refreshed.version))
        }
    }

    suspend fun cachedRanking(artistId: UUID): PopularRanking? {
        val session = sessionManager.currentSession.value ?: return null
        if (apiKey() == null) return null
        val entity =
            runCatching {
                databaseRepository.getLastFmPopular(
                    session.serverId,
                    session.userId.toString(),
                    artistId.toString(),
                )
            }
                .getOrNull() ?: return null
        val ids = entity.trackIds.mapNotNull { runCatching { UUID.fromString(it) }.getOrNull() }
        if (ids.isEmpty()) return null
        return PopularRanking(ids, entity.trackCount, entity.lastFmVersion)
    }

    suspend fun saveRanking(artistId: UUID, ranking: PopularRanking) {
        val session = sessionManager.currentSession.value ?: return
        runCatching {
            databaseRepository.insertLastFmPopular(
                LastFmPopularCacheEntity(
                    serverId = session.serverId,
                    userId = session.userId.toString(),
                    artistId = artistId.toString(),
                    trackIds = ranking.trackIds.map { it.toString() },
                    trackCount = ranking.trackCount,
                    lastFmVersion = ranking.lastFmVersion,
                )
            )
        }
    }

    private suspend fun refresh(key: String, name: String, freshForMs: Long): CachedIndex? {
        val gate = inFlightLock.withLock { inFlight.getOrPut(key) { Mutex() } }
        try {
            gate.withLock {
                val current = fromMemory(key) ?: fromDatabase(key)
                if (current != null && !current.needsRefresh(freshForMs)) return current

                val apiKey = apiKey() ?: return null
                val fetched = fetch(key, name, apiKey)
                val entity = fetched.entity
                val succeeded = entity.tracks.isNotEmpty() || entity.confirmed
                if (!succeeded) {
                    if (fetched.persist) failedAt[key] = System.currentTimeMillis()
                    if (current != null || !fetched.persist) return null
                    runCatching { databaseRepository.insertLastFmArtist(entity) }
                    return rememberIndex(key, entity)
                }

                failedAt.remove(key)
                runCatching {
                    databaseRepository.insertLastFmArtist(entity)
                    databaseRepository.deleteLastFmArtistsOlderThan(
                        System.currentTimeMillis() - PRUNE_AFTER_MS
                    )
                }
                return rememberIndex(key, entity)
            }
        } finally {
            inFlightLock.withLock { if (!gate.isLocked) inFlight.remove(key) }
        }
    }

    private suspend fun apiKey(): String? {
        val session = sessionManager.currentSession.value ?: return null
        return securePreferencesRepository
            .getLastFmApiKey(session.serverId, session.userId.toString())
            ?.takeIf { it.isNotBlank() }
    }

    private fun fromMemory(artistKey: String): CachedIndex? =
        synchronized(memory) { memory[artistKey] }

    private suspend fun fromDatabase(artistKey: String): CachedIndex? {
        val entity =
            runCatching { databaseRepository.getLastFmArtist(artistKey) }.getOrNull() ?: return null
        return rememberIndex(artistKey, entity)
    }

    private suspend fun rememberIndex(
        artistKey: String,
        entity: LastFmArtistCacheEntity,
    ): CachedIndex {
        val index =
            if (entity.tracks.isEmpty()) emptyMap()
            else withContext(Dispatchers.Default) { HotTracks.index(entity.tracks) }
        val cached =
            CachedIndex(
                index = index,
                version = entity.tracks.hashCode(),
                fetchedAt = entity.fetchedAt,
                hasTracks = entity.tracks.isNotEmpty(),
                confirmed = entity.confirmed,
            )
        synchronized(memory) { memory[artistKey] = cached }
        return cached
    }

    private fun CachedIndex.needsRefresh(freshForMs: Long): Boolean {
        val window =
            when {
                hasTracks -> freshForMs
                confirmed -> NEGATIVE_TTL_MS
                else -> RETRY_TTL_MS
            }
        return System.currentTimeMillis() - fetchedAt > window
    }

    private suspend fun fetch(artistKey: String, artistName: String, apiKey: String): Fetched =
        withContext(Dispatchers.IO) {
            try {
                val response =
                    lastFmApiService.getArtistTopTracks(
                        artist = artistName,
                        apiKey = apiKey,
                        limit = TOP_TRACKS_LIMIT,
                    )
                val topTracks = response.toptracks
                when {
                    topTracks != null ->
                        Fetched(
                            LastFmArtistCacheEntity(
                                artistKey = artistKey,
                                tracks = topTracks.track.toPopularity(),
                                confirmed = true,
                            ),
                            persist = true,
                        )
                    response.error == ERROR_INVALID_PARAMETERS ->
                        Fetched(
                            LastFmArtistCacheEntity(artistKey = artistKey, confirmed = true),
                            persist = true,
                        )
                    else -> {
                        Timber.w("Last.fm top tracks error ${response.error}: ${response.message}")
                        Fetched(
                            LastFmArtistCacheEntity(artistKey = artistKey),
                            persist = response.error != ERROR_INVALID_API_KEY,
                        )
                    }
                }
            } catch (e: HttpException) {
                Timber.w(e, "Last.fm top tracks HTTP ${e.code()} for $artistName")
                Fetched(LastFmArtistCacheEntity(artistKey = artistKey), persist = e.code() != 403)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Last.fm top tracks lookup failed for $artistName")
                Fetched(LastFmArtistCacheEntity(artistKey = artistKey), persist = true)
            }
        }

    private fun List<LastFmTopTrack>.toPopularity() = mapNotNull { track ->
        val trackName = track.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        LastFmTrackPopularity(
            name = trackName,
            listeners = track.listeners?.toLongOrNull() ?: 0L,
        )
    }

    companion object {
        private const val TOP_TRACKS_LIMIT = 1000
        private const val MEMORY_CACHE_SIZE = 24
        private const val ERROR_INVALID_PARAMETERS = 6
        private const val ERROR_INVALID_API_KEY = 10
        private const val DAY_MS = 24L * 60L * 60L * 1000L
        const val RECENT_RELEASE_FRESH_MS = DAY_MS
        const val DEFAULT_FRESH_MS = 7L * DAY_MS
        const val NEGATIVE_TTL_MS = 7L * DAY_MS
        const val RETRY_TTL_MS = 60L * 60L * 1000L
        const val PRUNE_AFTER_MS = 90L * DAY_MS

        fun freshnessFor(releaseYears: List<Int?>): Long {
            val recent = Year.now().value - 1
            return if (releaseYears.any { it != null && it >= recent }) RECENT_RELEASE_FRESH_MS
            else DEFAULT_FRESH_MS
        }
    }
}
