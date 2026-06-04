package com.makd.afinity.data.database.entities

import androidx.room.Entity

@Entity(
    tableName = "audible_ratings",
    primaryKeys = ["itemId", "jellyfinServerId", "jellyfinUserId"],
)
data class AudibleRatingEntity(
    val itemId: String,
    val jellyfinServerId: String,
    val jellyfinUserId: String,
    val asin: String,
    val rating: Double,
    val numRatings: Int?,
    val fetchedAt: Long,
)