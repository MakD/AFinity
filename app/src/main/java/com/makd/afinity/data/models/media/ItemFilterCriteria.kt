package com.makd.afinity.data.models.media

data class ItemFilterCriteria(
    val genres: List<String> = emptyList(),
    val years: List<Int> = emptyList(),
    val isFavorite: Boolean? = null,
    val isPlayed: Boolean? = null,
    val isLiked: Boolean? = null,
    val isResumable: Boolean? = null,
    val hasOverview: Boolean? = null,
    val studios: List<String> = emptyList(),
    val officialRatings: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val videoTypes: List<String> = emptyList(),
    val seriesStatuses: List<String> = emptyList(),
    val hasSubtitles: Boolean? = null,
    val hasTrailer: Boolean? = null,
    val hasSpecialFeature: Boolean? = null,
    val hasThemeSong: Boolean? = null,
    val hasThemeVideo: Boolean? = null,
    val isHd: Boolean? = null,
    val is4k: Boolean? = null,
    val is3d: Boolean? = null,
)

fun LibraryFilters.toItemFilterCriteria(studioName: String? = null): ItemFilterCriteria =
    ItemFilterCriteria(
        genres = genres.toList(),
        years = years.toList(),
        isFavorite = if (favorites) true else null,
        isPlayed =
            when {
                played && !unplayed -> true
                unplayed && !played -> false
                else -> null
            },
        isLiked = if (watchlist) true else null,
        isResumable = if (resumable) true else null,
        studios = if (studioName != null) listOf(studioName) else emptyList(),
        officialRatings = officialRatings.toList(),
        tags = tags.toList(),
        videoTypes = buildList {
            if (VideoTypeFilter.BLU_RAY in videoTypes) add("BluRay")
            if (VideoTypeFilter.DVD in videoTypes) add("Dvd")
        },
        seriesStatuses = seriesStatuses.map { it.serialName },
        hasSubtitles = if (LibraryFeature.SUBTITLES in features) true else null,
        hasTrailer = if (LibraryFeature.TRAILER in features) true else null,
        hasSpecialFeature = if (LibraryFeature.SPECIAL_FEATURE in features) true else null,
        hasThemeSong = if (LibraryFeature.THEME_SONG in features) true else null,
        hasThemeVideo = if (LibraryFeature.THEME_VIDEO in features) true else null,
        isHd =
            when {
                VideoTypeFilter.HD in videoTypes && VideoTypeFilter.SD !in videoTypes -> true
                VideoTypeFilter.SD in videoTypes && VideoTypeFilter.HD !in videoTypes -> false
                else -> null
            },
        is4k = if (VideoTypeFilter.UHD_4K in videoTypes) true else null,
        is3d = if (VideoTypeFilter.THREE_D in videoTypes) true else null,
    )