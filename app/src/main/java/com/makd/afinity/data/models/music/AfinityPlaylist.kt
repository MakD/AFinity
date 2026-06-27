package com.makd.afinity.data.models.music

import com.makd.afinity.data.models.media.AfinityImages
import java.util.UUID

data class AfinityPlaylist(
    val id: UUID,
    val name: String,
    val overview: String?,
    val songCount: Int?,
    val runtimeTicks: Long,
    val favorite: Boolean,
    val images: AfinityImages,
)