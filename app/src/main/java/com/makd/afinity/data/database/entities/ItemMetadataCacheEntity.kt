package com.makd.afinity.data.database.entities

import androidx.room.Entity
import com.makd.afinity.data.models.mdblist.MdbListRating
import com.makd.afinity.data.models.tmdb.TmdbReview
import java.util.UUID

@Entity(
    tableName = "item_metadata_cache",
    primaryKeys = ["itemId", "serverId", "userId"],
)
data class ItemMetadataCacheEntity(
    val itemId: UUID,
    val serverId: String,
    val userId: String,
    val tmdbReviews: List<TmdbReview> = emptyList(),
    val mdbRatings: List<MdbListRating> = emptyList(),
    val lastUpdated: Long = System.currentTimeMillis(),
)