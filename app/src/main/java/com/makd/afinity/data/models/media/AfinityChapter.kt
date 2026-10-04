package com.makd.afinity.data.models.media

import java.util.UUID
import kotlinx.serialization.Serializable
import org.jellyfin.sdk.model.api.BaseItemDto

@Serializable
data class AfinityChapter(
    val startPosition: Long,
    val name: String? = null,
    val imageIndex: Int? = null,
    val imageTag: String? = null,
)

fun BaseItemDto.toAfinityChapters(): List<AfinityChapter> {
    return chapters?.mapIndexed { index, chapter ->
        AfinityChapter(
            startPosition = chapter.startPositionTicks / 10000,
            name = chapter.name,
            imageIndex = if (chapter.imagePath.isNullOrEmpty()) null else index,
            imageTag = chapter.imageTag,
        )
    } ?: emptyList()
}

fun AfinityChapter.getChapterImageUrl(baseUrl: String, itemId: UUID): String? {
    return imageIndex?.let { index ->
        val url = "$baseUrl/Items/$itemId/Images/Chapter/$index"
        imageTag?.let { "$url?tag=$it" } ?: url
    }
}
