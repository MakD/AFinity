package com.makd.afinity.data.models.music

import com.makd.afinity.data.models.media.AfinityImages
import java.util.UUID

data class AfinityAlbum(
    val id: UUID,
    val name: String,
    val artistId: UUID?,
    val artist: String?,
    val artists: List<String>,
    val productionYear: Int?,
    val songCount: Int?,
    val runtimeTicks: Long,
    val genres: List<String>,
    val overview: String?,
    val favorite: Boolean,
    val played: Boolean,
    val playCount: Int?,
    val images: AfinityImages,
)