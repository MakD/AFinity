package com.makd.afinity.data.models.media

import com.makd.afinity.data.database.entities.AfinityMediaStreamDto
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.VideoRangeType

data class AfinityMediaStream(
    val title: String,
    val displayTitle: String?,
    val language: String,
    val type: MediaStreamType,
    val codec: String,
    val isExternal: Boolean,
    val path: String?,
    val channelLayout: String?,
    val videoRangeType: VideoRangeType?,
    val height: Int?,
    val width: Int?,
    val videoDoViTitle: String?,
    val hdr10PlusPresentFlag: Boolean = false,
    val index: Int,
    val channels: Int?,
    val isDefault: Boolean,
    val isForced: Boolean = false,
    val isHearingImpaired: Boolean = false,
    val isOriginal: Boolean = false,
    val profile: String? = null,
    val localizedLanguage: String? = null,
    val localizedForced: String? = null,
    val localizedExternal: String? = null,
    val localizedHearingImpaired: String? = null,
    val localizedOriginal: String? = null,
)

private val DOLBY_VISION_RANGES =
    setOf(
        VideoRangeType.DOVI,
        VideoRangeType.DOVI_WITH_HDR10,
        VideoRangeType.DOVI_WITH_HLG,
        VideoRangeType.DOVI_WITH_SDR,
        VideoRangeType.DOVI_WITH_EL,
        VideoRangeType.DOVI_WITH_HDR10_PLUS,
        VideoRangeType.DOVI_WITH_ELHDR10_PLUS,
    )

fun AfinityMediaStream.isDolbyVision(): Boolean =
    videoDoViTitle != null || videoRangeType in DOLBY_VISION_RANGES

fun AfinityMediaStream.hdrLabel(): String? =
    when (videoRangeType) {
        VideoRangeType.HDR10_PLUS -> "HDR10+"
        VideoRangeType.HDR10 -> if (hdr10PlusPresentFlag) "HDR10+" else "HDR10"
        VideoRangeType.HLG -> "HLG"
        else -> null
    }

fun parseVideoRangeType(stored: String?): VideoRangeType? {
    if (stored.isNullOrBlank()) return null
    return VideoRangeType.fromNameOrNull(stored)
        ?: VideoRangeType.entries.firstOrNull { it.name == stored }
}

fun MediaStream.toAfinityMediaStream(baseUrl: String): AfinityMediaStream {
    return AfinityMediaStream(
        title = title.orEmpty(),
        displayTitle = displayTitle,
        language = language.orEmpty(),
        type = type,
        codec = codec.orEmpty(),
        isExternal = isExternal,
        path =
            if (isExternal && !deliveryUrl.isNullOrBlank()) {
                baseUrl + deliveryUrl
            } else {
                null
            },
        channelLayout = channelLayout,
        videoRangeType = videoRangeType,
        height = height,
        width = width,
        videoDoViTitle = videoDoViTitle,
        hdr10PlusPresentFlag = hdr10PlusPresentFlag == true,
        index = index,
        channels = channels,
        isDefault = isDefault,
        isForced = isForced,
        isHearingImpaired = isHearingImpaired,
        isOriginal = isOriginal,
        profile = profile,
        localizedLanguage = localizedLanguage,
        localizedForced = localizedForced,
        localizedExternal = localizedExternal,
        localizedHearingImpaired = localizedHearingImpaired,
        localizedOriginal = localizedOriginal,
    )
}

fun AfinityMediaStreamDto.toAfinityMediaStream(): AfinityMediaStream {
    return AfinityMediaStream(
        title = title,
        displayTitle = displayTitle,
        language = language,
        type = type,
        codec = codec,
        isExternal = isExternal,
        path = path,
        channelLayout = channelLayout,
        videoRangeType = parseVideoRangeType(videoRangeType),
        height = height,
        width = width,
        videoDoViTitle = videoDoViTitle,
        hdr10PlusPresentFlag = hdr10PlusPresentFlag,
        index = index,
        channels = channels,
        isDefault = isDefault,
        isForced = isForced,
        isHearingImpaired = isHearingImpaired,
        isOriginal = isOriginal,
        profile = profile,
        localizedLanguage = localizedLanguage,
        localizedForced = localizedForced,
        localizedExternal = localizedExternal,
        localizedHearingImpaired = localizedHearingImpaired,
        localizedOriginal = localizedOriginal,
    )
}
