package com.makd.afinity.data.models.music

import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.models.media.AfinityUserDataOwner
import java.util.UUID

data class AfinityTrack(
    override val id: UUID,
    val name: String,
    val albumId: UUID?,
    val album: String?,
    val artistId: UUID?,
    val artist: String?,
    val artists: List<String>,
    val indexNumber: Int?,
    val discNumber: Int?,
    val productionYear: Int?,
    val runtimeTicks: Long,
    override val playbackPositionTicks: Long,
    override val played: Boolean,
    override val favorite: Boolean,
    val playCount: Int?,
    val normalizationGain: Float?,
    val images: AfinityImages,
    override val liked: Boolean = false,
    val playlistItemId: String? = null,
    val localFilePath: String? = null,
    val localImagePath: String? = null,
) : AfinityUserDataOwner