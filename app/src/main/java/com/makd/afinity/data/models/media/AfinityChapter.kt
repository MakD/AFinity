package com.makd.afinity.data.models.media

import java.util.UUID
import kotlinx.serialization.Serializable
import org.jellyfin.sdk.model.api.BaseItemDto

@Serializable
data class AfinityChapter(
    val startPosition: Long,
    val name: String? = null,
    val imageIndex: Int? = null,
)

fun BaseItemDto.toAfinityChapters(): List<AfinityChapter> {
    return chapters?.mapIndexed { index, chapter ->
        AfinityChapter(
            startPosition = chapter.startPositionTicks / 10000,
            name = chapter.name,
            imageIndex = index,
        )
    } ?: emptyList()
}

fun AfinityChapter.getChapterImageUrl(baseUrl: String, itemId: UUID): String? {
    return imageIndex?.let { index -> "$baseUrl/Items/$itemId/Images/Chapter/$index" }
}
