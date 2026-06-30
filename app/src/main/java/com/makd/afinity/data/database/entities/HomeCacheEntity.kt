package com.makd.afinity.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "home_cache")
data class HomeCacheEntity(
    @PrimaryKey val key: String,
    val json: String,
    val updatedAt: Long,
)
