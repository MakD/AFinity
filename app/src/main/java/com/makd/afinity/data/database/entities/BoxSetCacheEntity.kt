package com.makd.afinity.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "boxset_cache")
data class BoxSetCacheEntity(
    @PrimaryKey val itemId: String,
    val boxSetIds: String,
    val lastUpdated: Long,
)
