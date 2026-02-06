package com.makd.afinity.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "person_section_cache")
data class PersonSectionCacheEntity(
    @PrimaryKey val cacheKey: String,
    val personData: String,
    val itemsData: String,
    val sectionType: String,
    val cachedTimestamp: Long,
)
