package com.makd.afinity.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "top_people_cache")
data class TopPeopleCacheEntity(
    @PrimaryKey val personType: String,
    val peopleData: String,
    val cachedTimestamp: Long,
)
