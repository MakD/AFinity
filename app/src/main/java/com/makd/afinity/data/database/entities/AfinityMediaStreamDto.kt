package com.makd.afinity.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.makd.afinity.data.models.media.AfinityMediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import java.util.UUID

@Entity(tableName = "mediastreams")
data class AfinityMediaStreamDto(
    @PrimaryKey val id: UUID,
    val sourceId: String,
    val title: String,
    val displayTitle: String?,
    val language: String,
    val type: MediaStreamType,
    val codec: String,
    val isExternal: Boolean,
    val path: String,
    val channelLayout: String?,
    val videoRangeType: String?,
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
    val downloadId: Long? = null,
)

fun AfinityMediaStream.toAfinityMediaStreamDto(
    id: UUID,
    sourceId: String,
    path: String,
): AfinityMediaStreamDto {
    return AfinityMediaStreamDto(
        id = id,
        sourceId = sourceId,
        title = title,
        displayTitle = displayTitle,
        language = language,
        type = type,
        codec = codec,
        isExternal = isExternal,
        path = path,
        channelLayout = channelLayout,
        videoRangeType = videoRangeType?.serialName,
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
