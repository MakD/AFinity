package com.makd.afinity.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.models.media.AfinityPerson
import com.makd.afinity.data.models.media.AfinityShow
import java.time.LocalDateTime
import java.util.UUID

@Entity(tableName = "shows")
data class AfinityShowDto(
    @PrimaryKey
    val id: UUID,
    val serverId: String,
    val name: String,
    val originalTitle: String?,
    val overview: String,
    val runtimeTicks: Long,
    val communityRating: Float?,
    val officialRating: String?,
    val status: String,
    val productionYear: Int?,
    val premiereDate: LocalDateTime?,
    val dateCreated: LocalDateTime?,
    val dateLastContentAdded: LocalDateTime?,
    val endDate: LocalDateTime?,
    val images: AfinityImages? = null,
    val genres: List<String>? = null,
    val people: List<AfinityPerson>? = null,
)

fun AfinityShow.toAfinityShowDto(serverId: String): AfinityShowDto {
    return AfinityShowDto(
        id = id,
        serverId = serverId,
        name = name,
        originalTitle = originalTitle,
        overview = overview,
        runtimeTicks = runtimeTicks,
        communityRating = communityRating,
        officialRating = officialRating,
        status = status,
        productionYear = productionYear,
        premiereDate = premiereDate,
        dateCreated = dateCreated,
        dateLastContentAdded = dateLastContentAdded,
        endDate = endDate,
        images = images,
        genres = genres,
        people = people,
    )
}