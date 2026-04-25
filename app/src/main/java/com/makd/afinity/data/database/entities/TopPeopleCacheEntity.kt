package com.makd.afinity.data.database.entities

import androidx.room.Entity

@Entity(tableName = "top_people_cache", primaryKeys = ["personType", "serverId", "userId"])
data class TopPeopleCacheEntity(
    val personType: String,
    val serverId: String,
    val userId: String,
    val peopleData: String,
    val cachedTimestamp: Long,
)