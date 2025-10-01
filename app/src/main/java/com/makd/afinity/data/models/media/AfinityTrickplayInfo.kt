package com.makd.afinity.data.models.media

import com.makd.afinity.data.database.entities.AfinityTrickplayInfoDto
import org.jellyfin.sdk.model.api.TrickplayInfo

data class AfinityTrickplayInfo(
    val width: Int,
    val height: Int,
    val tileWidth: Int,
    val tileHeight: Int,
    val thumbnailCount: Int,
    val interval: Int,
    val bandwidth: Int,
)

fun TrickplayInfo.toAfinityTrickplayInfo(): AfinityTrickplayInfo {
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