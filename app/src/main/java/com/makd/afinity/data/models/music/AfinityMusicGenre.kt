package com.makd.afinity.data.models.music

import java.util.UUID

data class AfinityMusicGenre(
    val id: UUID,
    val name: String,
    val imageUrl: String?,
)
