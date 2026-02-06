package com.makd.afinity.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "studio_cache")
data class StudioCacheEntity(
    @PrimaryKey val studioId: String,
    val studioData: String,
    val position: Int,
    val cachedTimestamp: Long,
)
