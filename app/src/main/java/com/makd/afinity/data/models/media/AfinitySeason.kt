package com.makd.afinity.data.models.media

import com.makd.afinity.data.repository.JellyfinRepository
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.PlayAccess
import java.time.LocalDateTime
import java.util.UUID

data class AfinitySeason(
    override val id: UUID,
    override val name: String,
    val seriesId: UUID,
    val seriesName: String,
    override val originalTitle: String?,
    override val overview: String,
    override val sources: List<AfinitySource>,
    val indexNumber: Int,
    val episodes: Collection<AfinityEpisode>,
    val episodeCount: Int?,
    val productionYear: Int?,
    val premiereDate: LocalDateTime?,
    val people: List<AfinityPerson>,
    override val played: Boolean,
    override val favorite: Boolean,
    override val liked: Boolean,
    override val canPlay: Boolean,
    override val canDownload: Boolean,
    override val runtimeTicks: Long = 0L,
    override val playbackPositionTicks: Long = 0L,
    override val unplayedItemCount: Int?,
    override val images: AfinityImages,
    override val chapters: List<AfinityChapter> = emptyList(),
    override val providerIds: Map<String, String>?,
    override val externalUrls: List<AfinityExternalUrl>?,
) : AfinityItem

fun BaseItemDto.toAfinitySeason(
    jellyfinRepository: JellyfinRepository,
): AfinitySeason {
    return AfinitySeason(
        id = id,
        name = name.orEmpty(),
        originalTitle = originalTitle,
        overview = overview.orEmpty(),
        played = userData?.played == true,
        favorite = userData?.isFavorite == true,
        liked = userData?.likes == true,
        canPlay = playAccess != PlayAccess.NONE,
        canDownload = canDownload == true,
        unplayedItemCount = userData?.unplayedItemCount,
        indexNumber = indexNumber ?: 0,
        sources = emptyList(),
        episodes = emptyList(),
        seriesId = seriesId!!,
        seriesName = seriesName.orEmpty(),
        images = toAfinityImages(jellyfinRepository),
        episodeCount = childCount,
        productionYear = productionYear,
        premiereDate = premiereDate,
        people = people?.map { it.toAfinityPerson(jellyfinRepository) } ?: emptyList(),
        providerIds = providerIds?.mapNotNull { (key, value) ->
            value?.let { key to it }
        }?.toMap(),
        externalUrls = externalUrls?.map { it.toAfinityExternalUrl() }
    )
}