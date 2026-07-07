package com.makd.afinity.data.models.jellyseerr

enum class MovieSortField(val apiKey: String) {
    POPULARITY("popularity"),
    RELEASE_DATE("release_date"),
    RATING("vote_average"),
    TITLE("original_title"),
}