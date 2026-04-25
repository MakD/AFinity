package com.makd.afinity.data.repository

import androidx.core.net.toUri
import com.makd.afinity.data.database.AfinityDatabase
import com.makd.afinity.data.database.AfinityTypeConverters
import com.makd.afinity.data.database.entities.PersonSectionCacheEntity
import com.makd.afinity.data.database.entities.TopPeopleCacheEntity
import com.makd.afinity.data.manager.SessionManager
import com.makd.afinity.data.models.CachedPersonWithCount
import com.makd.afinity.data.models.PersonSection
import com.makd.afinity.data.models.PersonSectionType
import com.makd.afinity.data.models.PersonWithCount
import com.makd.afinity.data.models.common.SortBy
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityPerson
import com.makd.afinity.data.models.media.AfinityPersonImage
import com.makd.afinity.data.models.media.AfinityShow
import com.makd.afinity.data.repository.media.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.PersonKind
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.hours

@Singleton
class PeopleRepository
@Inject
constructor(
    private val mediaRepository: MediaRepository,
    private val sessionManager: SessionManager,
    database: AfinityDatabase,
) {
    private val personCacheTTL = 48.hours.inWholeMilliseconds
    private val peopleCacheTTL = 24.hours.inWholeMilliseconds

    private val topPeopleDao = database.topPeopleDao()
    private val personSectionDao = database.personSectionDao()
    private val afinityTypeConverters = AfinityTypeConverters()
    private val json = Json { ignoreUnknownKeys = true }

    private fun currentServerId(): String = sessionManager.currentSession.value?.serverId ?: ""
    private fun currentUserId(): String = sessionManager.currentSession.value?.userId?.toString() ?: ""

    suspend fun getTopPeople(
        type: PersonKind,
        limit: Int = 100,
        minAppearances: Int = 10,
    ): List<PersonWithCount> {
        try {
            val serverId = currentServerId()
            val userId = currentUserId()
            val cached = topPeopleDao.getCachedTopPeople(type.name, serverId, userId)
            val currentTime = System.currentTimeMillis()

            if (
                cached != null &&
                    topPeopleDao.isTopPeopleCacheFresh(type.name, serverId, userId, peopleCacheTTL, currentTime)
            ) {
                val cachedData =
                    json.decodeFromString<List<CachedPersonWithCount>>(cached.peopleData)
                val baseUrl = mediaRepository.getBaseUrl()
                return cachedData.map { PersonWithCount.fromCached(it, baseUrl) }
            }

            Timber.d("Fetching top ${type.name}...")
            val baseUrl = mediaRepository.getBaseUrl()

            val scanLimit = 150
            val peopleFrequency = mutableMapOf<String, Pair<AfinityPerson, Int>>()

            val moviesResponse =
                mediaRepository.getItems(
                    includeItemTypes = listOf("Movie"),
                    fields = listOf(ItemFields.PEOPLE),
                    limit = scanLimit,
                    sortBy = SortBy.DATE_ADDED,
                    sortDescending = true,
                )

            val movies = moviesResponse.items ?: emptyList()

            movies.forEach { movieItem ->
                movieItem.people
                    ?.filter { it.type == type }
                    ?.forEach { personDto ->
                        val key = personDto.name ?: return@forEach

                        if (!peopleFrequency.containsKey(key)) {
                            val id = personDto.id
                            val primaryTag = personDto.primaryImageTag

                            val imageUri =
                                primaryTag?.let { tag ->
                                    baseUrl
                                        .toUri()
                                        .buildUpon()
                                        .appendEncodedPath("Items/$id/Images/Primary")
                                        .appendQueryParameter("tag", tag)
                                        .build()
                                }

                            val afinityPerson =
                                AfinityPerson(
                                    id = id,
                                    name = key,
                                    type = type,
                                    role = personDto.role ?: type.name,
                                    image = AfinityPersonImage(imageUri, null),
                                )
                            peopleFrequency[key] = afinityPerson to 1
                        } else {
                            val current = peopleFrequency[key]!!
                            peopleFrequency[key] = current.first to (current.second + 1)
                        }
                    }
            }

            val mappedPeople =
                peopleFrequency.values
                    .filter { it.second >= 2 }
                    .sortedByDescending { it.second }
                    .take(limit)
                    .map { PersonWithCount(it.first, it.second) }

            Timber.d("Scan complete: Found ${mappedPeople.size} ${type.name}s")

            if (mappedPeople.isNotEmpty()) {
                val cachedData = mappedPeople.map { it.toCached() }
                val entity =
                    TopPeopleCacheEntity(
                        personType = type.name,
                        serverId = serverId,
                        userId = userId,
                        peopleData = json.encodeToString(cachedData),
                        cachedTimestamp = System.currentTimeMillis(),
                    )
                topPeopleDao.insertTopPeople(entity)
            }

            return mappedPeople
        } catch (e: Exception) {
            Timber.e(e, "Failed to get top ${type.name}")
            return emptyList()
        }
    }

    suspend fun getPersonSection(
        personWithCount: PersonWithCount,
        sectionType: PersonSectionType,
    ): PersonSection? {
        try {
            val serverId = currentServerId()
            val userId = currentUserId()
            val person = personWithCount.person
            val cacheKey = "${person.name}_${sectionType.name}"

            val cached = personSectionDao.getCachedSection(cacheKey, serverId, userId)
            val currentTime = System.currentTimeMillis()

            if (
                cached != null &&
                    personSectionDao.isSectionCacheFresh(cacheKey, serverId, userId, personCacheTTL, currentTime)
            ) {
                val baseUrl = mediaRepository.getBaseUrl()
                val cachedPersonData =
                    PersonWithCount.fromCached(
                        json.decodeFromString<CachedPersonWithCount>(cached.personData),
                        baseUrl,
                    )
                val cachedItems =
                    json.decodeFromString<List<String>>(cached.itemsData).mapNotNull {
                        afinityTypeConverters.toAfinityItem(it)
                    }
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

            val section =
                PersonSection(
                    person = person,
                    appearanceCount = personWithCount.appearanceCount,
                    items = selectedItems,
                    sectionType = sectionType,
                )

            val itemJsonStrings =
                selectedItems.mapNotNull { afinityTypeConverters.fromAfinityItem(it) }
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
        } catch (e: Exception) {
            Timber.e(e, "Failed to get person section for ${personWithCount.person.name}")
            return null
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
                        val newItemStrings =
                            itemStrings.map { itemJson ->
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
            } catch (e: Exception) {
                Timber.e(e, "Failed to update Person DB caches")
            }
        }
    }

    suspend fun clearAllData() {
        try {
            topPeopleDao.clearAllCache()
            personSectionDao.clearAllCache()
        } catch (e: Exception) {
            Timber.e(e, "Failed to clear people database caches")
        }
    }
}