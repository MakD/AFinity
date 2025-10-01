package com.makd.afinity.data.models.media

import android.net.Uri
import com.makd.afinity.data.database.dao.ServerDatabaseDao
import com.makd.afinity.data.database.entities.AfinityEpisodeDto
import com.makd.afinity.data.models.extensions.logoBlurHash
import com.makd.afinity.data.repository.JellyfinRepository
import org.jellyfin.sdk.model.DateTime
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.LocationType
import org.jellyfin.sdk.model.api.PlayAccess
import java.util.UUID

data class AfinityEpisode(
    override val id: UUID,
    override val name: String,
    override val originalTitle: String?,
    override val overview: String,
    val indexNumber: Int,
    val indexNumberEnd: Int?,
    val parentIndexNumber: Int,
    override val sources: List<AfinitySource>,
    override val played: Boolean,
    override val favorite: Boolean,
    override val canPlay: Boolean,
    override val canDownload: Boolean,
    override val runtimeTicks: Long,
    override val playbackPositionTicks: Long,
    val premiereDate: DateTime?,
    val seriesName: String,
    val seriesId: UUID,
    val seriesLogo: Uri?,
    val seriesLogoBlurHash: String?,
    val seasonId: UUID,
    val communityRating: Float?,
    val people: List<AfinityPerson>,
    override val unplayedItemCount: Int? = null,
    val missing: Boolean = false,
    override val images: AfinityImages,
    override val chapters: List<AfinityChapter>,
    override val trickplayInfo: Map<String, AfinityTrickplayInfo>?,
    override val providerIds: Map<String, String>?,
    override val externalUrls: List<AfinityExternalUrl>?,
) : AfinityItem, AfinitySources

suspend fun BaseItemDto.toAfinityEpisode(
    jellyfinRepository: JellyfinRepository,
    database: ServerDatabaseDao? = null,
): AfinityEpisode? {
    val sources = mutableListOf<AfinitySource>()
    sources.addAll(mediaSources?.map { it.toAfinitySource(jellyfinRepository, id) } ?: emptyList())
    if (database != null) {
        sources.addAll(database.getSources(id).map { it.toAfinitySource(database) })
    }
    val seriesLogoInfo = try {
        if (seriesId != null) {
            val seriesItem = jellyfinRepository.getItem(seriesId!!)
            val seriesImages = seriesItem?.toAfinityImages(jellyfinRepository)
            Pair(seriesImages?.logo, seriesImages?.logoBlurHash)
        } else {
            Pair(null, null)
        }
    } catch (e: Exception) {
        Pair(null, null)
    }
    return try {
        AfinityEpisode(
            id = id,
            name = name.orEmpty(),
            originalTitle = originalTitle,
            overview = overview.orEmpty(),
            indexNumber = indexNumber ?: 0,
            indexNumberEnd = indexNumberEnd,
            parentIndexNumber = parentIndexNumber ?: 0,
            sources = sources,
            played = userData?.played == true,
            favorite = userData?.isFavorite == true,
            canPlay = playAccess != PlayAccess.NONE,
            canDownload = canDownload == true,
            runtimeTicks = runTimeTicks ?: 0,
            playbackPositionTicks = userData?.playbackPositionTicks ?: 0L,
            premiereDate = premiereDate,
            seriesName = seriesName.orEmpty(),
            seriesId = seriesId!!,
            seriesLogo = seriesLogoInfo.first,
            seriesLogoBlurHash = seriesLogoInfo.second,
            seasonId = seasonId!!,
            communityRating = communityRating,
            people = people?.map { it.toAfinityPerson(jellyfinRepository) } ?: emptyList(),
            missing = locationType == LocationType.VIRTUAL,
            images = toAfinityImages(jellyfinRepository),
            chapters = toAfinityChapters(),
            trickplayInfo = trickplay?.mapValues { it.value[it.value.keys.max()]!!.toAfinityTrickplayInfo() },
            providerIds = providerIds?.mapNotNull { (key, value) ->
                value?.let { key to it }
            }?.toMap(),
            externalUrls = externalUrls?.map { it.toAfinityExternalUrl() }
        )
    } catch (_: NullPointerException) {
        null
    }
}

suspend fun AfinityEpisodeDto.toAfinityEpisode(database: ServerDatabaseDao, userId: UUID): AfinityEpisode {
    val userData = database.getUserDataOrCreateNew(id, userId)
    val sources = database.getSources(id).map { it.toAfinitySource(database) }
    val trickplayInfos = mutableMapOf<String, AfinityTrickplayInfo>()
    for (source in sources) {
        database.getTrickplayInfo(source.id)?.toAfinityTrickplayInfo()?.let {
            trickplayInfos[source.id] = it
        }
    }
    return AfinityEpisode(
        id = id,
        name = name,
        originalTitle = "",
        overview = overview,
        indexNumber = indexNumber,
        indexNumberEnd = indexNumberEnd,
        parentIndexNumber = parentIndexNumber,
        sources = sources,
        played = userData.played,
        favorite = userData.favorite,
        canPlay = true,
        canDownload = false,
        runtimeTicks = runtimeTicks,
        playbackPositionTicks = userData.playbackPositionTicks,
        premiereDate = premiereDate,
        seriesName = seriesName,
        seriesId = seriesId,
        seasonId = seasonId,
        seriesLogo = null,
        seriesLogoBlurHash = null,
        communityRating = communityRating,
        people = emptyList(),
        images = AfinityImages(),
        chapters = chapters ?: emptyList(),
        trickplayInfo = trickplayInfos,
        providerIds = null,
        externalUrls = null
    )
}