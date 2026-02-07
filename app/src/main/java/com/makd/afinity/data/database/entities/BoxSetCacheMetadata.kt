package com.makd.afinity.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "boxset_cache_metadata")
data class BoxSetCacheMetadata(
    @PrimaryKey val id: Int = 1,
    val lastFullBuild: Long,
    val cacheVersion: Int,
)
