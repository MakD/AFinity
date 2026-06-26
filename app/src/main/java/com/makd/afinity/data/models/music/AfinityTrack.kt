package com.makd.afinity.data.models.music

import com.makd.afinity.data.models.media.AfinityImages
import java.util.UUID

data class AfinityTrack(
    val id: UUID,
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
    val playbackPositionTicks: Long,
    val played: Boolean,
    val favorite: Boolean,
    val playCount: Int?,
    val normalizationGain: Float?,
    val images: AfinityImages,
    val playlistItemId: String? = null,
    val localFilePath: String? = null,
    val localImagePath: String? = null,
)