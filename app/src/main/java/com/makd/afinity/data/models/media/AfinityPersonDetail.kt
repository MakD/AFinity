package com.makd.afinity.data.models.media

import com.makd.afinity.data.repository.JellyfinRepository
import org.jellyfin.sdk.model.api.BaseItemDto
import java.util.UUID

data class AfinityPersonDetail(
    val id: UUID,
    val name: String,
    val overview: String,
    val images: AfinityImages,
)

fun BaseItemDto.toAfinityPersonDetail(
    repository: JellyfinRepository,
): AfinityPersonDetail {
    return AfinityPersonDetail(
        id = id,
        name = name.orEmpty(),
        overview = overview.orEmpty(),
        images = toAfinityImages(repository),
    )
}