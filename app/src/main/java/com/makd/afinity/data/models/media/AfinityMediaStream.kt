package com.makd.afinity.data.models.media

import com.makd.afinity.data.database.entities.AfinityMediaStreamDto
import com.makd.afinity.data.repository.JellyfinRepository
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
    val index: Int,
    val channels: Int?,
    val isDefault: Boolean,
)

fun MediaStream.toAfinityMediaStream(jellyfinRepository: JellyfinRepository): AfinityMediaStream {
    return AfinityMediaStream(
        title = title.orEmpty(),
        displayTitle = displayTitle,
        language = language.orEmpty(),
        type = type,
        codec = codec.orEmpty(),
        isExternal = isExternal,
        path =
            if (isExternal && !deliveryUrl.isNullOrBlank()) {
                jellyfinRepository.getBaseUrl() + deliveryUrl
            } else {
                null
            },
        channelLayout = channelLayout,
        videoRangeType = videoRangeType,
        height = height,
        width = width,
        videoDoViTitle = videoDoViTitle,
        index = index,
        channels = channels,
        isDefault = isDefault,
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
        videoRangeType = VideoRangeType.fromNameOrNull(videoRangeType ?: ""),
        height = height,
        width = width,
        videoDoViTitle = videoDoViTitle,
        index = index,
        channels = null,
        isDefault = false,
    )
}
