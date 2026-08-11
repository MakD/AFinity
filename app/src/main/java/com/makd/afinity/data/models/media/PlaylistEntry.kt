package com.makd.afinity.data.models.media

import com.makd.afinity.data.models.music.AfinityTrack
import java.util.UUID

sealed interface PlaylistEntry {
    val id: UUID
    val playlistItemId: String?

    data class Audio(override val playlistItemId: String?, val track: AfinityTrack) :
        PlaylistEntry {
        override val id: UUID
            get() = track.id
    }

    data class Video(override val playlistItemId: String?, val item: AfinityItem) : PlaylistEntry {
        override val id: UUID
            get() = item.id
    }
}