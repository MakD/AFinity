package com.makd.afinity.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.makd.afinity.data.models.media.AfinityBook
import com.makd.afinity.data.models.media.AfinityChapter
import com.makd.afinity.data.models.media.AfinityExternalUrl
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.models.media.AfinityPerson
import java.time.LocalDateTime
import java.util.UUID

@Entity(tableName = "books")
data class AfinityBookDto(
    @PrimaryKey val id: UUID,
    val serverId: String,
    val name: String,
    val originalTitle: String?,
    val overview: String,
    val runtimeTicks: Long,
    val images: AfinityImages? = null,
    val chapters: List<AfinityChapter>? = null,
    val providerIds: Map<String, String>? = null,
    val externalUrls: List<AfinityExternalUrl>? = null,
    val seriesName: String? = null,
    val seriesIndex: Int? = null,
    val people: List<AfinityPerson>? = null,
    val publisher: String? = null,
    val premiereDate: LocalDateTime? = null,
    val productionYear: Int? = null,
    val genres: List<String>? = null,
    val tags: List<String>? = null,
    val communityRating: Float? = null,
    val dateCreated: LocalDateTime? = null,
    val format: String,
    val parentId: UUID? = null,
)

fun AfinityBook.toAfinityBookDto(serverId: String): AfinityBookDto =
    AfinityBookDto(
        id = id,
        serverId = serverId,
        name = name,
        originalTitle = originalTitle,
        overview = overview,
        runtimeTicks = runtimeTicks,
        images = images,
        chapters = chapters,
        providerIds = providerIds,
        externalUrls = externalUrls,
        seriesName = seriesName,
        seriesIndex = seriesIndex,
        people = people,
        publisher = publisher,
        premiereDate = premiereDate,
        productionYear = productionYear,
        genres = genres,
        tags = tags,
        communityRating = communityRating,
        dateCreated = dateCreated,
        format = format.name,
        parentId = parentId,
    )
