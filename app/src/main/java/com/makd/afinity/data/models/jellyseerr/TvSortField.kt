package com.makd.afinity.data.models.jellyseerr

enum class TvSortField(val apiKey: String) {
    POPULARITY("popularity"),
    FIRST_AIR_DATE("first_air_date"),
    RATING("vote_average"),
    TITLE("original_title"),
}