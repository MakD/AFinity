package com.makd.afinity.data.models.media

import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto

data class AfinityBoxSet(
    override val id: UUID,
    override val name: String,
    override val originalTitle: String? = null,
    override val overview: String = "",
    override val played: Boolean = false,
    override val favorite: Boolean = false,
    override val liked: Boolean = false,
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

