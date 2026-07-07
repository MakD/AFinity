package com.makd.afinity.data.database.entities

import androidx.room.Entity

@Entity(
    tableName = "jellyseerr_discover_filters",
    primaryKeys = ["jellyfinServerId", "jellyfinUserId", "filterContextKey"],
)
data class JellyseerrDiscoverFilterEntity(
    val jellyfinServerId: String,
    val jellyfinUserId: String,
    val filterContextKey: String,
    val sortBy: String,
    val filterOptionsJson: String,
)