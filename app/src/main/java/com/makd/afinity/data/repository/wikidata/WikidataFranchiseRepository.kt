package com.makd.afinity.data.repository.wikidata

import com.makd.afinity.data.database.dao.WikidataFranchiseDao
import com.makd.afinity.data.database.entities.WikidataFranchiseEntityEntity
import com.makd.afinity.data.database.entities.WikidataFranchiseTitleEntity
import com.makd.afinity.data.models.franchise.WikidataEntityFacts
import com.makd.afinity.data.models.franchise.WikidataFranchiseFacts
import com.makd.afinity.data.models.franchise.WikidataRef
import com.makd.afinity.data.models.franchise.WikidataTitleFacts
import com.makd.afinity.data.models.wikidata.SparqlValue
import com.makd.afinity.data.network.WikidataApiService
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import timber.log.Timber

@Singleton
class WikidataFranchiseRepository
@Inject
constructor(
    private val wikidataApiService: WikidataApiService,
    private val dao: WikidataFranchiseDao,
) {
    private val mutex = Mutex()

    suspend fun facts(imdbIds: Collection<String>): WikidataFranchiseFacts? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val ids = imdbIds.filter { IMDB_ID.matches(it) }.distinct()

            val titles =
                ids.chunked(SQL_CHUNK)
                    .flatMap { dao.getTitles(it) }
                    .filterNot { it.isExpired(now) }
                    .associateByTo(HashMap()) { it.imdbId }
            for (batch in ids.filterNot { it in titles }.chunked(TITLE_BATCH)) {
                val fetched = fetchTitles(batch) ?: return@withContext null
                dao.insertTitles(fetched)
                fetched.associateByTo(titles) { it.imdbId }
            }

            val libraryEntities = titles.values.mapNotNullTo(HashSet()) { it.entityId }
            val wanted = HashSet<String>()
            for (title in titles.values) {
                title.groupRefs.mapNotNullTo(wanted) { parseRef(it)?.entityId }
                val source = title.entityId ?: continue
                for (target in title.linkRefs.mapNotNull { parseRef(it)?.entityId }) {
                    if (target in libraryEntities) {
                        wanted.add(source)
                        wanted.add(target)
                    }
                }
            }

            val entities =
                wanted
                    .toList()
                    .chunked(SQL_CHUNK)
                    .flatMap { dao.getEntities(it) }
                    .filterNot { now - it.fetchedAt > FOUND_TTL_MS }
                    .associateByTo(HashMap()) { it.entityId }
            for (batch in wanted.filterNot { it in entities }.chunked(ENTITY_BATCH)) {
                val fetched = fetchEntities(batch) ?: return@withContext null
                dao.insertEntities(fetched)
                fetched.associateByTo(entities) { it.entityId }
            }

            WikidataFranchiseFacts(
                titles =
                    titles.mapValues { (_, t) ->
                        WikidataTitleFacts(
                            entityId = t.entityId,
                            groups = t.groupRefs.mapNotNull(::parseRef),
                            links = t.linkRefs.mapNotNull(::parseRef),
                        )
                    },
                entities =
                    entities.mapValues { (_, e) ->
                        WikidataEntityFacts(
                            label = e.label,
                            types = e.types.toSet(),
                            parents = e.parents,
                            logo = e.logo,
                        )
                    },
            )
        }
    }

    private suspend fun fetchTitles(imdbIds: List<String>): List<WikidataFranchiseTitleEntity>? {
        val values = imdbIds.joinToString(" ") { "\"$it\"" }
        val branches =
            (GROUP_PROPS + LINK_PROPS).joinToString(" UNION ") {
                "{ ?item wdt:$it ?g . BIND(\"$it\" AS ?gp) }"
            }
        val query =
            "SELECT ?id ?item ?g ?gp WHERE { VALUES ?id { $values } " +
                "?item wdt:P345 ?id . OPTIONAL { $branches } }"
        val bindings = runQuery(query) ?: return null

        val entityOf = HashMap<String, String>()
        val groups = HashMap<String, LinkedHashSet<String>>()
        val links = HashMap<String, LinkedHashSet<String>>()
        for (row in bindings) {
            val id = row["id"]?.value ?: continue
            val item = row["item"].entityId() ?: continue
            entityOf.putIfAbsent(id, item)
            val target = row["g"].entityId() ?: continue
            val property = row["gp"]?.value ?: continue
            val bucket = if (property in GROUP_PROPS) groups else links
            bucket.getOrPut(id) { LinkedHashSet() }.add("$target|$property")
        }
        val now = System.currentTimeMillis()
        return imdbIds.map { id ->
            WikidataFranchiseTitleEntity(
                imdbId = id,
                entityId = entityOf[id],
                groupRefs = groups[id]?.toList().orEmpty(),
                linkRefs = links[id]?.toList().orEmpty(),
                fetchedAt = now,
            )
        }
    }

    private suspend fun fetchEntities(
        entityIds: List<String>
    ): List<WikidataFranchiseEntityEntity>? {
        val values = entityIds.joinToString(" ") { "wd:$it" }
        val query =
            "SELECT ?e ?eLabel ?type ?parent ?logo WHERE { VALUES ?e { $values } " +
                "OPTIONAL { ?e wdt:P31 ?type } " +
                "OPTIONAL { { ?e wdt:P361 ?parent } UNION { ?e wdt:P179 ?parent } " +
                "UNION { ?e wdt:P8345 ?parent } } " +
                "OPTIONAL { ?e wdt:P154 ?logo } " +
                "SERVICE wikibase:label { bd:serviceParam wikibase:language \"en,mul\". } }"
        val bindings = runQuery(query) ?: return null

        val labels = HashMap<String, String>()
        val types = HashMap<String, LinkedHashSet<String>>()
        val parents = HashMap<String, LinkedHashSet<String>>()
        val logos = HashMap<String, String>()
        for (row in bindings) {
            val entity = row["e"].entityId() ?: continue
            row["eLabel"]
                ?.value
                ?.takeIf { it.isNotBlank() && it != entity }
                ?.let { labels.putIfAbsent(entity, it) }
            row["type"].entityId()?.let { types.getOrPut(entity) { LinkedHashSet() }.add(it) }
            row["parent"].entityId()?.let { parents.getOrPut(entity) { LinkedHashSet() }.add(it) }
            row["logo"]?.value?.let { logos.putIfAbsent(entity, it) }
        }
        val now = System.currentTimeMillis()
        return entityIds.map { id ->
            WikidataFranchiseEntityEntity(
                entityId = id,
                label = labels[id],
                types = types[id]?.toList().orEmpty(),
                parents = parents[id]?.toList().orEmpty(),
                logo = logos[id],
                fetchedAt = now,
            )
        }
    }

    private suspend fun runQuery(query: String): List<Map<String, SparqlValue>>? {
        repeat(QUERY_ATTEMPTS) { attempt ->
            try {
                return wikidataApiService.queryPost(query).results.bindings
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Wikidata franchise query failed (attempt ${attempt + 1})")
                if (attempt < QUERY_ATTEMPTS - 1) delay(retryDelay(e))
            }
        }
        return null
    }

    private fun retryDelay(e: Exception): Duration {
        if (e !is HttpException || e.code() != HTTP_TOO_MANY_REQUESTS) return RETRY_DELAY
        val seconds = e.response()?.headers()?.get("Retry-After")?.trim()?.toLongOrNull()
        return seconds?.seconds?.coerceIn(RETRY_DELAY, MAX_RETRY_AFTER) ?: MAX_RETRY_AFTER
    }

    private fun SparqlValue?.entityId(): String? =
        this?.value?.substringAfterLast('/')?.takeIf { ENTITY_ID.matches(it) }

    private fun parseRef(raw: String): WikidataRef? {
        val entity = raw.substringBefore('|')
        val property = raw.substringAfter('|', "")
        if (!ENTITY_ID.matches(entity) || property.isEmpty()) return null
        return WikidataRef(entity, property)
    }

    private fun WikidataFranchiseTitleEntity.isExpired(now: Long): Boolean =
        now - fetchedAt > if (entityId != null) FOUND_TTL_MS else NOT_FOUND_TTL_MS

    companion object {
        private val IMDB_ID = Regex("^tt\\d+$")
        private val ENTITY_ID = Regex("^Q\\d+$")
        private val GROUP_PROPS = listOf("P8345", "P179", "P1434")
        private val LINK_PROPS = listOf("P2512", "P4969", "P144", "P155", "P156")
        private const val TITLE_BATCH = 500
        private const val ENTITY_BATCH = 500
        private const val SQL_CHUNK = 500
        private const val QUERY_ATTEMPTS = 2
        private val RETRY_DELAY = 3.seconds
        private val MAX_RETRY_AFTER = 60.seconds
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val FOUND_TTL_MS = 180L * 24L * 60L * 60L * 1000L
        private const val NOT_FOUND_TTL_MS = 30L * 24L * 60L * 60L * 1000L
    }
}
