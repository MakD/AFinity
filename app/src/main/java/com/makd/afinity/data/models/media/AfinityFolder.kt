package com.makd.afinity.data.models.media

import com.makd.afinity.data.repository.JellyfinRepository
import org.jellyfin.sdk.model.api.BaseItemDto
import java.util.UUID

data class AfinityFolder(
    override val id: UUID,
    override val name: String,
    override val originalTitle: String? = null,
    override val overview: String = "",
    override val played: Boolean,
    override val favorite: Boolean,
    override val liked: Boolean,
    override val canPlay: Boolean = false,
    override val canDownload: Boolean = false,
    override val sources: List<AfinitySource> = emptyList(),
    override val runtimeTicks: Long = 0L,
    override val playbackPositionTicks: Long = 0L,
    override val unplayedItemCount: Int?,
    override val images: AfinityImages,
    override val chapters: List<AfinityChapter> = emptyList(),
    override val providerIds: Map<String, String>?,
    override val externalUrls: List<AfinityExternalUrl>?,
) : AfinityItem

fun BaseItemDto.toAfinityFolder(
    jellyfinRepository: JellyfinRepository,
): AfinityFolder {
    return AfinityFolder(
        id = id,
        name = name.orEmpty(),
        played = userData?.played == true,
        favorite = userData?.isFavorite == true,
        liked = userData?.likes == true,
        unplayedItemCount = userData?.unplayedItemCount,
        images = toAfinityImages(jellyfinRepository),
        providerIds = providerIds?.mapNotNull { (key, value) ->
            value?.let { key to it }
        }?.toMap(),
        externalUrls = externalUrls?.map { it.toAfinityExternalUrl() }
    )
}