package com.makd.afinity.data.repository.home

import com.makd.afinity.data.database.AfinityTypeConverters
import com.makd.afinity.data.database.dao.HomeCacheDao
import com.makd.afinity.data.database.entities.HomeCacheEntity
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityShow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class StringList(val items: List<String>)

@Singleton
class HomeCacheRepository @Inject constructor(
    private val dao: HomeCacheDao,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val converters = AfinityTypeConverters()

    suspend fun getItems(key: String): List<AfinityItem>? {
        val entity = dao.get(key) ?: return null
        return try {
            val wrapper = json.decodeFromString<StringList>(entity.json)
            wrapper.items.mapNotNull { converters.toAfinityItem(it) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to deserialize AfinityItem list for key=$key")
            null
        }
    }

    suspend fun putItems(key: String, items: List<AfinityItem>) {
        if (items.isEmpty()) return
        try {
            val strings = items.mapNotNull { converters.fromAfinityItem(it) }
            val jsonStr = json.encodeToString(StringList(strings))
            dao.upsert(HomeCacheEntity(key, jsonStr, System.currentTimeMillis()))
        } catch (e: Exception) {
            Timber.e(e, "Failed to cache AfinityItem list for key=$key")
        }
    }

    suspend fun getLatestMovies(key: String): List<AfinityMovie>? {
        val entity = dao.get(key) ?: return null
        return try {
            val wrapper = json.decodeFromString<StringList>(entity.json)
            wrapper.items.mapNotNull { converters.toAfinityMovie(it) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to deserialize AfinityMovie list for key=$key")
            null
        }
    }

    suspend fun putLatestMovies(key: String, movies: List<AfinityMovie>) {
        if (movies.isEmpty()) return
        try {
            val strings = movies.mapNotNull { converters.fromAfinityMovie(it) }
            val jsonStr = json.encodeToString(StringList(strings))
            dao.upsert(HomeCacheEntity(key, jsonStr, System.currentTimeMillis()))
        } catch (e: Exception) {
            Timber.e(e, "Failed to cache AfinityMovie list for key=$key")
        }
    }

    suspend fun getLatestShows(key: String): List<AfinityShow>? {
        val entity = dao.get(key) ?: return null
        return try {
            val wrapper = json.decodeFromString<StringList>(entity.json)
            wrapper.items.mapNotNull { converters.toAfinityShow(it) }
        } catch (e: Exception) {
            Timber.e(e, "Failed to deserialize AfinityShow list for key=$key")
            null
        }
    }

    suspend fun putLatestShows(key: String, shows: List<AfinityShow>) {
        if (shows.isEmpty()) return
        try {
            val strings = shows.mapNotNull { converters.fromAfinityShow(it) }
            val jsonStr = json.encodeToString(StringList(strings))
            dao.upsert(HomeCacheEntity(key, jsonStr, System.currentTimeMillis()))
        } catch (e: Exception) {
            Timber.e(e, "Failed to cache AfinityShow list for key=$key")
        }
    }

    suspend fun invalidate(key: String) = dao.delete(key)

    suspend fun invalidateAll() = dao.deleteAll()
}