package com.makd.afinity.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.models.media.AfinitySeason
import java.time.LocalDateTime
import java.util.UUID

@Entity(
    tableName = "seasons",
    foreignKeys =
        [
            ForeignKey(
                entity = AfinityShowDto::class,
                parentColumns = arrayOf("id"),
                childColumns = arrayOf("seriesId"),
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index("seriesId")],
)
data class AfinitySeasonDto(
    @PrimaryKey val id: UUID,
    val serverId: String,
    val seriesId: UUID,
    val name: String,
    val seriesName: String,
    val overview: String,
    val indexNumber: Int,
    val images: AfinityImages? = null,
    val productionYear: Int? = null,
    val premiereDate: LocalDateTime? = null,
)

fun AfinitySeason.toAfinitySeasonDto(serverId: String): AfinitySeasonDto {
    return AfinitySeasonDto(
        id = id,
        serverId = serverId,
        seriesId = seriesId,
        name = name,
        seriesName = seriesName,
        overview = overview,
        indexNumber = indexNumber,
        images = images,
        productionYear = productionYear,
        premiereDate = premiereDate,
    )
}
