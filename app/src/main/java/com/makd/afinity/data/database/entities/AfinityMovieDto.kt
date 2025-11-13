package com.makd.afinity.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.makd.afinity.data.models.media.AfinityChapter
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.models.media.AfinityMovie
import com.makd.afinity.data.models.media.AfinityPerson
import java.time.LocalDateTime
import java.util.UUID

@Entity(tableName = "movies")
data class AfinityMovieDto(
    @PrimaryKey
    val id: UUID,
    val serverId: String?,
    val name: String,
    val originalTitle: String?,
    val overview: String,
    val runtimeTicks: Long,
    val premiereDate: LocalDateTime?,
    val dateCreated: LocalDateTime?,
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
)

fun AfinityMovie.toAfinityMovieDto(serverId: String? = null): AfinityMovieDto {
    return AfinityMovieDto(
        id = id,
        serverId = serverId,
        name = name,
        originalTitle = originalTitle,
        overview = overview,
        runtimeTicks = runtimeTicks,
        premiereDate = premiereDate,
        dateCreated = dateCreated,
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
    )
}