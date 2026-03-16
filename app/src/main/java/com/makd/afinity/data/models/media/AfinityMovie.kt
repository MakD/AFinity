package com.makd.afinity.data.models.media

import com.makd.afinity.data.database.dao.ServerDatabaseDao
import com.makd.afinity.data.models.extensions.toAfinityImages
import com.makd.afinity.data.models.extensions.toAfinityPerson
import com.makd.afinity.data.models.mdblist.MdbListRating
import com.makd.afinity.data.models.tmdb.TmdbReview
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.PlayAccess
import java.time.LocalDateTime
import java.util.UUID

data class AfinityMovie(
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
    val premiereDate: LocalDateTime?,
    val dateCreated: LocalDateTime?,
    val people: List<AfinityPerson>,
    val genres: List<String>,
    val communityRating: Float?,
    val officialRating: String?,
    val criticRating: Float?,
    val taglines: List<String> = emptyList(),
    val status: String,
    val productionYear: Int?,
    val endDate: LocalDateTime?,
    val trailer: String?,
    val tagline: String?,
    override val unplayedItemCount: Int? = null,
    override val images: AfinityImages,
    override val chapters: List<AfinityChapter>,
    override val trickplayInfo: Map<String, AfinityTrickplayInfo>?,
    override val providerIds: Map<String, String>?,
    override val externalUrls: List<AfinityExternalUrl>?,
    val tmdbReviews: List<TmdbReview> = emptyList(),
    val mdbRatings: List<MdbListRating> = emptyList(),
) : AfinityItem, AfinitySources

suspend fun BaseItemDto.toAfinityMovie(
    baseUrl: String,
    serverDatabase: ServerDatabaseDao? = null,
): AfinityMovie {
    val sources = mutableListOf<AfinitySource>()
    sources.addAll(mediaSources?.map { it.toAfinitySource(baseUrl, id) } ?: emptyList())
    if (serverDatabase != null) {
        sources.addAll(serverDatabase.getSources(id).map { it.toAfinitySource(serverDatabase) })
    }
    return AfinityMovie(
        id = id,
        name = name.orEmpty(),
        originalTitle = originalTitle,
        overview = overview.orEmpty(),
        sources = sources,
        played = userData?.played == true,
        favorite = userData?.isFavorite == true,
        liked = userData?.likes == true,
        canPlay = playAccess != PlayAccess.NONE,
        canDownload = canDownload == true,
        runtimeTicks = runTimeTicks ?: 0,
        playbackPositionTicks = userData?.playbackPositionTicks ?: 0,
        premiereDate = premiereDate,
        dateCreated = dateCreated,
        communityRating = communityRating,
        criticRating = criticRating,
        genres = genres ?: emptyList(),
        people = people?.map { it.toAfinityPerson(baseUrl) } ?: emptyList(),
        officialRating = officialRating,
        status = status ?: "Ended",
        productionYear = productionYear,
        tagline = taglines?.firstOrNull(),
        endDate = endDate,
        trailer = remoteTrailers?.getOrNull(0)?.url,
        images = toAfinityImages(baseUrl),
        chapters = toAfinityChapters(),
        trickplayInfo =
            trickplay?.mapValues { it.value[it.value.keys.max()]!!.toAfinityTrickplayInfo() },
        providerIds = providerIds?.mapNotNull { (key, value) -> value?.let { key to it } }?.toMap(),
        externalUrls = externalUrls?.map { it.toAfinityExternalUrl() },
        tmdbReviews = emptyList(),
        mdbRatings = emptyList(),
    )
}
