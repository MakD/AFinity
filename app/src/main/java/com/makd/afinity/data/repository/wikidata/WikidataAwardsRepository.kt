package com.makd.afinity.data.repository.wikidata

import com.makd.afinity.data.database.entities.WikidataAwardsCacheEntity
import com.makd.afinity.data.models.wikidata.WikidataAwards
import com.makd.afinity.data.models.wikidata.WikidataSubjectType
import com.makd.afinity.data.network.WikidataApiService
import com.makd.afinity.data.network.WikidataAwardParser
import com.makd.afinity.data.network.WikidataAwardQueries
import com.makd.afinity.data.repository.DatabaseRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

@Singleton
class WikidataAwardsRepository
@Inject
constructor(
    private val wikidataApiService: WikidataApiService,
    private val databaseRepository: DatabaseRepository,
) {

    private val inFlight = mutableMapOf<String, Mutex>()
    private val inFlightLock = Mutex()
    private val seriesTmdbIds = ConcurrentHashMap<UUID, String>()

    suspend fun resolveSeriesTmdbId(seriesId: UUID, resolve: suspend () -> String?): String? {
        seriesTmdbIds[seriesId]?.let { cached ->
            return cached.takeIf { it != NO_TMDB_ID }
        }
        val resolved = resolve()
        seriesTmdbIds[seriesId] = resolved ?: NO_TMDB_ID
        return resolved
    }

    suspend fun getAwards(subjectType: WikidataSubjectType, tmdbId: String?): WikidataAwards {
        if (!WikidataAwardQueries.isValidTmdbId(tmdbId)) return WikidataAwards.UNCONFIRMED
        val id = tmdbId!!

        cached(subjectType, id)?.let { return it }

        val key = "${subjectType.value}-$id"
        val gate = inFlightLock.withLock { inFlight.getOrPut(key) { Mutex() } }
        try {
            gate.withLock {
                cached(subjectType, id)?.let { return it }

                val fetched = fetch(subjectType, id)
                persist(subjectType, id, fetched)
                return fetched
            }
        } finally {
            inFlightLock.withLock { if (!gate.isLocked) inFlight.remove(key) }
        }
    }

    suspend fun cachedAwards(
        subjectType: WikidataSubjectType,
        tmdbId: String?,
    ): WikidataAwards? {
        if (!WikidataAwardQueries.isValidTmdbId(tmdbId)) return null
        return cached(subjectType, tmdbId!!)
    }

    private suspend fun cached(
        subjectType: WikidataSubjectType,
        tmdbId: String,
    ): WikidataAwards? {
        val entity =
            runCatching { databaseRepository.getWikidataAwards(subjectType.value, tmdbId) }
                .getOrNull() ?: return null

        if (entity.isExpired()) return null

        return WikidataAwards(awards = entity.awards, confirmed = entity.confirmed)
    }

    private suspend fun fetch(
        subjectType: WikidataSubjectType,
        tmdbId: String,
    ): WikidataAwards =
        withContext(Dispatchers.IO) {
            try {
                val response =
                    wikidataApiService.query(WikidataAwardQueries.build(subjectType, tmdbId))
                WikidataAwardParser.parse(response, subjectType)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Wikidata awards lookup failed for ${subjectType.value}:$tmdbId")
                WikidataAwards.UNCONFIRMED
            }
        }

    private suspend fun persist(
        subjectType: WikidataSubjectType,
        tmdbId: String,
        awards: WikidataAwards,
    ) {
        runCatching {
            databaseRepository.insertWikidataAwards(
                WikidataAwardsCacheEntity(
                    subjectType = subjectType.value,
                    tmdbId = tmdbId,
                    awards = awards.awards,
                    confirmed = awards.confirmed,
                )
            )
        }
    }

    private fun WikidataAwardsCacheEntity.isExpired(): Boolean {
        val age = System.currentTimeMillis() - fetchedAt
        return when {
            awards.isNotEmpty() -> age > POSITIVE_TTL_MS
            confirmed -> age > NEGATIVE_TTL_MS
            else -> age > RETRY_TTL_MS
        }
    }

    companion object {
        private const val NO_TMDB_ID = ""
        const val POSITIVE_TTL_MS = 180L * 24L * 60L * 60L * 1000L
        const val NEGATIVE_TTL_MS = 30L * 24L * 60L * 60L * 1000L
        const val RETRY_TTL_MS = 60L * 60L * 1000L
    }
}