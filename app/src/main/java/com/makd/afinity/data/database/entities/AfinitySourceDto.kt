package com.makd.afinity.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.makd.afinity.data.models.media.AfinitySource
import com.makd.afinity.data.models.media.AfinitySourceType
import java.util.UUID

@Entity(
    tableName = "sources",
)
data class AfinitySourceDto(
    @PrimaryKey
    val id: String,
    val itemId: UUID,
    val name: String,
    val type: AfinitySourceType,
    val path: String,
    val downloadId: Long? = null,
)

fun AfinitySource.toAfinitySourceDto(itemId: UUID, path: String): AfinitySourceDto {
    return AfinitySourceDto(
        id = id,
        itemId = itemId,
        name = name,
        type = AfinitySourceType.LOCAL,
        path = path,
    )
}