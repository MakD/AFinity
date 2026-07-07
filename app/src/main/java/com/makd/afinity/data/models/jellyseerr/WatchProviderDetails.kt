package com.makd.afinity.data.models.jellyseerr

import kotlinx.serialization.Serializable

@Serializable
data class WatchProviderDetails(
    val id: Int,
    val name: String,
    val logoPath: String? = null,
    val displayPriority: Int? = null,
) {
    fun logoUrl(): String? = logoPath?.let { "https://image.tmdb.org/t/p/original$it" }
}