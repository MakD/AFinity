package com.makd.afinity.data.models.media

import com.makd.afinity.data.repository.JellyfinRepository
import java.time.LocalDateTime
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto

data class AfinityPersonDetail(
    val id: UUID,
    val name: String,
    val overview: String,
    val images: AfinityImages,
    val premiereDate: LocalDateTime?,
    val productionLocations: List<String>,
    val externalUrls: List<AfinityExternalUrl>?,
)

fun BaseItemDto.toAfinityPersonDetail(repository: JellyfinRepository): AfinityPersonDetail {
    return AfinityPersonDetail(
        id = id,
        name = name.orEmpty(),
        overview = overview.orEmpty(),
        images = toAfinityImages(repository),
        premiereDate = premiereDate,
        productionLocations = productionLocations ?: emptyList(),
        externalUrls = externalUrls?.map { it.toAfinityExternalUrl() },
    )
}
