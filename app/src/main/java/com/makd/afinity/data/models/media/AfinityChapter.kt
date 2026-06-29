package com.makd.afinity.data.models.media

import kotlinx.serialization.Serializable
import org.jellyfin.sdk.model.api.BaseItemDto
import java.util.UUID

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
            imageIndex = if (chapter.imagePath.isNullOrEmpty()) null else index,
        )
    } ?: emptyList()
}

fun AfinityChapter.getChapterImageUrl(baseUrl: String, itemId: UUID): String? {
    return imageIndex?.let { index -> "$baseUrl/Items/$itemId/Images/Chapter/$index" }
}
