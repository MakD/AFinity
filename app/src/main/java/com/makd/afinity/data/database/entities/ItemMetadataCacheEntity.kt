package com.makd.afinity.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.makd.afinity.data.models.mdblist.MdbListRating
import com.makd.afinity.data.models.tmdb.TmdbReview
import java.util.UUID

@Entity(tableName = "item_metadata_cache")
data class ItemMetadataCacheEntity(
    @PrimaryKey val itemId: UUID,
    val tmdbReviews: List<TmdbReview> = emptyList(),
    val mdbRatings: List<MdbListRating> = emptyList(),
    val lastUpdated: Long = System.currentTimeMillis(),
)
