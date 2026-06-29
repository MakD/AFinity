package com.makd.afinity.data.models.music

import java.util.UUID

data class RadioState(
    val isActive: Boolean = false,
    val mode: RadioMode = RadioMode.SIMILAR,
    val seedTrackId: UUID? = null,
    val albumId: UUID? = null,
    val continuousSeedId: UUID? = null,
    val sourceTracks: List<AfinityTrack> = emptyList(),
    val isGenerating: Boolean = false,
)