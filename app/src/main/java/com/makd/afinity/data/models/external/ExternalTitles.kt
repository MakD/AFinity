package com.makd.afinity.data.models.external

import com.makd.afinity.data.models.jellyseerr.SearchResultItem

enum class ExternalTitlesSource {
    SEERR,
    TMDB,
}

data class ExternalTitles(val source: ExternalTitlesSource, val items: List<SearchResultItem>)