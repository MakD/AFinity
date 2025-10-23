package com.makd.afinity.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.makd.afinity.data.models.media.AfinityItem
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityShow
import java.util.UUID

@Entity(tableName = "item_metadata")
data class ItemMetadataEntity(
    @PrimaryKey
    val itemId: UUID,
    val genres: String,
    val tags: String?,
    val providerIds: String?,
    val externalUrls: String?,
    val trailer: String?,
    val tagline: String?,
    val cachedAt: Long = System.currentTimeMillis()
)

fun AfinityItem.toItemMetadataEntity(): ItemMetadataEntity {
    val gson = Gson()

    val genres = when (this) {
        is AfinityMovie -> gson.toJson(this.genres)
        is AfinityShow -> gson.toJson(this.genres)
        else -> "[]"
    }

    val trailer = when (this) {
        is AfinityMovie -> this.trailer
        is AfinityShow -> this.trailer
        else -> null
    }

    val tagline = when (this) {
        is AfinityMovie -> this.tagline
        is AfinityShow -> this.tagline
        else -> null
    }

    val providerIds = when (this) {
        is AfinityMovie -> this.providerIds?.let { gson.toJson(it) }
        is AfinityShow -> this.providerIds?.let { gson.toJson(it) }
        else -> null
    }

    val externalUrls = when (this) {
        is AfinityMovie -> this.externalUrls?.let { gson.toJson(it) }
        is AfinityShow -> this.externalUrls?.let { gson.toJson(it) }
        else -> null
    }

    return ItemMetadataEntity(
        itemId = this.id,
        genres = genres,
        tags = null,
        providerIds = providerIds,
        externalUrls = externalUrls,
        trailer = trailer,
        tagline = tagline
    )
}

fun ItemMetadataEntity.getGenresList(): List<String> {
    return try {
        val gson = Gson()
        val type = object : TypeToken<List<String>>() {}.type
        gson.fromJson(this.genres, type) ?: emptyList()
    } catch (e: Exception) {
        emptyList()
    }
}

fun ItemMetadataEntity.getProviderIdsMap(): Map<String, String>? {
    return try {
        if (this.providerIds == null) return null
        val gson = Gson()
        val type = object : TypeToken<Map<String, String>>() {}.type
        gson.fromJson(this.providerIds, type)
    } catch (e: Exception) {
        null
    }
}

fun ItemMetadataEntity.getExternalUrlsList(): List<Map<String, String>>? {
    return try {
        if (this.externalUrls == null) return null
        val gson = Gson()
        val type = object : TypeToken<List<Map<String, String>>>() {}.type
        gson.fromJson(this.externalUrls, type)
    } catch (e: Exception) {
        null
    }
}