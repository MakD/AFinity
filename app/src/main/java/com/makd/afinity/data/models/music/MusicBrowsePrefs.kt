package com.makd.afinity.data.models.music

import kotlinx.serialization.Serializable

@Serializable
data class MusicBrowsePrefs(
    val albumSortField: String = "Name",
    val albumSortDescending: Boolean = false,
    val albumFilters: MusicFilters = MusicFilters(),
    val artistFilters: MusicFilters = MusicFilters(),
    val trackSortField: String = "Name",
    val trackSortDescending: Boolean = false,
    val trackFilters: MusicFilters = MusicFilters(),
)