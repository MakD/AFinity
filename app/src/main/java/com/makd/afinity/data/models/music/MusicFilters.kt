package com.makd.afinity.data.models.music

import kotlinx.serialization.Serializable

@Serializable
data class MusicFilters(
    val favoritesOnly: Boolean = false,
    val unplayedOnly: Boolean = false,
    val playedOnly: Boolean = false,
    val genres: Set<String> = emptySet(),
    val years: Set<Int> = emptySet(),
) {
    val isActive: Boolean
        get() =
            favoritesOnly ||
                unplayedOnly ||
                playedOnly ||
                genres.isNotEmpty() ||
                years.isNotEmpty()
}

data class MusicFilterOptions(
    val genres: List<String> = emptyList(),
    val years: List<Int> = emptyList(),
)