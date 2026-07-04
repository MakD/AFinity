package com.makd.afinity.data.models.jellyseerr

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DiscoverSlider(
    @SerialName("id") val id: Int,
    @SerialName("type") val type: Int,
    @SerialName("order") val order: Int = 0,
    @SerialName("enabled") val enabled: Boolean = true,
    @SerialName("isBuiltIn") val isBuiltIn: Boolean = false,
    @SerialName("data") val data: String? = null,
    @SerialName("title") val title: String? = null,
) {
    object Type {
        const val RECENTLY_ADDED = 1
        const val RECENT_REQUESTS = 2
        const val PLEX_WATCHLIST = 3
        const val TRENDING = 4
        const val POPULAR_MOVIES = 5
        const val MOVIE_GENRES = 6
        const val UPCOMING_MOVIES = 7
        const val STUDIOS = 8
        const val POPULAR_TV = 9
        const val TV_GENRES = 10
        const val UPCOMING_TV = 11
        const val NETWORKS = 12
        const val TMDB_MOVIE_KEYWORD = 13
        const val TMDB_TV_KEYWORD = 14
        const val TMDB_MOVIE_GENRE = 15
        const val TMDB_TV_GENRE = 16
        const val TMDB_STUDIO = 17
        const val TMDB_NETWORK = 18
        const val TMDB_SEARCH = 19
        const val TMDB_MOVIE_STREAMING_SERVICES = 20
        const val TMDB_TV_STREAMING_SERVICES = 21
    }
}