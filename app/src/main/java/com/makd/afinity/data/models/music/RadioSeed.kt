package com.makd.afinity.data.models.music

import java.util.UUID

data class RadioSeed(
    val trackId: UUID,
    val albumId: UUID?,
    val sourceTracks: List<AfinityTrack>,
)