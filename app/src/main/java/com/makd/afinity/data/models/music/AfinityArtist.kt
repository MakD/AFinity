package com.makd.afinity.data.models.music

import com.makd.afinity.data.models.media.AfinityImages
import java.util.UUID

data class AfinityArtist(
    val id: UUID,
    val name: String,
    val overview: String?,
    val albumCount: Int?,
    val genres: List<String>,
    val favorite: Boolean,
    val images: AfinityImages,
)