package com.makd.afinity.data.models.media

import java.time.LocalDateTime
import java.util.UUID

data class AfinityMusicVideo(
    override val id: UUID,
    override val name: String,
    override val originalTitle: String?,
    override val overview: String,
    override val sources: List<AfinitySource>,
    override val played: Boolean,
    override val favorite: Boolean,
    override val liked: Boolean,
    override val canPlay: Boolean,
    override val canDownload: Boolean,
    override val runtimeTicks: Long,
    override val playbackPositionTicks: Long,
    override val unplayedItemCount: Int? = null,
    override val images: AfinityImages,
    override val chapters: List<AfinityChapter>,
    override val trickplayInfo: Map<String, List<AfinityTrickplayInfo>>?,
    override val providerIds: Map<String, String>?,
    override val externalUrls: List<AfinityExternalUrl>?,
    val artists: List<String>,
    val artistItems: List<AfinityMusicVideoArtist>,
    val album: String?,
    val albumId: UUID?,
    val parentId: UUID?,
    val premiereDate: LocalDateTime?,
    val dateCreated: LocalDateTime?,
    val productionYear: Int?,
    val people: List<AfinityPerson>,
    val genres: List<String>,
    val studios: List<String>,
    val tags: List<String>,
    val communityRating: Float?,
    val officialRating: String?,
    val criticRating: Float?,
    val tagline: String?,
    val mediaSourceCount: Int?,
) : AfinityItem, AfinitySources

data class AfinityMusicVideoArtist(val id: UUID, val name: String)
