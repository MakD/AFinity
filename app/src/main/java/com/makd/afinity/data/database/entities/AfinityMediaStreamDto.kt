package com.makd.afinity.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.makd.afinity.data.models.media.AfinityMediaStream
import java.util.UUID
import org.jellyfin.sdk.model.api.MediaStreamType

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
    val index: Int,
    val channels: Int?,
    val isDefault: Boolean,
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
        videoRangeType = videoRangeType?.name,
        height = height,
        width = width,
        videoDoViTitle = videoDoViTitle,
        index = index,
        channels = channels,
        isDefault = isDefault,
    )
}
