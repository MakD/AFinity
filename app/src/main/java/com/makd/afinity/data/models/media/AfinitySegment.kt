package com.makd.afinity.data.models.media

import com.makd.afinity.data.database.entities.AfinitySegmentDto
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.api.MediaSegmentType

enum class AfinitySegmentType {
    INTRO,
    OUTRO,
    RECAP,
    PREVIEW,
    COMMERCIAL,
    UNKNOWN,
}

private fun MediaSegmentType.toAfinitySegmentType(): AfinitySegmentType =
    when (this) {
        MediaSegmentType.UNKNOWN -> AfinitySegmentType.UNKNOWN
        MediaSegmentType.INTRO -> AfinitySegmentType.INTRO
        MediaSegmentType.OUTRO -> AfinitySegmentType.OUTRO
        MediaSegmentType.RECAP -> AfinitySegmentType.RECAP
        MediaSegmentType.PREVIEW -> AfinitySegmentType.PREVIEW
        MediaSegmentType.COMMERCIAL -> AfinitySegmentType.COMMERCIAL
    }

data class AfinitySegment(val type: AfinitySegmentType, val startTicks: Long, val endTicks: Long)

fun List<AfinitySegment>.mergeOverlapping(): List<AfinitySegment> = groupBy {
    it.type
}
    .values
    .flatMap { sameType ->
        sameType
            .sortedBy { it.startTicks }
            .fold(mutableListOf<AfinitySegment>()) { merged, segment ->
                val last = merged.lastOrNull()
                if (last != null && segment.startTicks <= last.endTicks) {
                    merged[merged.lastIndex] =
                        last.copy(endTicks = maxOf(last.endTicks, segment.endTicks))
                } else {
                    merged.add(segment)
                }
                merged
            }
    }
    .sortedBy { it.startTicks }

fun AfinitySegmentDto.toAfinitySegment(): AfinitySegment {
    return AfinitySegment(type = type, startTicks = startTicks, endTicks = endTicks)
}

fun MediaSegmentDto.toAfinitySegment(): AfinitySegment {
    return AfinitySegment(
        type = type.toAfinitySegmentType(),
        startTicks = startTicks / 10000,
        endTicks = endTicks / 10000,
    )
}
