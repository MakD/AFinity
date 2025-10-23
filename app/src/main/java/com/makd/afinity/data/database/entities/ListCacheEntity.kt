package com.makd.afinity.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

@Entity(tableName = "list_cache")
data class ListCacheEntity(
    @PrimaryKey
    val cacheKey: String,
    val userId: UUID,
    val listType: String,
    val itemIds: String,
    val itemTypes: String,
    val cachedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long,
    val metadata: String? = null
)

object ListType {
    const val LATEST_MEDIA = "LATEST_MEDIA"
    const val HERO_CAROUSEL = "HERO_CAROUSEL"
    const val CONTINUE_WATCHING = "CONTINUE_WATCHING"
    const val NEXT_UP = "NEXT_UP"
    const val LIBRARIES = "LIBRARIES"
    const val LATEST_MOVIES = "LATEST_MOVIES"
    const val LATEST_TV_SERIES = "LATEST_TV_SERIES"
    const val HIGHEST_RATED = "HIGHEST_RATED"
    const val RECOMMENDATION_CATEGORIES = "RECOMMENDATION_CATEGORIES"
}

object ItemType {
    const val MOVIE = "movie"
    const val SHOW = "show"
    const val EPISODE = "episode"
    const val SEASON = "season"
    const val COLLECTION = "collection"
}

fun List<UUID>.toJsonString(): String {
    val gson = Gson()
    return gson.toJson(this.map { it.toString() })
}

fun String.toUuidList(): List<UUID> {
    return try {
        val gson = Gson()
        val type = object : TypeToken<List<String>>() {}.type
        val stringList: List<String> = gson.fromJson(this, type)
        stringList.map { UUID.fromString(it) }
    } catch (e: Exception) {
        emptyList()
    }
}

fun List<String>.toItemTypesJsonString(): String {
    val gson = Gson()
    return gson.toJson(this)
}

fun String.toItemTypesList(): List<String> {
    return try {
        val gson = Gson()
        val type = object : TypeToken<List<String>>() {}.type
        gson.fromJson(this, type) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}

fun createCacheKey(listType: String, userId: UUID): String {
    return "${listType}_${userId}"
}

fun getItemTypeString(item: Any): String {
    return when (item::class.simpleName) {
        "AfinityMovie" -> ItemType.MOVIE
        "AfinityShow" -> ItemType.SHOW
        "AfinityEpisode" -> ItemType.EPISODE
        "AfinitySeason" -> ItemType.SEASON
        "AfinityCollection" -> ItemType.COLLECTION
        else -> "unknown"
    }
}