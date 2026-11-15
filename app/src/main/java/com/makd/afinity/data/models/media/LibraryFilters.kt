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

enum class LibraryFormatFilter {
    BOOKS,
    AUDIOBOOKS,
}

enum class BookLibraryComposition {
    BOOKS,
    AUDIOBOOKS,
    MIXED;

    fun includesBooks(filters: LibraryFilters): Boolean =
        this != AUDIOBOOKS &&
            (this == BOOKS ||
                filters.formats.isEmpty() ||
                LibraryFormatFilter.BOOKS in filters.formats)

    fun includesAudiobooks(filters: LibraryFilters): Boolean =
        this != BOOKS &&
            (this == AUDIOBOOKS ||
                filters.formats.isEmpty() ||
                LibraryFormatFilter.AUDIOBOOKS in filters.formats)
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
    val audioLanguages: Set<String> = emptySet(),
    val subtitleLanguages: Set<String> = emptySet(),
    val studios: Set<String> = emptySet(),
    val formats: Set<LibraryFormatFilter> = emptySet(),
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
                years.size +
                audioLanguages.size +
                subtitleLanguages.size +
                studios.size +
                formats.size

    val isEmpty: Boolean
        get() = activeCount == 0
}

data class LibraryLanguageOption(val name: String, val code: String)

data class LibraryFilterOptions(
    val genres: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val officialRatings: List<String> = emptyList(),
    val years: List<Int> = emptyList(),
    val audioLanguages: List<LibraryLanguageOption> = emptyList(),
    val subtitleLanguages: List<LibraryLanguageOption> = emptyList(),
    val studios: List<String> = emptyList(),
) {
    val isEmpty: Boolean
        get() =
            genres.isEmpty() &&
                tags.isEmpty() &&
                officialRatings.isEmpty() &&
                years.isEmpty() &&
                audioLanguages.isEmpty() &&
                subtitleLanguages.isEmpty() &&
                studios.isEmpty()
}
