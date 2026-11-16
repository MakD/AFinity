package com.makd.afinity.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.makd.afinity.data.models.media.AfinityChapter
import com.makd.afinity.data.models.media.AfinityExternalUrl
import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.models.media.AfinityMusicVideo
import com.makd.afinity.data.models.media.AfinityMusicVideoArtist
import com.makd.afinity.data.models.media.AfinityPerson
import java.time.LocalDateTime
import java.util.UUID

@Entity(tableName = "music_videos")
data class AfinityMusicVideoDto(
    @PrimaryKey val id: UUID,
    val serverId: String,
    val name: String,
    val originalTitle: String?,
    val overview: String,
    val runtimeTicks: Long,
    val premiereDate: LocalDateTime?,
    val dateCreated: LocalDateTime?,
    val productionYear: Int?,
    val communityRating: Float?,
    val officialRating: String?,
    val criticRating: Float?,
    val chapters: List<AfinityChapter>?,
    val images: AfinityImages? = null,
    val genres: List<String>? = null,
    val studios: List<String>? = null,
    val tags: List<String>? = null,
    val tagline: String? = null,
    val people: List<AfinityPerson>? = null,
    val providerIds: Map<String, String>? = null,
    val externalUrls: List<AfinityExternalUrl>? = null,
    val artists: List<String>? = null,
    val artistItems: List<AfinityMusicVideoArtist>? = null,
    val album: String? = null,
    val albumId: UUID? = null,
    val parentId: UUID? = null,
)

fun AfinityMusicVideo.toAfinityMusicVideoDto(serverId: String): AfinityMusicVideoDto =
    AfinityMusicVideoDto(
        id = id,
        serverId = serverId,
        name = name,
        originalTitle = originalTitle,
        overview = overview,
        runtimeTicks = runtimeTicks,
        premiereDate = premiereDate,
        dateCreated = dateCreated,
        productionYear = productionYear,
        communityRating = communityRating,
        officialRating = officialRating,
        criticRating = criticRating,
        chapters = chapters,
        images = images,
        genres = genres,
        studios = studios,
        tags = tags,
        tagline = tagline,
        people = people,
        providerIds = providerIds,
        externalUrls = externalUrls,
        artists = artists,
        artistItems = artistItems,
        album = album,
        albumId = albumId,
        parentId = parentId,
    )
