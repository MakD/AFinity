package com.makd.afinity.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "watchlist")
data class WatchlistItemEntity(
    @PrimaryKey
    val itemId: UUID,
    val itemType: String,
    val addedAt: Long = System.currentTimeMillis()
)