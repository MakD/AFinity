package com.makd.afinity.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "deleted_items")
data class DeletedItemEntity(
    @PrimaryKey val itemId: String,
    val serverId: String,
    val deletedAt: Long,
)
