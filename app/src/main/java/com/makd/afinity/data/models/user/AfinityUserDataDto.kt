package com.makd.afinity.data.models.user

import androidx.room.Entity
import com.makd.afinity.data.models.media.AfinityItem
import java.util.UUID

@Entity(
    tableName = "userdata",
    primaryKeys = ["userId", "itemId", "serverId"],
)
data class AfinityUserDataDto(
    val userId: UUID,
    val itemId: UUID,
    val serverId: String,
    val played: Boolean,
    val favorite: Boolean,
    val playbackPositionTicks: Long,
    val toBeSynced: Boolean = false,
)

fun AfinityItem.toAfinityUserDataDto(userId: UUID, serverId: String): AfinityUserDataDto {
    return AfinityUserDataDto(
        userId = userId,
        itemId = id,
        serverId = serverId,
        played = played,
        favorite = favorite,
        playbackPositionTicks = playbackPositionTicks,
    )
}