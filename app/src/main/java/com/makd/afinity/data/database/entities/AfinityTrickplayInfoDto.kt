package com.makd.afinity.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.makd.afinity.data.models.media.AfinityTrickplayInfo

@Entity(
    tableName = "trickplayInfos",
    foreignKeys =
        [
            ForeignKey(
                entity = AfinitySourceDto::class,
                parentColumns = arrayOf("id"),
                childColumns = arrayOf("sourceId"),
                onDelete = ForeignKey.CASCADE,
            )
        ],
)
data class AfinityTrickplayInfoDto(
    @PrimaryKey val sourceId: String,
    val width: Int,
    val height: Int,
    val tileWidth: Int,
    val tileHeight: Int,
    val thumbnailCount: Int,
    val interval: Int,
    val bandwidth: Int,
)

fun AfinityTrickplayInfo.toAfinityTrickplayInfoDto(sourceId: String): AfinityTrickplayInfoDto {
    return AfinityTrickplayInfoDto(
        sourceId = sourceId,
        width = width,
        height = height,
        tileWidth = tileWidth,
        tileHeight = tileHeight,
        thumbnailCount = thumbnailCount,
        interval = interval,
        bandwidth = bandwidth,
    )
}
