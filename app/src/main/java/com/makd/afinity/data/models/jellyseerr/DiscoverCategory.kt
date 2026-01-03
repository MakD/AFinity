package com.makd.afinity.data.models.jellyseerr

enum class DiscoverCategory {
    TRENDING,
    POPULAR_MOVIES,
    POPULAR_TV,
    UPCOMING_MOVIES,
    UPCOMING_TV;

    fun getDisplayName(): String = when (this) {
        TRENDING -> "Trending Now"
        POPULAR_MOVIES -> "Popular Movies"
        POPULAR_TV -> "Popular TV Shows"
        UPCOMING_MOVIES -> "Upcoming Movies"
        UPCOMING_TV -> "Upcoming TV Shows"
    }
}