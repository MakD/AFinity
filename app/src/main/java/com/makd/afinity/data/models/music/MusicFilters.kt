package com.makd.afinity.data.models.music

import java.util.UUID

data class MusicFilters(
    val favoritesOnly: Boolean = false,
    val unplayedOnly: Boolean = false,
    val playedOnly: Boolean = false,
    val genreIds: List<UUID> = emptyList(),
    val yearMin: Int? = null,
    val yearMax: Int? = null,
) {
    val isActive: Boolean get() = favoritesOnly || unplayedOnly || playedOnly || genreIds.isNotEmpty()
}