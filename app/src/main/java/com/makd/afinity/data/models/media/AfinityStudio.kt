package com.makd.afinity.data.models.media

import java.util.UUID

data class AfinityStudio(
    val id: UUID,
    val name: String,
    val primaryImageUrl: String?,
    val itemCount: Int = 0
)