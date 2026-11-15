package com.makd.afinity.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "book_annotations",
    indices = [Index(value = ["serverId", "userId", "itemId"])],
)
data class BookAnnotationEntity(
    @PrimaryKey val id: String,
    val serverId: String,
    val userId: String,
    val itemId: String,
    val kind: String,
    val locator: String?,
    val page: Int?,
    val progression: Double,
    val label: String?,
    val text: String?,
    val color: String?,
    val createdAt: Long,
)
