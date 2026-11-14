package com.makd.afinity.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "offline_plays", indices = [Index(value = ["userId", "serverId"])])
data class OfflinePlayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val itemId: UUID,
    val userId: UUID,
    val serverId: String,
    val playedAt: Long,
)
