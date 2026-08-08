package com.makd.afinity.data.database.entities

import androidx.room.Entity
import com.makd.afinity.data.models.media.AfinitySegment
import com.makd.afinity.data.models.media.AfinitySegmentType
import java.util.UUID

@Entity(tableName = "segments", primaryKeys = ["itemId", "type"])
data class AfinitySegmentDto(
    val itemId: UUID,
    val type: AfinitySegmentType,
    val startTicks: Long,
    val endTicks: Long,
)

fun AfinitySegment.toAfinitySegmentsDto(itemId: UUID): AfinitySegmentDto {
    return AfinitySegmentDto(
        itemId = itemId,
        type = type,
        startTicks = startTicks,
        endTicks = endTicks,
    )
}
