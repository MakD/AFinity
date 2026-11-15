package com.makd.afinity.data.models.media

import java.time.LocalDateTime
import java.util.UUID
import org.jellyfin.sdk.model.api.PersonKind

enum class BookFormat {
    EPUB,
    PDF,
    COMIC,
    KINDLE,
    UNKNOWN;

    companion object {
        fun fromPath(path: String?): BookFormat =
            when (path?.substringAfterLast('.', "")?.lowercase()) {
                "epub" -> EPUB
                "pdf" -> PDF
                "cbz",
                "cbr",
                "cb7",
                "cbt" -> COMIC
                "mobi",
                "azw",
                "azw3" -> KINDLE
                else -> UNKNOWN
            }
    }
}

data class AfinityBook(
    override val id: UUID,
    override val name: String,
    override val originalTitle: String?,
    override val overview: String,
    override val played: Boolean,
    override val favorite: Boolean,
    override val liked: Boolean,
    override val canPlay: Boolean,
    override val canDownload: Boolean,
    override val sources: List<AfinitySource> = emptyList(),
    override val runtimeTicks: Long,
    override val playbackPositionTicks: Long,
    override val unplayedItemCount: Int?,
    override val images: AfinityImages,
    override val chapters: List<AfinityChapter> = emptyList(),
    override val providerIds: Map<String, String>?,
    override val externalUrls: List<AfinityExternalUrl>?,
    val seriesName: String?,
    val seriesIndex: Int?,
    val people: List<AfinityPerson>,
    val publisher: String?,
    val premiereDate: LocalDateTime?,
    val productionYear: Int?,
    val genres: List<String>,
    val tags: List<String>,
    val communityRating: Float?,
    val dateCreated: LocalDateTime?,
    val format: BookFormat,
    val parentId: UUID?,
    val lastPlayedDate: LocalDateTime? = null,
) : AfinityItem {
    val authors: List<AfinityPerson>
        get() = people.filter { it.type == PersonKind.AUTHOR }

    val pageCount: Int?
        get() =
            if (format == BookFormat.COMIC || format == BookFormat.PDF) {
                (runtimeTicks / TICKS_PER_PAGE).toInt().takeIf { it > 0 }
            } else {
                null
            }

    val readingProgress: Float?
        get() =
            if (runtimeTicks > 0) {
                (playbackPositionTicks.toFloat() / runtimeTicks).coerceIn(0f, 1f)
            } else {
                null
            }

    companion object {
        const val TICKS_PER_PAGE = 10_000L
    }
}
