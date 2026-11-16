package com.makd.afinity.data.models.media

import java.time.LocalDateTime
import java.util.UUID

data class AfinityPhotoAlbum(
    override val id: UUID,
    override val name: String,
    override val originalTitle: String? = null,
    override val overview: String,
    override val played: Boolean,
    override val favorite: Boolean,
    override val liked: Boolean,
    override val canPlay: Boolean,
    override val canDownload: Boolean = false,
    override val sources: List<AfinitySource> = emptyList(),
    override val runtimeTicks: Long = 0L,
    override val playbackPositionTicks: Long = 0L,
    override val unplayedItemCount: Int? = null,
    override val images: AfinityImages,
    override val chapters: List<AfinityChapter> = emptyList(),
    override val providerIds: Map<String, String>?,
    override val externalUrls: List<AfinityExternalUrl>?,
    val parentId: UUID?,
    val childCount: Int?,
    val aspectRatio: Float?,
    val dateCreated: LocalDateTime?,
) : AfinityItem
