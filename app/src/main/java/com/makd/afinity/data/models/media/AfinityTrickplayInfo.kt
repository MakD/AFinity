package com.makd.afinity.data.models.media

import com.makd.afinity.data.database.entities.AfinityTrickplayInfoDto
import org.jellyfin.sdk.model.api.TrickplayInfoDto

data class AfinityTrickplayInfo(
    val width: Int,
    val height: Int,
    val tileWidth: Int,
    val tileHeight: Int,
    val thumbnailCount: Int,
    val interval: Int,
    val bandwidth: Int,
)

private const val LOCAL_SOURCE_SUFFIX = "_local"

fun TrickplayInfoDto.toAfinityTrickplayInfo(): AfinityTrickplayInfo {
    return AfinityTrickplayInfo(
        width = width,
        height = height,
        tileWidth = tileWidth,
        tileHeight = tileHeight,
        thumbnailCount = thumbnailCount,
        interval = interval,
        bandwidth = bandwidth,
    )
}

fun AfinityTrickplayInfoDto.toAfinityTrickplayInfo(): AfinityTrickplayInfo {
    return AfinityTrickplayInfo(
        width = width,
        height = height,
        tileWidth = tileWidth,
        tileHeight = tileHeight,
        thumbnailCount = thumbnailCount,
        interval = interval,
        bandwidth = bandwidth,
    )
}

fun Map<String, Map<String, TrickplayInfoDto>?>.toAfinityTrickplayInfoMap():
    Map<String, List<AfinityTrickplayInfo>> = mapValues { (_, widthMap) ->
    widthMap.orEmpty().values.map { it.toAfinityTrickplayInfo() }.sortedBy { it.width }
}

fun Map<String, List<AfinityTrickplayInfo>>.forSource(
    mediaSourceId: String?
): List<AfinityTrickplayInfo> {
    if (mediaSourceId == null) return if (size == 1) values.first() else emptyList()
    return this[mediaSourceId]
        ?: this[mediaSourceId.removeSuffix(LOCAL_SOURCE_SUFFIX)]
        ?: this[mediaSourceId + LOCAL_SOURCE_SUFFIX]
        ?: emptyList()
}

fun List<AfinityTrickplayInfo>.closestTo(targetWidthPx: Int): AfinityTrickplayInfo? {
    val usable = filter { it.width > 0 && it.height > 0 && it.interval > 0 }.sortedBy { it.width }
    return usable.firstOrNull { it.width >= targetWidthPx } ?: usable.lastOrNull()
}
