package com.makd.afinity.data.models.user

import androidx.room.Entity
import java.util.UUID

@Entity(tableName = "userdata", primaryKeys = ["userId", "itemId", "serverId"])
data class AfinityUserDataDto(
    val userId: UUID,
    val itemId: UUID,
    val serverId: String,
    val played: Boolean,
    val favorite: Boolean,
    val playbackPositionTicks: Long,
    val toBeSynced: Boolean = false,
    val audioStreamIndex: Int? = null,
    val subtitleStreamIndex: Int? = null,
)
