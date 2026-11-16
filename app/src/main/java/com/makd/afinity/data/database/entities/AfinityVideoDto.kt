package com.makd.afinity.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.makd.afinity.data.models.media.AfinityChapter
import com.makd.afinity.data.models.media.AfinityExternalUrl
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.models.media.AfinityPerson
import com.makd.afinity.data.models.media.AfinityVideo
import java.time.LocalDateTime
import java.util.UUID

@Entity(tableName = "videos")
data class AfinityVideoDto(
    @PrimaryKey val id: UUID,
    val serverId: String,
    val name: String,
    val originalTitle: String?,
    val overview: String,
    val runtimeTicks: Long,
    val premiereDate: LocalDateTime?,
    val communityRating: Float?,
    val officialRating: String?,
    val criticRating: Float?,
    val status: String,
    val productionYear: Int?,
    val endDate: LocalDateTime?,
    val chapters: List<AfinityChapter>?,
    val images: AfinityImages? = null,
    val genres: List<String>? = null,
    val tagline: String? = null,
    val people: List<AfinityPerson>? = null,
    val providerIds: Map<String, String>? = null,
    val externalUrls: List<AfinityExternalUrl>? = null,
    val extraType: String? = null,
)

fun AfinityVideo.toAfinityVideoDto(serverId: String): AfinityVideoDto =
    AfinityVideoDto(
        id = id,
        serverId = serverId,
        name = name,
        originalTitle = originalTitle,
        overview = overview,
        runtimeTicks = runtimeTicks,
        premiereDate = premiereDate,
        communityRating = communityRating,
        officialRating = officialRating,
        criticRating = criticRating,
        status = status,
        productionYear = productionYear,
        endDate = endDate,
        chapters = chapters,
        images = images,
        genres = genres,
        tagline = tagline,
        people = people,
        providerIds = providerIds,
        externalUrls = externalUrls,
        extraType = extraType?.name,
    )
