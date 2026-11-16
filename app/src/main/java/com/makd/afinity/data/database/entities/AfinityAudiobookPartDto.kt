package com.makd.afinity.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.makd.afinity.data.models.media.AfinityAudiobook
import com.makd.afinity.data.models.media.AfinityChapter
import com.makd.afinity.data.models.media.AfinityExternalUrl
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.models.media.AfinityPerson
import java.time.LocalDateTime
import java.util.UUID

@Entity(tableName = "audiobook_parts")
data class AfinityAudiobookPartDto(
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
    val album: String? = null,
    val people: List<AfinityPerson>? = null,
    val publisher: String? = null,
    val premiereDate: LocalDateTime? = null,
    val productionYear: Int? = null,
    val genres: List<String>? = null,
    val communityRating: Float? = null,
    val dateCreated: LocalDateTime? = null,
    val parentId: UUID? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val container: String? = null,
    val bookPartCount: Int? = null,
)

fun AfinityAudiobook.toAfinityAudiobookPartDto(serverId: String): AfinityAudiobookPartDto =
    AfinityAudiobookPartDto(
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
        album = album,
        people = people,
        publisher = publisher,
        premiereDate = premiereDate,
        productionYear = productionYear,
        genres = genres,
        communityRating = communityRating,
        dateCreated = dateCreated,
        parentId = parentId,
        trackNumber = trackNumber,
        discNumber = discNumber,
        container = container,
        bookPartCount = bookPartCount,
    )
