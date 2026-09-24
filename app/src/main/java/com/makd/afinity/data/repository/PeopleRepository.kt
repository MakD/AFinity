package com.makd.afinity.data.repository

import androidx.core.net.toUri
import com.makd.afinity.data.database.AfinityDatabase
import com.makd.afinity.data.database.AfinityTypeConverters
import com.makd.afinity.data.database.entities.PersonSectionCacheEntity
import com.makd.afinity.data.database.entities.TopPeopleCacheEntity
import com.makd.afinity.data.manager.BackgroundWorkQueue
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.CachedPersonWithCount
import com.makd.afinity.data.models.PersonSection
import com.makd.afinity.data.models.PersonSectionType
import com.makd.afinity.data.models.PersonWithCount
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.media.AfinityEpisode
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityPerson
import com.makd.afinity.data.models.media.AfinityPersonImage
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.models.media.withBaseUrl
import com.makd.afinity.data.repository.media.MediaRepository
import com.makd.afinity.di.ApplicationScope
import com.makd.afinity.util.ItemIds
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.PersonKind
import timber.log.Timber

@Singleton
class PeopleRepository
@Inject
constructor(
    private val mediaRepository: MediaRepository,
    private val sessionManager: SessionManager,
    private val deletedItemsRepository: DeletedItemsRepository,
    private val backgroundWorkQueue: BackgroundWorkQueue,
    @ApplicationScope private val scope: CoroutineScope,
    database: AfinityDatabase,
) {
    private val personCacheTTL = 48.hours.inWholeMilliseconds
    private val peopleCacheTTL = 3.days.inWholeMilliseconds
    private val peopleScanTTL = 5.minutes.inWholeMilliseconds
    private val scanFailureBackoff = 10.minutes.inWholeMilliseconds

    private val topPeopleDao = database.topPeopleDao()
    private val personSectionDao = database.personSectionDao()
    private val afinityTypeConverters = AfinityTypeConverters()
    private val json = Json { ignoreUnknownKeys = true }

    private fun currentServerId(): String = sessionManager.currentSession.value?.serverId ?: ""

    private fun currentUserId(): String =
        sessionManager.currentSession.value?.userId?.toString() ?: ""

    private fun sessionKey(): String = "${currentServerId()}_${currentUserId()}"

    private val scanMutex = Mutex()
    private var scanResult: Triple<String, Long, Map<PersonKind, List<PersonWithCount>>>? = null
    private var lastScanFailure: Pair<String, Long>? = null
    private var refreshJob: Job? = null

    fun invalidatePeopleScan() {
        scanResult = null
    }

    suspend fun getTopPeople(
        type: PersonKind,
        limit: Int = 100,
        minAppearances: Int = 10,
        scanLimit: Int = DEFAULT_SCAN_LIMIT,
    ): List<PersonWithCount> {
        try {
            val cached =
                topPeopleDao.getCachedTopPeople(type.name, currentServerId(), currentUserId())
            if (cached != null) {
                if (System.currentTimeMillis() - cached.cachedTimestamp >= peopleCacheTTL) {
                    refreshInBackground(scanLimit)
                }
                val cachedData =
                    json.decodeFromString<List<CachedPersonWithCount>>(cached.peopleData)
                val baseUrl = mediaRepository.getBaseUrl()
                return cachedData.take(limit).map { PersonWithCount.fromCached(it, baseUrl) }
            }

            return scanAllRoles(scanLimit, force = false)?.get(type).orEmpty().take(limit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to get top ${type.name}")
            return emptyList()
        }
    }

    private fun refreshInBackground(scanLimit: Int) {
        if (refreshJob?.isActive == true) return
        refreshJob = scope.launch {
            try {
                scanAllRoles(scanLimit, force = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Background top people refresh failed")
            }
        }
    }

    private suspend fun scanAllRoles(
        scanLimit: Int,
        force: Boolean,
    ): Map<PersonKind, List<PersonWithCount>>? = scanMutex.withLock {
        val sessionKey = sessionKey()
        val now = System.currentTimeMillis()
        scanResult?.let { (key, timestamp, result) ->
            if (key == sessionKey && now - timestamp < peopleScanTTL) return result
        }
        lastScanFailure?.let { (key, timestamp) ->
            if (!force && key == sessionKey && now - timestamp < scanFailureBackoff) return null
        }

        Timber.d("Scanning $scanLimit recent items for top people...")
        val items =
            backgroundWorkQueue
                .run("top people scan") {
                    mediaRepository.getItemsResult(
                        includeItemTypes = listOf("Movie", "Series"),
                        fields = listOf(ItemFields.PEOPLE),
                        limit = scanLimit,
                        sortBy = SortBy.DATE_ADDED,
                        sortDescending = true,
                    )
                }
                .getOrElse { e ->
                    if (e is CancellationException) throw e
                    Timber.w(e, "Top people scan failed")
                    lastScanFailure = sessionKey to now
                    return null
                }
                .items
        if (items.isEmpty()) {
            lastScanFailure = sessionKey to now
            return null
        }

        val baseUrl = mediaRepository.getBaseUrl()
        val result = SCANNED_ROLES.associateWith { role -> rankPeople(items, role, baseUrl) }
        Timber.d(
            "Scan complete: " +
                result.entries.joinToString { (role, people) -> "${people.size} ${role.name}s" }
        )

        val serverId = currentServerId()
        val userId = currentUserId()
        result.forEach { (role, people) ->
            topPeopleDao.insertTopPeople(
                TopPeopleCacheEntity(
                    personType = role.name,
                    serverId = serverId,
                    userId = userId,
                    peopleData = json.encodeToString(people.map { it.toCached() }),
                    cachedTimestamp = now,
                )
            )
        }
        scanResult = Triple(sessionKey, now, result)
        lastScanFailure = null
        result
    }

    private fun rankPeople(
        items: List<BaseItemDto>,
        type: PersonKind,
        baseUrl: String,
    ): List<PersonWithCount> {
        val peopleFrequency = mutableMapOf<String, Pair<AfinityPerson, Int>>()

        items.forEach { item ->
            item.people
                ?.filter { it.type == type }
                ?.forEach { personDto ->
                    val key = personDto.name ?: return@forEach

                    val current = peopleFrequency[key]
                    if (current == null) {
                        val id = personDto.id
                        val imageUri =
                            personDto.primaryImageTag?.let { tag ->
                                baseUrl
                                    .toUri()
                                    .buildUpon()
                                    .appendEncodedPath("Items/$id/Images/Primary")
                                    .appendQueryParameter("tag", tag)
                                    .build()
                            }
                        peopleFrequency[key] =
                            AfinityPerson(
                                id = id,
                                name = key,
                                type = type,
                                role = personDto.role ?: type.name,
                                image = AfinityPersonImage(imageUri, null),
                            ) to 1
                    } else {
                        peopleFrequency[key] = current.first to (current.second + 1)
                    }
                }
        }

        return peopleFrequency.values
            .filter { it.second >= 2 }
            .sortedByDescending { it.second }
            .take(STORED_PEOPLE_PER_ROLE)
            .map { PersonWithCount(it.first, it.second) }
    }

    suspend fun getPersonSection(
        personWithCount: PersonWithCount,
        sectionType: PersonSectionType,
        bypassCache: Boolean = false,
    ): PersonSection? {
        try {
            val serverId = currentServerId()
            val userId = currentUserId()
            val person = personWithCount.person
            val cacheKey = "${person.name}_${sectionType.name}"

            val cached =
                if (bypassCache) null
                else personSectionDao.getCachedSection(cacheKey, serverId, userId)
            val currentTime = System.currentTimeMillis()

            if (
                cached != null &&
                    personSectionDao.isSectionCacheFresh(
                        cacheKey,
                        serverId,
                        userId,
                        personCacheTTL,
                        currentTime,
                    )
            ) {
                val baseUrl = mediaRepository.getBaseUrl()
                val cachedPersonData =
                    PersonWithCount.fromCached(
                        json.decodeFromString<CachedPersonWithCount>(cached.personData),
                        baseUrl,
                    )
                val decodedItems =
                    json.decodeFromString<List<String>>(cached.itemsData).mapNotNull { itemJson ->
                        when (val item = afinityTypeConverters.toAfinityItem(itemJson)) {
                            is AfinityMovie -> item.copy(images = item.images.withBaseUrl(baseUrl))
                            is AfinityShow -> item.copy(images = item.images.withBaseUrl(baseUrl))
                            is AfinityEpisode ->
                                item.copy(images = item.images.withBaseUrl(baseUrl))
                            else -> item
                        }
                    }
                val cachedItems =
                    deletedItemsRepository.retainAlive(decodedItems) { it.id.toString() }

                return PersonSection(
                    person = cachedPersonData.person,
                    appearanceCount = cachedPersonData.appearanceCount,
                    items = cachedItems,
                    sectionType = sectionType,
                )
            }

            val filteredItems =
                mediaRepository.getPersonItems(
                    personId = person.id,
                    includeItemTypes = listOf("MOVIE", "SERIES"),
                    personTypes = listOf(sectionType.toPersonKind().serialName),
                )

            if (filteredItems.size < 5) return null

            val selectedItems =
                filteredItems.filter { it is AfinityMovie || it is AfinityShow }.shuffled().take(20)
            deletedItemsRepository.unmark(selectedItems.map { it.id.toString() })

            val section =
                PersonSection(
                    person = person,
                    appearanceCount = personWithCount.appearanceCount,
                    items = selectedItems,
                    sectionType = sectionType,
                )

            val itemJsonStrings = selectedItems.mapNotNull {
                afinityTypeConverters.fromAfinityItem(it)
            }
            val entity =
                PersonSectionCacheEntity(
                    cacheKey = cacheKey,
                    serverId = serverId,
                    userId = userId,
                    personData = json.encodeToString(personWithCount.toCached()),
                    itemsData = json.encodeToString(itemJsonStrings),
                    sectionType = sectionType.name,
                    cachedTimestamp = currentTime,
                )
            personSectionDao.insertSection(entity)

            return section
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to get person section for ${personWithCount.person.name}")
            return null
        }
    }

    suspend fun removeItem(itemId: String) {
        val normalized = ItemIds.normalize(itemId) ?: return
        withContext(Dispatchers.IO) {
            try {
                val serverId = currentServerId()
                val userId = currentUserId()
                val allSections = personSectionDao.getAllCachedSections(serverId, userId)
                for (section in allSections) {
                    val itemStrings = json.decodeFromString<List<String>>(section.itemsData)
                    val retained = itemStrings.filterNot { itemJson ->
                        val existing = afinityTypeConverters.toAfinityItem(itemJson)
                        existing != null && ItemIds.normalize(existing.id.toString()) == normalized
                    }
                    if (retained.size != itemStrings.size) {
                        personSectionDao.insertSection(
                            section.copy(itemsData = json.encodeToString(retained))
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to remove $itemId from person section caches")
            }
        }
    }

    suspend fun updateItemInCaches(updatedItem: AfinityItem) {
        withContext(Dispatchers.IO) {
            try {
                val serverId = currentServerId()
                val userId = currentUserId()
                val updatedJson =
                    when (updatedItem) {
                        is AfinityMovie -> afinityTypeConverters.fromAfinityMovie(updatedItem)
                        is AfinityShow -> afinityTypeConverters.fromAfinityShow(updatedItem)
                        else -> null
                    }
                if (updatedJson != null) {
                    val allSections = personSectionDao.getAllCachedSections(serverId, userId)
                    for (section in allSections) {
                        val itemStrings = json.decodeFromString<List<String>>(section.itemsData)
                        var changed = false
                        val newItemStrings = itemStrings.map { itemJson ->
                            val existing = afinityTypeConverters.toAfinityItem(itemJson)
                            if (existing?.id == updatedItem.id) {
                                changed = true
                                updatedJson
                            } else {
                                itemJson
                            }
                        }
                        if (changed) {
                            personSectionDao.insertSection(
                                section.copy(itemsData = json.encodeToString(newItemStrings))
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to update Person DB caches")
            }
        }
    }

    suspend fun clearAllData() {
        refreshJob?.cancel()
        scanMutex.withLock {
            scanResult = null
            lastScanFailure = null
        }
        try {
            topPeopleDao.clearAllCache()
            personSectionDao.clearAllCache()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear people database caches")
        }
    }

    private companion object {
        const val DEFAULT_SCAN_LIMIT = 250
        const val STORED_PEOPLE_PER_ROLE = 100
        val SCANNED_ROLES = listOf(PersonKind.ACTOR, PersonKind.DIRECTOR, PersonKind.WRITER)
    }
}
