package com.makd.afinity.data.models.media

import java.time.LocalDateTime
import java.util.UUID

/**
 * Represents a Video item (special features, trailers, etc.)
 */
data class AfinityVideo(
    override val id: UUID,
    override val name: String,
    override val originalTitle: String?,
    override val overview: String,
    override val played: Boolean,
    override val favorite: Boolean,
    override val canPlay: Boolean,
    override val canDownload: Boolean,
    override val sources: List<AfinitySource>,
    override val runtimeTicks: Long,
    override val playbackPositionTicks: Long,
    override val unplayedItemCount: Int?,
    override val images: AfinityImages,
    override val chapters: List<AfinityChapter>,
    override val providerIds: Map<String, String>?,
    override val externalUrls: List<AfinityExternalUrl>?,

    val premiereDate: LocalDateTime?,
    val people: List<AfinityPerson>,
    val genres: List<String>,
    val communityRating: Float?,
    val officialRating: String?,
    val criticRating: Float?,
    val status: String,
    val productionYear: Int?,
    val endDate: LocalDateTime?,
    val trailer: String?,
    val tagline: String?,
    val trickplayInfo: AfinityTrickplayInfo?,
) : AfinityItem