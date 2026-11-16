package com.makd.afinity.data.models.media

import com.makd.afinity.data.models.audiobookshelf.BookChapter
import java.time.LocalDateTime
import java.util.UUID
import org.jellyfin.sdk.model.api.PersonKind

data class AfinityAudiobook(
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
    val album: String?,
    override val people: List<AfinityPerson>,
    val publisher: String?,
    val premiereDate: LocalDateTime?,
    val productionYear: Int?,
    val genres: List<String>,
    val communityRating: Float?,
    val dateCreated: LocalDateTime?,
    val parentId: UUID?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val container: String?,
    val lastPlayedDate: LocalDateTime? = null,
    val parts: List<AfinityAudiobook> = emptyList(),
    val bookPartCount: Int? = null,
) : AfinityItem {
    val isGrouped: Boolean
        get() = parts.size > 1

    val playableParts: List<AfinityAudiobook>
        get() = if (isGrouped) parts else listOf(this)

    val authors: List<AfinityPerson>
        get() = people.filter { it.type == PersonKind.AUTHOR }

    val authorLine: String
        get() = authors.map { it.name }.distinct().joinToString(", ")

    val narrators: List<AfinityPerson>
        get() = people.filter { it.type == PersonKind.NARRATOR }

    val listeningProgress: Float?
        get() =
            if (runtimeTicks > 0) {
                (playbackPositionTicks.toFloat() / runtimeTicks).coerceIn(0f, 1f)
            } else {
                null
            }

    val resumePartIndex: Int
        get() {
            val list = playableParts
            val inProgress = list.indexOfFirst { !it.played && it.playbackPositionTicks > 0 }
            if (inProgress >= 0) return inProgress
            val firstUnplayed = list.indexOfFirst { !it.played }
            return if (firstUnplayed >= 0) firstUnplayed else 0
        }
}

private const val TICKS_PER_SECOND = 10_000_000.0

fun AfinityAudiobook.timeline(): List<BookChapter> {
    if (isGrouped) {
        var start = 0.0
        return parts.mapIndexed { index, part ->
            val length = part.runtimeTicks / TICKS_PER_SECOND
            BookChapter(id = index, start = start, end = start + length, title = part.name).also {
                start += length
            }
        }
    }
    val total = runtimeTicks / TICKS_PER_SECOND
    val sorted = chapters.sortedBy { it.startPosition }
    if (sorted.size < 2) return emptyList()
    return sorted
        .mapIndexed { index, chapter ->
            BookChapter(
                id = index,
                start = chapter.startPosition / 1000.0,
                end = sorted.getOrNull(index + 1)?.startPosition?.div(1000.0) ?: total,
                title = chapter.name.orEmpty(),
            )
        }
        .filter { it.end > it.start }
}

private val partOrder =
    compareBy<AfinityAudiobook>(
        { it.discNumber ?: 0 },
        { it.trackNumber ?: Int.MAX_VALUE },
        { it.name.lowercase() },
    )

fun List<AfinityAudiobook>.inPartOrder(): List<AfinityAudiobook> = sortedWith(partOrder)

fun List<AfinityAudiobook>.asBook(): AfinityAudiobook? =
    when (size) {
        0 -> null
        1 -> single()
        else -> combineParts(this)
    }

fun audiobookGroupId(parentId: UUID?, album: String): UUID =
    UUID.nameUUIDFromBytes("afinity-audiobook:$parentId:${album.lowercase()}".toByteArray())

fun List<AfinityAudiobook>.groupedIntoBooks(): List<AfinityAudiobook> {
    val keyed = map { item ->
        val album = item.album?.trim()?.takeIf { it.isNotEmpty() }
        val key = album?.let { item.parentId to it.lowercase() }
        key to item
    }
    val groupSizes = keyed.mapNotNull { it.first }.groupingBy { it }.eachCount()
    val emitted = HashSet<Pair<UUID?, String>>()
    val result = mutableListOf<AfinityAudiobook>()
    for ((key, item) in keyed) {
        if (key == null || (groupSizes[key] ?: 0) < 2) {
            result += item
            continue
        }
        if (!emitted.add(key)) continue
        val parts = keyed.filter { it.first == key }.map { it.second }.sortedWith(partOrder)
        result += combineParts(parts)
    }
    return result
}

fun combineParts(parts: List<AfinityAudiobook>): AfinityAudiobook {
    val sorted = parts.sortedWith(partOrder)
    val lead = sorted.first()
    val album = lead.album?.trim().orEmpty().ifEmpty { lead.name }
    val resumeIndex = sorted.indexOfFirst { !it.played && it.playbackPositionTicks > 0 }
    val position =
        if (sorted.all { it.played }) {
            0L
        } else {
            sorted.filter { it.played }.sumOf { it.runtimeTicks } +
                (sorted.getOrNull(resumeIndex)?.playbackPositionTicks ?: 0L)
        }
    return lead.copy(
        id = audiobookGroupId(lead.parentId, album),
        name = album,
        played = sorted.all { it.played },
        favorite = sorted.any { it.favorite },
        liked = sorted.any { it.liked },
        canDownload = sorted.all { it.canDownload },
        runtimeTicks = sorted.sumOf { it.runtimeTicks },
        playbackPositionTicks = position,
        unplayedItemCount = sorted.count { !it.played },
        dateCreated = sorted.mapNotNull { it.dateCreated }.maxOrNull(),
        lastPlayedDate = sorted.mapNotNull { it.lastPlayedDate }.maxOrNull(),
        bookPartCount = sorted.mapNotNull { it.bookPartCount }.maxOrNull(),
        chapters = emptyList(),
        trackNumber = null,
        discNumber = null,
        parts = sorted,
    )
}
