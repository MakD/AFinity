package com.makd.afinity.data.models.media

import java.time.LocalDateTime
import java.util.UUID

data class AfinityPersonDetail(
    val id: UUID,
    val name: String,
    val overview: String,
    val images: AfinityImages,
    val premiereDate: LocalDateTime?,
    val endDate: LocalDateTime?,
    val productionLocations: List<String>,
    val externalUrls: List<AfinityExternalUrl>?,
    val favorite: Boolean = false,
)
