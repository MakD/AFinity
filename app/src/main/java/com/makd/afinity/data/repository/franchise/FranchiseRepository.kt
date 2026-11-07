package com.makd.afinity.data.repository.franchise

import com.makd.afinity.data.manager.BackgroundWorkQueue
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.franchise.Franchise
import com.makd.afinity.data.models.franchise.FranchiseCard
import com.makd.afinity.data.models.franchise.FranchiseTitle
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.LibraryIndexEntry
import com.makd.afinity.data.repository.FieldSets
import com.makd.afinity.data.repository.home.HomeCacheRepository
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.data.repository.wikidata.WikidataFranchiseRepository
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random
import kotlin.time.Duration.Companion.hours
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber

@Singleton
class FranchiseRepository
@Inject
constructor(
    private val mediaRepository: MediaRepository,
    private val wikidataFranchiseRepository: WikidataFranchiseRepository,
    private val sessionManager: SessionManager,
    private val homeCacheRepository: HomeCacheRepository,
    private val backgroundWorkQueue: BackgroundWorkQueue,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val listTTL = 24.hours.inWholeMilliseconds
    private val mutex = Mutex()
    private var memory: Pair<String, List<Franchise>>? = null

    suspend fun franchises(force: Boolean = false): List<Franchise> = mutex.withLock {
        val sk = sessionKey() ?: return emptyList()
        if (!force) {
            memory
                ?.takeIf { it.first == sk }
                ?.let {
                    return it.second
                }
            restore(sk, listTTL)?.let {
                memory = sk to it
                return it
            }
        }
        val built =
            build()
                ?: return restore(sk, null)?.also { memory = sk to it }
                    ?: memory?.takeIf { it.first == sk }?.second.orEmpty()
        homeCacheRepository.putRaw(cacheKey(sk), json.encodeToString(built))
        memory = sk to built
        built
    }

    suspend fun franchise(id: String): Franchise? = franchises().firstOrNull { it.id == id }

    suspend fun cards(franchises: List<Franchise>, seed: Long): List<FranchiseCard> {
        val picks = franchises.map { franchise ->
            val ids = franchise.itemIds
            val backdrop = ids.random(Random(seed + franchise.id.hashCode()))
            franchise to (ids.first() to backdrop)
        }
        val wanted =
            picks
                .flatMap { (_, pair) -> listOf(pair.first, pair.second) }
                .distinct()
                .mapNotNull { it.toUuidOrNull() }
        val items =
            mediaRepository.getItemsByIds(wanted, FieldSets.MEDIA_ITEM_CARDS).associateBy {
                it.id.toString()
            }
        return picks.map { (franchise, pair) ->
            val logoItem = items[pair.first]
            val backdropItem = items[pair.second]?.takeIf { it.images.backdrop != null } ?: logoItem
            FranchiseCard(franchise, backdropItem, logoItem)
        }
    }

    suspend fun items(franchise: Franchise): List<AfinityItem> {
        val byId =
            mediaRepository
                .getItemsByIds(
                    franchise.itemIds.mapNotNull { it.toUuidOrNull() },
                    FieldSets.MEDIA_ITEM_CARDS,
                )
                .associateBy { it.id.toString() }
        return franchise.itemIds.mapNotNull { byId[it] }
    }

    fun clear() {
        memory = null
    }

    private suspend fun build(): List<Franchise>? {
        val movies = index(BaseItemKind.MOVIE) ?: return null
        val series = index(BaseItemKind.SERIES) ?: return null
        val collections =
            backgroundWorkQueue
                .run("franchise collections") { mediaRepository.getCollectionTmdbIdsResult() }
                .getOrElse { e ->
                    if (e is CancellationException) throw e
                    Timber.w(e, "Failed to load collections for franchises")
                    return null
                }

        val titles =
            (movies + series)
                .mapNotNull { entry ->
                    val imdb = entry.imdbId ?: return@mapNotNull null
                    FranchiseTitle(
                        key = FranchiseGrouper.normalizeImdbId(imdb),
                        itemId = entry.id.toString(),
                        name = entry.name,
                        year = entry.year,
                        isSeries = entry.isSeries,
                        tmdbCollectionId = entry.tmdbCollectionId,
                    )
                }
                .distinctBy { it.key }
        val facts = wikidataFranchiseRepository.facts(titles.map { it.key }) ?: return null
        val byKey = titles.associateBy { it.key }

        return FranchiseGrouper.group(titles, facts, collections).map { group ->
            val members = group.titleKeys.map { byKey.getValue(it) }
            Franchise(
                id = group.id,
                name = group.name,
                itemIds = members.map { it.itemId },
                movieCount = members.count { !it.isSeries },
                seriesCount = members.count { it.isSeries },
                logoUrl = group.logo?.let(::commonsLogoUrl),
            )
        }
    }

    private suspend fun index(type: BaseItemKind): List<LibraryIndexEntry>? =
        backgroundWorkQueue
            .run("franchise index $type") { mediaRepository.getLibraryIndexResult(type) }
            .getOrElse { e ->
                if (e is CancellationException) throw e
                Timber.w(e, "Failed to load library index ($type) for franchises")
                null
            }

    private suspend fun restore(sk: String, maxAgeMs: Long?): List<Franchise>? =
        homeCacheRepository.getRaw(cacheKey(sk), maxAgeMs)?.let { raw ->
            runCatching { json.decodeFromString<List<Franchise>>(raw) }.getOrNull()
        }

    private fun commonsLogoUrl(raw: String): String? {
        val path = raw.substringAfter(COMMONS_FILE_PATH, "")
        if (path.isEmpty()) return null
        return "https://$COMMONS_FILE_PATH$path?width=$LOGO_WIDTH"
    }

    private fun String.toUuidOrNull(): UUID? =
        try {
            UUID.fromString(this)
        } catch (e: IllegalArgumentException) {
            null
        }

    private fun sessionKey(): String? {
        val session = sessionManager.currentSession.value ?: return null
        if (session.serverId.isBlank()) return null
        return "${session.serverId}_${session.userId}"
    }

    private fun cacheKey(sk: String) = "home_franchises_$sk"

    companion object {
        private const val COMMONS_FILE_PATH = "commons.wikimedia.org/wiki/Special:FilePath/"
        private const val LOGO_WIDTH = 500
    }
}
