package com.makd.afinity.data.database.entities

import androidx.room.Entity

@Entity(
    tableName = "library_cache",
    primaryKeys = ["libraryId", "itemId"]
)
data class LibraryCacheEntity(
    val libraryId: String,
    val itemId: String,
    val itemName: String,
    val originalTitle: String?,
    val overview: String,
    val itemType: String,
    val productionYear: Int?,
    val communityRating: Double?,
    val criticRating: Double?,
    val primaryImageUrl: String?,
    val backdropImageUrl: String?,
    val logoImageUrl: String?,
    val thumbImageUrl: String?,
    val showPrimaryImageUrl: String?,
    val showBackdropImageUrl: String?,
    val showLogoImageUrl: String?,
    val genres: String,
    val played: Boolean,
    val favorite: Boolean,
    val playbackPositionTicks: Long,
    val runtimeTicks: Long,
    val episodeCount: Int?,
    val seasonCount: Int?,
    val unplayedItemCount: Int?,
    val officialRating: String?,
    val status: String,
    val premiereDate: String?,
    val endDate: String?,
    val tagline: String?,
    val trailer: String?,
    val cacheTimestamp: Long,
    val sortBy: String,
    val sortDescending: Boolean,
    val filterType: String
)