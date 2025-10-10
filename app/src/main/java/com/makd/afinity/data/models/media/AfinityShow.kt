package com.makd.afinity.data.models.media

import com.makd.afinity.data.database.dao.ServerDatabaseDao
import com.makd.afinity.data.repository.JellyfinRepository
import org.jellyfin.sdk.model.DateTime
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.PlayAccess
import java.time.LocalDateTime
import java.util.UUID

data class AfinityShow(
    override val id: UUID,
    override val name: String,
    override val originalTitle: String?,
    override val overview: String,
    override val sources: List<AfinitySource>,
    val seasons: List<AfinitySeason>,
    override val played: Boolean,
    override val favorite: Boolean,
    override val canPlay: Boolean,
    override val canDownload: Boolean,
    override val playbackPositionTicks: Long = 0L,
    override val unplayedItemCount: Int?,
    val genres: List<String>,
    val people: List<AfinityPerson>,
    override val runtimeTicks: Long,
    val communityRating: Float?,
    val officialRating: String?,
    val taglines: List<String> = emptyList(),
    val status: String,
    val productionYear: Int?,
    val premiereDate: LocalDateTime?,
    val dateCreated: LocalDateTime?,
    val dateLastContentAdded: LocalDateTime?,
    val endDate: DateTime?,
    val trailer: String?,
    val tagline: String?,
    val seasonCount: Int?,
    val episodeCount: Int?,
    override val images: AfinityImages,
    override val chapters: List<AfinityChapter> = emptyList(),
    override val providerIds: Map<String, String>?,
    override val externalUrls: List<AfinityExternalUrl>?,
) : AfinityItem

fun BaseItemDto.toAfinityShow(
    jellyfinRepository: JellyfinRepository,
): AfinityShow {
    return AfinityShow(
        id = id,
        name = name.orEmpty(),
        originalTitle = originalTitle,
        overview = overview.orEmpty(),
        played = userData?.played == true,
        favorite = userData?.isFavorite == true,
        canPlay = playAccess != PlayAccess.NONE,
        canDownload = canDownload == true,
        unplayedItemCount = userData?.unplayedItemCount,
        sources = emptyList(),
        seasons = emptyList(),
        genres = genres ?: emptyList(),
        people = people?.map { it.toAfinityPerson(jellyfinRepository) } ?: emptyList(),
        runtimeTicks = runTimeTicks ?: 0,
        communityRating = communityRating,
        officialRating = officialRating,
        status = status ?: "Ended",
        productionYear = productionYear,
        premiereDate = premiereDate,
        dateCreated = dateCreated,
        dateLastContentAdded = dateLastMediaAdded,
        endDate = endDate,
        tagline = taglines?.firstOrNull(),
        trailer = remoteTrailers?.getOrNull(0)?.url,
        seasonCount = childCount,
        episodeCount = recursiveItemCount,
        images = toAfinityImages(jellyfinRepository),
        providerIds = providerIds?.mapNotNull { (key, value) ->
            value?.let { key to it }
        }?.toMap(),
        externalUrls = externalUrls?.map { it.toAfinityExternalUrl() }
    )
}