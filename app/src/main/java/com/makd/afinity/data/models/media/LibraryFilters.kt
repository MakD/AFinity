package com.makd.afinity.data.models.media

import kotlinx.serialization.Serializable

enum class LibraryFeature {
    SUBTITLES,
    TRAILER,
    SPECIAL_FEATURE,
    THEME_SONG,
    THEME_VIDEO,
}

enum class VideoTypeFilter {
    BLU_RAY,
    DVD,
    HD,
    UHD_4K,
    SD,
    THREE_D,
}

enum class SeriesStatusFilter(val serialName: String) {
    CONTINUING("Continuing"),
    ENDED("Ended"),
    UNRELEASED("Unreleased"),
}

@Serializable
data class LibraryFilters(
    val played: Boolean = false,
    val unplayed: Boolean = false,
    val resumable: Boolean = false,
    val favorites: Boolean = false,
    val watchlist: Boolean = false,
    val features: Set<LibraryFeature> = emptySet(),
    val genres: Set<String> = emptySet(),
    val officialRatings: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val videoTypes: Set<VideoTypeFilter> = emptySet(),
    val seriesStatuses: Set<SeriesStatusFilter> = emptySet(),
    val years: Set<Int> = emptySet(),
) {
    val activeCount: Int
        get() =
            listOf(played, unplayed, resumable, favorites, watchlist).count { it } +
                features.size +
                genres.size +
                officialRatings.size +
                tags.size +
                videoTypes.size +
                seriesStatuses.size +
                years.size

    val isEmpty: Boolean
        get() = activeCount == 0
}

data class LibraryFilterOptions(
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val officialRatings: List<String> = emptyList(),
    val years: List<Int> = emptyList(),
) {
    val isEmpty: Boolean
        get() =
            genres.isEmpty() && tags.isEmpty() && officialRatings.isEmpty() && years.isEmpty()
}