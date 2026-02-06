package com.makd.afinity.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "movie_section_cache")
data class MovieSectionCacheEntity(
    @PrimaryKey val sectionId: String,
    val referenceMovieData: String,
    val recommendedItemsData: String,
    val sectionType: String,
    val cachedTimestamp: Long,
)
