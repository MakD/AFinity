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
