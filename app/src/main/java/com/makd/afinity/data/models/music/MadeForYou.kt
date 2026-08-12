package com.makd.afinity.data.models.music

enum class MadeForYouMixKind {
    Random,
    AlbumRadio,
    GenreSongs,
}

sealed interface MadeForYouSlot {
    val slotId: String

    data class Mix(
        override val slotId: String,
        val kind: MadeForYouMixKind,
        val seedName: String?,
        val tracks: List<AfinityTrack>,
    ) : MadeForYouSlot

    data class Album(override val slotId: String, val album: AfinityAlbum) : MadeForYouSlot

    data class Pending(override val slotId: String) : MadeForYouSlot
}