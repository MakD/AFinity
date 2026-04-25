package com.makd.afinity.data.database.entities

import androidx.room.Entity

@Entity(tableName = "movie_section_cache", primaryKeys = ["sectionId", "serverId", "userId"])
data class MovieSectionCacheEntity(
    val sectionId: String,
    val serverId: String,
    val userId: String,
    val referenceMovieData: String,
    val recommendedItemsData: String,
    val sectionType: String,
    val cachedTimestamp: Long,
)