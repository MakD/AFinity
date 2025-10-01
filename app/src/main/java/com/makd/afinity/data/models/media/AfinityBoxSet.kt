package com.makd.afinity.data.models.media

import com.makd.afinity.data.repository.JellyfinRepository
import org.jellyfin.sdk.model.api.BaseItemDto
import java.util.UUID

data class AfinityBoxSet(
    override val id: UUID,
    override val name: String,
    override val originalTitle: String? = null,
    override val overview: String = "",
    override val played: Boolean = false,
    override val favorite: Boolean = false,
    override val canPlay: Boolean = false,
    override val canDownload: Boolean = false,
    override val sources: List<AfinitySource> = emptyList(),
    override val runtimeTicks: Long = 0L,
    override val playbackPositionTicks: Long = 0L,
    override val unplayedItemCount: Int? = null,
    override val images: AfinityImages,
    override val chapters: List<AfinityChapter> = emptyList(),
    val items: List<AfinityItem> = emptyList(),
    val itemCount: Int? = null,
    val productionYear: Int? = null,
    val genres: List<String> = emptyList(),
    val communityRating: Float? = null,
    val officialRating: String? = null,
    val people: List<AfinityPerson> = emptyList(),
    override val providerIds: Map<String, String>?,
    override val externalUrls: List<AfinityExternalUrl>?,
) : AfinityItem

fun BaseItemDto.toAfinityBoxSet(
    jellyfinRepository: JellyfinRepository,
): AfinityBoxSet {
    return AfinityBoxSet(
        id = id,
        name = name.orEmpty(),
        images = toAfinityImages(jellyfinRepository),
        providerIds = providerIds?.mapNotNull { (key, value) ->
            value?.let { key to it }
        }?.toMap(),
        externalUrls = externalUrls?.map { it.toAfinityExternalUrl() }
    )
}