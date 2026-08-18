package com.makd.afinity.data.models.music

import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.models.media.AfinityUserDataOwner
import java.util.UUID

data class AfinityArtist(
    override val id: UUID,
    val name: String,
    val overview: String?,
    val albumCount: Int?,
    val genres: List<String>,
    override val favorite: Boolean,
    val images: AfinityImages,
    override val liked: Boolean = false,
    override val played: Boolean = false,
    override val playbackPositionTicks: Long = 0L,
) : AfinityUserDataOwner