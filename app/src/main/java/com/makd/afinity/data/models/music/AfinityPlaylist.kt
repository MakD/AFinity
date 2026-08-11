package com.makd.afinity.data.models.music

import com.makd.afinity.data.models.media.AfinityImages
import com.makd.afinity.data.models.media.PlaylistEntry
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

data class AfinityPlaylistContents(
    val entries: List<PlaylistEntry> = emptyList(),
    val audioCount: Int = 0,
    val videoCount: Int = 0,
) {
    val tracks: List<AfinityTrack>
        get() = entries.filterIsInstance<PlaylistEntry.Audio>().map { it.track }
}