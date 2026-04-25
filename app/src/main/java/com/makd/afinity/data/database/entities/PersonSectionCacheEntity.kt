package com.makd.afinity.data.database.entities

import androidx.room.Entity

@Entity(tableName = "person_section_cache", primaryKeys = ["cacheKey", "serverId", "userId"])
data class PersonSectionCacheEntity(
    val cacheKey: String,
    val serverId: String,
    val userId: String,
    val personData: String,
    val itemsData: String,
    val sectionType: String,
    val cachedTimestamp: Long,
)