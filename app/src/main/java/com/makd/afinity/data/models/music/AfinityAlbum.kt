package com.makd.afinity.data.models.music

import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.models.media.AfinityUserDataOwner
import java.util.UUID

data class AfinityAlbum(
    override val id: UUID,
    val name: String,
    val artistId: UUID?,
    val artist: String?,
    val artists: List<String>,
    val productionYear: Int?,
    val songCount: Int?,
    val runtimeTicks: Long,
    val genres: List<String>,
    val overview: String?,
    override val favorite: Boolean,
    override val played: Boolean,
    val playCount: Int?,
    val images: AfinityImages,
    override val liked: Boolean = false,
    override val playbackPositionTicks: Long = 0L,
) : AfinityUserDataOwner